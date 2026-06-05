package com.mostlyvanilla.auctionhouse;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;

public class AhListener implements Listener {

    private final AuctionManager auctionManager;
    private final OrderManager   orderManager;

    public AhListener(AuctionManager auctionManager, OrderManager orderManager) {
        this.auctionManager = auctionManager;
        this.orderManager   = orderManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();

        if (auctionManager.isAhGui(top)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top) {
                auctionManager.handleClick(player, top, event.getSlot());
            }
            return;
        }

        if (orderManager.isOrdersGui(top)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top) {
                orderManager.handleClick(player, top, event.getSlot());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        auctionManager.onClose(inv);
        orderManager.onClose(inv);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        orderManager.deliverPending(event.getPlayer());
    }
}
