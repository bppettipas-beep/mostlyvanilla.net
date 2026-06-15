package com.mostlyvanilla.crates;

import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class CrateListener implements Listener {

    private final CrateManager manager;

    public CrateListener(CrateManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!Tag.SHULKER_BOXES.isTagged(block.getType())) return;
        if (!manager.isCrate(block)) return;

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) {
            manager.openPreview(event.getPlayer(), block);
        } else {
            manager.tryOpen(event.getPlayer(), block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        org.bukkit.inventory.Inventory inv = event.getInventory();

        if (manager.isColorPickerGui(inv)) {
            event.setCancelled(true);
            if (event.getRawSlot() < inv.getSize()) {
                manager.handleColorPickClick(player, inv, event.getRawSlot());
            }
            return;
        }

        if (!(inv.getHolder() instanceof CrateGuiHolder holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() >= inv.getSize()) return;
        manager.handleGuiClick(player, holder, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        org.bukkit.inventory.Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof CrateGuiHolder || manager.isColorPickerGui(inv)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        manager.onColorPickerClose(event.getInventory());
    }
}
