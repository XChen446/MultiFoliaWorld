# MultiFoliaWorld 故障排除

## 常见问题

### 1. "Agent未激活"警告

**症状**：
```
[MultiFoliaWorld] Agent激活超时（等待了15秒）
[MultiFoliaWorld] 这通常是正常的，Agent会在稍后的MinecraftServer初始化时激活
```

**原因**：
- Plugin.onLoad()在服务器初始化早期执行
- Agent在MinecraftServer.initServer()时才激活
- 中间有约10秒的延迟

**解决方案**：
- 这是**正常现象**，不影响功能
- Agent会在稍后自动激活
- 可以忽略此警告

### 2. 世界没有被过滤

**症状**：
```
[MultiFoliaWorld] 禁用的世界 (0): []
[MultiFoliaWorld] 世界过滤完成，已隐藏 0 个世界
```

**可能原因**：
1. 所有世界都设置为`enabled: true`
2. `config/worlds.json`配置文件不存在或格式错误
3. 世界文件夹不存在

**检查步骤**：
1. 确认`config/worlds.json`存在
2. 检查配置格式：
   ```json
   {
     "worlds": [
       {
         "name": "world",
         "enabled": true
       },
       {
         "name": "test_world",
         "enabled": false  // 这个世界会被隐藏
       }
     ]
   }
   ```
3. 确保禁用的世界文件夹存在且包含`level.dat`

### 3. 时序问题详解

**正常的启动顺序**：
```
1. JavaAgent加载 (服务器启动时)
   ↓
2. DedicatedServer.initServer() (约10秒后)
   └─ WorldFolderFilter执行（方案B）
   ↓
3. Plugin.onLoad() (稍后)
   └─ 等待Agent激活
   ↓
4. MinecraftServer.loadLevel()
   └─ WorldLoadHook执行
   └─ Agent标记为激活
   ↓
5. Plugin.onEnable()
   └─ 显示最终状态
```

### 4. 验证过滤是否生效

**方法1：查看日志**
```
[MultiFoliaWorld] 已隐藏世界: world_test -> .world_test.disabled
```

**方法2：检查文件系统**
- 启动前：`/world_test/`
- 启动后：`/.world_test.disabled/`
- 关闭后：`/world_test/`（自动恢复）

**方法3：使用命令**
```
/mfw list
```
禁用的世界不应该出现在列表中

### 5. 调试建议

1. **启用详细日志**：
   - 查看完整的启动日志
   - 特别注意`[STDOUT]`开头的Agent日志

2. **测试配置**：
   ```json
   {
     "worlds": [
       { "name": "world", "enabled": true },
       { "name": "world_nether", "enabled": true },
       { "name": "world_the_end", "enabled": false }
     ]
   }
   ```
   重启后，末地应该不会加载

3. **检查Agent加载**：
   - 确保启动参数：`-javaagent:MultiFoliaWorldAgent.jar`
   - Agent JAR必须在服务器根目录

### 6. 已知限制

1. **不支持热加载**：修改配置需要重启服务器
2. **不支持动态创建/删除世界**：Folia的多线程架构限制
3. **文件夹必须存在**：只能隐藏已存在的世界文件夹
