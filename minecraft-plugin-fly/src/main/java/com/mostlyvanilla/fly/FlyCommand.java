package com.mostlyvanilla.fly;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FlyCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            // Toggle own flight
            if (!canUseFly(staff)) {
                staff.sendMessage(Component.text("You don't have permission to fly.", NamedTextColor.RED));
                return true;
            }
            toggleFlight(staff, staff, true);
            return true;
        }

        // Target another player
        if (!staff.hasPermission("mv.fly.other")) {
            staff.sendMessage(Component.text("You don't have permission to toggle flight for others.", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            staff.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
            return true;
        }

        toggleFlight(staff, target, false);
        return true;
    }

    private void toggleFlight(Player staff, Player target, boolean isSelf) {
        boolean nowFlying = !target.getAllowFlight();
        target.setAllowFlight(nowFlying);
        if (!nowFlying) target.setFlying(false);

        Component state = nowFlying
            ? Component.text("enabled", NamedTextColor.GREEN)
            : Component.text("disabled", NamedTextColor.RED);

        if (isSelf) {
            staff.sendMessage(Component.text("Flight ", NamedTextColor.GRAY).append(state).append(Component.text(".", NamedTextColor.GRAY)));
        } else {
            staff.sendMessage(Component.text("Flight ", NamedTextColor.GRAY).append(state)
                .append(Component.text(" for ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.GRAY)));
            target.sendMessage(Component.text("Your flight has been ", NamedTextColor.GRAY).append(state)
                .append(Component.text(" by staff.", NamedTextColor.GRAY)));
        }
    }

    private boolean canUseFly(Player player) {
        if (player.hasPermission("mv.fly")) return true;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            return (boolean) rm.getClass().getMethod("canUseFly", UUID.class).invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("mv.fly.other")) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
