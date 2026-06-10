package com.mostlyvanilla.anticheat.commands;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.gui.SusGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SusCommand implements CommandExecutor {

    private final MostlyVanillaAnticheat plugin;

    public SusCommand(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /sus.", NamedTextColor.RED));
            return true;
        }
        if (!plugin.getRolesBridge().canNotifySus(player)) {
            player.sendMessage(Component.text("You don't have permission to use /sus.", NamedTextColor.RED));
            return true;
        }
        player.openInventory(new SusGui(plugin, 0).getInventory());
        return true;
    }
}
