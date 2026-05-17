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

import java.util.List;

public class HomeGui {

    private static final int PAGE_SIZE   = 45;
    private static final int ROW_SIZE    = 54; // 6 rows
    private static final int SLOT_PREV   = 45;
    private static final int SLOT_ACTION = 49;
    private static final int SLOT_NEXT   = 53;

    private final MostlyVanillaHome plugin;
    private final HomeManager homeManager;

    public HomeGui(MostlyVanillaHome plugin, HomeManager homeManager) {
        this.plugin      = plugin;
        this.homeManager = homeManager;
    }

    public void open(Player player, int page) {
        List<Home> homes      = homeManager.getHomes(player.getUniqueId());
        int totalPages        = Math.max(1, (int) Math.ceil((double) homes.size() / PAGE_SIZE));
        page                  = Math.max(0, Math.min(page, totalPages - 1));
        int limit             = homeManager.getHomeLimit(player);

        HomeGuiHolder holder  = new HomeGuiHolder(page);
        Inventory inv = Bukkit.createInventory(holder, ROW_SIZE,
            Component.text("Your Homes", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        // Gray filler for the control row
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = PAGE_SIZE; i < ROW_SIZE; i++) inv.setItem(i, filler);

        // Home items
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, homes.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, makeHomeItem(homes.get(i)));
        }

        // Set Home / Limit indicator (centre of control row)
        if (limit < 0 || homes.size() < limit) {
            inv.setItem(SLOT_ACTION, makeItem(Material.LIME_DYE,
                Component.text("Set Home Here", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(
                    Component.text("Sets a home at your current location", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Homes: " + homes.size() + (limit < 0 ? "" : "/" + limit), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                )));
        } else {
            inv.setItem(SLOT_ACTION, makeItem(Material.GRAY_DYE,
                Component.text("Home Limit Reached", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Homes: " + homes.size() + "/" + limit, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))));
        }

        // Navigation arrows
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeItem(Material.ARROW,
                Component.text("← Previous", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Page " + page + "/" + totalPages, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeItem(Material.ARROW,
                Component.text("Next →", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Page " + (page + 2) + "/" + totalPages, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))));
        }

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
            Component.text("Home: " + home.getName(), NamedTextColor.GOLD));
        holder.setInventory(inv);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(11, makeItem(Material.LIME_CONCRETE,
            Component.text("Teleport", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
            List.of(
                Component.text("Click to teleport to this home", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(String.format("%.1f, %.1f, %.1f", home.getX(), home.getY(), home.getZ()), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("World: " + home.getWorld(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            )));

        inv.setItem(13, makeItem(Material.NAME_TAG,
            Component.text("Rename", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("Click to rename this home", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        inv.setItem(15, makeItem(Material.RED_CONCRETE,
            Component.text("Delete", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
            List.of(Component.text("Click to permanently delete this home", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        inv.setItem(22, makeItem(Material.ARROW,
            Component.text("← Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));

        player.openInventory(inv);
    }

    // ── Item helpers ─────────────────────────────────────────────────────────

    private ItemStack makeHomeItem(Home h) {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(h.getName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Left-click to teleport", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Right-click for options", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text(String.format("%.1f, %.1f, %.1f", h.getX(), h.getY(), h.getZ()), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("World: " + h.getWorld(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, Component name) {
        return makeItem(mat, name, List.of());
    }

    private ItemStack makeItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
