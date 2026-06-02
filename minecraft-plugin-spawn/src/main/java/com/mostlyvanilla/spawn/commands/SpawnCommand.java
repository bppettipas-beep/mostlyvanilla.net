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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class SpawnCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;

    public SpawnCommand(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        SpawnManager sm = plugin.getSpawnManager();

        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("mostlyvanilla.spawn.admin")) {
                player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            if (!sm.isInSpawnWorld(player.getLocation())) {
                player.sendMessage(Component.text("You must be in the spawn world to set the spawn point.", NamedTextColor.RED));
                player.sendMessage(Component.text("Use /spawn to get there first.", NamedTextColor.GRAY));
                return true;
            }
            sm.setSpawn(player.getLocation());
            player.sendMessage(Component.text("Spawn point set.", NamedTextColor.GREEN));
            return true;
        }

        // /spawn — ops teleport instantly, everyone else gets a 5s countdown
        if (!sm.isSpawnSet()) {
            if (player.hasPermission("mostlyvanilla.spawn.admin")) {
                Location fallback = new Location(sm.getSpawnWorld(), 0, 64, 0);
                sm.flagTeleport(player.getUniqueId());
                player.teleport(fallback);
                player.sendMessage(Component.text("Spawn point not configured — dropped at world origin.", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Stand where you want spawn to be and run /setspawn.", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("Spawn has not been set up yet.", NamedTextColor.RED));
            }
            return true;
        }

        if (player.hasPermission("mostlyvanilla.spawn.admin")) {
            sm.flagTeleport(player.getUniqueId());
            player.teleport(sm.getSpawnPoint());
            player.sendMessage(Component.text("Teleported to spawn.", NamedTextColor.GREEN));
            return true;
        }

        // Cancel any existing countdown for this player
        if (sm.hasPendingSpawn(player.getUniqueId())) {
            sm.removePendingSpawn(player.getUniqueId()).cancel();
        }

        player.sendMessage(Component.text("Teleporting to spawn in 5 seconds... don't move!", NamedTextColor.YELLOW));

        int[] seconds = {5};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    sm.removePendingSpawn(player.getUniqueId());
                    cancel();
                    return;
                }
                seconds[0]--;
                if (seconds[0] > 0) {
                    player.sendMessage(Component.text(seconds[0] + "...", NamedTextColor.YELLOW));
                } else {
                    sm.removePendingSpawn(player.getUniqueId());
                    cancel();
                    sm.flagTeleport(player.getUniqueId());
                    player.teleport(sm.getSpawnPoint());
                    player.sendMessage(Component.text("Teleported to spawn.", NamedTextColor.GREEN));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        sm.addPendingSpawn(player.getUniqueId(), task);
        return true;
    }
}
