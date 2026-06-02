package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlockHereCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;

    public BlockHereCommand(MostlyVanillaSpawn plugin) {
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
        if (!plugin.getSpawnManager().isInSpawnWorld(player.getLocation())) {
            player.sendMessage(Component.text("You must be in the spawn world to use this.", NamedTextColor.RED));
            return true;
        }

        Location feet = player.getLocation();
        Block below = feet.getWorld().getBlockAt(feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ());
        below.setType(Material.COBBLESTONE);
        player.sendMessage(Component.text(
            "Placed cobblestone at " + below.getX() + ", " + below.getY() + ", " + below.getZ() + ".",
            NamedTextColor.GREEN
        ));
        return true;
    }
}
