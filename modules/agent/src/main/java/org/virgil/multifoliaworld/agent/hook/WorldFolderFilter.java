package org.virgil.multifoliaworld.agent.hook;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.virgil.multifoliaworld.agent.WorldConfigLoader;
import org.virgil.multifoliaworld.common.WorldConfig;

/**
 * 世界文件夹过滤器（方案B）
 * 
 * 通过临时重命名文件夹的方式，让Folia看不到禁用的世界
 * 这是一个更安全的替代方案，避免修改Java核心类
 */
public class WorldFolderFilter {
    
    private static Set<String> hiddenWorlds = new HashSet<>();
    
    /**
     * 在DedicatedServer.initServer()开始时调用
     * 临时隐藏禁用的世界文件夹
     */
    public static void hideDisabledWorlds(Object dedicatedServer) {
        try {
            System.out.println("[MultiFoliaWorld] ========================================");
            System.out.println("[MultiFoliaWorld] 世界文件夹过滤器已激活");
            System.out.println("[MultiFoliaWorld] 执行时机：DedicatedServer.initServer()");
            System.out.println("[MultiFoliaWorld] ========================================");
            
            // 获取服务器根目录
            File serverDir = new File(".");
            System.out.println("[MultiFoliaWorld] 服务器目录: " + serverDir.getAbsolutePath());
            
            // 加载配置
            List<WorldConfig.WorldEntry> worlds = WorldConfigLoader.loadConfig();
            
            // 收集世界信息
            Set<String> enabledWorlds = new HashSet<>();
            Set<String> disabledWorlds = new HashSet<>();
            Set<String> newWorlds = new HashSet<>();  // 需要创建的新世界
            
            for (WorldConfig.WorldEntry world : worlds) {
                if (world.getEnabled()) {
                    enabledWorlds.add(world.getName());
                    
                    // 检查世界文件夹是否存在
                    File worldFolder = new File(serverDir, world.getName());
                    if (!worldFolder.exists()) {
                        // 需要创建新世界
                        newWorlds.add(world.getName());
                        
                        // 创建世界文件夹
                        if (worldFolder.mkdirs()) {
                            System.out.println("[MultiFoliaWorld] ✓ 创建世界文件夹: " + world.getName());
                            
                            // 为新世界准备基本文件结构
                            // 创建必要的文件以确保Folia能正确识别和加载世界
                            if (prepareWorldStructure(worldFolder, world)) {
                                System.out.println("[MultiFoliaWorld]   ✓ 世界准备完成: " + world.getName());
                            } else {
                                System.err.println("[MultiFoliaWorld]   ✗ 世界准备失败: " + world.getName());
                            }
                            
                            // 如果是NORMAL世界且启用了createDims，创建配套维度
                            if ("NORMAL".equalsIgnoreCase(world.getEnvironment()) && world.getCreateDims()) {
                                System.out.println("[MultiFoliaWorld] 检测到 createDims=true，创建配套维度...");
                                createCompanionDimensions(serverDir, world, worlds, enabledWorlds, newWorlds);
                            }
                        } else {
                            System.err.println("[MultiFoliaWorld] ✗ 无法创建世界文件夹: " + world.getName());
                        }
                    }
                } else {
                    disabledWorlds.add(world.getName());
                }
            }
            
            System.out.println("[MultiFoliaWorld] 启用的世界 (" + enabledWorlds.size() + "): " + enabledWorlds);
            System.out.println("[MultiFoliaWorld] 禁用的世界 (" + disabledWorlds.size() + "): " + disabledWorlds);
            if (!newWorlds.isEmpty()) {
                System.out.println("[MultiFoliaWorld] 新建的世界 (" + newWorlds.size() + "): " + newWorlds);
            }
            
            // 检查是否需要突破Folia的3个世界限制
            if (enabledWorlds.size() > 3) {
                // 需要突破模式
                System.out.println("[MultiFoliaWorld] ========================================");
                System.out.println("[MultiFoliaWorld] 🚀 世界限制突破模式");
                System.out.println("[MultiFoliaWorld] 🚀 检测到超过3个世界需要加载");
                System.out.println("[MultiFoliaWorld] 🚀 当前启用的世界数: " + enabledWorlds.size());
                System.out.println("[MultiFoliaWorld] 🚀 ");
                System.out.println("[MultiFoliaWorld] 🚀 将尝试通过字节码修改加载所有世界");
                System.out.println("[MultiFoliaWorld] 🚀 这是实验性功能，请谨慎使用！");
                System.out.println("[MultiFoliaWorld] ========================================");
            }
            
            // 临时重命名禁用的世界文件夹
            for (String worldName : disabledWorlds) {
                File worldFolder = new File(serverDir, worldName);
                if (worldFolder.exists() && worldFolder.isDirectory()) {
                    File levelDat = new File(worldFolder, "level.dat");
                    if (levelDat.exists()) {
                        // 重命名为 .worldname.disabled
                        File hiddenFolder = new File(serverDir, "." + worldName + ".disabled");
                        if (worldFolder.renameTo(hiddenFolder)) {
                            hiddenWorlds.add(worldName);
                            System.out.println("[MultiFoliaWorld] 已隐藏世界: " + worldName + " -> " + hiddenFolder.getName());
                        } else {
                            System.err.println("[MultiFoliaWorld] 无法隐藏世界: " + worldName);
                        }
                    }
                }
            }
            
            // 注册关闭钩子，在服务器关闭时恢复文件夹名称
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                restoreHiddenWorlds(serverDir);
            }));
            
            System.out.println("[MultiFoliaWorld] ========================================");
            System.out.println("[MultiFoliaWorld] 世界过滤完成");
            System.out.println("[MultiFoliaWorld] - 已隐藏: " + hiddenWorlds.size() + " 个世界");
            System.out.println("[MultiFoliaWorld] - 将加载: " + enabledWorlds.size() + " 个世界");
            
            // 输出将要加载的世界列表
            if (!enabledWorlds.isEmpty()) {
                System.out.println("[MultiFoliaWorld] ");
                System.out.println("[MultiFoliaWorld] 即将加载的世界：");
                for (String worldName : enabledWorlds) {
                    WorldConfig.WorldEntry config = null;
                    for (WorldConfig.WorldEntry entry : worlds) {
                        if (entry.getName().equals(worldName)) {
                            config = entry;
                            break;
                        }
                    }
                    if (config != null) {
                        System.out.println("[MultiFoliaWorld]   ✓ " + worldName + " (" + config.getEnvironment() + ")");
                    } else {
                        System.out.println("[MultiFoliaWorld]   ✓ " + worldName);
                    }
                }
            }
            
            System.out.println("[MultiFoliaWorld] ========================================");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 世界文件夹过滤失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 为新世界准备基本文件结构
     * @return 是否成功准备世界结构
     */
    private static boolean prepareWorldStructure(File worldFolder, WorldConfig.WorldEntry world) {
        try {
            // 根据环境类型创建对应的DIM文件夹
            String environment = world.getEnvironment();
            
            if ("NETHER".equalsIgnoreCase(environment)) {
                // 下界世界需要DIM-1文件夹
                File dimFolder = new File(worldFolder, "DIM-1");
                dimFolder.mkdirs();
                System.out.println("[MultiFoliaWorld]   - 创建下界维度文件夹: DIM-1");
            } else if ("THE_END".equalsIgnoreCase(environment)) {
                // 末地世界需要DIM1文件夹
                File dimFolder = new File(worldFolder, "DIM1");
                dimFolder.mkdirs();
                System.out.println("[MultiFoliaWorld]   - 创建末地维度文件夹: DIM1");
            }
            
            // 创建region文件夹（所有世界都需要）
            File regionFolder = new File(worldFolder, "region");
            regionFolder.mkdirs();
            
            // 如果是下界或末地，也需要在DIM文件夹内创建region
            if ("NETHER".equalsIgnoreCase(environment)) {
                File dimRegion = new File(worldFolder, "DIM-1/region");
                dimRegion.mkdirs();
            } else if ("THE_END".equalsIgnoreCase(environment)) {
                File dimRegion = new File(worldFolder, "DIM1/region");
                dimRegion.mkdirs();
            }
            
            System.out.println("[MultiFoliaWorld]   - 世界结构准备完成: " + world.getName() + " (" + environment + ")");
            return true;
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 准备世界结构失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 创建配套维度（下界和末地）
     */
    private static void createCompanionDimensions(File serverDir, WorldConfig.WorldEntry baseWorld, 
                                                 List<WorldConfig.WorldEntry> allWorlds,
                                                 Set<String> enabledWorlds, Set<String> newWorlds) {
        String baseName = baseWorld.getName();
        
        // 创建下界维度
        String netherName = baseName + "_nether";
        if (!worldExists(allWorlds, netherName)) {
            WorldConfig.WorldEntry netherWorld = new WorldConfig.WorldEntry();
            netherWorld.setName(netherName);
            netherWorld.setEnvironment("NETHER");
            netherWorld.setEnabled(true);
            netherWorld.setStructures(baseWorld.getStructures());
            netherWorld.setSeed(baseWorld.getSeed());
            
            File netherFolder = new File(serverDir, netherName);
            if (netherFolder.mkdirs()) {
                System.out.println("[MultiFoliaWorld]   ✓ 创建配套下界: " + netherName);
                prepareWorldStructure(netherFolder, netherWorld);
                
                // 添加到启用世界列表
                enabledWorlds.add(netherName);
                newWorlds.add(netherName);
            }
        }
        
        // 创建末地维度
        String endName = baseName + "_the_end";
        if (!worldExists(allWorlds, endName)) {
            WorldConfig.WorldEntry endWorld = new WorldConfig.WorldEntry();
            endWorld.setName(endName);
            endWorld.setEnvironment("THE_END");
            endWorld.setEnabled(true);
            endWorld.setStructures(baseWorld.getStructures());
            endWorld.setSeed(baseWorld.getSeed());
            
            File endFolder = new File(serverDir, endName);
            if (endFolder.mkdirs()) {
                System.out.println("[MultiFoliaWorld]   ✓ 创建配套末地: " + endName);
                prepareWorldStructure(endFolder, endWorld);
                
                // 添加到启用世界列表
                enabledWorlds.add(endName);
                newWorlds.add(endName);
            }
        }
    }
    
    /**
     * 检查世界是否已存在于配置中
     */
    private static boolean worldExists(List<WorldConfig.WorldEntry> worlds, String worldName) {
        for (WorldConfig.WorldEntry world : worlds) {
            if (world.getName().equals(worldName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 恢复被隐藏的世界文件夹
     */
    public static void restoreHiddenWorlds(File serverDir) {
        if (hiddenWorlds.isEmpty()) {
            return;
        }
        
        System.out.println("[MultiFoliaWorld] 正在恢复隐藏的世界文件夹...");
        
        for (String worldName : hiddenWorlds) {
            File hiddenFolder = new File(serverDir, "." + worldName + ".disabled");
            File worldFolder = new File(serverDir, worldName);
            
            if (hiddenFolder.exists() && !worldFolder.exists()) {
                if (hiddenFolder.renameTo(worldFolder)) {
                    System.out.println("[MultiFoliaWorld] 已恢复世界: " + worldName);
                } else {
                    System.err.println("[MultiFoliaWorld] 无法恢复世界: " + worldName);
                }
            }
        }
        
        hiddenWorlds.clear();
    }
    
    /**
     * 手动恢复世界（供插件调用）
     */
    public static void restoreWorld(String worldName) {
        File serverDir = new File(".");
        File hiddenFolder = new File(serverDir, "." + worldName + ".disabled");
        File worldFolder = new File(serverDir, worldName);
        
        if (hiddenFolder.exists() && !worldFolder.exists()) {
            if (hiddenFolder.renameTo(worldFolder)) {
                hiddenWorlds.remove(worldName);
                System.out.println("[MultiFoliaWorld] 已恢复世界: " + worldName);
            }
        }
    }
}
