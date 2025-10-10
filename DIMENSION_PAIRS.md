# 🌍 成对维度功能说明

## 📋 功能概述

MultiFoliaWorld 现在支持**成对维度**功能，可以为主世界自动创建配套的下界和末地维度。

## 🎯 使用方法

在 `worlds.json` 中配置世界时，设置 `createDims: true`：

```json
{
  "name": "survival2",
  "environment": "NORMAL",
  "enabled": true,
  "createDims": true,    // ✅ 启用成对维度
  "structures": true,
  "seed": 12345
}
```

## 🔧 工作原理

当 `createDims: true` 时（仅对 `NORMAL` 环境的世界生效）：

1. **自动创建下界**：`worldname_nether`
2. **自动创建末地**：`worldname_the_end`
3. **继承设置**：
   - 使用相同的种子（seed）
   - 使用相同的结构生成设置（structures）
   - 自动设置为启用状态

## 📝 示例

### 单独世界（默认）
```json
{
  "name": "creative",
  "environment": "NORMAL",
  "enabled": true,
  "createDims": false,   // 不创建配套维度（默认值）
  "structures": false
}
```
结果：只创建 `creative` 世界

### 成对维度
```json
{
  "name": "survival2",
  "environment": "NORMAL",
  "enabled": true,
  "createDims": true,    // 创建配套维度
  "structures": true,
  "seed": 12345
}
```
结果：自动创建3个世界
- `survival2` (主世界)
- `survival2_nether` (下界)
- `survival2_the_end` (末地)

## ⚠️ 注意事项

1. **仅对NORMAL世界生效**
   - 只有 `environment: "NORMAL"` 的世界支持此功能
   - 下界和末地世界忽略此选项

2. **世界数量限制**
   - 成对维度会计入世界总数
   - 如果启用的世界总数超过3个，会自动启用突破模式

3. **命名规则**
   - 下界：`worldname_nether`
   - 末地：`worldname_the_end`
   - 确保这些名称不与现有世界冲突

4. **不会覆盖现有世界**
   - 如果配套维度名称已存在，不会重复创建

## 🌟 使用场景

1. **多服务器环境**
   - 快速创建完整的游戏世界组
   - 每个世界组包含完整的三个维度

2. **测试环境**
   - 一键创建包含所有维度的测试世界
   - 便于测试跨维度功能

3. **活动世界**
   - 为特殊活动创建临时世界组
   - 活动结束后可以整组禁用或删除

## 🔄 自动世界数量检测

从最新版本开始，不再需要手动设置 `maxWorlds`：
- 系统会自动计算所有启用的世界数量
- 包括通过 `createDims` 创建的配套维度
- 超过3个世界会自动启用突破模式

## 💡 最佳实践

1. **规划世界结构**
   - 提前规划好需要哪些独立世界
   - 哪些世界需要完整的维度组

2. **合理命名**
   - 使用清晰的世界名称
   - 避免使用 `_nether` 或 `_the_end` 结尾

3. **批量操作**
   - 修改配置后一次性重启服务器
   - 避免频繁重启

## 📊 配置示例（完整）

```json
{
  "worlds": [
    {
      "name": "world",
      "environment": "NORMAL",
      "enabled": true,
      "createDims": false,
      "comment": "默认主世界，已有配套维度"
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
      "createDims": false,
      "comment": "创造模式世界，不需要其他维度"
    },
    {
      "name": "survival2",
      "environment": "NORMAL",
      "enabled": true,
      "createDims": true,
      "comment": "第二个生存世界，自动创建完整维度组"
    }
  ]
}
```

此配置将创建7个世界（超过3个，自动启用突破模式）：
- world, world_nether, world_the_end（默认世界组）
- creative（独立创造世界）
- survival2, survival2_nether, survival2_the_end（自动创建的世界组）
