# MultiFoliaWorld

为Folia服务器提供多世界管理的JavaAgent解决方案

## 🎯 项目背景

Folia采用区域化多线程架构，世界必须在插件加载前完成加载。传统的多世界插件无法在Folia上工作，因为：

1. **加载时机问题**：Folia在插件加载前完成世界初始化
2. **线程安全限制**：区域化线程池初始化后无法安全添加新世界
3. **API不可用**：Folia的世界加载/卸载API被标记为broken
4. **文件扫描机制**：Folia通过扫描根目录中包含`level.dat`的文件夹来发现世界

本项目使用**JavaAgent字节码注入**技术，拦截`Files.list()`方法，在Folia扫描世界时进行**文件系统级过滤**。

## 📋 技术架构

### 模块结构

```
MultiFoliaWorld/
├── modules/
│   ├── agent/          # JavaAgent - 字节码注入核心
│   ├── plugin/         # Bukkit插件 - 命令和用户交互
│   └── common/         # 共享模块 - 配置模型和通信桥
```

### 核心技术

| 技术 | 用途 |
|------|------|
| **Java Instrumentation** | Agent入口和类加载拦截 |
| **Javassist** | 字节码操作和方法注入 |
| **Gson** | JSON配置解析 |
| **Folia API** | 服务器接口和命令系统 |

### 工作原理（优化版）

```
JVM启动 (-javaagent:MultiFoliaWorldAgent.jar)
   ↓
JavaAgent注册并拦截关键类
   ↓
DedicatedServer.initServer()
   └─ WorldFolderFilter执行
       ├─ 读取config/worlds.json
       ├─ 重命名禁用的世界文件夹
       ├─ 输出即将加载的世界列表
       └─ 注册关闭钩子（自动恢复）
   ↓
Folia扫描世界文件夹
   └─ 只能看到未被隐藏的文件夹
   ↓
Folia加载可见的世界
   ↓
Plugin加载（无需等待Agent）
   ├─ onLoad(): 简单初始化
   └─ onEnable(): 注册命令，显示状态
   ↓
服务器启动完成
```

**关键优化（基于Folia源码分析）：**
- ✅ Folia在`DedicatedServer.initServer()`时扫描根目录
- ✅ 只要文件夹包含`level.dat`就被认为是世界
- ✅ Agent在扫描前临时重命名禁用的世界文件夹
- ✅ 配置中`enabled: false`的世界对Folia"不可见"
- ✅ 服务器关闭时自动恢复文件夹名称

## 🚀 快速开始

### 环境要求

- Java 21+
- Folia 1.21.8+
- Gradle 8.x

### 构建项目

```bash
# Windows
.\gradlew buildAll

# Linux/Mac
./gradlew buildAll
```

构建产物：
- `modules/agent/build/libs/MultiFoliaWorldAgent.jar`
- `modules/plugin/build/libs/MultiFoliaWorldPlugin.jar`

### 部署

1. **放置Agent JAR**
   ```
   将 MultiFoliaWorldAgent.jar 复制到服务器根目录
   ```

2. **放置Plugin JAR**
   ```
   将 MultiFoliaWorldPlugin.jar 复制到 plugins/ 目录
   ```

3. **创建配置文件**
   
   在服务器根目录创建 `config/worlds.json`：
   ```json
   {
     "worlds": [
       {
         "name": "world",
         "environment": "NORMAL",
         "structures": true
       },
       {
         "name": "world_nether",
         "environment": "NETHER"
       },
       {
         "name": "world_the_end",
         "environment": "THE_END"
       }
     ]
   }
   ```
   
   注意：首次启动时，如果`config/worlds.json`不存在，Agent会自动创建默认配置。
   
   你也可以复制示例配置文件：
   ```bash
   cp config/worlds.json.example config/worlds.json
   ```

4. **修改启动脚本**
   
   添加 `-javaagent` 参数：
   ```bash
   java -javaagent:MultiFoliaWorldAgent.jar -Xms4G -Xmx4G -jar folia.jar
   ```

## 📖 配置说明

### config/worlds.json 配置项

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | ✅ | 世界名称 |
| `environment` | String | ✅ | 环境类型：NORMAL, NETHER, THE_END |
| `enabled` | Boolean | ❌ | 是否启用该世界（默认true） |
| `generator` | String | ❌ | 自定义世界生成器 |
| `seed` | Long | ❌ | 世界种子 |
| `structures` | Boolean | ❌ | 是否生成结构（默认true） |

### 配置示例

```json
{
  "worlds": [
    {
      "name": "survival",
  "environment": "NORMAL",
      "enabled": true,
  "seed": 12345,
      "structures": true
    },
    {
      "name": "creative",
      "environment": "NORMAL",
  "enabled": true,
      "seed": 67890,
      "structures": false
    },
    {
      "name": "nether",
      "environment": "NETHER",
      "enabled": true
    },
    {
      "name": "old_world",
      "environment": "NORMAL",
      "enabled": false,
      "comment": "临时禁用的世界"
    }
  ]
}
```

## 🎮 命令使用

| 命令 | 说明 | 权限 |
|------|------|------|
| `/mfw list` | 列出所有世界 | `multifoliaworld.use` |
| `/mfw info <世界>` | 查看世界信息 | `multifoliaworld.use` |
| `/mfw tp <世界>` | 传送到指定世界 | `multifoliaworld.tp` |
| `/mfw reload` | 重载配置 | `multifoliaworld.admin` |
| `/mfw help` | 帮助信息 | `multifoliaworld.use` |

## 🔍 技术细节

### Folia架构分析

根据[Folia官方文档](https://github.com/PaperMC/Folia)：

1. **区域化多线程**：每个区域独立拥有chunk/entity/poi数据
2. **并行执行**：区域并行tick，不是并发
3. **Broken API**：世界加载/卸载API明确标记为broken
4. **RegionScheduler**：替代传统的BukkitScheduler

### Minecraft区块加载模型

基于Minecraft的原生架构：

```
World → ChunkProvider → ChunkGenerator → id2ChunkMap → ChunkIOExecutor
  ↓           ↓              ↓                ↓              ↓
世界对象   区块提供器    区块生成器      区块缓存      异步IO执行
```

我们的插件充分利用这个流程，在`Plugin.onLoad()`阶段使用`WorldCreator` API，触发完整的世界初始化流程

### 注入点分析

**目标类**：`net.minecraft.server.MinecraftServer`

**注入方法**：
- `loadLevel()` - 世界加载入口
- `initServer()` - 服务器初始化

**注入策略**：
```java
// 在方法开始处注入
method.insertBefore(
    "WorldLoadHook.onServerInit(this);"
);
```

### ClassLoader隔离

- **Agent ClassLoader**：Bootstrap ClassLoader
- **Plugin ClassLoader**：Plugin ClassLoader
- **通信桥**：WorldBridge（静态共享类）

## ⚠️ 已知限制

1. **运行时创建世界**：目前仅支持启动时预加载，运行时创建世界需要进一步研究
2. **世界卸载**：Folia暂不支持安全的世界卸载
3. **传送限制**：必须使用 `teleportAsync()`，同步传送不可用
4. **配置热重载**：需要重启服务器生效

## 🛠️ 开发

### 项目结构

```
modules/
├── agent/
│   └── src/main/java/org/virgil/multifoliaworld/agent/
│       ├── MultiFoliaWorldAgent.java       # Agent主类
│       ├── WorldConfigLoader.java          # 配置加载器
│       └── hook/
│           ├── WorldLoadHook.java          # 世界加载钩子
│           └── WorldCreateHook.java        # 世界创建钩子
├── plugin/
│   └── src/main/java/org/virgil/multifoliaworld/plugin/
│       ├── MultiFoliaWorldPlugin.java      # 插件主类
│       └── command/
│           └── WorldCommand.java           # 命令处理器
└── common/
    └── src/main/java/org/virgil/multifoliaworld/common/
        ├── WorldConfig.java                # 配置模型
        └── WorldBridge.java                # 通信桥
```

### 构建命令

```bash
# 构建所有模块
./gradlew buildAll

# 清理构建
./gradlew cleanAll

# 只构建Agent
./gradlew :agent:shadowJar

# 只构建Plugin
./gradlew :plugin:shadowJar
```

## 📖 深入阅读

- [ARCHITECTURE.md](ARCHITECTURE.md) - 技术架构详解
- [OPTIMIZATION.md](OPTIMIZATION.md) - 基于Minecraft区块加载模型的优化方案
- [DEPLOY.md](DEPLOY.md) - 详细部署指南

## 📝 许可证

GPL-3.0 License

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📚 参考资料

- [Folia官方仓库](https://github.com/PaperMC/Folia)
- [Folia区域逻辑文档](https://github.com/PaperMC/Folia/blob/ver/1.21.8/REGION_LOGIC.md)
- [Java Instrumentation API](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/Instrumentation.html)
- [Javassist文档](https://www.javassist.org/)

---

**注意**：本项目仍在开发中，部分功能可能需要根据Folia的实际实现进行调整。建议在测试环境中验证后再用于生产环境。
