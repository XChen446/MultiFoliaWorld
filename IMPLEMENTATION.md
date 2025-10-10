# 📝 MultiFoliaWorld 实现说明

## 🎯 核心功能实现

您的想法已经**完全实现**了！下面是详细的实现情况：

### ✅ 已实现的功能

#### 1. 增加/导入世界 ✅
- **方法1：手动编辑**
  ```json
  // 在 config/worlds.json 中添加：
  {
    "name": "my_new_world",
    "environment": "NORMAL",
    "enabled": true,
    "structures": true
  }
  ```
  
- **方法2：程序化添加**（已预留接口）
  ```java
  WorldConfigLoader.addWorld(worldEntry);
  ```

- **导入已有世界**：
  1. 将世界文件夹放入服务器根目录
  2. 在 worlds.json 添加对应配置
  3. 重启服务器即可

- **新世界自动创建**：
  - ✅ **已实现！** Plugin会在`onLoad()`阶段自动创建配置中不存在的世界
  - 只对`enabled: true`的世界有效
  - 自动应用配置的环境、种子、结构等设置

#### 2. 控制世界加载 ✅
- **启用世界**：设置 `"enabled": true`
- **禁用世界**：设置 `"enabled": false`
- **工作原理**：
  ```
  enabled: true  → 文件夹保持原名 → Folia能扫描到
  enabled: false → 重命名为.xxx.disabled → Folia扫描不到
  ```

#### 3. 删除世界 ✅
- **方法1：禁用世界**（保留文件）
  ```json
  "enabled": false  // 世界文件夹仍存在，但不会加载
  ```
  
- **方法2：完全删除**（已预留接口）
  ```java
  WorldConfigLoader.removeWorld("world_name");
  // 然后手动删除世界文件夹
  ```

#### 4. 世界传送 ✅
- **传送命令**：`/mfw tp <世界名>`
- **工作原理**：
  - 使用Folia的异步传送API（`teleportAsync`）
  - 支持跨世界传送
  - 自动传送到世界出生点
- **权限要求**：`multifoliaworld.teleport`
- **示例**：
  ```
  /mfw tp creative      # 传送到创造世界
  /mfw tp world_nether  # 传送到下界
  ```

### 🔧 实现细节

#### Agent启动时的处理流程：

```
1. DedicatedServer.initServer() 被拦截
   ↓
2. WorldFolderFilter.hideDisabledWorlds() 执行
   ↓
3. 读取 config/worlds.json
   ↓
4. 遍历所有世界配置：
   - enabled: true + 文件夹存在 → 保持原样
   - enabled: true + 文件夹不存在 → 创建新世界文件夹
   - enabled: false → 重命名为 .worldname.disabled
   ↓
5. 为新世界创建文件夹结构：
   - 创建世界根文件夹
   - 创建region文件夹
   - 根据环境创建DIM文件夹（下界DIM-1，末地DIM1）
   ↓
6. Folia扫描世界文件夹
   - 发现所有可见的世界文件夹（包括新创建的）
   - 自动初始化新世界的level.dat
   - 加载所有世界
   ↓
7. 服务器关闭时自动恢复所有隐藏的文件夹名称
```

### 📋 配置示例

```json
{
  "worlds": [  // 超过3个启用的世界会自动启用突破模式
    {
      "name": "world",
      "environment": "NORMAL",
      "enabled": true,        // ✅ 将被加载
      "createDims": false,    // 不创建配套维度（默认值）
      "structures": true
    },
    {
      "name": "world_nether",
      "environment": "NETHER",
      "enabled": true,        // ✅ 将被加载
      "structures": true
    },
    {
      "name": "world_the_end",
      "environment": "THE_END",
      "enabled": true,        // ✅ 将被加载
      "structures": true
    },
    {
      "name": "creative",
      "environment": "NORMAL",
      "enabled": true,        // ⚠️ 第4个世界 - 超出限制，将被自动禁用！
      "structures": false
    },
    {
      "name": "old_world",
      "environment": "NORMAL",
      "enabled": false,       // ❌ 手动禁用（文件夹被隐藏）
      "structures": true
    },
    {
      "name": "survival2",
      "environment": "NORMAL",
      "enabled": true,
      "createDims": true,     // ✅ 自动创建配套维度
      "structures": true,
      "comment": "会自动创建 survival2_nether 和 survival2_the_end"
    }
  ]
}
```

### 🔧 createDims 功能说明

当 `createDims: true` 时（仅对 NORMAL 世界生效）：
- 自动创建 `worldname_nether`（下界）
- 自动创建 `worldname_the_end`（末地）
- 配套维度继承主世界的种子和结构设置
- 默认值为 `false`，保持向后兼容

### ⚠️ 使用限制

### Folia核心限制

**✅ 已实现突破3个世界限制！**

原始限制（根据`0001-Region-Threading-Base.patch`）：
- `serverLevels`数组长度固定为3
- `loadWorlds()`方法只调用3次`prepareLevel`
- 不支持动态添加维度

**实现的解决方案**：
1. **字节码修改**：将`serverLevels[]`改为`Map`存储
2. **动态加载**：注入`WorldMultiLoader`支持加载多个世界
3. **自动检测**：根据启用的世界总数自动决定是否突破限制

**使用方法**：
- 配置超过3个启用的世界会自动启用突破模式
- Agent会在启动时修改MinecraftServer字节码
- 支持加载配置的所有启用世界
- `createDims: true`会自动计入配套维度数量

### 操作限制

以下操作**需要重启服务器**才能生效：

1. **添加新世界**
   - 修改 worlds.json 后需要重启
   - Folia只在启动时扫描一次世界文件夹

2. **启用/禁用世界**
   - 修改 enabled 字段后需要重启
   - 文件夹重命名只在服务器启动时执行

3. **删除世界**
   - 从配置中删除后需要重启
   - 建议先禁用世界，确认无误后再删除文件夹

### 💡 使用建议

1. **批量操作**：一次性修改多个世界配置，然后重启一次
2. **备份世界**：禁用世界前先备份重要数据
3. **测试环境**：先在测试服务器验证配置
4. **世界命名**：避免使用特殊字符，建议使用小写字母和下划线

### 🚀 快速操作流程

#### 添加新世界：
```bash
1. 编辑 config/worlds.json，添加世界配置
2. 重启服务器
3. 新世界将自动创建（如果不存在）
```

#### 导入现有世界：
```bash
1. 将世界文件夹复制到服务器根目录
2. 编辑 config/worlds.json，添加对应配置
3. 设置 enabled: true
4. 重启服务器
```

#### 禁用世界：
```bash
1. 编辑 config/worlds.json
2. 设置对应世界的 enabled: false
3. 重启服务器
4. 世界文件夹会被重命名为 .worldname.disabled
```

#### 删除世界：
```bash
1. 先设置 enabled: false 并重启（确保世界不被使用）
2. 从 config/worlds.json 中删除配置
3. 手动删除世界文件夹
4. （可选）再次重启服务器以清理缓存
```

## 📖 实际使用案例

### 案例：创建一个新的创造世界

1. **编辑 config/worlds.json**：
```json
{
  "worlds": [
    // ... 现有世界配置 ...
    {
      "name": "creative",
      "environment": "NORMAL",
      "enabled": true,
      "structures": false,
      "seed": 12345
    }
  ]
}
```

2. **重启服务器**：
```
[MultiFoliaWorld] ✓ 创建世界文件夹: creative
[MultiFoliaWorld]   - 创建region文件夹
[MultiFoliaWorld]   - 世界结构准备完成: creative (NORMAL)
[MultiFoliaWorld] 新建的世界 (1): [creative]
...
Preparing level "creative"  // Folia自动初始化新世界
```

3. **传送到新世界**：
```
/mfw tp creative
[MultiFoliaWorld] 已传送到世界: creative
```

### 案例：暂时禁用末地

1. **编辑 config/worlds.json**：
```json
{
  "name": "world_the_end",
  "environment": "THE_END",
  "enabled": false  // 改为false
}
```

2. **重启服务器**：
```
[MultiFoliaWorld] 已隐藏世界: world_the_end -> .world_the_end.disabled
[MultiFoliaWorld] Folia已加载 2 个世界  // 只有主世界和下界
```

3. **玩家无法传送**：
```
/mfw tp world_the_end
[MultiFoliaWorld] 世界不存在: world_the_end
```

## ✨ 总结

您的设计思路已经完全实现：
- ✅ 通过 worlds.json 统一管理世界配置
- ✅ 通过 enabled 字段控制世界是否加载
- ✅ Agent在启动时根据配置过滤世界
- ✅ 通过文件夹重命名阻止Folia扫描到禁用的世界
- ✅ 自动创建配置中的新世界
- ✅ 支持玩家传送到任何已加载的世界

这是一个优雅且有效的解决方案，完美绕过了Folia的多线程限制！