package com.mostlyvanilla.roles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class HistoryGui implements Listener {

    // Row 0: tab bar
    private static final int SLOT_TAB_SALES  = 0;
    private static final int SLOT_TAB_AH     = 3;
    private static final int SLOT_SKULL      = 4;
    private static final int SLOT_TAB_ORDERS = 5;

    // Row 5: navigation
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT  = 53;

    // Content area: rows 1-4, all 9 columns (slots 9-44 = 36 items)
    private static final int ITEMS_PER_PAGE = 36;

    public enum Tab { SALES, AH, ORDERS }

    private record Session(UUID targetUuid, String targetName, Tab tab, int page) {}

    private final Map<Inventory, Session> sessions = new HashMap<>();

    public void open(Player viewer, UUID targetUuid, String targetName) {
        open(viewer, targetUuid, targetName, Tab.SALES, 0);
    }

    public void open(Player viewer, UUID targetUuid, String targetName, Tab tab, int page) {
        List<HistoryReader.Entry> entries = getEntries(targetUuid, tab);
        int maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / ITEMS_PER_PAGE;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String tabLabel = switch (tab) {
            case SALES  -> "Sales";
            case AH     -> "Auction House";
            case ORDERS -> "Orders";
        };
        String pageStr = maxPage > 0 ? " (" + (page + 1) + "/" + (maxPage + 1) + ")" : "";
        String title   = targetName + " — " + tabLabel + pageStr;

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(title).decoration(TextDecoration.ITALIC, false));

        fillGlass(inv);

        // Tab bar (row 0)
        inv.setItem(SLOT_TAB_SALES,  tabButton(Material.GOLD_INGOT,    tab == Tab.SALES,  "Sales",        "Shop & spawner sells"));
        inv.setItem(SLOT_TAB_AH,     tabButton(Material.NETHER_STAR,   tab == Tab.AH,     "Auction House","AH listings, sales & purchases"));
        inv.setItem(SLOT_TAB_ORDERS, tabButton(Material.WRITABLE_BOOK, tab == Tab.ORDERS, "Orders",       "Buy orders placed, filled & cancelled"));
        inv.setItem(SLOT_SKULL,      buildSkull(targetUuid, targetName));

        // Content (slots 9-44)
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, entries.size());
        for (int i = start; i < end; i++) {
            inv.setItem(9 + (i - start), buildEntryItem(entries.get(i)));
        }

        // Navigation (row 5)
        if (page > 0)
            inv.setItem(SLOT_PREV, navButton(Material.ARROW, "← Previous"));
        inv.setItem(SLOT_CLOSE, navButton(Material.OAK_DOOR, "Close"));
        if (page < maxPage)
            inv.setItem(SLOT_NEXT, navButton(Material.ARROW, "Next →"));

        sessions.put(inv, new Session(targetUuid, targetName, tab, page));
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Inventory top = e.getView().getTopInventory();
        Session session = sessions.get(top);
        if (session == null) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        UUID   uuid = session.targetUuid();
        String name = session.targetName();
        int    page = session.page();
        Tab    tab  = session.tab();

        switch (e.getSlot()) {
            case SLOT_TAB_SALES  -> { if (tab != Tab.SALES)  { player.closeInventory(); open(player, uuid, name, Tab.SALES,  0); } }
            case SLOT_TAB_AH     -> { if (tab != Tab.AH)     { player.closeInventory(); open(player, uuid, name, Tab.AH,     0); } }
            case SLOT_TAB_ORDERS -> { if (tab != Tab.ORDERS) { player.closeInventory(); open(player, uuid, name, Tab.ORDERS, 0); } }
            case SLOT_CLOSE      -> player.closeInventory();
            case SLOT_PREV       -> { if (page > 0) { player.closeInventory(); open(player, uuid, name, tab, page - 1); } }
            case SLOT_NEXT       -> { player.closeInventory(); open(player, uuid, name, tab, page + 1); }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        sessions.remove(e.getInventory());
    }

    public boolean isHistoryGui(Inventory inv) { return sessions.containsKey(inv); }

    // ── Data ──────────────────────────────────────────────────────────────────

    private List<HistoryReader.Entry> getEntries(UUID target, Tab tab) {
        return switch (tab) {
            case SALES  -> HistoryReader.forPlayerMerged(target, "MostlyVanillaShop", "MVSpawners");
            case AH     -> HistoryReader.forPlayer(target, "MostlyVanillaAuctionHouse")
                               .stream().filter(e -> e.type().startsWith("AH_")).toList();
            case ORDERS -> HistoryReader.forPlayer(target, "MostlyVanillaAuctionHouse")
                               .stream().filter(e -> e.type().startsWith("ORDER_")).toList();
        };
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack tabButton(Material mat, boolean active, String label, String hint) {
        Material display = active ? mat : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item   = new ItemStack(display);
        ItemMeta  meta   = item.getItemMeta();
        NamedTextColor color = active ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        meta.displayName(Component.text((active ? "► " : "") + label)
            .color(color).decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, active));
        meta.lore(List.of(Component.text(hint).color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSkull(UUID uuid, String name) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        meta.displayName(Component.text(name)
            .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        try {
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
        } catch (Exception ignored) {}
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildEntryItem(HistoryReader.Entry entry) {
        Material mat = switch (entry.type()) {
            case "SELL"            -> Material.GOLD_NUGGET;
            case "AH_SALE"         -> Material.DIAMOND;
            case "AH_PURCHASE"     -> Material.EMERALD;
            case "AH_LISTED"       -> Material.NAME_TAG;
            case "AH_CANCELLED"    -> Material.BARRIER;
            case "ORDER_CREATED"   -> Material.WRITABLE_BOOK;
            case "ORDER_FILL"      -> Material.BOOK;
            case "ORDER_RECEIVED"  -> Material.CHEST;
            case "ORDER_CANCELLED" -> Material.BARRIER;
            default                -> Material.PAPER;
        };

        NamedTextColor nameColor = switch (entry.type()) {
            case "SELL", "AH_SALE", "ORDER_FILL"     -> NamedTextColor.YELLOW;
            case "AH_PURCHASE", "ORDER_CREATED"       -> NamedTextColor.RED;
            case "AH_LISTED", "ORDER_RECEIVED"        -> NamedTextColor.AQUA;
            case "AH_CANCELLED", "ORDER_CANCELLED"    -> NamedTextColor.GRAY;
            default                                    -> NamedTextColor.WHITE;
        };

        String typeLabel = switch (entry.type()) {
            case "SELL"            -> "Sale";
            case "AH_SALE"         -> "AH Sale";
            case "AH_PURCHASE"     -> "AH Purchase";
            case "AH_LISTED"       -> "Listed on AH";
            case "AH_CANCELLED"    -> "Listing Cancelled";
            case "ORDER_CREATED"   -> "Order Created";
            case "ORDER_FILL"      -> "Order Filled";
            case "ORDER_RECEIVED"  -> "Order Received";
            case "ORDER_CANCELLED" -> "Order Cancelled";
            default                -> entry.type();
        };

        // Prefix the amount for types where player earned/spent money
        String amountLine = null;
        if (entry.amount() > 0) {
            boolean earned = switch (entry.type()) {
                case "SELL", "AH_SALE", "ORDER_FILL", "ORDER_CANCELLED" -> true;
                default -> false;
            };
            amountLine = (earned ? "+" : "-") + "$" + fmt(entry.amount());
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(typeLabel).color(nameColor)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(entry.description())
            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (amountLine != null) {
            boolean earned = amountLine.startsWith("+");
            lore.add(Component.text(amountLine)
                .color(earned ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(timeAgo(entry.timestamp()))
            .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navButton(Material mat, String label) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(label).color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    private void fillGlass(Inventory inv) {
        ItemStack g    = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = g.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        g.setItemMeta(meta);
        for (int i = 0; i < 54; i++) inv.setItem(i, g);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static String fmt(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.format("%.2f", d);
    }

    static String timeAgo(long epochMs) {
        long diff = System.currentTimeMillis() - epochMs;
        long secs  = diff / 1000;
        long mins  = secs  / 60;
        long hours = mins  / 60;
        long days  = hours / 24;
        if (days  > 0) return days  + "d ago";
        if (hours > 0) return hours + "h ago";
        if (mins  > 0) return mins  + "m ago";
        return "just now";
    }
}
