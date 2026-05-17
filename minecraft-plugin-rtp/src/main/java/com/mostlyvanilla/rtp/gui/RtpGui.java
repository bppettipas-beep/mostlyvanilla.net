package com.mostlyvanilla.rtp.gui;

import com.mostlyvanilla.rtp.RtpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RtpGui {

    // Slot layout for up to 7 worlds centered in the middle row (slots 9–17)
    // n=1→[13], n=2→[12,14], n=3→[11,13,15], n=4→[10,12,14,16], n=5+→packed
    private static final int[] SPACED_OFFSETS  = {4, 2, 6, 0, 8, 1, 7, 3, 5}; // from slot 9
    private static final int   ROW_START        = 9;

    private final RtpManager rtpManager;

    public RtpGui(RtpManager rtpManager) {
        this.rtpManager = rtpManager;
    }

    public void open(Player player) {
        List<World> worlds = Bukkit.getWorlds();

        RtpGuiHolder holder = new RtpGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
            Component.text("Random Teleport", NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);

        // Dark glass border
        ItemStack border = makeItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // Place world items in the middle row
        int n = Math.min(worlds.size(), 9);
        int[] slots = getSlots(n);
        for (int i = 0; i < n; i++) {
            inv.setItem(slots[i], makeWorldItem(worlds.get(i)));
        }

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack makeWorldItem(World world) {
        boolean disabled = rtpManager.isDisabled(world);
        String  name     = RtpManager.displayName(world);

        if (disabled) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta  meta = item.getItemMeta();
            meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Disabled by an admin", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            return item;
        }

        Material mat = switch (world.getEnvironment()) {
            case NETHER  -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default      -> Material.GRASS_BLOCK;
        };

        NamedTextColor color = switch (world.getEnvironment()) {
            case NETHER  -> NamedTextColor.RED;
            case THE_END -> NamedTextColor.LIGHT_PURPLE;
            default      -> NamedTextColor.GREEN;
        };

        String desc = switch (world.getEnvironment()) {
            case NETHER  -> "Brave the hellish landscape";
            case THE_END -> "Explore the outer islands";
            default      -> "Wander the open world";
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Click to teleport", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // ── Slot helpers ──────────────────────────────────────────────────────────

    /** Returns the inventory slot for the world at index i in the worlds list. */
    public static int worldSlot(int worldIndex, int totalWorlds) {
        int n = Math.min(totalWorlds, 9);
        return getSlots(n)[worldIndex];
    }

    private static int[] getSlots(int n) {
        n = Math.min(n, 9);
        int[] slots = new int[n];
        if (n <= 4) {
            // Spaced with one-slot gaps, centered on slot 13
            int start = 13 - (n - 1) * 2 / 2 * 2; // keeps it even
            // Simpler: center = 13, each item 2 apart
            int center = 13;
            int half = (n - 1);
            for (int i = 0; i < n; i++) slots[i] = center - half + i * 2;
        } else {
            // Packed, centered in row (slots 9–17)
            int start = ROW_START + (9 - n) / 2;
            for (int i = 0; i < n; i++) slots[i] = start + i;
        }
        return slots;
    }
}
