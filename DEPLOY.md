# MultiFoliaWorld 部署指南

## 📦 部署步骤

### 1. 准备文件

构建项目后，你会得到以下文件：
- `modules/agent/build/libs/MultiFoliaWorldAgent.jar`
- `modules/plugin/build/libs/MultiFoliaWorldPlugin.jar`

### 2. 服务器目录结构

```
folia-server/
├── MultiFoliaWorldAgent.jar          # Agent JAR (放在根目录)
├── folia.jar                          # Folia服务器核心
├── config/
│   └── worlds.json                    # 世界配置文件
├── plugins/
│   └── MultiFoliaWorldPlugin.jar      # Plugin JAR (放在plugins目录)
├── world/                             # 主世界
├── world_nether/                      # 下界
└── world_the_end/                     # 末地
```

### 3. 详细部署流程

#### Step 1: 复制Agent JAR
```bash
cp modules/agent/build/libs/MultiFoliaWorldAgent.jar /path/to/server/
```

#### Step 2: 复制Plugin JAR
```bash
cp modules/plugin/build/libs/MultiFoliaWorldPlugin.jar /path/to/server/plugins/
```

#### Step 3: 创建配置文件

创建 `config/worlds.json`：

```bash
mkdir -p config
cp config/worlds.json.example config/worlds.json
```

或手动创建：

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
      "environment": "NETHER",
      "structures": true
    },
    {
      "name": "world_the_end",
      "environment": "THE_END",
      "structures": true
    }
  ]
}
```

#### Step 4: 修改启动脚本

**Windows (start.bat)**:
```batch
@echo off
java -Xms4G -Xmx4G ^
     -javaagent:MultiFoliaWorldAgent.jar ^
     -jar folia.jar nogui
pause
```

**Linux (start.sh)**:
```bash
#!/bin/bash
java -Xms4G -Xmx4G \
     -javaagent:MultiFoliaWorldAgent.jar \
     -jar folia.jar nogui
```

记得给脚本添加执行权限：
```bash
chmod +x start.sh
```

### 4. 启动服务器

```bash
# Windows
start.bat

# Linux
./start.sh
```

### 5. 验证安装

启动后检查控制台输出，应该看到：

```
========================================
  MultiFoliaWorld Agent v1.0-SNAPSHOT
  Folia Multi-World Support
========================================
[MultiFoliaWorld] Agent已加载，正在监听Folia类...
[MultiFoliaWorld] 将在服务器初始化时注入多世界加载逻辑
========================================
```

然后在服务器启动过程中：

```
[MultiFoliaWorld] 世界文件夹过滤器已激活
[MultiFoliaWorld] 执行时机：DedicatedServer.initServer()
[MultiFoliaWorld] 启用的世界 (2): [world, world_nether]
[MultiFoliaWorld] 禁用的世界 (1): [world_the_end]
[MultiFoliaWorld] 已隐藏世界: world_the_end -> .world_the_end.disabled
[MultiFoliaWorld] 世界过滤完成
[MultiFoliaWorld] - 已隐藏: 1 个世界
[MultiFoliaWorld] - 将加载: 2 个世界
[MultiFoliaWorld] 
[MultiFoliaWorld] 即将加载的世界：
[MultiFoliaWorld]   ✓ world (NORMAL)
[MultiFoliaWorld]   ✓ world_nether (NETHER)
```

插件加载时：

```
[MultiFoliaWorld] ========================================
[MultiFoliaWorld]   MultiFoliaWorld Plugin
[MultiFoliaWorld]   世界管理插件已加载
[MultiFoliaWorld] ========================================

[MultiFoliaWorld] 检查世界加载状态...
[MultiFoliaWorld] Folia已加载 2 个世界
[MultiFoliaWorld] ✓ Agent通信正常，版本: 1.0-SNAPSHOT
[MultiFoliaWorld] ✓ 世界过滤已生效，隐藏了 1 个世界
```

## 🎮 使用命令

进入服务器后，使用以下命令：

```
/mfw list              # 查看所有世界
/mfw info world        # 查看世界信息
/mfw tp world_nether   # 传送到下界
/mfw help              # 查看帮助
```

## ⚙️ 配置说明

### worlds.json 完整配置示例

```json
{
  "worlds": [
    {
      "name": "survival",
      "environment": "NORMAL",
      "enabled": true,
      "seed": 123456789,
      "structures": true,
      "generator": null
    },
    {
      "name": "creative",
      "environment": "NORMAL",
      "enabled": true,
      "seed": null,
      "structures": false
    },
    {
      "name": "nether",
      "environment": "NETHER",
      "enabled": true
    },
    {
      "name": "end",
      "environment": "THE_END",
      "enabled": true
    },
    {
      "name": "test_world",
      "environment": "NORMAL",
      "enabled": false,
      "comment": "临时禁用的测试世界"
    }
  ]
}
```

### 配置项说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| name | String | ✅ | - | 世界名称，必须唯一 |
| environment | String | ✅ | - | NORMAL, NETHER, THE_END |
| enabled | Boolean | ❌ | true | 是否启用该世界 |
| seed | Long | ❌ | 随机 | 世界种子 |
| structures | Boolean | ❌ | true | 是否生成结构（村庄、要塞等） |
| generator | String | ❌ | null | 自定义世界生成器 |

## 🔧 故障排除

### 问题1: Agent未加载

**症状**：插件启动时显示"错误: 未检测到Agent！"

**解决方案**：
1. 检查启动参数是否包含 `-javaagent:MultiFoliaWorldAgent.jar`
2. 确认Agent JAR文件在服务器根目录
3. 检查JAR文件名是否正确

### 问题2: 配置文件无法读取

**症状**：日志显示"配置文件加载失败"

**解决方案**：
1. 检查 `config/worlds.json` 是否存在
2. 验证JSON格式是否正确（可使用 jsonlint.com）
3. 检查文件权限

### 问题3: 世界未加载

**症状**：配置的世界没有出现在 `/mfw list` 中

**解决方案**：
1. 检查控制台日志，查看注入是否成功
2. 确认Folia版本兼容（需要1.21.8+）
3. 查看是否有错误堆栈信息

### 问题4: 传送失败

**症状**：使用 `/mfw tp` 命令后显示"传送失败"

**解决方案**：
1. 确认目标世界已正确加载
2. 检查目标世界的出生点区块是否已生成
3. 查看控制台是否有异步传送相关错误

## 📊 性能优化

### 内存分配建议

根据世界数量调整JVM内存：

| 世界数 | 推荐内存 |
|--------|----------|
| 1-3个 | 4-6GB |
| 4-6个 | 6-10GB |
| 7-10个 | 10-16GB |
| 10+个 | 16GB+ |

### 启动参数优化

```bash
java -Xms8G -Xmx8G \
     -XX:+UseG1GC \
     -XX:+ParallelRefProcEnabled \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UnlockExperimentalVMOptions \
     -XX:+DisableExplicitGC \
     -XX:+AlwaysPreTouch \
     -XX:G1NewSizePercent=30 \
     -XX:G1MaxNewSizePercent=40 \
     -XX:G1HeapRegionSize=8M \
     -XX:G1ReservePercent=20 \
     -XX:G1HeapWastePercent=5 \
     -XX:G1MixedGCCountTarget=4 \
     -XX:InitiatingHeapOccupancyPercent=15 \
     -XX:G1MixedGCLiveThresholdPercent=90 \
     -XX:G1RSetUpdatingPauseTimePercent=5 \
     -XX:SurvivorRatio=32 \
     -XX:+PerfDisableSharedMem \
     -XX:MaxTenuringThreshold=1 \
     -javaagent:MultiFoliaWorldAgent.jar \
     -jar folia.jar nogui
```

## 🔄 更新

### 更新Agent或Plugin

1. 停止服务器
2. 备份配置文件：`cp config/worlds.json config/worlds.json.backup`
3. 替换JAR文件
4. 重启服务器

### 迁移现有世界

如果你已有世界文件：

1. 将世界文件夹复制到服务器目录
2. 在 `config/worlds.json` 中添加对应配置
3. 重启服务器

## 📝 注意事项

1. **首次启动**：首次启动时会自动创建默认配置，包含world、world_nether、world_the_end
2. **配置修改**：修改配置后需要重启服务器才能生效
3. **备份**：定期备份世界文件和配置文件
4. **兼容性**：目前仅支持Folia 1.21.8+
5. **世界删除**：删除世界需要先从配置文件中移除，然后手动删除世界文件夹

## 🆘 获取帮助

如遇到问题，请提供以下信息：

1. Folia版本
2. Java版本
3. MultiFoliaWorld版本
4. 完整的启动日志
5. `config/worlds.json` 内容
6. 错误信息或堆栈跟踪

---

**祝你使用愉快！** 🎉

