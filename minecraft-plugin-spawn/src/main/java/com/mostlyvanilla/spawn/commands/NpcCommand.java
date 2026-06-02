package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

public class NpcCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;

    public NpcCommand(MostlyVanillaSpawn plugin) {
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
        if (plugin.getNpcManager() == null) {
            player.sendMessage(Component.text("Citizens is not installed. NPCs are unavailable.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("delete")) {
            if (plugin.getNpcManager().deleteNearest(player.getLocation(), 10)) {
                player.sendMessage(Component.text("Nearest NPC deleted.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("No NPC found within 10 blocks.", NamedTextColor.RED));
            }
            return true;
        }

        // /npc <skin> <command> [hologram text...]
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /createnpc <skin> <command> [hologram text...]", NamedTextColor.RED));
            player.sendMessage(Component.text("       /createnpc delete", NamedTextColor.RED));
            return true;
        }

        String skin     = args[0];
        String cmd      = args[1].replace("/", "");
        String holoText = args.length > 2
            ? Arrays.stream(args, 2, args.length).collect(Collectors.joining(" "))
            : null;

        plugin.getNpcManager().create(player.getLocation(), skin, cmd, holoText);

        player.sendMessage(Component.text("NPC created with skin ", NamedTextColor.GREEN)
            .append(Component.text(skin, NamedTextColor.WHITE))
            .append(Component.text(" → runs /", NamedTextColor.GREEN))
            .append(Component.text(cmd, NamedTextColor.WHITE)));
        if (holoText != null)
            player.sendMessage(Component.text("Hologram: ", NamedTextColor.GREEN)
                .append(Component.text(holoText, NamedTextColor.WHITE)));
        return true;
    }
}
