package com.mostlyvanilla.auctionhouse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class OrderManager {

    // ── GUI constants ──────────────────────────────────────────────────────────

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = 28;

    private static final int SLOT_MY_ORDERS = 45;
    private static final int SLOT_PREV      = 48;
    private static final int SLOT_CLOSE     = 49;
    private static final int SLOT_NEXT      = 50;

    // ── Inner types ────────────────────────────────────────────────────────────

    public enum GuiMode { ALL, MY_ORDERS }

    private record OrderSession(UUID playerUuid, GuiMode mode, int page) {}

    // ── Fields ─────────────────────────────────────────────────────────────────

    private final MostlyVanillaAuctionHouse plugin;
    private final EconomyBridge bridge;
    private final List<BuyOrder> orders = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new HashMap<>();
    private final Map<Inventory, OrderSession> sessions = new HashMap<>();
    private File ordersFile;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // ── Constructor ────────────────────────────────────────────────────────────

    public OrderManager(MostlyVanillaAuctionHouse plugin, EconomyBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    // ── Config helpers ─────────────────────────────────────────────────────────

    private int getMaxOrders() { return plugin.getConfig().getInt("max-orders-per-player", 5); }

    // ── Persistence ────────────────────────────────────────────────────────────

    public void load() {
        ordersFile = new File(plugin.getDataFolder(), "orders.yml");
        if (!ordersFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(ordersFile);

        // Load orders
        ConfigurationSection orderSec = c.getConfigurationSection("orders");
        if (orderSec != null) {
            for (String id : orderSec.getKeys(false)) {
                try {
                    String base      = "orders." + id + ".";
                    UUID buyerUuid   = UUID.fromString(c.getString(base + "buyer-uuid"));
                    String buyerName = c.getString(base + "buyer-name", "Unknown");
                    Material mat     = Material.valueOf(c.getString(base + "material"));
                    int total        = c.getInt(base + "total-amount");
                    int filled       = c.getInt(base + "filled-amount", 0);
                    double price     = c.getDouble(base + "price-each");
                    long created     = c.getLong(base + "created-at");
                    boolean cancelled = c.getBoolean(base + "cancelled", false);

                    BuyOrder order = new BuyOrder(id, buyerUuid, buyerName, mat, total, price, created);
                    order.setFilledAmount(filled);
                    order.setCancelled(cancelled);
                    if (order.isActive()) orders.add(order);
                } catch (Exception e) {
                    plugin.getLogger().warning("[AH] Could not load order " + id + ": " + e.getMessage());
                }
            }
        }

        // Load pending deliveries
        ConfigurationSection pendSec = c.getConfigurationSection("pending-deliveries");
        if (pendSec != null) {
            for (String uuidStr : pendSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection items = pendSec.getConfigurationSection(uuidStr);
                    if (items == null) continue;
                    List<ItemStack> list = new ArrayList<>();
                    for (String idx : items.getKeys(false)) {
                        ItemStack item = items.getItemStack(idx);
                        if (item != null) list.add(item);
                    }
                    if (!list.isEmpty()) pendingDeliveries.put(uuid, list);
                } catch (Exception e) {
                    plugin.getLogger().warning("[AH] Could not load pending delivery for " + uuidStr + ": " + e.getMessage());
                }
            }
        }

        plugin.getLogger().info("[AH] Loaded " + orders.size() + " order(s).");
    }

    public void save() {
        if (ordersFile == null) ordersFile = new File(plugin.getDataFolder(), "orders.yml");
        YamlConfiguration c = new YamlConfiguration();

        // Save active orders only
        for (BuyOrder o : orders) {
            if (!o.isActive()) continue;
            String base = "orders." + o.getId() + ".";
            c.set(base + "buyer-uuid",    o.getBuyerUuid().toString());
            c.set(base + "buyer-name",    o.getBuyerName());
            c.set(base + "material",      o.getMaterial().name());
            c.set(base + "total-amount",  o.getTotalAmount());
            c.set(base + "filled-amount", o.getFilledAmount());
            c.set(base + "price-each",    o.getPriceEach());
            c.set(base + "created-at",    o.getCreatedAt());
            c.set(base + "cancelled",     o.isCancelled());
        }

        // Save pending deliveries
        int i = 0;
        for (Map.Entry<UUID, List<ItemStack>> entry : pendingDeliveries.entrySet()) {
            String base = "pending-deliveries." + entry.getKey() + ".";
            int idx = 0;
            for (ItemStack item : entry.getValue()) {
                c.set(base + idx, item);
                idx++;
            }
        }

        try { c.save(ordersFile); }
        catch (IOException e) { plugin.getLogger().warning("[AH] Could not save orders: " + e.getMessage()); }
    }

    // ── Order management ───────────────────────────────────────────────────────

    /** Called by /orders create. Escrows money and creates a buy order. */
    public void createOrder(Player player, int amount, double priceEach) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Component.text("Hold the item you want to buy.", NamedTextColor.RED));
            return;
        }
        if (amount <= 0 || priceEach <= 0) {
            player.sendMessage(Component.text("Amount and price must be greater than zero.", NamedTextColor.RED));
            return;
        }

        long myOrders = orders.stream()
            .filter(o -> o.getBuyerUuid().equals(player.getUniqueId()) && o.isActive())
            .count();
        if (myOrders >= getMaxOrders()) {
            player.sendMessage(Component.text("You have reached your order limit (" + getMaxOrders() + ").", NamedTextColor.RED));
            return;
        }

        double total = amount * priceEach;
        if (!bridge.withdraw(player.getUniqueId(), total)) {
            player.sendMessage(Component.text("You cannot afford to place this order (" + bridge.getSymbol() + AuctionManager.fmt(total) + " required).", NamedTextColor.RED));
            return;
        }

        String id = UUID.randomUUID().toString();
        BuyOrder order = new BuyOrder(id, player.getUniqueId(), player.getName(), hand.getType(), amount, priceEach, System.currentTimeMillis());
        orders.add(order);
        save();

        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Buy order created: ", NamedTextColor.GREEN))
            .append(Component.text(amount + "x " + prettify(hand.getType()), NamedTextColor.WHITE))
            .append(Component.text(" at ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + AuctionManager.fmt(priceEach) + " each", NamedTextColor.YELLOW))
            .append(Component.text(". Escrowed: " + bridge.getSymbol() + AuctionManager.fmt(total) + ".", NamedTextColor.GREEN)));
    }

    /** Called by /orders cancel <id>. */
    public void cancelOrder(Player player, String id) {
        BuyOrder order = orders.stream().filter(o -> o.getId().equals(id) && o.getBuyerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
        if (order == null) {
            player.sendMessage(Component.text("Order not found or not yours.", NamedTextColor.RED));
            return;
        }
        if (!order.isActive()) {
            player.sendMessage(Component.text("That order is already complete or cancelled.", NamedTextColor.RED));
            return;
        }
        double refund = order.getRemainingEscrow();
        order.setCancelled(true);
        bridge.deposit(player.getUniqueId(), refund);
        save();
        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Order cancelled. ", NamedTextColor.YELLOW))
            .append(Component.text(bridge.getSymbol() + AuctionManager.fmt(refund) + " refunded.", NamedTextColor.GREEN)));
    }

    // ── GUI opening ────────────────────────────────────────────────────────────

    public void openGui(Player player, GuiMode mode, int page) {
        List<BuyOrder> view = buildView(player, mode);
        int maxPage = view.isEmpty() ? 0 : (view.size() - 1) / ITEMS_PER_PAGE;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String title = mode == GuiMode.MY_ORDERS ? "My Orders" : "Buy Orders";
        if (maxPage > 0) title += "  (" + (page + 1) + "/" + (maxPage + 1) + ")";

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(title).decoration(TextDecoration.ITALIC, false));

        fillBorder(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, view.size());
        for (int i = start; i < end; i++) {
            inv.setItem(ITEM_SLOTS[i - start], buildOrderDisplay(view.get(i), player));
        }

        // Nav
        String myLabel = mode == GuiMode.MY_ORDERS ? "&e&lMy Orders" : "&fMy Orders";
        inv.setItem(SLOT_MY_ORDERS, makeButton(Material.BOOK, myLabel, List.of("&7View your buy orders", "&7Click to cancel")));
        if (page > 0)       inv.setItem(SLOT_PREV, makeButton(Material.ARROW, "&7← Previous", List.of()));
        inv.setItem(SLOT_CLOSE, makeButton(Material.OAK_DOOR, "&cClose", List.of()));
        if (page < maxPage) inv.setItem(SLOT_NEXT, makeButton(Material.ARROW, "&7Next →", List.of()));

        sessions.put(inv, new OrderSession(player.getUniqueId(), mode, page));
        player.openInventory(inv);
    }

    private List<BuyOrder> buildView(Player player, GuiMode mode) {
        return switch (mode) {
            case ALL -> orders.stream()
                .filter(BuyOrder::isActive)
                .sorted(Comparator.comparingLong(BuyOrder::getCreatedAt).reversed())
                .collect(Collectors.toList());
            case MY_ORDERS -> orders.stream()
                .filter(o -> o.isActive() && o.getBuyerUuid().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(BuyOrder::getCreatedAt).reversed())
                .collect(Collectors.toList());
        };
    }

    // ── Click handling ─────────────────────────────────────────────────────────

    public void handleClick(Player player, Inventory inv, int slot) {
        OrderSession session = sessions.get(inv);
        if (session == null) return;

        switch (slot) {
            case SLOT_CLOSE     -> player.closeInventory();
            case SLOT_MY_ORDERS -> { player.closeInventory(); openGui(player, GuiMode.MY_ORDERS, 0); }
            case SLOT_PREV      -> { player.closeInventory(); openGui(player, session.mode(), session.page() - 1); }
            case SLOT_NEXT      -> { player.closeInventory(); openGui(player, session.mode(), session.page() + 1); }
            default -> {
                int idx = slotToIndex(slot);
                if (idx < 0) return;
                List<BuyOrder> view = buildView(player, session.mode());
                int orderIdx = session.page() * ITEMS_PER_PAGE + idx;
                if (orderIdx >= view.size()) return;
                BuyOrder order = view.get(orderIdx);

                if (session.mode() == GuiMode.MY_ORDERS) {
                    // Cancel your own order
                    player.closeInventory();
                    cancelOrder(player, order.getId());
                    openGui(player, GuiMode.MY_ORDERS, 0);
                } else {
                    // Fill the order
                    fillOrder(player, order, inv, session);
                }
            }
        }
    }

    private void fillOrder(Player player, BuyOrder order, Inventory inv, OrderSession session) {
        if (!order.isActive()) {
            player.sendMessage(Component.text("[Orders] That order is no longer active.", NamedTextColor.RED));
            player.closeInventory();
            openGui(player, GuiMode.ALL, 0);
            return;
        }
        if (order.getBuyerUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("[Orders] You cannot fill your own order.", NamedTextColor.RED));
            return;
        }

        // Count matching items in seller's inventory
        ItemStack[] contents = player.getInventory().getContents();
        int available = 0;
        for (ItemStack s : contents) {
            if (s != null && s.getType() == order.getMaterial()) available += s.getAmount();
        }

        if (available == 0) {
            player.sendMessage(Component.text("[Orders] You don't have any " + prettify(order.getMaterial()) + ".", NamedTextColor.RED));
            return;
        }

        int toFill    = Math.min(available, order.getRemainingAmount());
        double payment = toFill * order.getPriceEach();

        // Remove items from seller
        int remaining = toFill;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null || contents[i].getType() != order.getMaterial()) continue;
            int take = Math.min(remaining, contents[i].getAmount());
            contents[i].setAmount(contents[i].getAmount() - take);
            if (contents[i].getAmount() == 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setContents(contents);

        // Pay seller
        bridge.deposit(player.getUniqueId(), payment);

        // Deliver items to buyer (or queue for pending delivery if offline)
        Player buyer = Bukkit.getPlayer(order.getBuyerUuid());
        if (buyer != null && buyer.isOnline()) {
            giveOrDrop(buyer, new ItemStack(order.getMaterial(), toFill));
            buyer.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text(player.getName() + " filled ", NamedTextColor.GREEN))
                .append(Component.text(toFill + "x " + prettify(order.getMaterial()), NamedTextColor.WHITE))
                .append(Component.text(" from your order!", NamedTextColor.GREEN)));
        } else {
            // Queue for delivery on login
            pendingDeliveries
                .computeIfAbsent(order.getBuyerUuid(), k -> new ArrayList<>())
                .add(new ItemStack(order.getMaterial(), toFill));
        }

        // Update order
        order.setFilledAmount(order.getFilledAmount() + toFill);
        save();

        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Filled ", NamedTextColor.GREEN))
            .append(Component.text(toFill + "x " + prettify(order.getMaterial()), NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + AuctionManager.fmt(payment) + ".", NamedTextColor.YELLOW)));

        if (order.isComplete()) {
            player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text("That order is now fully filled!", NamedTextColor.AQUA)));
        }

        // Refresh GUI
        player.closeInventory();
        openGui(player, GuiMode.ALL, session.page());
    }

    // ── Pending deliveries ─────────────────────────────────────────────────────

    /** Called on PlayerJoinEvent to give any items that were filled while the buyer was offline. */
    public void deliverPending(Player player) {
        List<ItemStack> items = pendingDeliveries.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) return;

        int totalDelivered = 0;
        for (ItemStack item : items) {
            giveOrDrop(player, item);
            totalDelivered += item.getAmount();
        }
        save();

        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("You received ", NamedTextColor.GREEN))
            .append(Component.text(totalDelivered + " pending item(s)", NamedTextColor.WHITE))
            .append(Component.text(" from buy orders filled while you were offline!", NamedTextColor.GREEN)));
    }

    // ── Session management ─────────────────────────────────────────────────────

    public boolean isOrdersGui(Inventory inv) { return sessions.containsKey(inv); }
    public void    onClose(Inventory inv)     { sessions.remove(inv); }

    // ── Item display builder ───────────────────────────────────────────────────

    private ItemStack buildOrderDisplay(BuyOrder o, Player viewer) {
        ItemStack display = new ItemStack(o.getMaterial());
        ItemMeta meta = display.getItemMeta();
        meta.displayName(comp("&f" + prettify(o.getMaterial()) + "  &8[" + o.getFilledAmount() + "/" + o.getTotalAmount() + "]"));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(comp("&7Buyer:    &f" + o.getBuyerName()));
        lore.add(comp("&7Wants:    &f" + o.getRemainingAmount() + " more"));
        lore.add(comp("&7Pays:     &e" + bridge.getSymbol() + AuctionManager.fmt(o.getPriceEach()) + " each"));
        lore.add(comp("&7Total:    &e" + bridge.getSymbol() + AuctionManager.fmt(o.getRemainingEscrow())));
        lore.add(Component.empty());

        if (o.getBuyerUuid().equals(viewer.getUniqueId())) {
            lore.add(comp("&7Your order"));
            lore.add(comp("&cClick to cancel (refunds escrow)"));
        } else {
            lore.add(comp("&aClick to fill from your inventory"));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    // ── GUI helpers ────────────────────────────────────────────────────────────

    private void fillBorder(Inventory inv) {
        ItemStack glass = makeGlass();
        for (int i = 0; i < 9; i++)  inv.setItem(i, glass);
        for (int r = 1; r <= 4; r++) { inv.setItem(r * 9, glass); inv.setItem(r * 9 + 8, glass); }
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
    }

    private ItemStack makeGlass() {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        g.setItemMeta(m);
        return g;
    }

    private ItemStack makeButton(Material mat, String amp, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(amp).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreLines.stream().map(l -> LEGACY.deserialize(l).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private Component comp(String amp) {
        return LEGACY.deserialize(amp).decoration(TextDecoration.ITALIC, false);
    }

    private int slotToIndex(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) if (ITEM_SLOTS[i] == slot) return i;
        return -1;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    private String prettify(Material mat) {
        String raw = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }
}
