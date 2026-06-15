package com.mostlyvanilla.macelimit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MaceLimitListener implements Listener {

    private final MaceLimitManager manager;
    private final Material maceMaterial;

    public MaceLimitListener(MaceLimitManager manager) {
        this.manager = manager;
        // MACE was added in 1.21 — matchMaterial returns null on older versions
        this.maceMaterial = Material.matchMaterial("MACE");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (maceMaterial == null) return;
        if (event.getRecipe().getResult().getType() != maceMaterial) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!manager.canCraft()) {
            event.setCancelled(true);
            player.sendMessage(
                Component.text("The server mace limit of ", NamedTextColor.RED)
                    .append(Component.text(manager.getLimit(), NamedTextColor.YELLOW))
                    .append(Component.text(" has been reached. No more maces can be crafted.", NamedTextColor.RED)));
            return;
        }

        // Count how many will actually be crafted
        int amount = event.isShiftClick()
            ? countShiftCraft(event)
            : event.getRecipe().getResult().getAmount();

        // If shift-crafting would exceed the limit, cancel the whole thing
        if (manager.getLimit() > 0 && manager.getCrafted() + amount > manager.getLimit()) {
            event.setCancelled(true);
            player.sendMessage(
                Component.text("Crafting " + amount + " mace(s) would exceed the server limit of ", NamedTextColor.RED)
                    .append(Component.text(manager.getLimit(), NamedTextColor.YELLOW))
                    .append(Component.text(". Only " + manager.getRemaining() + " more can be crafted.", NamedTextColor.RED)));
            return;
        }

        manager.recordCraft(amount);

        String total = manager.getLimit() <= 0 ? "∞" : String.valueOf(manager.getLimit());
        Bukkit.broadcast(
            Component.text("⚔ ", NamedTextColor.GOLD)
                .append(Component.text(player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" crafted a ", NamedTextColor.GOLD))
                .append(Component.text("Mace", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("! (Server total: ", NamedTextColor.GOLD))
                .append(Component.text(manager.getCrafted() + "/" + total, NamedTextColor.YELLOW))
                .append(Component.text(")", NamedTextColor.GOLD)));

        player.sendMessage(
            Component.text("Mace crafted. Server total: ", NamedTextColor.GRAY)
                .append(Component.text(manager.getCrafted(), NamedTextColor.YELLOW))
                .append(Component.text(" / ", NamedTextColor.GRAY))
                .append(Component.text(total, NamedTextColor.YELLOW)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (maceMaterial == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        // Only care about clicks inside a container, not the player's own inventory
        if (clicked.equals(player.getInventory())) return;
        // Crafting result slots are handled by CraftItemEvent
        if (event.getSlotType() == InventoryType.SlotType.RESULT) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != maceMaterial) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("This mace cannot be taken — it will remain here for discovery.", NamedTextColor.RED));
        manager.broadcastFind(player.getName());
    }

    /** Estimates how many maces a shift-click will produce from current matrix contents. */
    private int countShiftCraft(CraftItemEvent event) {
        int resultAmount = event.getRecipe().getResult().getAmount();
        int minIngredientCount = Integer.MAX_VALUE;
        for (var item : event.getInventory().getMatrix()) {
            if (item != null && !item.getType().isAir()) {
                minIngredientCount = Math.min(minIngredientCount, item.getAmount());
            }
        }
        if (minIngredientCount == Integer.MAX_VALUE) return resultAmount;
        return minIngredientCount * resultAmount;
    }
}
