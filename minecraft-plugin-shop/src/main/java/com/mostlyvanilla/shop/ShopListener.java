package com.mostlyvanilla.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class ShopListener implements Listener {

    private final ShopManager    shopManager;
    private final SellManager    sellManager;
    private final BitShopManager bitShopManager;

    public ShopListener(ShopManager shopManager, SellManager sellManager, BitShopManager bitShopManager) {
        this.shopManager    = shopManager;
        this.sellManager    = sellManager;
        this.bitShopManager = bitShopManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();

        if (shopManager.isShopInventory(top)) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) shopManager.handleClick(p, top, e.getSlot());
            return;
        }

        if (bitShopManager.isBitShop(top)) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) bitShopManager.handleClick(p, top, e.getSlot());
            return;
        }

        if (sellManager.isWorthGui(top)) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) sellManager.handleWorthClick(p, top, e.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        shopManager.onClose(e.getInventory());
        bitShopManager.onClose(e.getInventory());
        if (sellManager.isSellGui(e.getInventory())) {
            if (e.getPlayer() instanceof Player p)
                sellManager.handleSellClose(p, e.getInventory());
        } else {
            sellManager.onClose(e.getInventory());
        }
    }
}
