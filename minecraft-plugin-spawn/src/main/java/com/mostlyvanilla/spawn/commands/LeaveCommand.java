package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import com.mostlyvanilla.spawn.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaveCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;

    public LeaveCommand(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        SpawnManager sm = plugin.getSpawnManager();

        if (!sm.isInSpawnWorld(player.getLocation())) {
            player.sendMessage(Component.text("You're not in spawn.", NamedTextColor.RED));
            return true;
        }

        Location dest = sm.getLastOverworld(player.getUniqueId());
        if (dest == null) {
            player.sendMessage(Component.text("No overworld location found to return to.", NamedTextColor.RED));
            return true;
        }

        player.teleport(dest);
        player.sendMessage(Component.text("Returned to the overworld.", NamedTextColor.GREEN));
        return true;
    }
}
