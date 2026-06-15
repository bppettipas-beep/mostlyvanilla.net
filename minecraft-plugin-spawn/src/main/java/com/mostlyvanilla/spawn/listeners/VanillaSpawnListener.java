package com.mostlyvanilla.spawn.listeners;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public class VanillaSpawnListener implements Listener {

    private final MostlyVanillaSpawn plugin;

    public VanillaSpawnListener(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getVanillaSpawnManager().isInZone(event.getBlock().getLocation())) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot break blocks in the spawn zone.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getVanillaSpawnManager().isInZone(event.getBlock().getLocation())) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot place blocks in the spawn zone.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        // getBlock() is the solid block clicked; fluid lands in the adjacent block
        org.bukkit.Location fluidLoc = event.getBlock().getRelative(event.getBlockFace()).getLocation();
        if (!plugin.getVanillaSpawnManager().isInZone(fluidLoc)) return;
        if (event.getPlayer().hasPermission("mostlyvanilla.spawn.admin")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot place fluids in the spawn zone.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getVanillaSpawnManager().isInZone(player.getLocation())) return;
        event.setCancelled(true);
    }
}
