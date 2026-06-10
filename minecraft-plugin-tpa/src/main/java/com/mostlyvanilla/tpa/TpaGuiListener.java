package com.mostlyvanilla.tpa;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class TpaGuiListener implements Listener {

    private final RequestManager rm;

    public TpaGuiListener(RequestManager rm) {
        this.rm = rm;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TpaGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == TpaGui.ACCEPT_SLOT && gui.tryRespond()) {
            rm.accept(player);
            player.closeInventory();
        } else if (slot == TpaGui.DENY_SLOT && gui.tryRespond()) {
            rm.deny(player);
            player.closeInventory();
        }
    }

    // Closing without clicking accept/deny counts as a deny (silent — no error message to target).
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TpaGui gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!gui.isResponded() && gui.tryRespond()) {
            rm.denyQuiet(player);
        }
    }
}
