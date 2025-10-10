package org.virgil.multifoliaworld.plugin;

import java.io.File;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;
import org.virgil.multifoliaworld.common.WorldBridge;
import org.virgil.multifoliaworld.common.WorldConfig;
import org.virgil.multifoliaworld.plugin.command.WorldCommand;

/**
 * MultiFoliaWorld插件主类 - 优化版
 * 
 * 新策略：
 * 1. Agent阶段：注册世界配置，准备环境
 * 2. Plugin阶段：在onLoad()中创建世界（在服务器完全初始化前）
 * 3. 使用Bukkit高层API，保证兼容性
 * 
 * 工作流程：
 * JVM启动 → Agent注入 → 读取配置 → MinecraftServer初始化 → 
 * Plugin.onLoad() → 创建世界 → 区域线程池初始化 → Plugin.onEnable() → 提供命令
 */
public class MultiFoliaWorldPlugin extends JavaPlugin {
    
    private static MultiFoliaWorldPlugin instance;
    
    /**
     * 插件加载阶段（在onEnable之前）
     * 这是创建世界的最佳时机
     */
    @Override
    public void onLoad() {
        instance = this;
        
        getLogger().info("========================================");
        getLogger().info("  MultiFoliaWorld Plugin");
        getLogger().info("  世界管理插件已加载");
        getLogger().info("========================================");
        
        // 注意：不能在这里创建世界！
        // 因为WorldBridge的配置要在MinecraftServer.initServer()时才注册
        // 而那发生在Plugin.onLoad()之后
    }
    
    /**
     * 从配置创建世界
     * 
     * 这个方法主要用于：
     * 1. 创建配置中有但磁盘上还不存在的新世界
     * 2. 确保所有配置的世界都被加载
     */
    private void createWorldsFromConfig() {
        Map<String, WorldConfig.WorldEntry> configs = WorldBridge.getAllWorldConfigs();
        
        if (configs.isEmpty()) {
            getLogger().info("没有配置的世界");
            return;
        }
        
        // 只处理enabled=true的世界
        long enabledCount = configs.values().stream()
                .filter(WorldConfig.WorldEntry::getEnabled)
                .count();
        
        if (enabledCount == 0) {
            getLogger().info("没有启用的世界，跳过创建");
            return;
        }
        
        getLogger().info("========================================");
        getLogger().info("开始加载/创建 " + enabledCount + " 个启用的世界...");
        getLogger().info("========================================");
        
        for (Map.Entry<String, WorldConfig.WorldEntry> entry : configs.entrySet()) {
            WorldConfig.WorldEntry config = entry.getValue();
            
            // 跳过禁用的世界
            if (!config.getEnabled()) {
                getLogger().info("× 跳过禁用的世界: " + config.getName());
                continue;
            }
            
            try {
                // 检查世界是否已被Bukkit加载
                World existingWorld = getServer().getWorld(config.getName());
                if (existingWorld != null) {
                    getLogger().info("✓ 世界已加载: " + config.getName());
                    WorldBridge.registerWorld(config.getName(), existingWorld);
                    continue;
                }
                
                // 检查世界文件夹是否存在
                File worldFolder = new File(getServer().getWorldContainer(), config.getName());
                boolean worldExists = worldFolder.exists() && worldFolder.isDirectory();
                
                if (worldExists) {
                    getLogger().info("→ 正在加载现有世界: " + config.getName());
                } else {
                    getLogger().info("→ 正在创建新世界: " + config.getName());
                }
                
                // 创建WorldCreator
                WorldCreator creator = new WorldCreator(config.getName());
                
                // 设置环境
                World.Environment environment = World.Environment.valueOf(config.getEnvironment());
                creator.environment(environment);
                
                // 设置种子
                if (config.getSeed() != null) {
                    creator.seed(config.getSeed());
                }
                
                // 设置结构生成
                creator.generateStructures(config.getStructures());
                
                // 设置自定义生成器（如果有）
                if (config.getGenerator() != null && !config.getGenerator().isEmpty()) {
                    creator.generator(config.getGenerator());
                }
                
                // 创建世界
                getLogger().info("正在创建世界: " + config.getName() + 
                               " (环境: " + config.getEnvironment() + 
                               ", 种子: " + (config.getSeed() != null ? config.getSeed() : "随机") + 
                               ", 结构: " + config.getStructures() + ")");
                
                World world = creator.createWorld();
                
                if (world != null) {
                    if (worldExists) {
                        getLogger().info("✓ 世界加载成功: " + config.getName());
                    } else {
                        getLogger().info("✓ 世界创建成功: " + config.getName());
                    }
                    WorldBridge.registerWorld(config.getName(), world);
                } else {
                    getLogger().severe("✗ 世界加载/创建失败: " + config.getName());
                }
                
            } catch (Exception e) {
                getLogger().severe("加载/创建世界失败 (" + config.getName() + "): " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        getLogger().info("========================================");
        getLogger().info("世界加载完成！已加载 " + WorldBridge.getWorldCount() + " 个世界");
        getLogger().info("========================================");
    }
    
    @Override
    public void onEnable() {
        getLogger().info("========================================");
        getLogger().info("  MultiFoliaWorld Plugin");
        getLogger().info("  Folia Multi-World Management");
        getLogger().info("========================================");
        
        // 检查Agent状态
        if (!WorldBridge.isAgentActive()) {
            getLogger().warning("Agent未激活，某些功能可能不可用");
        } else {
            getLogger().info("✓ Agent通信正常，版本: " + WorldBridge.getAgentVersion());
            
            // 注意：世界创建现在由Agent在Folia扫描前完成
            // Plugin只负责验证和显示状态
        }
        
        // 注册命令
        registerCommands();
        
        // 输出世界信息
        logWorldInfo();
        
        getLogger().info("========================================");
        getLogger().info("  插件加载完成！");
        getLogger().info("  使用 /mfw help 查看命令");
        getLogger().info("========================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("MultiFoliaWorld插件已卸载");
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        WorldCommand worldCommand = new WorldCommand(this);
        getCommand("multifoliaworld").setExecutor(worldCommand);
        getCommand("multifoliaworld").setTabCompleter(worldCommand);
        
        // 别名
        getCommand("mfw").setExecutor(worldCommand);
        getCommand("mfw").setTabCompleter(worldCommand);
        
        getLogger().info("命令已注册: /multifoliaworld (别名: /mfw)");
    }
    
    /**
     * 输出世界信息
     */
    private void logWorldInfo() {
        getLogger().info("========================================");
        getLogger().info("最终加载的世界:");
        getServer().getWorlds().forEach(world -> {
            getLogger().info("  ✓ " + world.getName() + " (" + world.getEnvironment() + 
                           ", 玩家数: " + world.getPlayers().size() + 
                           ", 已加载区块: " + world.getLoadedChunks().length + ")");
        });
        getLogger().info("共 " + getServer().getWorlds().size() + " 个世界");
        
        // 如果超过3个世界，说明突破成功
        if (getServer().getWorlds().size() > 3) {
            getLogger().info("");
            getLogger().info("🎉 世界限制突破成功！");
            getLogger().info("🎉 Folia原本只支持3个世界，现已加载 " + getServer().getWorlds().size() + " 个世界");
            getLogger().info("🎉 这是实验性功能，请注意服务器稳定性");
        }
        
        getLogger().info("========================================");
    }
    
    /**
     * 获取插件实例
     */
    public static MultiFoliaWorldPlugin getInstance() {
        return instance;
    }
}
