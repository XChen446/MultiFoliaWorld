package org.virgil.multifoliaworld.agent.hook;

/**
 * 世界创建钩子
 * 用于在CraftServer.createWorld()被调用时进行拦截
 * 
 * 目的：
 * 1. 验证世界创建请求的合法性
 * 2. 确保世界创建在正确的线程上下文中执行
 * 3. 记录世界创建日志
 */
public class WorldCreateHook {
    
    /**
     * 在创建世界之前调用
     * @param worldCreator WorldCreator对象
     */
    public static void beforeCreateWorld(Object worldCreator) {
        try {
            System.out.println("[MultiFoliaWorld] 检测到世界创建请求");
            System.out.println("[MultiFoliaWorld] WorldCreator: " + worldCreator);
            
            // 获取世界名称
            if (worldCreator != null) {
                try {
                    java.lang.reflect.Method getNameMethod = worldCreator.getClass().getMethod("name");
                    Object worldName = getNameMethod.invoke(worldCreator);
                    System.out.println("[MultiFoliaWorld] 正在创建世界: " + worldName);
                } catch (Exception e) {
                    // 忽略异常
                }
            }
            
            // 在这里可以添加额外的验证逻辑
            // 例如：检查世界是否在配置中，验证命名规范等
            
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 世界创建钩子执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 在创建世界之后调用
     * @param world 创建的World对象
     */
    public static void afterCreateWorld(Object world) {
        try {
            if (world != null) {
                System.out.println("[MultiFoliaWorld] 世界创建成功: " + world);
            }
        } catch (Exception e) {
            System.err.println("[MultiFoliaWorld] 世界创建后钩子执行失败: " + e.getMessage());
        }
    }
}

