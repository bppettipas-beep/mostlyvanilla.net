package com.mostlyvanilla.home.listeners;

import com.mostlyvanilla.home.VanillaSpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class VanillaSpawnListener implements Listener {

    private final VanillaSpawnManager spawnManager;

    public VanillaSpawnListener(VanillaSpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!spawnManager.isInZone(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot break blocks in the spawn zone.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!spawnManager.isInZone(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot place blocks in the spawn zone.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!spawnManager.isInZone(player.getLocation())) return;
        event.setCancelled(true);
    }
}
