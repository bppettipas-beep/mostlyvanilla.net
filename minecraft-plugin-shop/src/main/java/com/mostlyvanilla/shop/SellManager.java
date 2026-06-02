package com.mostlyvanilla.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.HashSet;
import java.util.Set;

public class SellManager {

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = 28;
    private static final int SLOT_SORT  = 46;
    private static final int SLOT_PREV  = 47;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT  = 51;

    public enum SortMode {
        ALPHABETICAL, PRICE_ASC, PRICE_DESC;
        public SortMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    private record WorthSession(int page, SortMode sort) {}

    private final JavaPlugin plugin;
    private final EconomyBridge bridge;

    private final Map<Material, Double> sellPrices    = new LinkedHashMap<>();
    private final List<Material>        sortedItems   = new ArrayList<>();
    private final Map<Inventory, WorthSession> worthSessions = new HashMap<>();
    private final Set<Inventory>        sellSessions  = new HashSet<>();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public SellManager(JavaPlugin plugin, EconomyBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        loadSellPrices();
    }

    // ── Config ────────────────────────────────────────────────────────────────

    void loadSellPrices() {
        sellPrices.clear();
        sortedItems.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sell-prices");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                double price = sec.getDouble(key, 0.0);
                if (price > 0) sellPrices.put(mat, price);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Shop] Unknown sell material: " + key);
            }
        }
        sortedItems.addAll(sellPrices.keySet());
        sortedItems.sort(Comparator.comparing(Material::name));
    }

    public double getSellPrice(Material mat) {
        return sellPrices.getOrDefault(mat, 0.0);
    }

    // ── /sell ─────────────────────────────────────────────────────────────────

    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Component.text("Hold an item to sell it.", NamedTextColor.RED));
            return;
        }
        double unitPrice = getSellPrice(hand.getType());
        if (unitPrice <= 0) {
            player.sendMessage(Component.text("That item has no sell value.", NamedTextColor.RED));
            return;
        }
        int amount = hand.getAmount();
        double total = unitPrice * amount;
        player.getInventory().setItemInMainHand(null);
        bridge.deposit(player.getUniqueId(), total);
        player.sendMessage(Component.text("Sold ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(amount + "x " + prettify(hand.getType()), NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + fmt(total) + ".", NamedTextColor.YELLOW)));
    }

    public void sellAll(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        double totalEarned = 0.0;
        int    totalItems  = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;
            double price = getSellPrice(stack.getType());
            if (price <= 0) continue;
            totalEarned += price * stack.getAmount();
            totalItems  += stack.getAmount();
            contents[i] = null;
        }
        if (totalItems == 0) {
            player.sendMessage(Component.text("You have no sellable items.", NamedTextColor.RED));
            return;
        }
        player.getInventory().setContents(contents);
        bridge.deposit(player.getUniqueId(), totalEarned);
        player.sendMessage(Component.text("Sold ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(totalItems + " item(s)", NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + fmt(totalEarned) + ".", NamedTextColor.YELLOW)));
    }

    // ── /sell GUI ─────────────────────────────────────────────────────────────

    public void openSellGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("Sell Items").color(NamedTextColor.GOLD)
                .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Close to sell").color(NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        sellSessions.add(inv);
        player.openInventory(inv);
    }

    public boolean isSellGui(Inventory inv) { return sellSessions.contains(inv); }

    public void handleSellClose(Player player, Inventory inv) {
        sellSessions.remove(inv);
        double total     = 0.0;
        int    soldCount = 0;
        List<ItemStack> unsellable = new ArrayList<>();

        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            double price = getSellPrice(item.getType());
            if (price > 0) {
                total     += price * item.getAmount();
                soldCount += item.getAmount();
            } else {
                unsellable.add(item.clone());
            }
        }
        inv.clear();

        for (ItemStack item : unsellable) {
            player.getInventory().addItem(item).values()
                .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }

        if (soldCount == 0) return;

        bridge.deposit(player.getUniqueId(), total);
        player.sendMessage(Component.text("Sold ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(soldCount + " item(s)", NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + fmt(total) + ".", NamedTextColor.YELLOW)));
    }

    // ── /worth GUI ────────────────────────────────────────────────────────────

    public void openWorthGui(Player player, int page) {
        openWorthGui(player, page, SortMode.ALPHABETICAL);
    }

    public void openWorthGui(Player player, int page, SortMode sort) {
        List<Material> items = getItemsSorted(sort);
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String sortLabel = switch (sort) {
            case ALPHABETICAL -> "A→Z";
            case PRICE_ASC    -> "Cheapest";
            case PRICE_DESC   -> "Expensive";
        };

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("Item Worth  (pg " + (page + 1) + "/" + (maxPage + 1) + "  " + sortLabel + ")")
                .decoration(TextDecoration.ITALIC, false));
        fillAll(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            Material mat   = items.get(i);
            double   price = sellPrices.get(mat);
            inv.setItem(ITEM_SLOTS[i - start], buildWorthItem(mat, price));
        }

        inv.setItem(SLOT_SORT, buildSortButton(sort));
        if (page > 0)
            inv.setItem(SLOT_PREV,  makeButton(Material.ARROW, "&7← Previous"));
        inv.setItem(SLOT_CLOSE, makeButton(Material.OAK_DOOR, "&cClose"));
        if (page < maxPage)
            inv.setItem(SLOT_NEXT,  makeButton(Material.ARROW, "&7Next →"));

        worthSessions.put(inv, new WorthSession(page, sort));
        player.openInventory(inv);
    }

    private List<Material> getItemsSorted(SortMode sort) {
        List<Material> items = new ArrayList<>(sortedItems);
        switch (sort) {
            case PRICE_ASC  -> items.sort(Comparator.comparingDouble(sellPrices::get));
            case PRICE_DESC -> items.sort(Comparator.comparingDouble(sellPrices::get).reversed());
            case ALPHABETICAL -> {} // sortedItems is already alphabetical
        }
        return items;
    }

    private ItemStack buildSortButton(SortMode current) {
        Material mat = switch (current) {
            case ALPHABETICAL -> Material.BOOK;
            case PRICE_ASC    -> Material.GOLD_INGOT;
            case PRICE_DESC   -> Material.DIAMOND;
        };
        String label = switch (current) {
            case ALPHABETICAL -> "&fSort: A→Z";
            case PRICE_ASC    -> "&fSort: Cheapest First";
            case PRICE_DESC   -> "&fSort: Most Expensive First";
        };
        String hint = switch (current) {
            case ALPHABETICAL -> "&7Click → Cheapest First";
            case PRICE_ASC    -> "&7Click → Most Expensive First";
            case PRICE_DESC   -> "&7Click → A→Z";
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(label).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(LEGACY.deserialize(hint).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildWorthItem(Material mat, double price) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta  meta  = stack.getItemMeta();
        meta.displayName(Component.text(prettify(mat))
            .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Sell: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(bridge.getSymbol() + fmt(price))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    public void handleWorthClick(Player player, Inventory inv, int slot) {
        WorthSession session = worthSessions.get(inv);
        if (session == null) return;
        int      page = session.page();
        SortMode sort = session.sort();
        if (slot == SLOT_SORT) {
            player.closeInventory();
            openWorthGui(player, 0, sort.next());
        } else if (slot == SLOT_PREV && page > 0) {
            player.closeInventory();
            openWorthGui(player, page - 1, sort);
        } else if (slot == SLOT_NEXT) {
            player.closeInventory();
            openWorthGui(player, page + 1, sort);
        } else if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    public boolean isWorthGui(Inventory inv) { return worthSessions.containsKey(inv); }
    public void onClose(Inventory inv)        { worthSessions.remove(inv); }

    public void reload() { plugin.reloadConfig(); loadSellPrices(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack makeButton(Material mat, String ampName) {
        ItemStack btn  = new ItemStack(mat);
        ItemMeta  meta = btn.getItemMeta();
        meta.displayName(LEGACY.deserialize(ampName).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of());
        btn.setItemMeta(meta);
        return btn;
    }

    private void fillAll(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta  = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
    }

    private String fmt(double price) {
        if (price == Math.floor(price)) return String.valueOf((long) price);
        return String.valueOf(price);
    }

    private String prettify(Material mat) {
        String raw = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
