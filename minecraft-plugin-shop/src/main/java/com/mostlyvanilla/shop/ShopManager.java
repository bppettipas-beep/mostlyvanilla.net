package com.mostlyvanilla.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ShopManager {

    // ── Category GUI ──────────────────────────────────────────────────────────
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = 28;
    private static final int SLOT_PREV = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 51;

    // ── Confirm GUI (45 slots / 5 rows) ──────────────────────────────────────
    private static final int CONFIRM_SIZE          = 45;
    private static final int CONFIRM_SLOT_ITEM     = 13;
    private static final int CONFIRM_SLOT_MINUS64  = 19;
    private static final int CONFIRM_SLOT_MINUS10  = 20;
    private static final int CONFIRM_SLOT_MINUS1   = 21;
    private static final int CONFIRM_SLOT_QTY      = 22;
    private static final int CONFIRM_SLOT_PLUS1    = 23;
    private static final int CONFIRM_SLOT_PLUS10   = 24;
    private static final int CONFIRM_SLOT_PLUS64   = 25;
    private static final int CONFIRM_SLOT_CANCEL   = 38;
    private static final int CONFIRM_SLOT_CONFIRM  = 42;
    private static final int MAX_QUANTITY          = 64;

    private record Session(boolean isMain, String categoryKey, int page) {}
    private record ConfirmSession(ShopItem item, int quantity, String categoryKey, int page) {}

    private final JavaPlugin plugin;
    private final EconomyBridge bridge;
    private final List<ShopCategory> categories = new ArrayList<>();
    private final Map<Inventory, Session> sessions = new HashMap<>();
    private final Map<Inventory, ConfirmSession> confirmSessions = new HashMap<>();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public ShopManager(JavaPlugin plugin, EconomyBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        loadConfig();
    }

    // ── Config loading ────────────────────────────────────────────────────────

    void loadConfig() {
        categories.clear();
        ConfigurationSection cats = plugin.getConfig().getConfigurationSection("categories");
        if (cats == null) return;

        for (String key : cats.getKeys(false)) {
            ConfigurationSection cs = cats.getConfigurationSection(key);
            if (cs == null) continue;

            String displayName = cs.getString("name", key);
            Material icon;
            try {
                icon = Material.valueOf(cs.getString("icon", "CHEST").toUpperCase());
            } catch (IllegalArgumentException e) {
                icon = Material.CHEST;
            }
            int mainSlot = cs.getInt("main-slot", 22);

            List<ShopItem> items = new ArrayList<>();
            List<Map<?, ?>> itemList = cs.getMapList("items");
            for (Map<?, ?> raw : itemList) {
                ShopItem item = parseItem(raw);
                if (item != null) items.add(item);
            }

            categories.add(new ShopCategory(key, displayName, icon, mainSlot, items));
        }
    }

    @SuppressWarnings("unchecked")
    private ShopItem parseItem(Map<?, ?> raw) {
        String matStr = raw.containsKey("material") ? raw.get("material").toString() : null;
        if (matStr == null) return null;
        Material material;
        try {
            material = Material.valueOf(matStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Shop] Unknown material: " + matStr);
            return null;
        }

        String name   = raw.containsKey("name")   ? raw.get("name").toString()   : null;
        int    amount = raw.containsKey("amount")  ? toInt(raw.get("amount"), 1)  : 1;
        double price  = raw.containsKey("price")   ? toDouble(raw.get("price"), 0.0) : 0.0;

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        if (raw.containsKey("enchants") && raw.get("enchants") instanceof Map<?, ?> enchMap) {
            for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                String enchKey = e.getKey().toString().toLowerCase();
                Enchantment enc = Enchantment.getByKey(NamespacedKey.minecraft(enchKey));
                if (enc != null) {
                    enchants.put(enc, toInt(e.getValue(), 1));
                }
            }
        }

        List<String> lore = new ArrayList<>();
        if (raw.containsKey("lore") && raw.get("lore") instanceof List<?> loreList) {
            for (Object line : loreList) {
                if (line != null) lore.add(line.toString());
            }
        }

        return new ShopItem(material, name, enchants, lore, amount, price);
    }

    private int toInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }

    private double toDouble(Object o, double def) {
        if (o == null) return def;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return def; }
    }

    // ── GUI helpers ───────────────────────────────────────────────────────────

    private Component parseName(String ampersandStr) {
        return LEGACY.deserialize(ampersandStr).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack filler() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        return glass;
    }

    private void fillAll(Inventory inv) {
        ItemStack f = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
    }

    private ItemStack makeButton(Material mat, String name) {
        ItemStack btn = new ItemStack(mat);
        ItemMeta meta = btn.getItemMeta();
        meta.displayName(parseName(name));
        meta.lore(List.of());
        btn.setItemMeta(meta);
        return btn;
    }

    // ── Main menu ─────────────────────────────────────────────────────────────

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("Mostly Vanilla Shop").decoration(TextDecoration.ITALIC, false));
        fillAll(inv);

        for (ShopCategory cat : categories) {
            ItemStack icon = new ItemStack(cat.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(parseName(cat.displayName()));

            List<Component> lore = new ArrayList<>();
            lore.add(parseName("&7Click to browse"));
            lore.add(parseName("&7" + cat.items().size() + " items"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(cat.mainSlot(), icon);
        }

        sessions.put(inv, new Session(true, null, 0));
        player.openInventory(inv);
    }

    // ── Category GUI ──────────────────────────────────────────────────────────

    public void openCategory(Player player, String categoryKey, int page) {
        ShopCategory cat = getCategory(categoryKey);
        if (cat == null) return;

        List<ShopItem> items = cat.items();
        int maxPage = Math.max(0, (items.size() - 1) / ITEMS_PER_PAGE);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        Inventory inv = Bukkit.createInventory(null, 54, parseName(cat.displayName()));
        fillAll(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            int slotIdx = i - start;
            inv.setItem(ITEM_SLOTS[slotIdx], buildDisplayItem(item));
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(parseName("&7← Previous"));
            prev.setItemMeta(pm);
            inv.setItem(SLOT_PREV, prev);
        }

        ItemStack back = new ItemStack(Material.OAK_DOOR);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(parseName("&cBack to Shop"));
        back.setItemMeta(bm);
        inv.setItem(SLOT_BACK, back);

        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.displayName(parseName("&7Next →"));
            next.setItemMeta(nm);
            inv.setItem(SLOT_NEXT, next);
        }

        sessions.put(inv, new Session(false, categoryKey, page));
        player.openInventory(inv);
    }

    private ItemStack buildDisplayItem(ShopItem item) {
        ItemStack stack = new ItemStack(item.material(), item.amount());
        for (Map.Entry<Enchantment, Integer> e : item.enchants().entrySet()) {
            stack.addUnsafeEnchantment(e.getKey(), e.getValue());
        }

        ItemMeta meta = stack.getItemMeta();
        String rawName = item.displayName() != null
            ? item.displayName()
            : prettifyMaterial(item.material());
        meta.displayName(parseName(rawName));

        List<Component> lore = new ArrayList<>();
        if (item.amount() > 1) {
            lore.add(parseName("&7Amount: &f" + item.amount()));
        }
        lore.add(parseName("&7Price: &e" + bridge.getSymbol() + formatPrice(item.price())));
        lore.add(parseName("&aLeft-click to purchase"));
        for (String line : item.lore()) {
            lore.add(parseName(line));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── Confirm / quantity GUI ────────────────────────────────────────────────

    public void openConfirmMenu(Player player, ShopItem item, String categoryKey, int page) {
        String rawName = item.displayName() != null ? item.displayName() : prettifyMaterial(item.material());
        Inventory inv = Bukkit.createInventory(null, CONFIRM_SIZE, parseName("&8Buy: " + rawName));
        fillAll(inv);

        int qty = 1;
        inv.setItem(CONFIRM_SLOT_ITEM,    buildConfirmItemDisplay(item, qty));
        inv.setItem(CONFIRM_SLOT_MINUS64, makeButton(Material.RED_STAINED_GLASS_PANE, "&c-64"));
        inv.setItem(CONFIRM_SLOT_MINUS10, makeButton(Material.RED_STAINED_GLASS_PANE, "&c-10"));
        inv.setItem(CONFIRM_SLOT_MINUS1,  makeButton(Material.RED_STAINED_GLASS_PANE, "&c-1"));
        inv.setItem(CONFIRM_SLOT_QTY,     buildQtyDisplay(item, qty));
        inv.setItem(CONFIRM_SLOT_PLUS1,   makeButton(Material.LIME_STAINED_GLASS_PANE, "&a+1"));
        inv.setItem(CONFIRM_SLOT_PLUS10,  makeButton(Material.LIME_STAINED_GLASS_PANE, "&a+10"));
        inv.setItem(CONFIRM_SLOT_PLUS64,  makeButton(Material.LIME_STAINED_GLASS_PANE, "&a+64"));
        inv.setItem(CONFIRM_SLOT_CANCEL,  makeButton(Material.RED_CONCRETE, "&cCancel"));
        inv.setItem(CONFIRM_SLOT_CONFIRM, makeButton(Material.LIME_CONCRETE, "&aConfirm Purchase"));

        confirmSessions.put(inv, new ConfirmSession(item, qty, categoryKey, page));
        player.openInventory(inv);
    }

    private ItemStack buildConfirmItemDisplay(ShopItem item, int qty) {
        int totalAmount = item.amount() * qty;
        int visualAmount = Math.min(totalAmount, item.material().getMaxStackSize());
        ItemStack stack = new ItemStack(item.material(), Math.max(1, visualAmount));
        for (Map.Entry<Enchantment, Integer> e : item.enchants().entrySet()) {
            stack.addUnsafeEnchantment(e.getKey(), e.getValue());
        }

        ItemMeta meta = stack.getItemMeta();
        String rawName = item.displayName() != null ? item.displayName() : prettifyMaterial(item.material());
        meta.displayName(parseName(rawName));

        List<Component> lore = new ArrayList<>();
        if (totalAmount > 1) {
            lore.add(parseName("&7Amount: &f" + totalAmount));
        }
        lore.add(parseName("&7Unit price: &e" + bridge.getSymbol() + formatPrice(item.price())));
        lore.add(parseName("&7Total: &e" + bridge.getSymbol() + formatPrice(item.price() * qty)));
        for (String line : item.lore()) {
            lore.add(parseName(line));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildQtyDisplay(ShopItem item, int qty) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(parseName("&fQuantity: &e" + qty));
        List<Component> lore = new ArrayList<>();
        if (item.amount() > 1) {
            lore.add(parseName("&7Items: &f" + (item.amount() * qty)));
        }
        lore.add(parseName("&7Total cost: &e" + bridge.getSymbol() + formatPrice(item.price() * qty)));
        meta.lore(lore);
        paper.setItemMeta(meta);
        return paper;
    }

    private void handleConfirmClick(Player player, Inventory inv, int slot) {
        ConfirmSession session = confirmSessions.get(inv);
        if (session == null) return;

        ShopItem item = session.item();
        int qty = session.quantity();
        int newQty = qty;

        switch (slot) {
            case CONFIRM_SLOT_MINUS64 -> newQty = Math.max(1, qty - 64);
            case CONFIRM_SLOT_MINUS10 -> newQty = Math.max(1, qty - 10);
            case CONFIRM_SLOT_MINUS1  -> newQty = Math.max(1, qty - 1);
            case CONFIRM_SLOT_PLUS1   -> newQty = Math.min(MAX_QUANTITY, qty + 1);
            case CONFIRM_SLOT_PLUS10  -> newQty = Math.min(MAX_QUANTITY, qty + 10);
            case CONFIRM_SLOT_PLUS64  -> newQty = Math.min(MAX_QUANTITY, qty + 64);
            case CONFIRM_SLOT_CANCEL  -> {
                player.closeInventory();
                openCategory(player, session.categoryKey(), session.page());
                return;
            }
            case CONFIRM_SLOT_CONFIRM -> {
                player.closeInventory();
                purchaseItem(player, item, qty);
                return;
            }
            default -> { return; }
        }

        if (newQty != qty) {
            confirmSessions.put(inv, new ConfirmSession(item, newQty, session.categoryKey(), session.page()));
            inv.setItem(CONFIRM_SLOT_ITEM, buildConfirmItemDisplay(item, newQty));
            inv.setItem(CONFIRM_SLOT_QTY,  buildQtyDisplay(item, newQty));
        }
    }

    // ── Click routing ─────────────────────────────────────────────────────────

    public void handleClick(Player player, Inventory inv, int slot) {
        if (confirmSessions.containsKey(inv)) {
            handleConfirmClick(player, inv, slot);
            return;
        }

        Session session = sessions.get(inv);
        if (session == null) return;

        if (session.isMain()) {
            for (ShopCategory cat : categories) {
                if (cat.mainSlot() == slot) {
                    player.closeInventory();
                    openCategory(player, cat.key(), 0);
                    return;
                }
            }
            return;
        }

        String catKey = session.categoryKey();
        int page = session.page();

        if (slot == SLOT_PREV) {
            player.closeInventory();
            openCategory(player, catKey, page - 1);
            return;
        }
        if (slot == SLOT_BACK) {
            player.closeInventory();
            openMainMenu(player);
            return;
        }
        if (slot == SLOT_NEXT) {
            player.closeInventory();
            openCategory(player, catKey, page + 1);
            return;
        }

        int slotIdx = slotToItemIndex(slot);
        if (slotIdx < 0) return;

        ShopCategory cat = getCategory(catKey);
        if (cat == null) return;

        int itemIdx = page * ITEMS_PER_PAGE + slotIdx;
        if (itemIdx < 0 || itemIdx >= cat.items().size()) return;

        ShopItem item = cat.items().get(itemIdx);
        player.closeInventory();
        openConfirmMenu(player, item, catKey, page);
    }

    // ── Purchase ──────────────────────────────────────────────────────────────

    private void purchaseItem(Player player, ShopItem item, int qty) {
        double total = item.price() * qty;
        double bal = bridge.getBalance(player.getUniqueId());

        if (bal < total) {
            player.sendMessage(Component.text("Insufficient funds. Need ")
                .color(NamedTextColor.RED)
                .append(Component.text(bridge.getSymbol() + formatPrice(total), NamedTextColor.YELLOW))
                .append(Component.text(", you have ", NamedTextColor.RED))
                .append(Component.text(bridge.getSymbol() + formatPrice(bal) + ".", NamedTextColor.YELLOW)));
            return;
        }

        if (!bridge.withdraw(player.getUniqueId(), total)) {
            player.sendMessage(Component.text("Transaction failed. Insufficient funds.", NamedTextColor.RED));
            return;
        }

        // Give items, splitting across stacks if needed
        int remaining = item.amount() * qty;
        int maxStack = item.material().getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item.material(), give);
            for (Map.Entry<Enchantment, Integer> e : item.enchants().entrySet()) {
                stack.addUnsafeEnchantment(e.getKey(), e.getValue());
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            overflow.values().forEach(drop ->
                player.getWorld().dropItemNaturally(player.getLocation(), drop));
            remaining -= give;
        }

        String itemName = item.displayName() != null
            ? LEGACY.serialize(parseName(item.displayName()))
            : prettifyMaterial(item.material());

        int totalAmount = item.amount() * qty;
        player.sendMessage(Component.text("Purchased ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(totalAmount + "x " + itemName, NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + formatPrice(total) + ".", NamedTextColor.YELLOW)));

        player.playSound(player.getLocation(),
            org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String formatPrice(double price) {
        if (price == Math.floor(price)) return String.valueOf((long) price);
        return String.valueOf(price);
    }

    private String prettifyMaterial(Material mat) {
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

    private int slotToItemIndex(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private ShopCategory getCategory(String key) {
        if (key == null) return null;
        for (ShopCategory cat : categories) {
            if (cat.key().equals(key)) return cat;
        }
        return null;
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }

    public boolean isShopInventory(Inventory inv) {
        return sessions.containsKey(inv) || confirmSessions.containsKey(inv);
    }

    public void onClose(Inventory inv) {
        sessions.remove(inv);
        confirmSessions.remove(inv);
    }
}
