package com.mostlyvanilla.staff;

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

public class WipeCommand implements CommandExecutor, TabCompleter {

    private final WipeManager manager;

    public WipeCommand(WipeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!staff.isOp()) {
            staff.sendMessage(Component.text("Only operators can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            staff.sendMessage(Component.text("Usage: /wipe <player>", NamedTextColor.RED));
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            manager.openStep1(staff, online);
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore()) {
            staff.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        manager.openStep1(staff, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.isOp()) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .toList();
        }
        return List.of();
    }
}
