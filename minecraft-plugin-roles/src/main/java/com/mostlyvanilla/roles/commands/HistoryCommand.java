package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.HistoryGui;
import com.mostlyvanilla.roles.MostlyVanillaRoles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class HistoryCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaRoles plugin;
    private final HistoryGui         gui;

    public HistoryCommand(MostlyVanillaRoles plugin, HistoryGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (!player.isOp() && !plugin.getRoleManager().canUseHistory(player.getUniqueId())) {
            player.sendMessage(Component.text("You don't have permission to use /history.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /history <player>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];

        // Try online first (immediate)
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            gui.open(player, online.getUniqueId(), online.getName());
            return true;
        }

        // Offline player lookup (async to avoid blocking main thread)
        player.sendMessage(Component.text("Looking up " + targetName + "...", NamedTextColor.GRAY));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            String resolvedName = offline.getName() != null ? offline.getName() : targetName;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                gui.open(player, offline.getUniqueId(), resolvedName);
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
