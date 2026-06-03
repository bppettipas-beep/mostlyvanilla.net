package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DutyCommand implements CommandExecutor {

    private final MostlyVanillaRoles plugin;

    public DutyCommand(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        RoleManager rm = plugin.getRoleManager();
        if (!rm.isDutyRequired(player.getUniqueId()) && !player.hasPermission("mostlyvanilla.roles.admin")) {
            player.sendMessage(Component.text("Duty mode is not required for your role.", NamedTextColor.YELLOW));
            return true;
        }
        boolean onDuty = rm.toggleDuty(player.getUniqueId());
        if (onDuty) {
            player.sendMessage(Component.text("You are now ")
                .append(Component.text("ON DUTY", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(". Staff permissions are now active.", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("You are now ", NamedTextColor.YELLOW)
                .append(Component.text("OFF DUTY", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text(". Staff permissions have been disabled.", NamedTextColor.YELLOW)));
        }
        return true;
    }
}
