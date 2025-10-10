package org.virgil.multifoliaworld.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.virgil.multifoliaworld.agent.transformer.WorldLimitTransformer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * MultiFoliaWorld JavaAgent主类
 * 用于在Folia服务器启动时注入字节码，实现多世界预加载
 * 
 * 注入原理：
 * 1. Folia在插件加载前完成世界加载
 * 2. Folia的区域化多线程在世界加载后初始化
 * 3. 必须在MinecraftServer初始化时拦截并注入多世界加载逻辑
 */
public class MultiFoliaWorldAgent {
    
    private static final String AGENT_VERSION = "1.0-SNAPSHOT";
    private static boolean isEnabled = false;
    
    /**
     * JVM启动时调用（-javaagent参数）
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("========================================");
        System.out.println("  MultiFoliaWorld Agent v" + AGENT_VERSION);
        System.out.println("  Folia Multi-World Support");
        System.out.println("========================================");
        
        try {
            // 注册类文件转换器
            inst.addTransformer(new FoliaWorldTransformer(), true);
            isEnabled = true;
            
            System.out.println("[MultiFoliaWorld] Agent已加载，正在监听Folia类...");
            System.out.println("[MultiFoliaWorld] 将在服务器初始化时注入多世界加载逻辑");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] Agent加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 运行时附加调用（动态加载）
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }
    
    /**
     * 检查Agent是否已启用
     */
    public static boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * Folia类文件转换器
     * 负责拦截和修改Folia的核心类
     */
    static class FoliaWorldTransformer implements ClassFileTransformer {
        
        private static final String TARGET_CLASS = "net.minecraft.server.MinecraftServer";
        private static final String CRAFTSERVER_CLASS = "org.bukkit.craftbukkit.CraftServer";
        private static final String DEDICATED_SERVER_CLASS = "net.minecraft.server.dedicated.DedicatedServer";
        
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            
            try {
                // 将内部类名格式转换为点分隔格式
                String normalizedName = className.replace('/', '.');
                
                // 方案B: 拦截DedicatedServer（替代方案A）
                if (normalizedName.equals(DEDICATED_SERVER_CLASS)) {
                    System.out.println("[MultiFoliaWorld] 检测到DedicatedServer类，开始注入世界过滤逻辑...");
                    return transformDedicatedServer(classfileBuffer);
                }
                
        // 拦截MinecraftServer类
        if (normalizedName.equals(TARGET_CLASS)) {
            System.out.println("[MultiFoliaWorld] 检测到MinecraftServer类，开始注入...");
            
            // 先尝试突破世界限制
            byte[] transformedClass = tryBreakWorldLimit(classfileBuffer);
            if (transformedClass != null && transformedClass != classfileBuffer) {
                return transformedClass;
            }
            
            // 如果突破失败，使用常规转换
            return transformMinecraftServer(classfileBuffer);
        }
                
        // 拦截CraftServer类
        if (normalizedName.equals(CRAFTSERVER_CLASS)) {
            System.out.println("[MultiFoliaWorld] 检测到CraftServer类，开始注入...");
            return transformCraftServer(classfileBuffer);
        }
        
        // 拦截ServerLevel类（突破3个世界限制）
        if (normalizedName.equals("net.minecraft.server.level.ServerLevel")) {
            System.out.println("[MultiFoliaWorld] 检测到ServerLevel类，准备修改世界限制...");
            // 由于ServerLevel本身只是世界实例，真正的限制在MinecraftServer中
            // 这里暂时不做修改，后续在MinecraftServer中处理
        }
                
            } catch (Exception e) {
                System.err.println("[MultiFoliaWorld] 字节码转换失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            return null; // 返回null表示不修改
        }
        
        /**
         * 尝试突破世界数量限制
         * @return 转换后的字节码，如果失败返回null
         */
        private byte[] tryBreakWorldLimit(byte[] classfileBuffer) {
            try {
                // 确保配置已加载
                org.virgil.multifoliaworld.agent.WorldConfigLoader.loadConfig();
                
                // 检查是否需要突破限制
                int maxWorlds = org.virgil.multifoliaworld.agent.WorldConfigLoader.getMaxWorlds();
                
                System.out.println("[MultiFoliaWorld] 检查世界限制: 需要加载 " + maxWorlds + " 个世界");
                
                if (maxWorlds <= 3) {
                    System.out.println("[MultiFoliaWorld] 世界数 <= 3，无需突破限制");
                    return null;
                }
                
                System.out.println("[MultiFoliaWorld] 检测到需要突破3个世界限制 (需要加载: " + maxWorlds + " 个世界)");
                
                // 调用WorldLimitTransformer进行转换
                byte[] transformed = WorldLimitTransformer.transformMinecraftServer(classfileBuffer);
                
                if (transformed != classfileBuffer) {
                    System.out.println("[MultiFoliaWorld] ✓ 世界限制突破成功！");
                    return transformed;
                }
                
                return null;
                
            } catch (Exception e) {
                System.err.println("[MultiFoliaWorld] 突破世界限制时出错: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        
        /**
         * 转换MinecraftServer类
         * 目标：在服务器初始化时注入多世界加载逻辑
         */
        private byte[] transformMinecraftServer(byte[] classfileBuffer) throws Exception {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            
            boolean injected = false;
            
            // 查找loadLevel或initServer方法
            CtMethod[] methods = ctClass.getDeclaredMethods();
            for (CtMethod method : methods) {
                // 跳过抽象方法和native方法
                if (javassist.Modifier.isAbstract(method.getModifiers()) || 
                    javassist.Modifier.isNative(method.getModifiers())) {
                    continue;
                }
                
                // 在Folia中，世界加载通常在loadLevel或类似方法中
                if (method.getName().contains("loadLevel") || 
                    method.getName().contains("initServer") ||
                    method.getName().contains("loadWorld")) {
                    
                    try {
                        System.out.println("[MultiFoliaWorld] 找到目标方法: " + method.getName() + 
                                         " (修饰符: " + javassist.Modifier.toString(method.getModifiers()) + ")");
                        
                        // 在方法开始处插入钩子
                        method.insertBefore(
                            "org.virgil.multifoliaworld.agent.hook.WorldLoadHook.onServerInit(this);"
                        );
                        
                        System.out.println("[MultiFoliaWorld] ✓ MinecraftServer注入成功: " + method.getName());
                        injected = true;
                        break;
                        
                    } catch (Exception e) {
                        System.out.println("[MultiFoliaWorld] 注入失败 (" + method.getName() + "): " + e.getMessage());
                        // 继续尝试其他方法
                    }
                }
            }
            
            if (!injected) {
                System.out.println("[MultiFoliaWorld] ⚠ 警告: 未找到合适的注入点，将延迟到Plugin阶段");
            }
            
            byte[] result = ctClass.toBytecode();
            ctClass.detach();
            return result;
        }
        
        /**
         * 转换CraftServer类
         * 目标：拦截createWorld方法，使其能够在运行时创建世界
         */
        private byte[] transformCraftServer(byte[] classfileBuffer) throws Exception {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            
            try {
                // 查找createWorld方法
                CtMethod createWorldMethod = ctClass.getDeclaredMethod("createWorld");
                
                // 替换方法实现以支持Folia的区域化线程
                createWorldMethod.insertBefore(
                    "org.virgil.multifoliaworld.agent.hook.WorldCreateHook.beforeCreateWorld($1);"
                );
                
                System.out.println("[MultiFoliaWorld] CraftServer注入成功");
                
            } catch (javassist.NotFoundException e) {
                System.out.println("[MultiFoliaWorld] 未找到createWorld方法，跳过注入");
            }
            
            byte[] result = ctClass.toBytecode();
            ctClass.detach();
            return result;
        }
        
        /**
         * 转换DedicatedServer类（方案B）
         * 在世界扫描前过滤世界文件夹
         */
        private byte[] transformDedicatedServer(byte[] classfileBuffer) throws Exception {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
            
            try {
                // 查找initServer方法
                CtMethod initServerMethod = ctClass.getDeclaredMethod("initServer");
                
                // 检查方法修饰符
                if (javassist.Modifier.isAbstract(initServerMethod.getModifiers()) || 
                    javassist.Modifier.isNative(initServerMethod.getModifiers())) {
                    System.out.println("[MultiFoliaWorld] initServer是抽象或本地方法，跳过注入");
                    return null;
                }
                
                System.out.println("[MultiFoliaWorld] 找到DedicatedServer.initServer()方法");
                
                // 在方法开始处注入世界过滤逻辑
                String hookCode = 
                    "{ " +
                    "  System.out.println(\"[MultiFoliaWorld] 准备过滤世界文件夹...\"); " +
                    "  org.virgil.multifoliaworld.agent.hook.WorldFolderFilter.hideDisabledWorlds(this); " +
                    "}";
                
                initServerMethod.insertBefore(hookCode);
                
                System.out.println("[MultiFoliaWorld] DedicatedServer注入成功");
                
            } catch (javassist.NotFoundException e) {
                System.out.println("[MultiFoliaWorld] 未找到initServer方法: " + e.getMessage());
            } catch (javassist.CannotCompileException e) {
                System.err.println("[MultiFoliaWorld] 注入失败: " + e.getMessage());
                if (e.getCause() != null) {
                    e.getCause().printStackTrace();
                }
            }
            
            byte[] result = ctClass.toBytecode();
            ctClass.detach();
            return result;
        }
    }
}

