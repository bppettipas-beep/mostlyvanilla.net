package com.mostlyvanilla.spawn.listeners;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import com.mostlyvanilla.spawn.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

public class SpawnListener implements Listener {

    private final MostlyVanillaSpawn plugin;

    public SpawnListener(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    /** Block any teleport whose destination is the spawn world unless it came from /spawn. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        SpawnManager sm = plugin.getSpawnManager();
        Location to = event.getTo();

        if (to == null || !sm.isInSpawnWorld(to)) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;
        if (sm.consumeTeleportFlag(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("You can only reach spawn via /spawn.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        Player player = event.getPlayer();
        SpawnManager sm = plugin.getSpawnManager();

        // Cancel /spawn countdown if the player moved a full block
        if (sm.hasPendingSpawn(player.getUniqueId())
                && (from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ())) {
            BukkitTask task = sm.removePendingSpawn(player.getUniqueId());
            if (task != null) task.cancel();
            player.sendMessage(Component.text("Teleportation cancelled — you moved.", NamedTextColor.RED));
        }

        if (!sm.isInSpawnWorld(player.getLocation())) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;

        // Fell off the edge into the void — send back
        if (to.getY() < -10 && sm.isSpawnSet()) {
            sm.flagTeleport(player.getUniqueId());
            player.teleport(sm.getSpawnPoint());
            player.sendMessage(Component.text("Teleported back to spawn.", NamedTextColor.YELLOW));
            return;
        }

        // Fell through the drop zone rectangle — instant return to spawn
        if (sm.isSpawnSet() && sm.isBelowDropZone(to)) {
            sm.flagTeleport(player.getUniqueId());
            player.teleport(sm.getSpawnPoint());
        }
    }

    /** Portals in the spawn world go nowhere. */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (plugin.getSpawnManager().isInSpawnWorld(event.getPlayer().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Dying in spawn respawns at the spawn point. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        SpawnManager sm = plugin.getSpawnManager();
        if (!sm.isInSpawnWorld(event.getPlayer().getLocation())) return;
        if (!sm.isSpawnSet()) return;
        event.setRespawnLocation(sm.getSpawnPoint());
    }

    /** No breaking blocks in the spawn world unless admin. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getSpawnManager().isInSpawnWorld(event.getBlock().getLocation())) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
    }

    /** No placing blocks in the spawn world unless admin. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getSpawnManager().isInSpawnWorld(event.getBlock().getLocation())) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
    }

    /** Block ender pearls, XP bottles, and wind charges in the spawn world. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (!plugin.getSpawnManager().isInSpawnWorld(player.getLocation())) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;

        boolean blocked = event.getEntity() instanceof EnderPearl
            || event.getEntity() instanceof ThrownExpBottle
            || event.getEntity().getType().name().equals("WIND_CHARGE");

        if (blocked) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't use that at spawn.", NamedTextColor.RED));
        }
    }

    /** Block elytra gliding in the spawn world. */
    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getSpawnManager().isInSpawnWorld(player.getLocation())) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
        player.sendMessage(Component.text("You can't use elytra at spawn.", NamedTextColor.RED));
    }
}
