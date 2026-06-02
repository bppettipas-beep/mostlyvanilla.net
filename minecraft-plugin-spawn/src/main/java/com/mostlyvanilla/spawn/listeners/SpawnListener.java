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
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.scheduler.BukkitTask;

public class SpawnListener implements Listener {

    private final MostlyVanillaSpawn plugin;

    public SpawnListener(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    private boolean inSpawn(Location loc) {
        return plugin.getSpawnManager().isInSpawnWorld(loc);
    }

    // ── Teleport / movement ───────────────────────────────────────────────────

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

        if (to.getY() < -10 && sm.isSpawnSet()) {
            sm.flagTeleport(player.getUniqueId());
            player.teleport(sm.getSpawnPoint());
            player.sendMessage(Component.text("Teleported back to spawn.", NamedTextColor.YELLOW));
            return;
        }
        if (sm.isSpawnSet() && sm.isBelowDropZone(to)) {
            sm.flagTeleport(player.getUniqueId());
            player.teleport(sm.getSpawnPoint());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (inSpawn(event.getPlayer().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        SpawnManager sm = plugin.getSpawnManager();
        if (!sm.isInSpawnWorld(event.getPlayer().getLocation())) return;
        if (!sm.isSpawnSet()) return;
        event.setRespawnLocation(sm.getSpawnPoint());
    }

    // ── Player block changes ──────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!inSpawn(event.getBlock().getLocation())) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!inSpawn(event.getBlock().getLocation())) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
    }

    // ── Natural block changes ─────────────────────────────────────────────────

    /** Crops, grass, saplings, etc. growing. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Grass/mycelium spreading, fire spreading to adjacent blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Snow, ice, or coral forming. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Ice melting, snow melting, leaves losing colour. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Leaves decaying after a tree is cut. */
    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Trees and mushrooms growing from saplings / spores. */
    @EventHandler(ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (inSpawn(event.getLocation())) event.setCancelled(true);
    }

    /** A block being destroyed by fire. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Fluids flowing (water / lava spreading). */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Entity explosions (creepers, TNT) — remove blocks from the explosion list. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (inSpawn(event.getLocation())) event.blockList().clear();
    }

    /** Block explosions (beds, respawn anchors) — same treatment. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (inSpawn(event.getBlock().getLocation())) event.blockList().clear();
    }

    // ── Player protection ─────────────────────────────────────────────────────

    /** No damage of any kind for non-admin players in spawn. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inSpawn(player.getLocation())) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
    }

    /** Hunger bar is frozen in spawn — no gain, no loss. */
    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inSpawn(player.getLocation())) return;
        event.setCancelled(true);
    }

    // ── Projectile / elytra restrictions ─────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (!inSpawn(player.getLocation())) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;
        boolean blocked = event.getEntity() instanceof EnderPearl
            || event.getEntity() instanceof ThrownExpBottle
            || event.getEntity().getType().name().equals("WIND_CHARGE");
        if (blocked) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't use that at spawn.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inSpawn(player.getLocation())) return;
        if (player.hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
        player.sendMessage(Component.text("You can't use elytra at spawn.", NamedTextColor.RED));
    }
}
