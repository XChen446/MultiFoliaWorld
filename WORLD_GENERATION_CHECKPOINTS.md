# 🔍 世界生成检查点指南

## 📋 快速自检表

| 检查点 | 日志关键词 | 常见错误 | 我们的解决方案 |
|--------|-----------|----------|----------------|
| ① `prepareLevel` 是否被调用 | `Preparing level "xxx"` | 没出现 = Agent过滤时把目录整个隐藏了 | ✅ 确保启用的世界文件夹可见 |
| ② 维度 ID 是否 ≥ 3 且没改数组 | `ArrayIndexOutOfBoundsException: 3` | 还没把 `ServerLevel[]` 换成 Map | ✅ 已替换为 `Map<Integer, ServerLevel>` |
| ③ WorldData 是否为 null | `NullPointerException: worldData` | 自定义世界没给 `WorldData` 实例 | ✅ 让 prepareLevel 自动创建 |
| ④ 是否报"区域线程冲突" | `IllegalStateException: off-owner thread` | 在非主线程提前 `loadChunk` | ✅ 所有操作在主线程执行 |

## 🚀 实现细节

### 1. 世界加载流程监控

#### 原版世界（前3个）
```
[MultiFoliaWorld] ======== 世界生成检查点 ========
[MultiFoliaWorld] ① 调用原始loadLevel方法（应触发prepareLevel）
[MultiFoliaWorld] ✓ 原版世界加载完成
[MultiFoliaWorld]   - 如果看到 'Preparing level "world"' 等日志 = 检查点①通过
[MultiFoliaWorld]   - 如果没有崩溃 = 检查点②③④通过
```

#### 额外世界（第4个及以后）
```
[MultiFoliaWorld] ======== 加载额外世界: creative ========
[MultiFoliaWorld] ② 分配维度ID: 3 (如果 >= 3 且未改数组会崩溃)
[MultiFoliaWorld] ① 调用prepareLevel创建世界...
[MultiFoliaWorld] ✓ prepareLevel成功返回ServerLevel实例
[MultiFoliaWorld] ✓ 世界已存储到serverLevelMap
[MultiFoliaWorld] ✅ 成功加载额外世界: creative
[MultiFoliaWorld]   - 检查点①②③④全部通过
```

### 2. 错误诊断

系统会自动捕获并诊断各种错误：

```java
catch (ArrayIndexOutOfBoundsException e) {
    System.err.println("[MultiFoliaWorld] ❌ ArrayIndexOutOfBoundsException - 检查点②失败！");
    System.err.println("[MultiFoliaWorld]   - ServerLevel[]数组未成功替换为Map");
}
catch (NullPointerException e) {
    if (e.getMessage().contains("worldData")) {
        System.err.println("[MultiFoliaWorld] ❌ NullPointerException: worldData - 检查点③失败！");
        System.err.println("[MultiFoliaWorld]   - WorldData创建失败");
    }
}
catch (IllegalStateException e) {
    if (e.getMessage().contains("off-owner thread")) {
        System.err.println("[MultiFoliaWorld] ❌ IllegalStateException - 检查点④失败！");
        System.err.println("[MultiFoliaWorld]   - 在非主线程尝试加载区块");
    }
}
```

### 3. 世界创建详细日志

```
[MultiFoliaWorld] 准备创建世界: creative
[MultiFoliaWorld]   - 方法: prepareLevel
[MultiFoliaWorld]   - 维度ID: 3
[MultiFoliaWorld]   - 环境: NORMAL
[MultiFoliaWorld]   - 方法参数数量: 5
[MultiFoliaWorld]   - 参数[0]: java.lang.String
[MultiFoliaWorld]     -> 设置世界名称: creative
[MultiFoliaWorld]   - 参数[1]: net.minecraft.resources.ResourceKey
[MultiFoliaWorld]     -> 设置ResourceKey: NORMAL
[MultiFoliaWorld]   - 参数[2]: net.minecraft.world.level.storage.WorldData
[MultiFoliaWorld]     -> WorldData设为null（让prepareLevel自动创建）
[MultiFoliaWorld] 调用prepareLevel...
[MultiFoliaWorld] ✓ prepareLevel成功完成
[MultiFoliaWorld] ✓ 世界文件夹已创建: /path/to/creative
[MultiFoliaWorld] ✓ level.dat已生成
```

## 🛡️ 保障措施

### 1. 文件夹预创建
- Agent 在服务器启动前创建所有启用的世界文件夹
- 包括基础结构：`region/`, `DIM-1/`, `DIM1/`
- 防止空文件夹问题

### 2. 字节码修改验证
- 将 `ServerLevel[]` 替换为 `Map<Integer, ServerLevel>`
- 提供 `getLevel()` 和 `putLevel()` 辅助方法
- 确保维度ID >= 3 的世界可以正常加载

### 3. WorldData 处理
- 让 Minecraft 的 `prepareLevel` 自动创建 WorldData
- 避免手动创建复杂的数据结构
- 确保世界配置正确应用

### 4. 线程安全
- 所有世界加载操作在主线程执行
- 避免 Folia 的区域线程冲突
- 确保世界正确初始化

## 📊 成功标志

当看到以下日志时，说明世界生成成功：

1. **原版世界**：
   ```
   Preparing level "world"
   Preparing level "world_nether"
   Preparing level "world_the_end"
   ```

2. **额外世界**：
   ```
   [MultiFoliaWorld] ✅ 成功加载额外世界: creative
   [MultiFoliaWorld]   - 检查点①②③④全部通过
   ```

3. **文件验证**：
   - 世界文件夹存在
   - `level.dat` 文件已生成
   - 区域文件夹已创建

## 🔧 故障排除

### 问题1：看不到 "Preparing level" 日志
**原因**：世界文件夹被隐藏或不存在
**解决**：检查 `worlds.json` 中的 `enabled` 设置

### 问题2：ArrayIndexOutOfBoundsException
**原因**：尝试加载第4个世界但数组未扩展
**解决**：确保 Agent 正确加载并执行了字节码修改

### 问题3：NullPointerException: worldData
**原因**：WorldData 创建失败
**解决**：让 prepareLevel 自动创建，不要手动构造

### 问题4：IllegalStateException: off-owner thread
**原因**：在错误的线程中操作世界
**解决**：确保所有世界操作在主线程执行

## 📝 测试配置示例

```json
{
  "worlds": [
    {
      "name": "world",
      "environment": "NORMAL",
      "enabled": true
    },
    {
      "name": "world_nether",
      "environment": "NETHER",
      "enabled": true
    },
    {
      "name": "world_the_end",
      "environment": "THE_END",
      "enabled": true
    },
    {
      "name": "creative",
      "environment": "NORMAL",
      "enabled": true,
      "comment": "第4个世界 - 测试突破限制"
    },
    {
      "name": "survival2",
      "environment": "NORMAL",
      "enabled": true,
      "createDims": true,
      "comment": "测试成对维度创建"
    }
  ]
}
```

这个配置会创建7个世界，完整测试所有检查点。
