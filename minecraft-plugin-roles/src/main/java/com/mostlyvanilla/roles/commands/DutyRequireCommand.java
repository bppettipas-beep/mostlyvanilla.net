package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class DutyRequireCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaRoles plugin;

    public DutyRequireCommand(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.roles.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        RoleManager rm = plugin.getRoleManager();
        if (args.length < 1) {
            String current = rm.getDutyRole();
            if (current == null) {
                sender.sendMessage(Component.text("Duty requirement is not currently set.", NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("Duty is required for role ", NamedTextColor.YELLOW)
                    .append(Component.text(current, NamedTextColor.WHITE))
                    .append(Component.text(" and above (equal or lower weight).", NamedTextColor.YELLOW)));
            }
            sender.sendMessage(Component.text("Usage: /dutyrequire <role|disable>", NamedTextColor.GRAY));
            return true;
        }
        String input = args[0];
        if (input.equalsIgnoreCase("disable")) {
            rm.clearDutyRole();
            sender.sendMessage(Component.text("Duty requirement disabled.", NamedTextColor.GREEN));
            return true;
        }
        if (!rm.roleExists(input)) {
            sender.sendMessage(Component.text("Role '" + input + "' does not exist.", NamedTextColor.RED));
            return true;
        }
        rm.setDutyRole(input);
        sender.sendMessage(Component.text("Duty is now required for role ", NamedTextColor.GREEN)
            .append(Component.text(input, NamedTextColor.WHITE))
            .append(Component.text(" and any role ranked at or above it.", NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.roles.admin")) return List.of();
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(plugin.getRoleManager().getRoleNames());
            opts.add("disable");
            String partial = args[0].toLowerCase();
            opts.removeIf(s -> !s.toLowerCase().startsWith(partial));
            return opts;
        }
        return List.of();
    }
}
