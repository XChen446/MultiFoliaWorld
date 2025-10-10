package org.virgil.multifoliaworld.plugin.command;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.virgil.multifoliaworld.common.WorldBridge;
import org.virgil.multifoliaworld.common.WorldConfig;
import org.virgil.multifoliaworld.plugin.MultiFoliaWorldPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 世界管理命令
 * 
 * 命令列表:
 * /mfw list - 列出所有世界
 * /mfw info <world> - 查看世界信息
 * /mfw tp <world> - 传送到世界
 * /mfw add <name> <environment> - 添加世界配置（需重启生效）
 * /mfw disable <world> - 禁用世界（需重启生效）
 * /mfw enable <world> - 启用世界（需重启生效）
 * /mfw help - 帮助信息
 * 
 * 注意：所有配置修改需要重启服务器才能生效
 * 不支持运行时动态创建/卸载世界（Folia多线程架构限制）
 */
public class WorldCommand implements CommandExecutor, TabCompleter {
    
    private final MultiFoliaWorldPlugin plugin;
    
    public WorldCommand(MultiFoliaWorldPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "list":
                return listWorlds(sender);
            
            case "info":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /mfw info <世界名>");
                    return true;
                }
                return showWorldInfo(sender, args[1]);
            
            case "tp":
            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /mfw tp <世界名>");
                    return true;
                }
                return teleportToWorld((Player) sender, args[1]);
            
            case "add":
                if (!sender.hasPermission("multifoliaworld.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /mfw add <世界名> <环境>");
                    sender.sendMessage(ChatColor.YELLOW + "环境: NORMAL, NETHER, THE_END");
                    return true;
                }
                return addWorldConfig(sender, args[1], args[2]);
            
            case "disable":
                if (!sender.hasPermission("multifoliaworld.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /mfw disable <世界名>");
                    return true;
                }
                return toggleWorldConfig(sender, args[1], false);
            
            case "enable":
                if (!sender.hasPermission("multifoliaworld.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /mfw enable <世界名>");
                    return true;
                }
                return toggleWorldConfig(sender, args[1], true);
            
            case "help":
            default:
                return showHelp(sender);
        }
    }
    
    /**
     * 显示帮助信息
     */
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== MultiFoliaWorld ==========");
        sender.sendMessage(ChatColor.YELLOW + "/mfw list" + ChatColor.WHITE + " - 列出所有世界");
        sender.sendMessage(ChatColor.YELLOW + "/mfw info <世界>" + ChatColor.WHITE + " - 查看世界信息");
        sender.sendMessage(ChatColor.YELLOW + "/mfw tp <世界>" + ChatColor.WHITE + " - 传送到世界");
        
        if (sender.hasPermission("multifoliaworld.admin")) {
            sender.sendMessage(ChatColor.AQUA + "管理员命令:");
            sender.sendMessage(ChatColor.YELLOW + "/mfw add <名称> <环境>" + ChatColor.WHITE + " - 添加世界配置");
            sender.sendMessage(ChatColor.YELLOW + "/mfw enable <世界>" + ChatColor.WHITE + " - 启用世界");
            sender.sendMessage(ChatColor.YELLOW + "/mfw disable <世界>" + ChatColor.WHITE + " - 禁用世界");
            sender.sendMessage(ChatColor.GRAY + "  注意: 配置修改需重启服务器生效");
        }
        
        sender.sendMessage(ChatColor.YELLOW + "/mfw help" + ChatColor.WHITE + " - 显示此帮助");
        sender.sendMessage(ChatColor.GOLD + "====================================");
        
        if (!WorldBridge.isAgentActive()) {
            sender.sendMessage(ChatColor.RED + "警告: Agent未激活！部分功能不可用");
        }
        
        return true;
    }
    
    /**
     * 列出所有世界
     */
    private boolean listWorlds(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== 世界列表 ==========");
        
        // 显示Bukkit已加载的世界
        sender.sendMessage(ChatColor.YELLOW + "已加载的世界:");
        for (World world : plugin.getServer().getWorlds()) {
            String status = ChatColor.GREEN + "✓ ";
            String info = status + ChatColor.WHITE + world.getName() + 
                         ChatColor.GRAY + " (" + world.getEnvironment() + ")";
            sender.sendMessage(info);
        }
        
        // 如果Agent激活，显示配置的世界
        if (WorldBridge.isAgentActive()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "Agent配置的世界:");
            
            Map<String, WorldConfig.WorldEntry> configs = WorldBridge.getAllWorldConfigs();
            if (configs.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "  (无配置)");
            } else {
                for (Map.Entry<String, WorldConfig.WorldEntry> entry : configs.entrySet()) {
                    WorldConfig.WorldEntry config = entry.getValue();
                    String info = ChatColor.AQUA + "• " + ChatColor.WHITE + config.getName() +
                                 ChatColor.GRAY + " (" + config.getEnvironment() + ")";
                    sender.sendMessage(info);
                }
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "==============================");
            return true;
        }
    
    /**
     * 显示世界信息
     */
    private boolean showWorldInfo(CommandSender sender, String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "世界不存在: " + worldName);
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "========== 世界信息 ==========");
        sender.sendMessage(ChatColor.YELLOW + "名称: " + ChatColor.WHITE + world.getName());
        sender.sendMessage(ChatColor.YELLOW + "环境: " + ChatColor.WHITE + world.getEnvironment());
        sender.sendMessage(ChatColor.YELLOW + "种子: " + ChatColor.WHITE + world.getSeed());
        sender.sendMessage(ChatColor.YELLOW + "出生点: " + ChatColor.WHITE + 
                          world.getSpawnLocation().getBlockX() + ", " +
                          world.getSpawnLocation().getBlockY() + ", " +
                          world.getSpawnLocation().getBlockZ());
        sender.sendMessage(ChatColor.YELLOW + "玩家数: " + ChatColor.WHITE + world.getPlayers().size());
        sender.sendMessage(ChatColor.YELLOW + "实体数: " + ChatColor.WHITE + world.getEntities().size());
        sender.sendMessage(ChatColor.YELLOW + "已加载区块: " + ChatColor.WHITE + world.getLoadedChunks().length);
        
        // 如果Agent激活，显示配置信息
        if (WorldBridge.isAgentActive()) {
            WorldConfig.WorldEntry config = WorldBridge.getWorldConfig(worldName);
            if (config != null) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Agent配置:");
                sender.sendMessage(ChatColor.YELLOW + "  生成结构: " + ChatColor.WHITE + config.getStructures());
                if (config.getGenerator() != null) {
                    sender.sendMessage(ChatColor.YELLOW + "  生成器: " + ChatColor.WHITE + config.getGenerator());
                }
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "==============================");
        return true;
    }
    
    /**
     * 传送到世界
     */
    private boolean teleportToWorld(Player player, String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        
        if (world == null) {
            player.sendMessage(ChatColor.RED + "世界不存在: " + worldName);
            return true;
        }
        
        // 使用Folia的异步传送API
        player.teleportAsync(world.getSpawnLocation()).thenAccept(result -> {
            if (result) {
                player.sendMessage(ChatColor.GREEN + "已传送到世界: " + worldName);
            } else {
                player.sendMessage(ChatColor.RED + "传送失败");
            }
        });
        
        return true;
    }
    
    /**
     * 添加世界配置
     * 注意：不会立即创建世界，需要重启服务器
     */
    private boolean addWorldConfig(CommandSender sender, String worldName, String environmentStr) {
        sender.sendMessage(ChatColor.YELLOW + "正在添加世界配置...");
        sender.sendMessage(ChatColor.RED + "⚠ 警告: 此功能需要直接操作配置文件");
        sender.sendMessage(ChatColor.RED + "⚠ 建议手动编辑 config/worlds.json 并重启服务器");
        sender.sendMessage(ChatColor.YELLOW + "示例配置:");
        sender.sendMessage(ChatColor.GRAY + "{");
        sender.sendMessage(ChatColor.GRAY + "  \"name\": \"" + worldName + "\",");
        sender.sendMessage(ChatColor.GRAY + "  \"environment\": \"" + environmentStr + "\",");
        sender.sendMessage(ChatColor.GRAY + "  \"enabled\": true,");
        sender.sendMessage(ChatColor.GRAY + "  \"structures\": true");
        sender.sendMessage(ChatColor.GRAY + "}");
        
        return true;
    }
    
    /**
     * 切换世界启用状态
     * 注意：不会立即生效，需要重启服务器
     */
    private boolean toggleWorldConfig(CommandSender sender, String worldName, boolean enable) {
        String action = enable ? "启用" : "禁用";
        
        sender.sendMessage(ChatColor.YELLOW + "正在" + action + "世界: " + worldName);
        sender.sendMessage(ChatColor.RED + "⚠ 警告: 此功能需要直接操作配置文件");
        sender.sendMessage(ChatColor.RED + "⚠ 请手动编辑 config/worlds.json");
        sender.sendMessage(ChatColor.YELLOW + "将 \"" + worldName + "\" 的 enabled 设置为: " + enable);
        sender.sendMessage(ChatColor.GRAY + "然后重启服务器使更改生效");
        sender.sendMessage(ChatColor.GOLD + "");
        sender.sendMessage(ChatColor.GOLD + "为什么不支持动态" + action + "？");
        sender.sendMessage(ChatColor.WHITE + "Folia使用区域化多线程架构，运行时修改世界");
        sender.sendMessage(ChatColor.WHITE + "可能导致区域线程池状态不一致，引发严重问题。");
        sender.sendMessage(ChatColor.WHITE + "因此，所有世界配置修改必须在服务器启动前完成。");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一个参数：子命令
            List<String> commands = new ArrayList<>(Arrays.asList("list", "info", "tp", "help"));
            if (sender.hasPermission("multifoliaworld.admin")) {
                commands.addAll(Arrays.asList("add", "enable", "disable"));
            }
            completions.addAll(commands);
        } else if (args.length == 2) {
            // 第二个参数：世界名或环境
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("info") || subCommand.equals("tp") || subCommand.equals("teleport") ||
                subCommand.equals("enable") || subCommand.equals("disable")) {
                completions.addAll(plugin.getServer().getWorlds().stream()
                        .map(World::getName)
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 3) {
            // 第三个参数：环境类型（仅用于add命令）
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("add")) {
                completions.addAll(Arrays.asList("NORMAL", "NETHER", "THE_END"));
            }
        }
        
        // 过滤匹配的结果
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}

