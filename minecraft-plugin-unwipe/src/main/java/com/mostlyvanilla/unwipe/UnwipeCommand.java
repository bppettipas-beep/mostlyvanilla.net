package com.mostlyvanilla.unwipe;

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
import java.util.UUID;

public class UnwipeCommand implements CommandExecutor, TabCompleter {

    private final UnwipeManager manager;

    public UnwipeCommand(UnwipeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mv.unwipe") && !(sender instanceof Player p && p.isOp())
                && !sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /unwipe <player>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];

        // Try online first
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            if (!manager.hasSnapshot(online.getUniqueId())) {
                sender.sendMessage(Component.text("No snapshot found for ", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.RED)));
                return true;
            }
            manager.restoreSnapshot(sender, online.getUniqueId(), online.getName());
            return true;
        }

        // Offline lookup
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : targetName;

        if (!target.hasPlayedBefore() && !manager.hasSnapshot(uuid)) {
            sender.sendMessage(Component.text("No snapshot found for ", NamedTextColor.RED)
                .append(Component.text(targetName, NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.RED)));
            return true;
        }

        if (!manager.hasSnapshot(uuid)) {
            sender.sendMessage(Component.text("No snapshot found for ", NamedTextColor.RED)
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.RED)));
            return true;
        }

        manager.restoreSnapshot(sender, uuid, name);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("mv.unwipe")) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .toList();
        }
        return List.of();
    }
}
