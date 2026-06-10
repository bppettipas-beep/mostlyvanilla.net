package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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
        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;

        boolean hasPermission = isPlayer
                ? player.hasPermission("mostlyvanilla.spawn.admin")
                : sender.hasPermission("mostlyvanilla.spawn.admin") || sender instanceof ConsoleCommandSender;
        if (!hasPermission) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        // /hologram delete
        if (args.length == 1 && args[0].equalsIgnoreCase("delete")) {
            if (!requirePlayer(sender)) return true;
            if (!requireSpawnWorld(player)) return true;
            if (plugin.getHologramManager().deleteNearest(player.getLocation(), 10)) {
                player.sendMessage(Component.text("Nearest hologram deleted.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("No hologram found within 10 blocks.", NamedTextColor.RED));
            }
            return true;
        }

        // /hologram deleteall
        if (args.length == 1 && args[0].equalsIgnoreCase("deleteall")) {
            if (!requirePlayer(sender)) return true;
            plugin.getHologramManager().openPurge(player);
            return true;
        }

        // /hologram <x> <y> <z> <text...>
        if (args.length >= 4 && isDouble(args[0]) && isDouble(args[1]) && isDouble(args[2])) {
            World spawnWorld = plugin.getSpawnManager().getSpawnWorld();
            if (spawnWorld == null) {
                sender.sendMessage(Component.text("Spawn world is not loaded.", NamedTextColor.RED));
                return true;
            }
            double x = Double.parseDouble(args[0]);
            double y = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);
            String text = Arrays.stream(args, 3, args.length).collect(Collectors.joining(" "));
            plugin.getHologramManager().createHologram(new Location(spawnWorld, x, y, z), text);
            sender.sendMessage(Component.text("Hologram created at " + x + " " + y + " " + z + ".", NamedTextColor.GREEN));
            return true;
        }

        // /hologram player <name> <text...>
        if (args.length >= 3 && args[0].equalsIgnoreCase("player")) {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                return true;
            }
            if (!plugin.getSpawnManager().isInSpawnWorld(target.getLocation())) {
                sender.sendMessage(Component.text(target.getName() + " is not in the spawn world.", NamedTextColor.RED));
                return true;
            }
            String text = Arrays.stream(args, 2, args.length).collect(Collectors.joining(" "));
            Location loc = target.getEyeLocation().clone().add(0, 0.5, 0);
            plugin.getHologramManager().createHologram(loc, text);
            sender.sendMessage(Component.text("Hologram created at " + target.getName() + "'s eyes.", NamedTextColor.GREEN));
            return true;
        }

        // /hologram <text...>  — in-game only, places at your own eye level
        if (args.length >= 1) {
            if (!requirePlayer(sender)) return true;
            if (!requireSpawnWorld(player)) return true;
            String text = Arrays.stream(args, 0, args.length).collect(Collectors.joining(" "));
            Location loc = player.getEyeLocation().clone().add(0, 0.5, 0);
            plugin.getHologramManager().createHologram(loc, text);
            player.sendMessage(Component.text("Hologram created.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hologram <text...>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hologram <x> <y> <z> <text...>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hologram player <name> <text...>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hologram delete", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hologram deleteall", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Supports & color codes and <MiniMessage> tags.", NamedTextColor.GRAY));
        return true;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sender.sendMessage("This subcommand must be run by a player. Use: hologram <x> <y> <z> <text...>");
        return false;
    }

    private boolean requireSpawnWorld(Player player) {
        if (plugin.getSpawnManager().isInSpawnWorld(player.getLocation())) return true;
        player.sendMessage(Component.text("You must be in the spawn world to use this.", NamedTextColor.RED));
        return false;
    }

    private static boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; }
        catch (NumberFormatException e) { return false; }
    }
}
