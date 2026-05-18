package com.mostlyvanilla.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class ShopListener implements Listener {

    private final ShopManager manager;

    public ShopListener(ShopManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!manager.isShopInventory(top)) return;
        e.setCancelled(true);
        p.updateInventory();
        if (e.getClickedInventory() != top) return;
        manager.handleClick(p, top, e.getSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        manager.onClose(e.getInventory());
    }
}
