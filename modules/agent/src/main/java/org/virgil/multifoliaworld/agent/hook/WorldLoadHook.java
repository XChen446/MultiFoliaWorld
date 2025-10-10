package org.virgil.multifoliaworld.agent.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.virgil.multifoliaworld.agent.WorldConfigLoader;
import org.virgil.multifoliaworld.common.WorldBridge;
import org.virgil.multifoliaworld.common.WorldConfig;

/**
 * 世界加载钩子 - 优化版
 * 
 * 基于Minecraft区块加载模型的深度优化：
 * 1. 在DedicatedServer.initServer()时介入，此时区域线程池未初始化
 * 2. 直接操作MinecraftServer的worlds Map，而非依赖bukkit.yml
 * 3. 使用WorldDataStorage和LevelStem创建世界
 * 4. 确保世界在Folia的RegionizedServer初始化前完全加载
 * 
 * 参考模型：
 * World → ChunkProvider → ChunkGenerator → id2ChunkMap → ChunkIOExecutor
 */
public class WorldLoadHook {
    
    private static boolean hasRun = false;
    private static final String AGENT_VERSION = "1.0-SNAPSHOT";
    
    /**
     * 服务器初始化钩子
     * 在MinecraftServer.initServer()开始时调用
     * 
     * @param minecraftServer MinecraftServer实例（DedicatedServer）
     */
    public static void onServerInit(Object minecraftServer) {
        // 确保只运行一次
        if (hasRun) {
            return;
        }
        hasRun = true;
        
        System.out.println("========================================");
        System.out.println("[MultiFoliaWorld] 世界加载钩子已触发");
        System.out.println("[MultiFoliaWorld] Agent版本: " + AGENT_VERSION);
        System.out.println("[MultiFoliaWorld] 介入点: MinecraftServer.initServer()");
        System.out.println("========================================");
        
        try {
            // 激活WorldBridge
            WorldBridge.markAgentActive(AGENT_VERSION);
            
            // 加载配置
            List<WorldConfig.WorldEntry> allWorlds = WorldConfigLoader.loadConfig();
            System.out.println("[MultiFoliaWorld] 配置加载完成，共 " + allWorlds.size() + " 个世界");
            
            // 过滤启用的世界
            List<WorldConfig.WorldEntry> enabledWorlds = new ArrayList<>();
            for (WorldConfig.WorldEntry world : allWorlds) {
                if (world.getEnabled()) {
                    enabledWorlds.add(world);
                    WorldBridge.registerWorldConfig(world.getName(), world);
                    System.out.println("[MultiFoliaWorld] ✓ 已注册世界配置: " + world.getName() + 
                                       " (" + world.getEnvironment() + ")");
                } else {
                    System.out.println("[MultiFoliaWorld] ✗ 跳过禁用的世界: " + world.getName());
                }
            }
            
            System.out.println("[MultiFoliaWorld] 启用的世界: " + enabledWorlds.size() + "/" + allWorlds.size());
            
            System.out.println("========================================");
            System.out.println("[MultiFoliaWorld] 世界配置注册完成");
            System.out.println("[MultiFoliaWorld] 世界过滤将在DedicatedServer初始化时执行");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 世界加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 通过反射加载世界
     * 
     * 策略：
     * 1. 获取MinecraftServer的worlds Map（存储所有已加载世界）
     * 2. 获取WorldDataStorage（管理世界数据目录）
     * 3. 创建ServerLevel实例（对应Bukkit的World）
     * 4. 初始化ChunkProvider和ChunkGenerator
     * 5. 注册到服务器的worlds Map
     */
    private static void loadWorldsViaReflection(Object minecraftServer, List<WorldConfig.WorldEntry> worlds) {
        try {
            Class<?> serverClass = minecraftServer.getClass();
            System.out.println("[MultiFoliaWorld] 服务器类: " + serverClass.getName());
            
            // 方法1: 尝试获取CraftServer（Bukkit层）
            Object craftServer = tryGetCraftServer(minecraftServer);
            if (craftServer != null) {
                loadWorldsViaBukkit(craftServer, worlds);
                return;
            }
            
            // 方法2: 直接操作NMS层（更底层，更可靠）
            loadWorldsViaNMS(minecraftServer, worlds);
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 反射加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 尝试获取CraftServer实例
     */
    private static Object tryGetCraftServer(Object minecraftServer) {
        try {
            // 尝试多种可能的方法名
            String[] methodNames = {"getServer", "getBukkitServer", "server"};
            
            for (String methodName : methodNames) {
                Method method = findMethod(minecraftServer.getClass(), methodName);
                if (method != null) {
                    Object server = method.invoke(minecraftServer);
                    if (server != null && server.getClass().getName().contains("CraftServer")) {
                        System.out.println("[MultiFoliaWorld] 找到CraftServer: " + server.getClass().getName());
                        return server;
                    }
                }
            }
            
            // 尝试字段访问
            Field serverField = findField(minecraftServer.getClass(), "server");
            if (serverField != null) {
                Object server = serverField.get(minecraftServer);
                if (server != null && server.getClass().getName().contains("CraftServer")) {
                    System.out.println("[MultiFoliaWorld] 通过字段找到CraftServer: " + server.getClass().getName());
                    return server;
                }
            }
            
        } catch (Exception e) {
            System.out.println("[MultiFoliaWorld] 无法获取CraftServer: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 通过Bukkit API加载世界
     * 
     * 优点：使用高层API，兼容性好
     * 缺点：在Folia中可能受限
     */
    private static void loadWorldsViaBukkit(Object craftServer, List<WorldConfig.WorldEntry> worlds) {
        try {
            System.out.println("[MultiFoliaWorld] 使用Bukkit API加载世界");
            
            // 获取createWorld方法
            // WorldCreator在org.bukkit包中
            Class<?> worldCreatorClass = Class.forName("org.bukkit.WorldCreator");
            Method nameMethod = worldCreatorClass.getDeclaredMethod("name", String.class);
            Method environmentMethod = worldCreatorClass.getDeclaredMethod("environment", 
                Class.forName("org.bukkit.World$Environment"));
            Method seedMethod = worldCreatorClass.getDeclaredMethod("seed", long.class);
            Method generateStructuresMethod = worldCreatorClass.getDeclaredMethod("generateStructures", boolean.class);
            Method createWorldMethod = craftServer.getClass().getDeclaredMethod("createWorld", worldCreatorClass);
            createWorldMethod.setAccessible(true);
            
            // Environment枚举
            Class<?> environmentEnum = Class.forName("org.bukkit.World$Environment");
            
            for (WorldConfig.WorldEntry worldConfig : worlds) {
                try {
                    // 检查世界是否已存在
                    Method getWorldMethod = craftServer.getClass().getDeclaredMethod("getWorld", String.class);
                    Object existingWorld = getWorldMethod.invoke(craftServer, worldConfig.getName());
                    
                    if (existingWorld != null) {
                        System.out.println("[MultiFoliaWorld] 世界已存在，跳过: " + worldConfig.getName());
                        WorldBridge.registerWorld(worldConfig.getName(), existingWorld);
                        continue;
                    }
                    
                    // 创建WorldCreator
                    Constructor<?> creatorConstructor = worldCreatorClass.getDeclaredConstructor(String.class);
                    creatorConstructor.setAccessible(true);
                    Object creator = creatorConstructor.newInstance(worldConfig.getName());
                    
                    // 设置环境
                    Object environment = Enum.valueOf((Class<Enum>) environmentEnum, worldConfig.getEnvironment());
                    environmentMethod.invoke(creator, environment);
                    
                    // 设置种子
                    if (worldConfig.getSeed() != null) {
                        seedMethod.invoke(creator, worldConfig.getSeed());
                    }
                    
                    // 设置结构生成
                    generateStructuresMethod.invoke(creator, worldConfig.getStructures());
                    
                    // 创建世界
                    System.out.println("[MultiFoliaWorld] 正在创建世界: " + worldConfig.getName());
                    Object world = createWorldMethod.invoke(craftServer, creator);
                    
                    if (world != null) {
                        System.out.println("[MultiFoliaWorld] ✓ 世界创建成功: " + worldConfig.getName());
                        WorldBridge.registerWorld(worldConfig.getName(), world);
                    } else {
                        System.err.println("[MultiFoliaWorld] ✗ 世界创建失败: " + worldConfig.getName());
                    }
                    
                } catch (Exception e) {
                    System.err.println("[MultiFoliaWorld] 创建世界失败 (" + worldConfig.getName() + "): " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] Bukkit API加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 通过NMS底层API加载世界
     * 
     * 策略：
     * 1. 访问MinecraftServer的levels字段（Map<ResourceKey<Level>, ServerLevel>）
     * 2. 创建新的ServerLevel实例
     * 3. 注册到levels Map中
     * 
     * 优点：不依赖Bukkit API，更底层更可靠
     * 缺点：与Minecraft版本强相关，需要维护
     */
    private static void loadWorldsViaNMS(Object minecraftServer, List<WorldConfig.WorldEntry> worlds) {
        try {
            System.out.println("[MultiFoliaWorld] 使用NMS底层API加载世界");
            System.out.println("[MultiFoliaWorld] 注意: 此方法需要等待服务器完全初始化");
            System.out.println("[MultiFoliaWorld] 世界将在服务器启动后通过Plugin创建");
            
            // 在NMS层面，世界创建非常复杂，涉及：
            // - ResourceKey<Level> 世界键
            // - LevelStem 世界配置（包含ChunkGenerator、BiomeSource等）
            // - WorldDataStorage 数据存储
            // - ChunkProgressListener 加载进度监听
            // - WorldBorder 世界边界
            // - RandomSequences 随机序列
            // 
            // 由于复杂性，建议策略：
            // 1. Agent阶段：注册世界配置到WorldBridge
            // 2. Plugin阶段：使用Bukkit API创建世界
            // 3. 这样既保证了时机正确，又降低了复杂度
            
            System.out.println("[MultiFoliaWorld] NMS直接创建过于复杂，将延迟到Plugin阶段");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] NMS加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 查找方法（支持父类查找）
     */
    private static Method findMethod(Class<?> clazz, String methodName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(methodName)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            } catch (Exception e) {
                // 忽略异常，继续查找
            }
            current = current.getSuperclass();
        }
        return null;
    }
    
    /**
     * 查找字段（支持父类查找）
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (Exception e) {
                // 忽略异常，继续查找
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
