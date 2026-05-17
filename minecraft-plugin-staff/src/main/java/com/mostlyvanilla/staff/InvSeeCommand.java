package com.mostlyvanilla.staff;

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

public class InvSeeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        boolean isInvSee  = label.equalsIgnoreCase("invsee");
        boolean isCheckEc = label.equalsIgnoreCase("checkec");

        if (isInvSee && !canUseInvSee(staff)) {
            staff.sendMessage(Component.text("You don't have permission to use /invsee.", NamedTextColor.RED));
            return true;
        }
        if (isCheckEc && !canUseEcSee(staff)) {
            staff.sendMessage(Component.text("You don't have permission to use /checkec.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            String usage = isInvSee ? "Usage: /invsee <player>" : "Usage: /checkec <player>";
            staff.sendMessage(Component.text(usage, NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
            return true;
        }

        if (isInvSee) {
            staff.openInventory(target.getInventory());
        } else {
            staff.openInventory(target.getEnderChest());
        }

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

    private boolean canUseInvSee(Player player) {
        if (player.isOp()) return true;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            String invSeeRole = (String) rm.getClass().getMethod("getInvSeeRole").invoke(rm);
            if (invSeeRole == null) return false;
            return (boolean) rm.getClass().getMethod("canUseInvSee", UUID.class).invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canUseEcSee(Player player) {
        if (player.isOp()) return true;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            String ecSeeRole = (String) rm.getClass().getMethod("getEcSeeRole").invoke(rm);
            if (ecSeeRole == null) return false;
            return (boolean) rm.getClass().getMethod("canUseEcSee", UUID.class).invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}
