package com.mostlyvanilla.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class ShopListener implements Listener {

    private final ShopManager shopManager;
    private final SellManager sellManager;

    public ShopListener(ShopManager shopManager, SellManager sellManager) {
        this.shopManager = shopManager;
        this.sellManager = sellManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();

        if (shopManager.isShopInventory(top)) {
            e.setCancelled(true);
            p.updateInventory();
            if (e.getClickedInventory() == top) shopManager.handleClick(p, top, e.getSlot());
            return;
        }

        if (sellManager.isWorthGui(top)) {
            e.setCancelled(true);
            p.updateInventory();
            if (e.getClickedInventory() == top) sellManager.handleWorthClick(p, top, e.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        shopManager.onClose(e.getInventory());
        sellManager.onClose(e.getInventory());
    }
}
