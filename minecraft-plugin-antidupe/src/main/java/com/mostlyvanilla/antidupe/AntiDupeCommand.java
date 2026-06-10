package com.mostlyvanilla.antidupe;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.stream.Collectors;

public class AntiDupeCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaAntiDupe plugin;

    public AntiDupeCommand(MostlyVanillaAntiDupe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.antidupe.admin") && !sender.isOp()) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6AntiDupe §7— /antidupe <clearflags|reload> [player]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aAntiDupe config reloaded.");
            }
            case "clearflags" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /antidupe clearflags <player>");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore()) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                plugin.removeData(target.getUniqueId());
                sender.sendMessage("§aCleared AntiDupe session data for §f" + args[1] + "§a.");
            }
            default -> sender.sendMessage("§cUnknown subcommand. Use: clearflags, reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("clearflags", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("clearflags")) {
            String input = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
