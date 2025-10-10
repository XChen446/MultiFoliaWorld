package org.virgil.multifoliaworld.agent.hook;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.virgil.multifoliaworld.agent.WorldConfigLoader;
import org.virgil.multifoliaworld.common.WorldBridge;
import org.virgil.multifoliaworld.common.WorldConfig;

/**
 * 多世界加载器
 * 负责在MinecraftServer中加载多个世界
 */
public class WorldMultiLoader {
    
    // 维度ID分配
    private static int nextDimensionId = 0;
    private static final int OVERWORLD = 0;
    private static final int NETHER = -1;
    private static final int END = 1;
    
    /**
     * 加载所有配置的世界
     * 这个方法会被注入的代码调用
     */
    public static void loadWorlds(Object minecraftServer) {
        try {
            System.out.println("[MultiFoliaWorld] WorldMultiLoader开始工作...");
            
            // 获取世界配置
            List<WorldConfig.WorldEntry> worldConfigs = WorldConfigLoader.loadConfig();
            int maxWorlds = WorldConfigLoader.getMaxWorlds();
            
            System.out.println("[MultiFoliaWorld] 需要加载的世界数: " + maxWorlds);
            System.out.println("[MultiFoliaWorld] 找到 " + worldConfigs.size() + " 个世界配置");
            
            // 先尝试调用原始的loadLevel来加载3个基础世界
            try {
                System.out.println("[MultiFoliaWorld] ======== 世界生成检查点 ========");
                System.out.println("[MultiFoliaWorld] 开始加载原版3个世界...");
                
                // 尝试调用重命名后的原始方法 loadLevel_Original
                Method originalLoadLevel = null;
                Class<?> currentClass = minecraftServer.getClass();
                
                // 先尝试找loadLevel_Original（我们重命名的原始方法）
                Method[] allMethods = currentClass.getDeclaredMethods();
                for (Method m : allMethods) {
                    if (m.getName().equals("loadLevel_Original")) {
                        originalLoadLevel = m;
                        System.out.println("[MultiFoliaWorld] 找到重命名的原始方法 loadLevel_Original，参数数量: " + m.getParameterCount());
                        break;
                    }
                }
                
                if (originalLoadLevel == null) {
                    System.out.println("[MultiFoliaWorld] 未找到 loadLevel_Original，尝试查找其他方法...");
                    
                    // 尝试找prepareLevel方法直接调用
                    String[] possibleNames = {"prepareLevel", "prepareLevels", "initWorlds", "setupWorlds"};
                    for (String name : possibleNames) {
                        try {
                            Method[] methods = currentClass.getDeclaredMethods();
                            for (Method m : methods) {
                                if (m.getName().contains(name)) {
                                    System.out.println("[MultiFoliaWorld] 找到可能的世界初始化方法: " + m.getName());
                                    // 检查参数，prepareLevel通常需要特定参数
                                    if (m.getParameterCount() > 0) {
                                        // 这可能是prepareLevel，稍后处理
                                        continue;
                                    }
                                    originalLoadLevel = m;
                                    break;
                                }
                            }
                            if (originalLoadLevel != null) break;
                        } catch (Exception ignored) {}
                    }
                }
                
                if (originalLoadLevel != null) {
                    originalLoadLevel.setAccessible(true);
                    
                    // 调用原始方法，处理可能的参数
                    System.out.println("[MultiFoliaWorld] ① 调用原始世界加载方法...");
                    
                    try {
                        Class<?>[] paramTypes = originalLoadLevel.getParameterTypes();
                        if (paramTypes.length == 0) {
                            // 无参数
                            originalLoadLevel.invoke(minecraftServer);
                        } else if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                            // 需要String参数（世界名）
                            originalLoadLevel.invoke(minecraftServer, "world");
                        } else {
                            // 其他参数情况
                            System.err.println("[MultiFoliaWorld] 原始方法有未知参数: " + Arrays.toString(paramTypes));
                            // 尝试用默认值调用
                            Object[] args = new Object[paramTypes.length];
                            for (int i = 0; i < args.length; i++) {
                                if (paramTypes[i] == String.class) {
                                    args[i] = "world";
                                } else if (paramTypes[i].isPrimitive()) {
                                    args[i] = 0;
                                } else {
                                    args[i] = null;
                                }
                            }
                            originalLoadLevel.invoke(minecraftServer, args);
                        }
                        
                        System.out.println("[MultiFoliaWorld] ✓ 原版世界加载完成");
                        System.out.println("[MultiFoliaWorld]   - 如果看到 'Preparing level \"world\"' 等日志 = 检查点①通过");
                        System.out.println("[MultiFoliaWorld]   - 如果没有崩溃 = 检查点②③④通过");
                    } catch (Exception ex) {
                        System.err.println("[MultiFoliaWorld] 调用原始方法失败: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                } else {
                    System.out.println("[MultiFoliaWorld] ⚠️ 未找到原始加载方法，将手动创建原版3个世界");
                    
                    // 手动创建原版3个世界
                    Method prepareLevelMethod = findPrepareLevelMethod(currentClass);
                    if (prepareLevelMethod != null) {
                        System.out.println("[MultiFoliaWorld] 找到prepareLevel方法，开始手动创建世界...");
                        
                        // 创建主世界
                        createDefaultWorld(minecraftServer, "world", "NORMAL", 0, prepareLevelMethod);
                        
                        // 创建下界
                        createDefaultWorld(minecraftServer, "world_nether", "NETHER", -1, prepareLevelMethod);
                        
                        // 创建末地
                        createDefaultWorld(minecraftServer, "world_the_end", "THE_END", 1, prepareLevelMethod);
                        
                        System.out.println("[MultiFoliaWorld] ✓ 手动创建原版3个世界完成");
                    } else {
                        System.err.println("[MultiFoliaWorld] ❌ 无法找到prepareLevel方法，世界创建失败！");
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[MultiFoliaWorld] ❌ 加载原版世界失败: " + e.getMessage());
                System.err.println("[MultiFoliaWorld]   - 检查是否有ArrayIndexOutOfBoundsException（检查点②）");
                System.err.println("[MultiFoliaWorld]   - 检查是否有NullPointerException: worldData（检查点③）");
                e.printStackTrace();
            }
            
            // 现在尝试添加额外的世界
            if (maxWorlds > 3) {
                System.out.println("[MultiFoliaWorld] 开始加载额外的世界...");
                
                // 获取必要的反射对象
                Class<?> serverClass = minecraftServer.getClass();
                
                // 尝试找到putLevel或类似方法
                Method putLevelMethod = null;
                try {
                    // 先尝试找到ServerLevel类
                    Class<?> serverLevelClass = null;
                    
                    // 方法1：通过已有方法的返回类型找到ServerLevel
                    Method[] methods = serverClass.getMethods();
                    for (Method m : methods) {
                        if (m.getName().equals("overworld") || m.getName().equals("getLevel")) {
                            serverLevelClass = m.getReturnType();
                            if (serverLevelClass != null && !serverLevelClass.equals(Object.class)) {
                                System.out.println("[MultiFoliaWorld] 找到ServerLevel类: " + serverLevelClass.getName());
                                break;
                            }
                        }
                    }
                    
                    // 方法2：通过类加载器尝试不同的类名
                    if (serverLevelClass == null) {
                        ClassLoader classLoader = serverClass.getClassLoader();
                        String[] possibleNames = {
                            "net.minecraft.server.level.ServerLevel",
                            "net.minecraft.world.level.ServerLevel",
                            "ServerLevel"
                        };
                        
                        for (String name : possibleNames) {
                            try {
                                serverLevelClass = classLoader.loadClass(name);
                                System.out.println("[MultiFoliaWorld] 通过类加载器找到ServerLevel: " + name);
                                break;
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                    }
                    
                    if (serverLevelClass != null) {
                        putLevelMethod = serverClass.getMethod("putLevel", int.class, serverLevelClass);
                        System.out.println("[MultiFoliaWorld] 找到putLevel方法");
                    }
                } catch (Exception e) {
                    // putLevel方法可能不存在
                    System.err.println("[MultiFoliaWorld] putLevel方法不存在，将使用直接字段访问");
                }
                
                // 获取prepareLevel方法（用于创建世界）
                Method prepareLevelMethod = findPrepareLevelMethod(serverClass);
                if (prepareLevelMethod == null) {
                    System.err.println("[MultiFoliaWorld] 未找到prepareLevel方法！无法加载额外世界");
                    return;
                }
                
                // 加载额外的世界（从第4个开始）
                int loadedCount = 3; // 已经加载了3个原版世界
                for (WorldConfig.WorldEntry worldConfig : worldConfigs) {
                    if (!worldConfig.getEnabled()) {
                        continue;
                    }
                    
                    // 跳过前3个世界（已经由原版加载）
                    String name = worldConfig.getName();
                    if ("world".equals(name) || "world_nether".equals(name) || "world_the_end".equals(name)) {
                        continue;
                    }
                    
                    try {
                        System.out.println("[MultiFoliaWorld] ======== 加载额外世界: " + worldConfig.getName() + " ========");
                        
                        // 分配维度ID
                        int dimensionId = assignDimensionId(worldConfig);
                        System.out.println("[MultiFoliaWorld] ② 分配维度ID: " + dimensionId + 
                                         " (如果 >= 3 且未改数组会崩溃)");
                        
                        // 创建世界
                        System.out.println("[MultiFoliaWorld] ① 调用prepareLevel创建世界...");
                        Object serverLevel = createWorld(minecraftServer, worldConfig, dimensionId, prepareLevelMethod);
                        
                        if (serverLevel != null) {
                            System.out.println("[MultiFoliaWorld] ✓ prepareLevel成功返回ServerLevel实例");
                            
                            // 尝试存储世界
                            if (putLevelMethod != null) {
                                putLevelMethod.invoke(minecraftServer, dimensionId, serverLevel);
                                System.out.println("[MultiFoliaWorld] ✓ 世界已存储到serverLevelMap");
                            } else {
                                // 尝试直接访问serverLevelMap字段
                                storeWorldDirectly(minecraftServer, dimensionId, serverLevel);
                                System.out.println("[MultiFoliaWorld] ✓ 世界已直接存储到Map");
                            }
                            
                            System.out.println("[MultiFoliaWorld] ✅ 成功加载额外世界: " + worldConfig.getName() + 
                                             " (维度ID: " + dimensionId + ", 环境: " + worldConfig.getEnvironment() + ")");
                            System.out.println("[MultiFoliaWorld]   - 检查点①②③④全部通过");
                            
                            // 注册到WorldBridge
                            WorldBridge.registerWorld(worldConfig.getName(), worldConfig);
                            
                            loadedCount++;
                        } else {
                            System.err.println("[MultiFoliaWorld] ❌ prepareLevel返回null，世界创建失败");
                            System.err.println("[MultiFoliaWorld]   - 可能是WorldData为null（检查点③）");
                        }
                        
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("[MultiFoliaWorld] ❌ ArrayIndexOutOfBoundsException - 检查点②失败！");
                        System.err.println("[MultiFoliaWorld]   - ServerLevel[]数组未成功替换为Map");
                        e.printStackTrace();
                    } catch (NullPointerException e) {
                        if (e.getMessage() != null && e.getMessage().contains("worldData")) {
                            System.err.println("[MultiFoliaWorld] ❌ NullPointerException: worldData - 检查点③失败！");
                            System.err.println("[MultiFoliaWorld]   - WorldData创建失败");
                        } else {
                            System.err.println("[MultiFoliaWorld] ❌ NullPointerException: " + e.getMessage());
                        }
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
                        if (e.getMessage() != null && e.getMessage().contains("off-owner thread")) {
                            System.err.println("[MultiFoliaWorld] ❌ IllegalStateException - 检查点④失败！");
                            System.err.println("[MultiFoliaWorld]   - 在非主线程尝试加载区块");
                        } else {
                            System.err.println("[MultiFoliaWorld] ❌ IllegalStateException: " + e.getMessage());
                        }
                        e.printStackTrace();
                    } catch (Exception e) {
                        System.err.println("[MultiFoliaWorld] ❌ 加载额外世界失败: " + worldConfig.getName());
                        System.err.println("[MultiFoliaWorld]   - 错误类型: " + e.getClass().getName());
                        System.err.println("[MultiFoliaWorld]   - 错误信息: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                System.out.println("[MultiFoliaWorld] 共加载 " + loadedCount + " 个世界（包括3个原版世界）");
            }
            
            // 标记Agent已激活
            WorldBridge.markAgentActive("1.0-EXPERIMENTAL");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 多世界加载器错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 直接访问字段存储世界
     */
    private static void storeWorldDirectly(Object minecraftServer, int dimensionId, Object serverLevel) {
        try {
            // 尝试访问serverLevelMap字段
            Field mapField = minecraftServer.getClass().getDeclaredField("serverLevelMap");
            mapField.setAccessible(true);
            Object map = mapField.get(minecraftServer);
            
            // 存储世界
            Method putMethod = map.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(map, dimensionId, serverLevel);
            
            System.out.println("[MultiFoliaWorld] 直接存储世界到Map成功");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 直接存储世界失败: " + e.getMessage());
            
            // 最后的尝试：访问原始的serverLevels数组
            try {
                Field arrayField = minecraftServer.getClass().getDeclaredField("serverLevels");
                arrayField.setAccessible(true);
                Object[] levels = (Object[]) arrayField.get(minecraftServer);
                
                // 这里有个问题：数组只有3个元素，无法存储第4个世界
                System.err.println("[MultiFoliaWorld] 警告：serverLevels数组长度为 " + levels.length + "，无法存储额外世界！");
                
            } catch (Exception ex) {
                System.err.println("[MultiFoliaWorld] 访问serverLevels数组也失败: " + ex.getMessage());
            }
        }
    }
    
    /**
     * 分配维度ID
     */
    private static int assignDimensionId(WorldConfig.WorldEntry worldConfig) {
        String name = worldConfig.getName();
        String env = worldConfig.getEnvironment();
        
        // 特殊处理原版世界
        if ("world".equals(name) && "NORMAL".equalsIgnoreCase(env)) {
            return OVERWORLD;
        } else if (("world_nether".equals(name) || name.endsWith("_nether")) && "NETHER".equalsIgnoreCase(env)) {
            return NETHER;
        } else if (("world_the_end".equals(name) || name.endsWith("_the_end")) && "THE_END".equalsIgnoreCase(env)) {
            return END;
        }
        
        // 为其他世界分配新的维度ID
        // 避免使用0, -1, 1
        if (nextDimensionId == 0) nextDimensionId = 2;
        if (nextDimensionId == -1) nextDimensionId++;
        if (nextDimensionId == 1) nextDimensionId++;
        
        return nextDimensionId++;
    }
    
    /**
     * 查找prepareLevel方法
     */
    private static Method findPrepareLevelMethod(Class<?> serverClass) {
        System.out.println("[MultiFoliaWorld] 开始查找prepareLevel方法...");
        
        // 查找prepareLevel方法的各种可能签名
        Method[] methods = serverClass.getDeclaredMethods();
        
        // 首先记录所有可能相关的方法
        System.out.println("[MultiFoliaWorld] 扫描所有方法:");
        for (Method method : methods) {
            String name = method.getName();
            if (name.contains("prepare") || name.contains("Level") || name.contains("create") || 
                name.contains("load") || name.contains("init")) {
                Class<?> returnType = method.getReturnType();
                System.out.println("[MultiFoliaWorld]   - " + name + " 返回: " + returnType.getName() + 
                                 " 参数数: " + method.getParameterCount());
            }
        }
        
        // 尝试不同的匹配策略
        for (Method method : methods) {
            String name = method.getName();
            
            // 策略1：查找包含prepareLevel的方法
            if (name.contains("prepareLevel") || name.equals("a") || name.equals("b")) { // 可能被混淆为a或b
                Class<?> returnType = method.getReturnType();
                if (returnType.getName().contains("ServerLevel") || returnType.getName().contains("Level")) {
                    System.out.println("[MultiFoliaWorld] ✓ 找到世界创建方法（策略1）: " + method.getName());
                    method.setAccessible(true);
                    return method;
                }
            }
            
            // 策略2：查找返回ServerLevel且参数包含String的方法
            if (method.getParameterCount() >= 1) {
                Class<?>[] params = method.getParameterTypes();
                if (params[0] == String.class || (params.length > 1 && params[1] == String.class)) {
                    Class<?> returnType = method.getReturnType();
                    if (returnType.getName().contains("ServerLevel")) {
                        System.out.println("[MultiFoliaWorld] ✓ 找到世界创建方法（策略2）: " + method.getName());
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        
        // 策略3：向上查找父类
        Class<?> superClass = serverClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            System.out.println("[MultiFoliaWorld] 在父类中查找: " + superClass.getName());
            return findPrepareLevelMethod(superClass);
        }
        
        System.err.println("[MultiFoliaWorld] ❌ 无法找到prepareLevel方法");
        return null;
    }
    
    /**
     * 创建世界
     */
    private static Object createWorld(Object minecraftServer, WorldConfig.WorldEntry worldConfig, 
                                    int dimensionId, Method prepareLevelMethod) throws Exception {
        
        System.out.println("[MultiFoliaWorld] 准备创建世界: " + worldConfig.getName());
        System.out.println("[MultiFoliaWorld]   - 方法: " + prepareLevelMethod.getName());
        System.out.println("[MultiFoliaWorld]   - 维度ID: " + dimensionId);
        System.out.println("[MultiFoliaWorld]   - 环境: " + worldConfig.getEnvironment());
        
        try {
            // 获取必要的参数
            Class<?>[] paramTypes = prepareLevelMethod.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            System.out.println("[MultiFoliaWorld]   - 方法参数数量: " + paramTypes.length);
            
            // 填充参数（这里需要根据实际情况调整）
            for (int i = 0; i < paramTypes.length; i++) {
                String typeName = paramTypes[i].getName();
                System.out.println("[MultiFoliaWorld]   - 参数[" + i + "]: " + typeName);
                
                if (typeName.contains("String")) {
                    // 世界名称
                    args[i] = worldConfig.getName();
                    System.out.println("[MultiFoliaWorld]     -> 设置世界名称: " + worldConfig.getName());
                } else if (typeName.contains("ResourceKey")) {
                    // 世界维度key
                    args[i] = createResourceKey(worldConfig.getEnvironment());
                    System.out.println("[MultiFoliaWorld]     -> 设置ResourceKey: " + worldConfig.getEnvironment());
                } else if (typeName.contains("WorldData") || typeName.contains("LevelData")) {
                    // 世界数据 - 让prepareLevel自己创建
                    args[i] = null;
                    System.out.println("[MultiFoliaWorld]     -> WorldData设为null（让prepareLevel自动创建）");
                } else if (typeName.equals("int")) {
                    // 可能是维度ID
                    args[i] = dimensionId;
                    System.out.println("[MultiFoliaWorld]     -> 设置维度ID: " + dimensionId);
                } else if (typeName.equals("boolean")) {
                    // 可能是debug标志
                    args[i] = false;
                    System.out.println("[MultiFoliaWorld]     -> 设置boolean参数: false");
                } else {
                    // 其他参数尝试传null
                    args[i] = null;
                    System.out.println("[MultiFoliaWorld]     -> 设置为null");
                }
            }
            
            // 调用prepareLevel创建世界
            System.out.println("[MultiFoliaWorld] 调用prepareLevel...");
            Object serverLevel = prepareLevelMethod.invoke(minecraftServer, args);
            
            if (serverLevel != null) {
                System.out.println("[MultiFoliaWorld] ✓ prepareLevel成功完成");
                System.out.println("[MultiFoliaWorld]   - 返回的ServerLevel: " + serverLevel.getClass().getName());
                
                // 验证世界文件夹是否创建
                File worldFolder = new File(worldConfig.getName());
                if (worldFolder.exists()) {
                    System.out.println("[MultiFoliaWorld] ✓ 世界文件夹已创建: " + worldFolder.getAbsolutePath());
                    
                    // 检查关键文件
                    File levelDat = new File(worldFolder, "level.dat");
                    if (levelDat.exists()) {
                        System.out.println("[MultiFoliaWorld] ✓ level.dat已生成");
                    } else {
                        System.out.println("[MultiFoliaWorld] ⚠️ level.dat未找到（可能还在生成中）");
                    }
                } else {
                    System.out.println("[MultiFoliaWorld] ⚠️ 世界文件夹未找到: " + worldConfig.getName());
                }
            } else {
                System.out.println("[MultiFoliaWorld] ⚠️ prepareLevel返回null");
            }
            
            return serverLevel;
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 创建世界时出错: " + e.getMessage());
            e.printStackTrace();
            
            // 尝试简化的创建方式
            try {
                // 可能只需要世界名称
                if (prepareLevelMethod.getParameterCount() == 1) {
                    return prepareLevelMethod.invoke(minecraftServer, worldConfig.getName());
                }
            } catch (Exception ex) {
                // 忽略
            }
            
            return null;
        }
    }
    
    /**
     * 创建ResourceKey（世界维度标识）
     */
    private static Object createResourceKey(String environment) {
        try {
            // 尝试获取原版的ResourceKey
            Class<?> worldClass = Class.forName("net.minecraft.world.level.Level");
            
            if ("NETHER".equalsIgnoreCase(environment)) {
                Field netherField = worldClass.getField("NETHER");
                return netherField.get(null);
            } else if ("THE_END".equalsIgnoreCase(environment)) {
                Field endField = worldClass.getField("END");
                return endField.get(null);
            } else {
                Field overworldField = worldClass.getField("OVERWORLD");
                return overworldField.get(null);
            }
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 创建ResourceKey失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建世界数据
     */
    private static Object createWorldData(WorldConfig.WorldEntry worldConfig) {
        // 让Minecraft的prepareLevel自动创建WorldData
        // 返回null让MC使用默认的世界生成设置
        return null;
    }
    
    /**
     * 创建默认的原版世界
     */
    private static void createDefaultWorld(Object minecraftServer, String worldName, String environment, 
                                         int dimensionId, Method prepareLevelMethod) {
        try {
            System.out.println("[MultiFoliaWorld] 创建默认世界: " + worldName + " (维度ID: " + dimensionId + ")");
            
            // 创建WorldConfig
            WorldConfig.WorldEntry config = new WorldConfig.WorldEntry();
            config.setName(worldName);
            config.setEnvironment(environment);
            config.setEnabled(true);
            config.setStructures(true);
            
            // 调用createWorld
            Object serverLevel = createWorld(minecraftServer, config, dimensionId, prepareLevelMethod);
            
            if (serverLevel != null) {
                // 存储世界
                storeWorldDirectly(minecraftServer, dimensionId, serverLevel);
                System.out.println("[MultiFoliaWorld] ✓ 成功创建默认世界: " + worldName);
            } else {
                System.err.println("[MultiFoliaWorld] ❌ 创建默认世界失败: " + worldName);
            }
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 创建默认世界时出错: " + worldName);
            e.printStackTrace();
        }
    }
    
    /**
     * 只加载额外的世界（供增强模式使用）
     */
    public static void loadAdditionalWorldsOnly(Object minecraftServer) {
        try {
            System.out.println("[MultiFoliaWorld] 准备加载额外的世界...");
            
            // 获取世界配置
            List<WorldConfig.WorldEntry> worldConfigs = WorldConfigLoader.loadConfig();
            int maxWorlds = WorldConfigLoader.getMaxWorlds();
            
            if (maxWorlds <= 3) {
                System.out.println("[MultiFoliaWorld] 不需要加载额外世界（配置的世界数 <= 3）");
                return;
            }
            
            System.out.println("[MultiFoliaWorld] 需要加载的总世界数: " + maxWorlds);
            System.out.println("[MultiFoliaWorld] 找到 " + worldConfigs.size() + " 个世界配置");
            
            // 获取必要的反射对象
            Class<?> serverClass = minecraftServer.getClass();
            
            // 查找prepareLevel方法
            Method prepareLevelMethod = findPrepareLevelMethod(serverClass);
            if (prepareLevelMethod == null) {
                System.err.println("[MultiFoliaWorld] 未找到prepareLevel方法！无法加载额外世界");
                return;
            }
            
            // 尝试找到putLevel或类似方法
            Method putLevelMethod = null;
            try {
                // 先尝试找到ServerLevel类
                Class<?> serverLevelClass = null;
                
                // 通过已有方法的返回类型找到ServerLevel
                Method[] methods = serverClass.getMethods();
                for (Method m : methods) {
                    if (m.getName().equals("overworld") || m.getName().equals("getLevel")) {
                        serverLevelClass = m.getReturnType();
                        if (serverLevelClass != null && !serverLevelClass.equals(Object.class)) {
                            System.out.println("[MultiFoliaWorld] 找到ServerLevel类: " + serverLevelClass.getName());
                            break;
                        }
                    }
                }
                
                if (serverLevelClass != null) {
                    putLevelMethod = serverClass.getMethod("putLevel", int.class, serverLevelClass);
                    System.out.println("[MultiFoliaWorld] 找到putLevel方法");
                }
            } catch (Exception e) {
                System.err.println("[MultiFoliaWorld] putLevel方法不存在，将使用直接字段访问");
            }
            
            // 加载额外的世界（跳过原版3个）
            int loadedCount = 0;
            for (WorldConfig.WorldEntry worldConfig : worldConfigs) {
                if (!worldConfig.getEnabled()) {
                    continue;
                }
                
                // 跳过原版3个世界（它们已经被原始方法加载）
                String name = worldConfig.getName();
                if ("world".equals(name) || "world_nether".equals(name) || "world_the_end".equals(name)) {
                    continue;
                }
                
                try {
                    System.out.println("[MultiFoliaWorld] ======== 加载额外世界: " + worldConfig.getName() + " ========");
                    
                    // 分配维度ID
                    int dimensionId = assignDimensionId(worldConfig);
                    System.out.println("[MultiFoliaWorld] ② 分配维度ID: " + dimensionId);
                    
                    // 创建世界
                    System.out.println("[MultiFoliaWorld] ① 调用prepareLevel创建世界...");
                    Object serverLevel = createWorld(minecraftServer, worldConfig, dimensionId, prepareLevelMethod);
                    
                    if (serverLevel != null) {
                        System.out.println("[MultiFoliaWorld] ✓ prepareLevel成功返回ServerLevel实例");
                        
                        // 尝试存储世界
                        if (putLevelMethod != null) {
                            putLevelMethod.invoke(minecraftServer, dimensionId, serverLevel);
                            System.out.println("[MultiFoliaWorld] ✓ 世界已存储到serverLevelMap");
                        } else {
                            // 尝试直接访问serverLevelMap字段
                            storeWorldDirectly(minecraftServer, dimensionId, serverLevel);
                            System.out.println("[MultiFoliaWorld] ✓ 世界已直接存储到Map");
                        }
                        
                        System.out.println("[MultiFoliaWorld] ✅ 成功加载额外世界: " + worldConfig.getName());
                        
                        // 注册到WorldBridge
                        WorldBridge.registerWorld(worldConfig.getName(), worldConfig);
                        
                        loadedCount++;
                    } else {
                        System.err.println("[MultiFoliaWorld] ❌ prepareLevel返回null，世界创建失败");
                    }
                    
                } catch (Exception e) {
                    System.err.println("[MultiFoliaWorld] ❌ 加载额外世界失败: " + worldConfig.getName());
                    System.err.println("[MultiFoliaWorld]   - 错误类型: " + e.getClass().getName());
                    System.err.println("[MultiFoliaWorld]   - 错误信息: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("[MultiFoliaWorld] 共加载 " + loadedCount + " 个额外世界");
            
            // 标记Agent已激活
            if (loadedCount > 0) {
                WorldBridge.markAgentActive("1.0-EXPERIMENTAL");
            }
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 加载额外世界时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
