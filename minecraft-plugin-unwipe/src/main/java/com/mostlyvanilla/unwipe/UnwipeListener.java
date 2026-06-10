package com.mostlyvanilla.unwipe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UnwipeListener implements Listener {

    private final UnwipeManager manager;

    public UnwipeListener(UnwipeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        manager.applyPendingRestore(e.getPlayer());
    }
}
