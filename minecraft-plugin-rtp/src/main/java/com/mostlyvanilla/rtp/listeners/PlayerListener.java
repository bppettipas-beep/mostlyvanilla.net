package com.mostlyvanilla.rtp.listeners;

import com.mostlyvanilla.rtp.RtpManager;
import com.mostlyvanilla.rtp.TeleportManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final TeleportManager teleportManager;
    private final RtpManager      rtpManager;

    public PlayerListener(TeleportManager teleportManager, RtpManager rtpManager) {
        this.teleportManager = teleportManager;
        this.rtpManager      = rtpManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        teleportManager.checkMove(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleportManager.cancel(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        World destination = event.getTo() != null ? event.getTo().getWorld() : null;
        if (destination == null) return;
        if (!rtpManager.isDisabled(destination)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text(
            RtpManager.displayName(destination) + " is currently disabled.",
            NamedTextColor.RED));
    }
}
