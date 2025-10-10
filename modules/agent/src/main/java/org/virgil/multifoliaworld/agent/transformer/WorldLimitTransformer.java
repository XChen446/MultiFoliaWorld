package org.virgil.multifoliaworld.agent.transformer;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.DuplicateMemberException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

/**
 * 世界数量限制突破转换器
 * 将MinecraftServer的serverLevels数组改为Map存储
 */
public class WorldLimitTransformer {
    
    private static boolean isTransformed = false;
    
    /**
     * 转换MinecraftServer类以支持更多世界
     */
    public static byte[] transformMinecraftServer(byte[] classfileBuffer) throws Exception {
        if (isTransformed) {
            return classfileBuffer;
        }
        
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
        
        System.out.println("[MultiFoliaWorld] 开始突破3个世界限制...");
        
        try {
            // 1. 添加Map字段存储世界
            addWorldMapField(ctClass);
            
            // 2. 修改loadWorlds方法
            modifyLoadWorldsMethod(ctClass);
            
            // 3. 添加辅助方法
            addHelperMethods(ctClass);
            
            // 4. 替换所有serverLevels数组访问
            replaceArrayAccess(ctClass);
            
            isTransformed = true;
            System.out.println("[MultiFoliaWorld] ✓ 成功突破3个世界限制！");
            
            byte[] result = ctClass.toBytecode();
            ctClass.detach();
            return result;
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 突破世界限制失败: " + e.getMessage());
            e.printStackTrace();
            ctClass.detach();
            return classfileBuffer;
        }
    }
    
    /**
     * 添加Map字段存储世界
     */
    private static void addWorldMapField(CtClass ctClass) throws Exception {
        // 添加世界存储Map
        CtField worldMapField = CtField.make(
            "private final java.util.Map serverLevelMap = new java.util.concurrent.ConcurrentHashMap();", 
            ctClass
        );
        ctClass.addField(worldMapField);
        
        // 添加维度ID映射
        CtField dimensionMapField = CtField.make(
            "private final java.util.Map worldDimensionMap = new java.util.concurrent.ConcurrentHashMap();", 
            ctClass
        );
        ctClass.addField(dimensionMapField);
        
        System.out.println("[MultiFoliaWorld]   - 添加了世界存储Map");
    }
    
    /**
     * 修改loadWorlds方法以支持动态加载
     */
    private static void modifyLoadWorldsMethod(CtClass ctClass) throws Exception {
        try {
            // 首先尝试找loadWorlds方法
            CtMethod loadWorlds = null;
            try {
                loadWorlds = ctClass.getDeclaredMethod("loadWorlds");
                System.out.println("[MultiFoliaWorld]   - 找到loadWorlds方法");
            } catch (NotFoundException e) {
                // 方法不存在，尝试其他名称
            }
            
            // 如果没找到，尝试loadLevel
            if (loadWorlds == null) {
                try {
                    loadWorlds = ctClass.getDeclaredMethod("loadLevel");
                    System.out.println("[MultiFoliaWorld]   - 找到loadLevel方法");
                } catch (NotFoundException e) {
                    // 继续尝试
                }
            }
            
            if (loadWorlds != null && !javassist.Modifier.isAbstract(loadWorlds.getModifiers())) {
                // 获取原始方法的参数类型
                CtClass[] paramTypes = loadWorlds.getParameterTypes();
                String methodSignature = loadWorlds.getSignature();
                System.out.println("[MultiFoliaWorld]   - 原始方法签名: " + loadWorlds.getName() + methodSignature);
                
                // 保存原始方法内容
                loadWorlds.setName("loadLevel_Original");
                System.out.println("[MultiFoliaWorld]   - 重命名原始方法为 loadLevel_Original");
                
                // 创建新方法，保持相同的签名，但先调用原始方法
                String newMethodBody;
                if (paramTypes.length == 0) {
                    // 无参数版本
                    newMethodBody = 
                        "public void loadLevel() { " +
                        "  System.out.println(\"[MultiFoliaWorld] 增强世界加载流程(无参数)...\"); " +
                        "  this.loadLevel_Original(); " +  // 先调用原始方法
                        "  org.virgil.multifoliaworld.agent.transformer.WorldLimitTransformer.loadAdditionalWorlds(this); " +  // 然后加载额外世界
                        "}";
                } else if (paramTypes.length == 1 && paramTypes[0].getName().equals("java.lang.String")) {
                    // String参数版本
                    newMethodBody = 
                        "public void loadLevel(String worldName) { " +
                        "  System.out.println(\"[MultiFoliaWorld] 增强世界加载流程(世界名: \" + worldName + \")...\"); " +
                        "  this.loadLevel_Original(worldName); " +  // 先调用原始方法
                        "  org.virgil.multifoliaworld.agent.transformer.WorldLimitTransformer.loadAdditionalWorlds(this); " +  // 然后加载额外世界
                        "}";
                } else {
                    // 其他参数版本，构建通用方法
                    StringBuilder params = new StringBuilder();
                    StringBuilder args = new StringBuilder();
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (i > 0) {
                            params.append(", ");
                            args.append(", ");
                        }
                        params.append(paramTypes[i].getName()).append(" arg").append(i);
                        args.append("arg").append(i);
                    }
                    newMethodBody = 
                        "public void loadLevel(" + params + ") { " +
                        "  System.out.println(\"[MultiFoliaWorld] 增强世界加载流程...\"); " +
                        "  this.loadLevel_Original(" + args + "); " +  // 先调用原始方法
                        "  org.virgil.multifoliaworld.agent.transformer.WorldLimitTransformer.loadAdditionalWorlds(this); " +  // 然后加载额外世界
                        "}";
                }
                
                CtMethod newLoadLevel = CtNewMethod.make(newMethodBody, ctClass);
                ctClass.addMethod(newLoadLevel);
                System.out.println("[MultiFoliaWorld]   - 创建了新的loadLevel方法: " + newMethodBody.substring(0, Math.min(100, newMethodBody.length())) + "...");
                
                // 如果原始方法没有String参数，但DedicatedServer需要，创建一个额外的重载
                if (paramTypes.length == 0) {
                    try {
                        // 检查是否已存在loadLevel(String)
                        ctClass.getDeclaredMethod("loadLevel", new CtClass[]{ctClass.getClassPool().get("java.lang.String")});
                        System.out.println("[MultiFoliaWorld]   - loadLevel(String)方法已存在");
                    } catch (NotFoundException e) {
                        // 创建loadLevel(String)重载
                        String overloadMethod = 
                            "public void loadLevel(String worldName) { " +
                            "  System.out.println(\"[MultiFoliaWorld] 重载方法 - 世界名: \" + worldName); " +
                            "  this.loadLevel(); " +  // 调用无参数版本
                            "}";
                        CtMethod overload = CtNewMethod.make(overloadMethod, ctClass);
                        ctClass.addMethod(overload);
                        System.out.println("[MultiFoliaWorld]   - 创建了loadLevel(String)重载方法");
                    }
                }
            } else {
                System.err.println("[MultiFoliaWorld]   - 未找到合适的世界加载方法");
            }
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld]   - 修改loadWorlds失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 添加辅助方法访问世界
     */
    private static void addHelperMethods(CtClass ctClass) throws Exception {
        // 添加通过维度ID获取世界的方法
        try {
            CtMethod getLevel = CtNewMethod.make(
                "public net.minecraft.server.level.ServerLevel getLevel(int dimension) {" +
                "    Integer dim = Integer.valueOf(dimension);" +
                "    return (net.minecraft.server.level.ServerLevel) this.serverLevelMap.get(dim);" +
                "}",
                ctClass
            );
            ctClass.addMethod(getLevel);
            System.out.println("[MultiFoliaWorld]   - 添加了getLevel(int)方法");
        } catch (DuplicateMemberException e) {
            // 方法已存在，修改它
            CtMethod existingMethod = ctClass.getDeclaredMethod("getLevel", new CtClass[]{CtClass.intType});
            existingMethod.setBody(
                "{ Integer dim = Integer.valueOf($1);" +
                "  return (net.minecraft.server.level.ServerLevel) this.serverLevelMap.get(dim); }"
            );
            System.out.println("[MultiFoliaWorld]   - 修改了现有的getLevel(int)方法");
        }
        
        // 添加获取所有世界的方法
        try {
            CtMethod getAllLevels = CtNewMethod.make(
                "public java.util.Collection getAllLevels() {" +
                "    return java.util.Collections.unmodifiableCollection(this.serverLevelMap.values());" +
                "}",
                ctClass
            );
            ctClass.addMethod(getAllLevels);
            System.out.println("[MultiFoliaWorld]   - 添加了getAllLevels()方法");
        } catch (DuplicateMemberException e) {
            // 方法已存在，修改它
            CtMethod existingMethod = ctClass.getDeclaredMethod("getAllLevels");
            existingMethod.setBody(
                "{ return java.util.Collections.unmodifiableCollection(this.serverLevelMap.values()); }"
            );
            System.out.println("[MultiFoliaWorld]   - 修改了现有的getAllLevels()方法");
        }
        
        // 添加存储世界的方法
        CtMethod putLevel = CtNewMethod.make(
            "public void putLevel(int dimension, net.minecraft.server.level.ServerLevel level) {" +
            "    Integer dim = Integer.valueOf(dimension);" +
            "    this.serverLevelMap.put(dim, level);" +
            "    if (level != null) {" +
            "        this.worldDimensionMap.put(level.dimension().location().toString(), dim);" +
            "    }" +
            "}",
            ctClass
        );
        ctClass.addMethod(putLevel);
        System.out.println("[MultiFoliaWorld]   - 添加了putLevel()方法");
    }
    
    /**
     * 替换所有serverLevels数组访问
     */
    private static void replaceArrayAccess(CtClass ctClass) throws Exception {
        // 查找并修改使用serverLevels的关键方法
        String[] targetMethods = {"getLevel", "getAllLevels", "loadWorlds", "save", "tick"};
        
        for (String methodName : targetMethods) {
            try {
                CtMethod[] methods = ctClass.getDeclaredMethods(methodName);
                for (CtMethod method : methods) {
                    if (javassist.Modifier.isAbstract(method.getModifiers()) || 
                        javassist.Modifier.isNative(method.getModifiers())) {
                        continue;
                    }
                    
                    try {
                        // 替换方法中的serverLevels访问
                        method.instrument(new ExprEditor() {
                            @Override
                            public void edit(FieldAccess f) throws CannotCompileException {
                                if (f.getFieldName().equals("serverLevels")) {
                                    if (f.isReader()) {
                                        // 将数组访问替换为Map访问
                                        f.replace("$_ = this.serverLevelMap;");
                                    }
                                }
                            }
                        });
                        
                        System.out.println("[MultiFoliaWorld]   - 修改了方法: " + method.getName());
                        
                    } catch (Exception e) {
                        // 忽略无法修改的方法
                    }
                }
                } catch (Exception e) {
                // 方法可能不存在，继续
            }
        }
        
        System.out.println("[MultiFoliaWorld]   - 完成serverLevels访问替换");
    }
    
    /**
     * 动态加载多个世界（由注入的代码调用）
     */
    public static void loadMultipleWorlds(Object minecraftServer) {
        System.out.println("[MultiFoliaWorld] ========================================");
        System.out.println("[MultiFoliaWorld] 世界限制突破 - 开始加载多个世界");
        System.out.println("[MultiFoliaWorld] ========================================");
        
        try {
            // 直接调用WorldMultiLoader
            org.virgil.multifoliaworld.agent.hook.WorldMultiLoader.loadWorlds(minecraftServer);
            
            System.out.println("[MultiFoliaWorld] 多世界加载完成");
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 加载多世界失败: " + e.getMessage());
            e.printStackTrace();
            
            // 如果失败，尝试调用原始的加载方法
            try {
                System.err.println("[MultiFoliaWorld] 尝试回退到原始世界加载...");
                java.lang.reflect.Method originalMethod = minecraftServer.getClass().getDeclaredMethod("loadLevel");
                originalMethod.setAccessible(true);
                originalMethod.invoke(minecraftServer);
            } catch (Exception ex) {
                System.err.println("[MultiFoliaWorld] 回退失败: " + ex.getMessage());
            }
        }
    }
    
    /**
     * 只加载额外的世界（原版世界已经加载完成）
     */
    public static void loadAdditionalWorlds(Object minecraftServer) {
        System.out.println("[MultiFoliaWorld] ========================================");
        System.out.println("[MultiFoliaWorld] 开始加载额外的世界（原版世界已加载）");
        System.out.println("[MultiFoliaWorld] ========================================");
        
        try {
            // 委托给WorldMultiLoader处理额外世界
            org.virgil.multifoliaworld.agent.hook.WorldMultiLoader.loadAdditionalWorldsOnly(minecraftServer);
            
            System.out.println("[MultiFoliaWorld] 额外世界加载完成");

        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 额外世界加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
