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
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public class StaffCommand implements CommandExecutor, TabCompleter {

    private final StaffManager manager;

    public StaffCommand(StaffManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!staff.hasPermission("mv.staff")) {
            staff.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (!canUseStaff(staff.getUniqueId())) {
            staff.sendMessage(Component.text("You don't have a high enough role to use the staff panel.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            staff.sendMessage(Component.text("Usage: /staff <player>", NamedTextColor.RED));
            return true;
        }

        // Online player first
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            manager.openStaffPanel(staff, online);
            return true;
        }

        // Offline player from cache
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore()) {
            staff.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        manager.openStaffPanel(staff, target);
        return true;
    }

    private boolean canUseStaff(UUID uuid) {
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return true;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            return (boolean) rm.getClass().getMethod("canUseStaff", UUID.class).invoke(rm, uuid);
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .toList();
        }
        return List.of();
    }
}
