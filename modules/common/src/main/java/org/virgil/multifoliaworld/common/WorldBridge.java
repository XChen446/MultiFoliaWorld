package org.virgil.multifoliaworld.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent和Plugin之间的通信桥
 * 
 * 由于Agent运行在Bootstrap ClassLoader，Plugin运行在Plugin ClassLoader，
 * 我们需要一个共享的静态类来实现跨ClassLoader通信。
 * 
 * 这个类会被编译到common模块，并被agent和plugin同时包含（通过shadow插件）
 */
public class WorldBridge {
    
    // 使用ConcurrentHashMap确保线程安全
    private static final Map<String, Object> WORLD_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, WorldConfig.WorldEntry> WORLD_CONFIGS = new ConcurrentHashMap<>();
    
    private static volatile boolean agentActive = false;
    private static volatile String agentVersion = "unknown";
    
    /**
     * Agent调用：标记Agent已激活
     */
    public static void markAgentActive(String version) {
        agentActive = true;
        agentVersion = version;
        System.out.println("[WorldBridge] Agent已激活，版本: " + version);
    }
    
    /**
     * Plugin调用：检查Agent是否激活
     */
    public static boolean isAgentActive() {
        return agentActive;
    }
    
    /**
     * 获取Agent版本
     */
    public static String getAgentVersion() {
        return agentVersion;
    }
    
    /**
     * Agent调用：注册世界
     */
    public static void registerWorld(String worldName, Object world) {
        WORLD_REGISTRY.put(worldName, world);
        System.out.println("[WorldBridge] 世界已注册: " + worldName);
    }
    
    /**
     * Agent调用：注册世界配置
     */
    public static void registerWorldConfig(String worldName, WorldConfig.WorldEntry config) {
        WORLD_CONFIGS.put(worldName, config);
        System.out.println("[WorldBridge] 世界配置已注册: " + worldName);
    }
    
    /**
     * Plugin调用：获取所有已注册的世界
     */
    public static Map<String, Object> getAllWorlds() {
        return new ConcurrentHashMap<>(WORLD_REGISTRY);
    }
    
    /**
     * Plugin调用：获取所有世界配置
     */
    public static Map<String, WorldConfig.WorldEntry> getAllWorldConfigs() {
        return new ConcurrentHashMap<>(WORLD_CONFIGS);
    }
    
    /**
     * Plugin调用：获取世界
     */
    public static Object getWorld(String worldName) {
        return WORLD_REGISTRY.get(worldName);
    }
    
    /**
     * Plugin调用：获取世界配置
     */
    public static WorldConfig.WorldEntry getWorldConfig(String worldName) {
        return WORLD_CONFIGS.get(worldName);
    }
    
    /**
     * 移除世界
     */
    public static void unregisterWorld(String worldName) {
        WORLD_REGISTRY.remove(worldName);
        WORLD_CONFIGS.remove(worldName);
        System.out.println("[WorldBridge] 世界已注销: " + worldName);
    }
    
    /**
     * 获取已注册世界的数量
     */
    public static int getWorldCount() {
        return WORLD_REGISTRY.size();
    }
    
    /**
     * 清空所有注册的世界（谨慎使用）
     */
    public static void clear() {
        WORLD_REGISTRY.clear();
        WORLD_CONFIGS.clear();
        System.out.println("[WorldBridge] 所有世界已清空");
    }
}

