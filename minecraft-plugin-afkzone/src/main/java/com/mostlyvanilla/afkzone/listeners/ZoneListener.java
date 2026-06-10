package com.mostlyvanilla.afkzone.listeners;

import com.mostlyvanilla.afkzone.AfkZoneManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ZoneListener implements Listener {

    private final AfkZoneManager manager;

    public ZoneListener(AfkZoneManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip sub-block movement (head turns etc.) to avoid event spam
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (manager.hasPendingTp(event.getPlayer().getUniqueId())) {
            manager.cancelTeleport(event.getPlayer().getUniqueId(), true);
        }
        manager.handlePlayerMove(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }
}
