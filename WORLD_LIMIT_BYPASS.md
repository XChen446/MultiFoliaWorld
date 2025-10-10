# 🔧 突破Folia 3个世界限制的技术方案

## ✅ 实现状态

**已实现！** MultiFoliaWorld现在支持突破3个世界限制。

### 使用方法

1. 编辑 `config/worlds.json`，设置 `maxWorlds` > 3：
```json
{
  "maxWorlds": 5,  // 支持最多5个世界
  "worlds": [
    // ... 配置你的世界
  ]
}
```

2. 启动服务器，Agent会自动检测并启用突破模式
3. 查看日志确认：
```
[MultiFoliaWorld] 🚀 世界限制突破模式
[MultiFoliaWorld] 🚀 配置的最大世界数: 5
[MultiFoliaWorld] 🚀 将尝试通过字节码修改加载所有世界
```

## 📋 问题分析

根据`0001-Region-Threading-Base.patch`的分析，Folia硬编码了只能加载3个世界：

### 1. ServerLevel数组限制
```java
// MinecraftServer.java
public static final int OVERWORLD = 0;
public static final int NETHER = 1;
public static final int END = 2;
private final ServerLevel[] serverLevels = new ServerLevel[3];  // 硬编码数组长度
```

### 2. loadWorlds()方法限制
```java
protected void loadWorlds() {
    this.serverLevels[OVERWORLD] = prepareLevel(
        new WorldData(...), World.OVERWORLD, "world");
    this.serverLevels[NETHER] = prepareLevel(
        new WorldData(...), World.NETHER, "world_nether");
    this.serverLevels[END] = prepareLevel(
        new WorldData(...), World.END, "world_the_end");
}
```

### 3. DedicatedServer扫描限制
Folia删除了Paper的自动维度伙伴创建逻辑，每个扫描到的世界文件夹都被当作独立的主世界。

## 🚀 突破方案

### 方案1：动态数组扩展（推荐）

**实现步骤**：

1. **修改serverLevels为Map**
```java
// 通过字节码修改
private final Int2ObjectMap<ServerLevel> serverLevels = new Int2ObjectOpenHashMap<>();
```

2. **重写loadWorlds()方法**
```java
protected void loadWorlds() {
    Map<String, WorldEntry> worldConfigs = Agent$getWorldConfigs();
    int dimension = 0;
    
    for (WorldEntry config : worldConfigs.values()) {
        if (config.getEnabled() && dimension < MAX_WORLDS) {
            serverLevels.put(dimension, prepareLevel(...));
            dimension++;
        }
    }
}
```

3. **修改所有serverLevels访问**
```java
// 原代码：serverLevels[dimension]
// 修改为：serverLevels.get(dimension)
```

### 方案2：反射注入（备选）

**实现步骤**：

1. **在Agent中拦截MinecraftServer初始化**
2. **通过反射替换serverLevels字段**
3. **Hook loadWorlds()方法，注入自定义加载逻辑**

### 方案3：完全重写世界管理（复杂）

**实现步骤**：

1. **创建自定义WorldManager类**
2. **接管所有世界相关操作**
3. **绕过Folia的世界管理系统**

## 💻 具体实现代码

### 已实现的字节码修改

**WorldLimitTransformer.java** - 核心转换器：
```java
public class WorldLimitTransformer {
    
    public static byte[] transformMinecraftServer(byte[] classfileBuffer) throws Exception {
        // 1. 添加Map字段存储世界
        addWorldMapField(ctClass);
        
        // 2. 修改loadWorlds方法
        modifyLoadWorldsMethod(ctClass);
        
        // 3. 添加辅助方法
        addHelperMethods(ctClass);
        
        // 4. 替换所有serverLevels数组访问
        replaceArrayAccess(ctClass);
    }
    
    private static void addWorldMapField(CtClass ctClass) throws Exception {
        // 添加ConcurrentHashMap存储世界
        CtField worldMapField = CtField.make(
            "private final java.util.Map serverLevelMap = new java.util.concurrent.ConcurrentHashMap();", 
            ctClass
        );
        ctClass.addField(worldMapField);
    }
}
```

**WorldMultiLoader.java** - 世界加载器：
```java
public class WorldMultiLoader {
    
    public static void loadWorlds(Object minecraftServer) {
        // 加载所有配置的世界
        List<WorldEntry> worldConfigs = WorldConfigLoader.loadConfig();
        int maxWorlds = WorldConfigLoader.getMaxWorlds();
        
        for (WorldEntry config : worldConfigs) {
            if (config.getEnabled() && loadedCount < maxWorlds) {
                // 分配维度ID
                int dimensionId = assignDimensionId(config);
                
                // 创建并加载世界
                Object serverLevel = createWorld(minecraftServer, config, dimensionId);
                putLevelMethod.invoke(minecraftServer, dimensionId, serverLevel);
            }
        }
    }
}
```

### 世界加载器实现

```java
public class WorldLoader {
    
    public static void loadMultipleWorlds(Object server, Object properties) {
        try {
            // 获取世界配置
            Map<String, WorldEntry> configs = WorldBridge.getAllWorldConfigs();
            
            // 反射获取必要的方法
            Method prepareLevel = server.getClass().getDeclaredMethod(
                "prepareLevel", WorldData.class, ResourceKey.class, String.class
            );
            prepareLevel.setAccessible(true);
            
            // 获取serverLevels Map
            Field serverLevelsField = server.getClass().getDeclaredField("serverLevels");
            serverLevelsField.setAccessible(true);
            Map<Integer, Object> serverLevels = (Map) serverLevelsField.get(server);
            
            // 加载世界
            int dimension = 0;
            for (WorldEntry config : configs.values()) {
                if (!config.getEnabled()) continue;
                if (dimension >= getMaxWorlds()) break;
                
                // 创建世界数据
                Object worldData = createWorldData(config, dimension);
                Object resourceKey = createResourceKey(config.getEnvironment());
                
                // 调用prepareLevel
                Object serverLevel = prepareLevel.invoke(
                    server, worldData, resourceKey, config.getName()
                );
                
                // 存储到Map
                serverLevels.put(dimension, serverLevel);
                dimension++;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## ⚠️ 风险与注意事项

1. **兼容性风险**
   - 深度修改可能导致与其他插件不兼容
   - Folia更新可能破坏修改

2. **性能影响**
   - 更多世界意味着更多的tick线程
   - 可能影响服务器性能

3. **稳定性问题**
   - 未经充分测试的修改可能导致崩溃
   - 建议在测试环境充分验证

## 📊 测试计划

1. **基础功能测试**
   - 4个世界加载
   - 5个世界加载
   - 世界间传送

2. **性能测试**
   - TPS监控
   - 内存使用
   - CPU占用

3. **兼容性测试**
   - 与常用插件测试
   - 与Folia特性测试

## 🎯 实施建议

1. **分阶段实施**
   - 第一阶段：实现基础的4-5个世界支持
   - 第二阶段：优化性能和稳定性
   - 第三阶段：支持更多世界（如需要）

2. **保留降级方案**
   - 配置开关控制是否启用突破
   - 出现问题可快速回退到3个世界

3. **充分文档化**
   - 记录所有修改点
   - 提供详细的配置说明
   - 准备故障排除指南
