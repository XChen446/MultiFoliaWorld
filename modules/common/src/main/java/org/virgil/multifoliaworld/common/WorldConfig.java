package org.virgil.multifoliaworld.common;

import java.util.List;

/**
 * 世界配置模型
 * 用于Agent和Plugin之间共享世界配置信息
 */
public class WorldConfig {
    
    /**
     * 完整的配置包装类
     */
    public static class Config {
        private List<WorldEntry> worlds;
        
        public List<WorldEntry> getWorlds() {
            return worlds;
        }
        
        public void setWorlds(List<WorldEntry> worlds) {
            this.worlds = worlds;
        }
        
        /**
         * 计算启用的世界总数（包括配套维度）
         */
        public int getEnabledWorldCount() {
            if (worlds == null) return 0;
            
            int count = 0;
            for (WorldEntry world : worlds) {
                if (world.getEnabled()) {
                    count++;
                    // 如果是NORMAL世界且启用了createDims，计算配套维度
                    if ("NORMAL".equalsIgnoreCase(world.getEnvironment()) && world.getCreateDims()) {
                        count += 2; // 下界和末地
                    }
                }
            }
            return count;
        }
    }
    
    /**
     * 单个世界配置
     */
    public static class WorldEntry {
        private String name;
        private String environment;  // NORMAL, NETHER, THE_END
        private Boolean enabled;     // 是否启用该世界 (可选，默认true)
        private Boolean createDims;  // 是否创建配套维度 (可选，默认false，仅对NORMAL世界生效)
        private String generator;    // 自定义世界生成器 (可选)
        private Long seed;           // 世界种子 (可选)
        private Boolean structures;  // 是否生成结构 (可选)
        
        // Getters
        public String getName() {
            return name;
        }
        
        public String getEnvironment() {
            return environment != null ? environment : "NORMAL";
        }
        
        public Boolean getEnabled() {
            return enabled != null ? enabled : true;
        }
        
        public Boolean getCreateDims() {
            return createDims != null ? createDims : false;
        }
        
        public String getGenerator() {
            return generator;
        }
        
        public Long getSeed() {
            return seed;
        }
        
        public Boolean getStructures() {
            return structures != null ? structures : true;
        }
        
        // Setters
        public void setName(String name) {
            this.name = name;
        }
        
        public void setEnvironment(String environment) {
            this.environment = environment;
        }
        
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
        
        public void setCreateDims(Boolean createDims) {
            this.createDims = createDims;
        }
        
        public void setGenerator(String generator) {
            this.generator = generator;
        }
        
        public void setSeed(Long seed) {
            this.seed = seed;
        }
        
        public void setStructures(Boolean structures) {
            this.structures = structures;
        }
        
        @Override
        public String toString() {
            return "WorldEntry{" +
                    "name='" + name + '\'' +
                    ", environment='" + environment + '\'' +
                    ", enabled=" + enabled +
                    ", createDims=" + createDims +
                    ", generator='" + generator + '\'' +
                    ", seed=" + seed +
                    ", structures=" + structures +
                    '}';
        }
    }
}

