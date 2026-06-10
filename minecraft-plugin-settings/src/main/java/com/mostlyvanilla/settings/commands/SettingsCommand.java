package com.mostlyvanilla.settings.commands;

import com.mostlyvanilla.settings.MostlyVanillaSettings;
import com.mostlyvanilla.settings.gui.SettingsGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {

    private final MostlyVanillaSettings plugin;

    public SettingsCommand(MostlyVanillaSettings plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /settings.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("mostlyvanilla.settings.use")) {
            player.sendMessage(Component.text("You don't have permission to use this.", NamedTextColor.RED));
            return true;
        }
        player.openInventory(new SettingsGui(player.getUniqueId(), plugin.getSettingsManager()).getInventory());
        return true;
    }
}
