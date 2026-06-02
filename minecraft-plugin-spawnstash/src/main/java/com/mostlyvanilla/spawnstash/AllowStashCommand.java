package com.mostlyvanilla.spawnstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class AllowStashCommand implements CommandExecutor, TabCompleter {

    private final RolesBridge roles;

    public AllowStashCommand(RolesBridge roles) {
        this.roles = roles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /allowspawnstash <role|disable>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("disable")) {
            roles.setAllowedRole(null);
            sender.sendMessage(Component.text(
                "SpawnStash disabled — only ops can use /spawnstash.", NamedTextColor.YELLOW));
            return true;
        }

        String role = args[0].toLowerCase();
        if (!roles.roleExists(role)) {
            sender.sendMessage(Component.text("Role '" + role + "' does not exist.", NamedTextColor.RED));
            return true;
        }

        roles.setAllowedRole(role);
        sender.sendMessage(Component.text(
            "Players with role " + role + " or higher can now use /spawnstash.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
