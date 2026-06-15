package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.CombatTracker;
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

public class VanillaSpawnCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;
    private final CombatTracker combatTracker;

    public VanillaSpawnCommand(MostlyVanillaSpawn plugin, CombatTracker combatTracker) {
        this.plugin = plugin;
        this.combatTracker = combatTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!plugin.getVanillaSpawnManager().isConfigured()) {
            player.sendMessage(Component.text("Vanilla spawn has not been set yet.", NamedTextColor.RED));
            return true;
        }

        Location dest = plugin.getVanillaSpawnManager().getTeleportLocation();
        if (dest == null) {
            player.sendMessage(Component.text("The vanilla spawn world is not loaded.", NamedTextColor.RED));
            return true;
        }

        if (combatTracker.isInCombat(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot teleport while in combat!", NamedTextColor.RED));
            return true;
        }

        SpawnManager sm = plugin.getSpawnManager();

        // Ops teleport instantly
        if (player.hasPermission("mostlyvanilla.spawn.admin")) {
            player.teleportAsync(dest).thenAccept(success -> {
                if (success) player.sendMessage(Component.text("Teleported to vanilla spawn.", NamedTextColor.GREEN));
            });
            return true;
        }

        // Cancel any existing countdown
        if (sm.hasPendingSpawn(player.getUniqueId())) {
            sm.removePendingSpawn(player.getUniqueId()).cancel();
        }

        player.sendMessage(Component.text("Teleporting to vanilla spawn in 5 seconds... don't move!", NamedTextColor.YELLOW));

        int[] seconds = {5};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    sm.removePendingSpawn(player.getUniqueId());
                    cancel();
                    return;
                }
                if (combatTracker.isInCombat(player.getUniqueId())) {
                    sm.removePendingSpawn(player.getUniqueId());
                    cancel();
                    player.sendMessage(Component.text("Teleport cancelled — you entered combat!", NamedTextColor.RED));
                    return;
                }
                seconds[0]--;
                if (seconds[0] > 0) {
                    player.sendMessage(Component.text(seconds[0] + "...", NamedTextColor.YELLOW));
                } else {
                    sm.removePendingSpawn(player.getUniqueId());
                    cancel();
                    player.teleportAsync(dest).thenAccept(success -> {
                        if (success)
                            player.sendMessage(Component.text("Teleported to vanilla spawn.", NamedTextColor.GREEN));
                    });
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        sm.addPendingSpawn(player.getUniqueId(), task);
        return true;
    }
}
