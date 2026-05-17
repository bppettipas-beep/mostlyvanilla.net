package com.mostlyvanilla.home.commands;

import com.mostlyvanilla.home.HomeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HomeRoleCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public HomeRoleCommand(HomeManager homeManager) { this.homeManager = homeManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.home.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /homerole <role> <amount>", NamedTextColor.RED));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Amount must be a whole number.", NamedTextColor.RED));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(Component.text("Amount must be 0 or greater.", NamedTextColor.RED));
            return true;
        }
        homeManager.setHomeLimitForRole(args[0], amount);
        sender.sendMessage(Component.text("Set home limit for role ", NamedTextColor.GREEN)
            .append(Component.text(args[0], NamedTextColor.GOLD))
            .append(Component.text(" to ", NamedTextColor.GREEN))
            .append(Component.text(String.valueOf(amount), NamedTextColor.YELLOW))
            .append(Component.text(".", NamedTextColor.GREEN)));
        return true;
    }
}
