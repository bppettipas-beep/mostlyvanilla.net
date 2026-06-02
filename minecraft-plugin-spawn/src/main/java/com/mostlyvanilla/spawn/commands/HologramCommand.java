package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

public class HologramCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;

    public HologramCommand(MostlyVanillaSpawn plugin) {
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

        if (args.length == 1 && args[0].equalsIgnoreCase("delete")) {
            if (plugin.getHologramManager().deleteNearest(player.getLocation(), 10)) {
                player.sendMessage(Component.text("Nearest hologram deleted.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("No hologram found within 10 blocks.", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /hologram <text...>", NamedTextColor.RED));
            player.sendMessage(Component.text("       /hologram delete", NamedTextColor.RED));
            player.sendMessage(Component.text("Supports & color codes and <MiniMessage> tags.", NamedTextColor.GRAY));
            return true;
        }

        String text = Arrays.stream(args, 0, args.length).collect(Collectors.joining(" "));
        // Spawn at eye level so it floats nicely in front of the player
        Location loc = player.getEyeLocation().clone().add(0, 0.5, 0);

        plugin.getHologramManager().createHologram(loc, text);
        player.sendMessage(Component.text("Hologram created.", NamedTextColor.GREEN));
        return true;
    }
}
