package org.virgil.multifoliaworld.agent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.virgil.multifoliaworld.common.WorldConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 世界配置加载器
 * 负责读取和写入worlds.json配置文件
 */
public class WorldConfigLoader {
    
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = CONFIG_DIR + "/worlds.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static WorldConfig.Config currentConfig = null;
    
    /**
     * 加载世界配置
     * @return 世界配置列表，如果文件不存在则返回默认配置
     */
    public static List<WorldConfig.WorldEntry> loadConfig() {
        // 确保config目录存在
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
            System.out.println("[MultiFoliaWorld] 创建配置目录: " + CONFIG_DIR);
        }
        
        File configFile = new File(CONFIG_FILE);
        
        if (!configFile.exists()) {
            System.out.println("[MultiFoliaWorld] 配置文件不存在，创建默认配置...");
            return createDefaultConfig();
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            currentConfig = GSON.fromJson(reader, WorldConfig.Config.class);
            
            if (currentConfig == null || currentConfig.getWorlds() == null || currentConfig.getWorlds().isEmpty()) {
                System.out.println("[MultiFoliaWorld] 配置文件为空，使用默认配置");
                return createDefaultConfig();
            }
            
            System.out.println("[MultiFoliaWorld] 成功加载 " + currentConfig.getWorlds().size() + " 个世界配置");
            System.out.println("[MultiFoliaWorld] 启用的世界总数: " + currentConfig.getEnabledWorldCount());
            
            return currentConfig.getWorlds();
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 配置文件加载失败: " + e.getMessage());
            e.printStackTrace();
            return createDefaultConfig();
        }
    }
    
    /**
     * 获取实际需要加载的世界数量
     * 自动根据配置中启用的世界总数计算
     */
    public static int getMaxWorlds() {
        if (currentConfig == null) {
            return 3; // 默认值
        }
        
        // 自动计算启用的世界总数
        int enabledCount = currentConfig.getEnabledWorldCount();
        
        // 至少返回3（Folia默认值）
        return Math.max(3, enabledCount);
    }
    
    /**
     * 保存世界配置
     * @param worlds 世界配置列表
     */
    public static void saveConfig(List<WorldConfig.WorldEntry> worlds) {
        // 确保config目录存在
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        File configFile = new File(CONFIG_FILE);
        
        // 如果currentConfig为空，创建一个新的
        if (currentConfig == null) {
            currentConfig = new WorldConfig.Config();
        }
        
        currentConfig.setWorlds(worlds);
        
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(currentConfig, writer);
            System.out.println("[MultiFoliaWorld] 配置已保存: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[MultiFoliaWorld] 配置保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建默认配置
     * 包含主世界、下界和末地
     */
    private static List<WorldConfig.WorldEntry> createDefaultConfig() {
        currentConfig = new WorldConfig.Config();
        
        List<WorldConfig.WorldEntry> worlds = new ArrayList<>();
        
        // 主世界
        WorldConfig.WorldEntry mainWorld = new WorldConfig.WorldEntry();
        mainWorld.setName("world");
        mainWorld.setEnvironment("NORMAL");
        mainWorld.setEnabled(true);
        mainWorld.setCreateDims(false);  // 默认的世界已经有对应维度，不需要自动创建
        mainWorld.setStructures(true);
        worlds.add(mainWorld);
        
        // 下界
        WorldConfig.WorldEntry nether = new WorldConfig.WorldEntry();
        nether.setName("world_nether");
        nether.setEnvironment("NETHER");
        nether.setEnabled(true);
        nether.setStructures(true);
        worlds.add(nether);
        
        // 末地
        WorldConfig.WorldEntry end = new WorldConfig.WorldEntry();
        end.setName("world_the_end");
        end.setEnvironment("THE_END");
        end.setEnabled(true);
        end.setStructures(true);
        worlds.add(end);
        
        // 设置世界列表并保存
        currentConfig.setWorlds(worlds);
        saveConfig(worlds);
        
        System.out.println("[MultiFoliaWorld] 已创建默认配置文件: " + CONFIG_FILE);
        System.out.println("[MultiFoliaWorld] 包含世界: world, world_nether, world_the_end");
        System.out.println("[MultiFoliaWorld] 你可以编辑 " + CONFIG_FILE + " 来添加更多世界");
        System.out.println("[MultiFoliaWorld] 提示: ");
        System.out.println("[MultiFoliaWorld]   - 设置 enabled: false 可以临时禁用世界");
        System.out.println("[MultiFoliaWorld]   - 设置 createDims: true 可以自动创建配套维度（下界和末地）");
        System.out.println("[MultiFoliaWorld]   - 超过3个世界会自动启用突破模式");
        
        return worlds;
    }
    
    /**
     * 添加世界到配置
     */
    public static void addWorld(WorldConfig.WorldEntry world) {
        List<WorldConfig.WorldEntry> worlds = loadConfig();
        
        // 检查是否已存在
        boolean exists = worlds.stream()
                .anyMatch(w -> w.getName().equals(world.getName()));
        
        if (exists) {
            System.out.println("[MultiFoliaWorld] 世界已存在: " + world.getName());
            return;
        }
        
        worlds.add(world);
        saveConfig(worlds);
        System.out.println("[MultiFoliaWorld] 已添加世界: " + world.getName());
    }
    
    /**
     * 从配置中移除世界
     */
    public static void removeWorld(String worldName) {
        List<WorldConfig.WorldEntry> worlds = loadConfig();
        
        boolean removed = worlds.removeIf(w -> w.getName().equals(worldName));
        
        if (removed) {
            saveConfig(worlds);
            System.out.println("[MultiFoliaWorld] 已移除世界: " + worldName);
        } else {
            System.out.println("[MultiFoliaWorld] 未找到世界: " + worldName);
        }
    }
}

