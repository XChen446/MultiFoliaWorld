# MultiFoliaWorld 技术架构文档

## 核心问题分析

### Folia的世界加载机制

根据对[Folia 1.21.8源码](https://github.com/PaperMC/Folia/tree/ver/1.21.8)的分析：

#### 1. 加载时序问题

```
Folia启动流程:
1. MinecraftServer初始化
2. 加载主世界 (server.properties中的level-name)
3. 初始化RegionizedServer (区域线程池)
4. 加载插件
5. 插件onEnable()
```

**问题**：步骤3完成后，区域线程池已经初始化，无法安全添加新世界。

#### 2. 区域化多线程架构

```java
// Folia的区域所有权模型
Region {
    - ownedChunks: Set<Chunk>
    - ownedEntities: Set<Entity>
    - tickThread: Thread
}
```

每个区域独立拥有数据，不共享状态。新世界的加入意味着：
- 创建新的区域集合
- 分配新的线程池资源
- 可能导致线程同步问题

#### 3. API破损状态

Folia README明确指出：
```
Current broken API:
- World loading/unloading
- Entity#teleport (永久移除，必须用teleportAsync)
- Scoreboard API
```

## JavaAgent注入方案

### 为什么选择JavaAgent？

| 方案 | 优点 | 缺点 | 可行性 |
|------|------|------|--------|
| **传统插件** | 开发简单 | 加载太晚 | ❌ 不可行 |
| **Mixin注入** | 强大灵活 | 需要额外框架 | ⚠️ 复杂度高 |
| **JavaAgent** | 时机早 | 需要启动参数 | ✅ **推荐** |
| **修改核心** | 完全控制 | 维护困难 | ❌ 不推荐 |

### Agent注入时机

```
JVM启动
    ↓
加载bootstrap类
    ↓
[Agent.premain()] ← Agent介入点
    ↓
注册ClassFileTransformer
    ↓
拦截MinecraftServer类加载
    ↓
修改字节码
    ↓
MinecraftServer.initServer()
    ↓
[WorldLoadHook触发] ← 我们的逻辑
    ↓
加载配置的所有世界
    ↓
继续正常启动流程
```

### 字节码注入细节

#### 目标类：MinecraftServer

```java
// 原始代码（伪代码）
public class MinecraftServer {
    protected void initServer() {
        // 1. 初始化数据目录
        // 2. 加载主世界
        this.loadLevel();
        // 3. 启动网络监听
        // 4. 初始化插件
    }
    
    protected void loadLevel() {
        // 加载 server.properties 中配置的世界
        String levelName = this.getProperties().levelName;
        this.createWorld(levelName);
    }
}
```

#### 注入后的代码

```java
// 注入后（Javassist生成）
public class MinecraftServer {
    protected void initServer() {
        // [注入] 在方法开始处调用我们的钩子
        WorldLoadHook.onServerInit(this);
        
        // 原始代码继续执行
        this.loadLevel();
        // ...
    }
}
```

#### Javassist实现

```java
CtClass ctClass = pool.get("net.minecraft.server.MinecraftServer");
CtMethod method = ctClass.getDeclaredMethod("initServer");

// 在方法开始处插入代码
method.insertBefore(
    "org.virgil.multifoliaworld.agent.hook.WorldLoadHook.onServerInit(this);"
);

return ctClass.toBytecode();
```

## 世界加载策略

### 策略1：配置文件注入（当前实现）

```
config/worlds.json → Agent读取 → 在initServer时加载
```

**优点**：
- 简单直接
- 配置集中管理

**缺点**：
- 需要重启生效
- 无法动态创建世界

### 策略2：反射调用Bukkit API

```java
// 在WorldLoadHook中
Object bukkitServer = minecraftServer.getServer();
Method createWorldMethod = bukkitServer.getClass()
    .getMethod("createWorld", WorldCreator.class);

for (WorldConfig config : configs) {
    WorldCreator creator = new WorldCreator(config.getName());
    creator.environment(config.getEnvironment());
    createWorldMethod.invoke(bukkitServer, creator);
}
```

**挑战**：
- Bukkit类可能尚未加载
- WorldCreator可能不在ClassPath中
- 需要处理ClassLoader隔离

### 策略3：修改Bukkit配置文件

```java
// 修改 bukkit.yml
worlds:
  world:
    generator: vanilla
  world_nether:
    generator: vanilla
  world_the_end:
    generator: vanilla
  custom_world:
    generator: vanilla
```

**优点**：
- 利用Folia原生加载机制
- 无需额外注入逻辑

**缺点**：
- 修改配置文件需要文件IO
- 可能与用户配置冲突

## ClassLoader通信桥

### 问题：ClassLoader隔离

```
Bootstrap ClassLoader (Agent)
    ↑
    | 无法直接通信
    ↓
Plugin ClassLoader (Plugin)
```

### 解决方案：共享静态类

```java
// common模块中的WorldBridge
public class WorldBridge {
    // 使用static确保JVM级别共享
    private static final Map<String, Object> WORLD_REGISTRY 
        = new ConcurrentHashMap<>();
    
    // Agent调用
    public static void registerWorld(String name, Object world) {
        WORLD_REGISTRY.put(name, world);
    }
    
    // Plugin调用
    public static Map<String, Object> getAllWorlds() {
        return WORLD_REGISTRY;
    }
}
```

### Shadow插件的作用

```gradle
// agent/build.gradle
shadowJar {
    // 将common模块打包到Agent JAR
    // 包名: org.virgil.multifoliaworld.common.WorldBridge
}

// plugin/build.gradle
shadowJar {
    // 将common模块打包到Plugin JAR
    // 包名: org.virgil.multifoliaworld.common.WorldBridge
}
```

**关键**：两个JAR中都包含相同包名的WorldBridge类，JVM会确保它们引用同一个静态字段。

## Folia兼容性考虑

### RegionScheduler的使用

传统Bukkit：
```java
// 不再可用
Bukkit.getScheduler().runTask(plugin, () -> {
    // 任务
});
```

Folia方案：
```java
// 在特定区域执行
Bukkit.getRegionScheduler().execute(plugin, location, () -> {
    // 任务会在拥有该location的区域线程上执行
});

// 在实体所在区域执行
entity.getScheduler().run(plugin, task -> {
    // 任务会在拥有该实体的区域线程上执行
}, null);
```

### 异步传送

```java
// ❌ 不可用
player.teleport(location);

// ✅ 必须使用
player.teleportAsync(location).thenAccept(result -> {
    if (result) {
        player.sendMessage("传送成功");
    }
});
```

### 线程安全

```java
// ❌ 危险：跨区域访问
World world1 = getWorld("world");
World world2 = getWorld("nether");
world1.getBlockAt(0, 0, 0).setType(Material.STONE); // 当前区域
world2.getBlockAt(0, 0, 0).setType(Material.STONE); // 跨区域！

// ✅ 安全：使用RegionScheduler
Bukkit.getRegionScheduler().execute(plugin, 
    world2.getBlockAt(0, 0, 0).getLocation(), 
    () -> {
        world2.getBlockAt(0, 0, 0).setType(Material.STONE);
    }
);
```

## 性能考虑

### Agent开销

| 操作 | 时机 | 开销 |
|------|------|------|
| ClassFileTransformer注册 | 启动时 | 微小 |
| 字节码修改 | 类加载时 | 一次性 |
| WorldLoadHook执行 | 服务器初始化 | 取决于世界数量 |
| WorldBridge访问 | 运行时 | 几乎为0（静态字段） |

### 优化建议

1. **延迟加载**：只在需要时加载世界
2. **批量操作**：一次性加载所有配置的世界
3. **缓存配置**：避免重复读取JSON

## 未来改进方向

### 1. 运行时世界创建

**挑战**：Folia的区域线程池已初始化

**可能方案**：
```java
// 需要研究Folia内部API
// 可能需要：
1. 暂停区域tick
2. 创建新世界
3. 为新世界分配区域
4. 恢复tick
```

### 2. 世界卸载

**挑战**：区域可能持有该世界的引用

**可能方案**：
```java
1. 传送所有玩家离开
2. 等待所有区域释放chunk引用
3. 卸载世界
4. 回收资源
```

### 3. 跨世界传送优化

**当前问题**：传送可能需要等待目标区域tick

**优化方向**：
```java
// 预加载目标区块
Location target = ...;
target.getWorld().getChunkAtAsync(target).thenAccept(chunk -> {
    player.teleportAsync(target);
});
```

## 调试建议

### 启用详细日志

```bash
java -javaagent:MultiFoliaWorldAgent.jar \
     -Dmultifoliaworld.debug=true \
     -verbose:class \
     -jar folia.jar
```

### 验证Agent加载

```java
// 在Plugin中检查
if (!WorldBridge.isAgentActive()) {
    getLogger().severe("Agent未激活！");
}
```

### 反编译验证注入

```bash
# 使用CFR反编译器
java -jar cfr.jar MinecraftServer.class

# 检查是否包含
WorldLoadHook.onServerInit(this);
```

## 参考资料

- [Folia区域逻辑](https://github.com/PaperMC/Folia/blob/ver/1.21.8/REGION_LOGIC.md)
- [Java Instrumentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/package-summary.html)
- [Javassist Tutorial](https://www.javassist.org/tutorial/tutorial.html)
- [Paper API文档](https://jd.papermc.io/folia/1.21/)

---

**维护者注意**：此架构基于Folia 1.21.8，未来版本可能需要调整。

