package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanillaSpawnSetCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;

    public VanillaSpawnSetCommand(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("mostlyvanilla.spawn.admin")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        plugin.getVanillaSpawnManager().setSpawn(player);
        player.sendMessage(Component.text("Vanilla spawn set! ", NamedTextColor.GREEN)
            .append(Component.text("A 10×10×10 protected zone has been created here.", NamedTextColor.GRAY)));
        return true;
    }
}
