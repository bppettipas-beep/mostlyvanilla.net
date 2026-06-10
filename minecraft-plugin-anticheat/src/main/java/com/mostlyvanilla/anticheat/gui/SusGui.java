package com.mostlyvanilla.anticheat.gui;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SusGui implements InventoryHolder {

    private final Inventory inventory;
    private final int currentPage;
    private final UUID[] slotTargets;

    public SusGui(MostlyVanillaAnticheat plugin, int page) {
        List<Map.Entry<UUID, PlayerData>> suspects = buildSuspectList(plugin);
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) suspects.size() / pageSize));
        page = Math.min(page, totalPages - 1);
        this.currentPage = page;

        inventory = Bukkit.createInventory(this, 54,
                Component.text("Suspects — page " + (page + 1) + "/" + totalPages, NamedTextColor.DARK_RED));
        slotTargets = new UUID[54];

        // Glass pane border on bottom row
        ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        var paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.text(" "));
        pane.setItemMeta(paneMeta);
        for (int i = 45; i < 54; i++) inventory.setItem(i, pane);

        // Prev / next arrows
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            var m = prev.getItemMeta();
            m.displayName(Component.text("« Previous", NamedTextColor.YELLOW));
            prev.setItemMeta(m);
            inventory.setItem(45, prev);
            slotTargets[45] = null; // handled via special UUID sentinel
        }
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            var m = next.getItemMeta();
            m.displayName(Component.text("Next »", NamedTextColor.YELLOW));
            next.setItemMeta(m);
            inventory.setItem(53, next);
        }

        // Fill suspect heads
        int start = page * pageSize;
        int end   = Math.min(start + pageSize, suspects.size());
        for (int i = start; i < end; i++) {
            Map.Entry<UUID, PlayerData> entry = suspects.get(i);
            UUID uuid = entry.getKey();
            PlayerData data = entry.getValue();
            int slot = i - start;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : uuid.toString();
            meta.displayName(Component.text(name, NamedTextColor.RED));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Total violations: " + data.totalViolations(), NamedTextColor.YELLOW));
            for (Map.Entry<String, Integer> vl : data.violations.entrySet()) {
                lore.add(Component.text("  " + vl.getKey() + ": " + vl.getValue(), NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Click to inspect", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            skull.setItemMeta(meta);

            inventory.setItem(slot, skull);
            slotTargets[slot] = uuid;
        }
    }

    private List<Map.Entry<UUID, PlayerData>> buildSuspectList(MostlyVanillaAnticheat plugin) {
        return plugin.getAllData().entrySet().stream()
                .filter(e -> e.getValue().totalViolations() > 0)
                .sorted(Comparator.comparingInt((Map.Entry<UUID, PlayerData> e) ->
                        e.getValue().totalViolations()).reversed())
                .toList();
    }

    /** Returns the UUID of the suspect in this slot, or null if it's a UI slot. */
    public UUID getTargetAt(int slot) {
        if (slot < 0 || slot >= slotTargets.length) return null;
        return slotTargets[slot];
    }

    public int getCurrentPage() { return currentPage; }
    public boolean isPrevSlot(int slot) { return slot == 45; }
    public boolean isNextSlot(int slot) { return slot == 53; }

    @Override
    public Inventory getInventory() { return inventory; }
}
