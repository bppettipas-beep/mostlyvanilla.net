package com.mostlyvanilla.home.gui;

import com.mostlyvanilla.home.Home;
import com.mostlyvanilla.home.HomeManager;
import com.mostlyvanilla.home.MostlyVanillaHome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class HomeGui {

    // Keep these for any external references — no longer used for pagination
    public static final int PAGE_SIZE   = 27;
    public static final int ROW_SIZE    = 36;
    public static final int SLOT_PREV   = 27;
    public static final int SLOT_ACTION = 31;
    public static final int SLOT_NEXT   = 35;

    private final MostlyVanillaHome plugin;
    private final HomeManager homeManager;

    public HomeGui(MostlyVanillaHome plugin, HomeManager homeManager) {
        this.plugin      = plugin;
        this.homeManager = homeManager;
    }

    public void open(Player player, int page) {
        List<Home> homes    = homeManager.getHomes(player.getUniqueId());
        int playerLimit     = homeManager.getHomeLimit(player);
        int maxLimit        = homeManager.getMaxPossibleLimit();

        // Show all slots up to the highest attainable limit
        int totalSlots = Math.max(playerLimit < 0 ? homes.size() : playerLimit, maxLimit);
        if (totalSlots < homes.size()) totalSlots = homes.size();
        if (totalSlots == 0) totalSlots = 1;

        int contentRows = Math.min(4, Math.max(1, (int) Math.ceil((double) totalSlots / 9)));
        int invRows     = contentRows + 2; // header + content + control
        int invSize     = invRows * 9;
        int ctrlStart   = invSize - 9;

        HomeGuiHolder holder = new HomeGuiHolder(page);
        Inventory inv = Bukkit.createInventory(holder, invSize,
            Component.text("⌂ Your Homes", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        // Header row — green panes
        ItemStack header = makePane(Material.GREEN_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, header);

        // Control row — gray panes
        ItemStack ctrl = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = ctrlStart; i < invSize; i++) inv.setItem(i, ctrl);

        // Content background — black panes (overwritten by beds)
        ItemStack bg = makePane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 9; i < ctrlStart; i++) inv.setItem(i, bg);

        // Place beds — one per home slot, centered per row
        int bedIdx = 0;
        outer:
        for (int r = 0; r < contentRows; r++) {
            int rowCount = Math.min(9, totalSlots - r * 9);
            int startCol = (9 - rowCount) / 2;
            int rowBase  = 9 + r * 9;

            for (int c = 0; c < rowCount; c++, bedIdx++) {
                if (bedIdx >= totalSlots) break outer;
                int guiSlot = rowBase + startCol + c;

                if (bedIdx < homes.size()) {
                    Home home = homes.get(bedIdx);
                    inv.setItem(guiSlot, makeRedBed(home));
                    holder.mapHome(guiSlot, home.getName());
                } else if (playerLimit < 0 || bedIdx < playerLimit) {
                    inv.setItem(guiSlot, makeEmptyBed());
                    holder.addEmpty(guiSlot);
                } else {
                    String role = homeManager.getRoleForSlot(bedIdx + 1);
                    inv.setItem(guiSlot, makeLockedBed(role));
                    holder.addLocked(guiSlot, role != null ? role : "");
                }
            }
        }

        // Set Home / limit button — centered in control row
        int actionSlot = ctrlStart + 4;
        String countStr = homes.size() + "/" + (playerLimit < 0 ? "∞" : String.valueOf(playerLimit));
        if (playerLimit < 0 || homes.size() < playerLimit) {
            inv.setItem(actionSlot, makeItem(Material.NETHER_STAR,
                Component.text("Set Home Here", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false),
                List.of(
                    Component.text("Save your current location as a home", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text(countStr + " homes used", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                )));
        } else {
            inv.setItem(actionSlot, makeItem(Material.BARRIER,
                Component.text("Home Limit Reached", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false),
                List.of(Component.text(countStr + " homes used", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false))));
        }
        holder.setActionSlot(actionSlot);

        player.openInventory(inv);
    }

    public void openAction(Player player, String homeName, int returnPage) {
        Home home = homeManager.getHome(player.getUniqueId(), homeName.toLowerCase());
        if (home == null) {
            player.sendMessage(Component.text("That home no longer exists.", NamedTextColor.RED));
            open(player, returnPage);
            return;
        }

        HomeActionHolder holder = new HomeActionHolder(home.getName(), returnPage);
        Inventory inv = Bukkit.createInventory(holder, 27,
            Component.text("⌂ ", NamedTextColor.GOLD)
                .append(Component.text(home.getName(), NamedTextColor.YELLOW)));
        holder.setInventory(inv);

        ItemStack gray = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, gray);

        ItemStack green = makePane(Material.GREEN_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, green);

        // Teleport — slot 11
        inv.setItem(11, makeItem(Material.ENDER_PEARL,
            Component.text("Teleport", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false),
            List.of(
                Component.text("Warp to this home", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(String.format("%.1f, %.1f, %.1f", home.getX(), home.getY(), home.getZ()), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("World: " + home.getWorld(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )));

        // Rename — slot 13
        inv.setItem(13, makeItem(Material.NAME_TAG,
            Component.text("Rename", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("Type a new name in chat", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false))));

        // Delete — slot 15
        inv.setItem(15, makeItem(Material.BARRIER,
            Component.text("Delete", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("⚠ Permanently removes this home", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false))));

        // Back — slot 22
        inv.setItem(22, makeItem(Material.ARROW,
            Component.text("← Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("Return to your home list", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack makeRedBed(Home h) {
        return makeItem(Material.RED_BED,
            Component.text(h.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false),
            List.of(
                Component.text("Left-click to teleport", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click for options", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(String.format("%.1f, %.1f, %.1f", h.getX(), h.getY(), h.getZ()), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("World: " + h.getWorld(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
    }

    private ItemStack makeEmptyBed() {
        return makeItem(Material.LIGHT_GRAY_BED,
            Component.text("Empty Slot", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("Click to set a home here", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)));
    }

    private ItemStack makeLockedBed(String role) {
        List<Component> lore = new ArrayList<>();
        if (role != null && !role.isEmpty()) {
            lore.add(Component.text("Buy ", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(capitalize(role), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" to unlock this home slot", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        } else {
            lore.add(Component.text("This slot is locked", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return makeItem(Material.GRAY_BED,
            Component.text("🔒 Locked", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
            lore);
    }

    private ItemStack makePane(Material mat) {
        return makeItem(mat, Component.empty());
    }

    private ItemStack makeItem(Material mat, Component name) {
        return makeItem(mat, name, List.of());
    }

    private ItemStack makeItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
