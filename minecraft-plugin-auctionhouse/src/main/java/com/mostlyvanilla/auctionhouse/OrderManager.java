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
    private static final int SLOT_NEW_ORDER = 53;

    private static final int SLOT_CLAIMED_ITEMS = 47;

    // Currency picker (27-slot GUI)
    private static final int SLOT_CURRENCY_MONEY = 11;
    private static final int SLOT_CURRENCY_BITS  = 15;
    private static final int SLOT_CURRENCY_BACK  = 22;

    // Claim GUI (54-slot chest)
    private static final int CLAIM_TAKE_ALL = 45;
    private static final int CLAIM_BACK     = 49;

    // ── Tradeable materials (computed once) ────────────────────────────────────

    static final List<Material> TRADEABLE_MATERIALS;
    static {
        TRADEABLE_MATERIALS = Arrays.stream(Material.values())
            .filter(m -> m.isItem() && !m.isAir())
            .sorted(Comparator.comparing(Material::name))
            .collect(Collectors.toUnmodifiableList());
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    public enum GuiMode { ALL, MY_ORDERS, ADMIN }

    private record OrderSession(UUID playerUuid, GuiMode mode, int page) {}
    private record PickerSession(UUID playerUuid, int page) {}
    private record CurrencyPickerSession(UUID playerUuid, Material material) {}

    // ── Fields ─────────────────────────────────────────────────────────────────

    private final MostlyVanillaAuctionHouse plugin;
    private final EconomyBridge bridge;
    private final HistoryLogger historyLogger;
    private final List<BuyOrder> orders = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries             = new HashMap<>();
    private final Map<Inventory, OrderSession> sessions                    = new HashMap<>();
    private final Map<Inventory, PickerSession> pickerSessions             = new HashMap<>();
    private final Map<Inventory, CurrencyPickerSession> currencyPickerSessions = new HashMap<>();
    private final Map<Inventory, UUID> claimSessions                       = new HashMap<>();
    private final Map<UUID, Material> pendingOrderMaterial                 = new HashMap<>();
    private final Map<UUID, String>   pendingOrderCurrency                 = new HashMap<>();
    private File ordersFile;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // ── Constructor ────────────────────────────────────────────────────────────

    public OrderManager(MostlyVanillaAuctionHouse plugin, EconomyBridge bridge, HistoryLogger historyLogger) {
        this.plugin         = plugin;
        this.bridge         = bridge;
        this.historyLogger  = historyLogger;
    }

    // ── Config helpers ─────────────────────────────────────────────────────────

    private int getMaxOrders() { return plugin.getConfig().getInt("max-orders-per-player", 5); }

    // ── Persistence ────────────────────────────────────────────────────────────

    public void load() {
        ordersFile = new File(plugin.getDataFolder(), "orders.yml");
        if (!ordersFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(ordersFile);

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
                    String currency  = c.getString(base + "currency", "money");

                    BuyOrder order = new BuyOrder(id, buyerUuid, buyerName, mat, total, price, created, currency);
                    order.setFilledAmount(filled);
                    order.setCancelled(cancelled);
                    if (order.isActive()) orders.add(order);
                } catch (Exception e) {
                    plugin.getLogger().warning("[AH] Could not load order " + id + ": " + e.getMessage());
                }
            }
        }

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
            c.set(base + "currency",      o.getCurrency());
        }

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

    /** Called by /orders create. Takes item from player hand. Defaults to money. */
    public void createOrder(Player player, int amount, double priceEach) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Component.text("Hold the item you want to buy, or use /orders to pick via GUI.", NamedTextColor.RED));
            return;
        }
        createOrderByMaterial(player, hand.getType(), amount, priceEach);
    }

    /** Overload for sign/command usage — defaults to money currency. */
    public boolean createOrderByMaterial(Player player, Material mat, int amount, double priceEach) {
        return createOrderByMaterial(player, mat, amount, priceEach, "money");
    }

    public boolean createOrderByMaterial(Player player, Material mat, int amount, double priceEach, String currency) {
        if (amount <= 0 || priceEach <= 0) {
            player.sendMessage(Component.text("Amount and price must be greater than zero.", NamedTextColor.RED));
            return false;
        }

        long myOrders = orders.stream()
            .filter(o -> o.getBuyerUuid().equals(player.getUniqueId()) && o.isActive())
            .count();
        if (myOrders >= getMaxOrders()) {
            player.sendMessage(Component.text("You have reached your order limit (" + getMaxOrders() + ").", NamedTextColor.RED));
            return false;
        }

        double total = amount * priceEach;
        if (!bridge.withdraw(player.getUniqueId(), total, currency)) {
            player.sendMessage(Component.text("You cannot afford to place this order ("
                + bridge.getSymbol(currency) + AuctionManager.fmt(total) + " required).", NamedTextColor.RED));
            return false;
        }

        String id = UUID.randomUUID().toString();
        BuyOrder order = new BuyOrder(id, player.getUniqueId(), player.getName(), mat, amount, priceEach,
            System.currentTimeMillis(), currency);
        orders.add(order);
        save();
        historyLogger.log(player.getUniqueId(), player.getName(), "ORDER_CREATED",
            "Buy order: " + amount + "x " + prettify(mat) + " at "
                + bridge.getSymbol(currency) + AuctionManager.fmt(priceEach) + " each", total);

        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Buy order created: ", NamedTextColor.GREEN))
            .append(Component.text(amount + "x " + prettify(mat), NamedTextColor.WHITE))
            .append(Component.text(" at ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol(currency) + AuctionManager.fmt(priceEach) + " each", NamedTextColor.YELLOW))
            .append(Component.text(". Escrowed: " + bridge.getSymbol(currency) + AuctionManager.fmt(total) + ".", NamedTextColor.GREEN)));
        return true;
    }

    /** Called by /orders cancel <id>. */
    public void cancelOrder(Player player, String id) {
        BuyOrder order = orders.stream()
            .filter(o -> o.getId().equals(id) && o.getBuyerUuid().equals(player.getUniqueId()))
            .findFirst().orElse(null);
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
        bridge.deposit(player.getUniqueId(), refund, order.getCurrency());
        save();
        historyLogger.log(player.getUniqueId(), player.getName(), "ORDER_CANCELLED",
            "Cancelled order for " + prettify(order.getMaterial()) + ", "
                + bridge.getSymbol(order.getCurrency()) + AuctionManager.fmt(refund) + " refunded", refund);
        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Order cancelled. ", NamedTextColor.YELLOW))
            .append(Component.text(bridge.getSymbol(order.getCurrency()) + AuctionManager.fmt(refund) + " refunded.", NamedTextColor.GREEN)));
    }

    // ── Pending order creation (chat input after item+currency picker) ──────────

    public boolean hasPendingOrder(UUID uuid) { return pendingOrderMaterial.containsKey(uuid); }

    public void cancelPendingOrder(UUID uuid) {
        pendingOrderMaterial.remove(uuid);
        pendingOrderCurrency.remove(uuid);
    }

    /** Called from AsyncChatEvent (scheduled back to main thread). */
    public void handleChatInput(Player player, String message) {
        Material mat = pendingOrderMaterial.get(player.getUniqueId());
        if (mat == null) return;

        if (message.trim().equalsIgnoreCase("cancel")) {
            pendingOrderMaterial.remove(player.getUniqueId());
            pendingOrderCurrency.remove(player.getUniqueId());
            player.sendMessage(Component.text("[Orders] Order creation cancelled.", NamedTextColor.YELLOW));
            return;
        }

        String[] parts = message.trim().split("\\s+");
        if (parts.length < 2) {
            player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text("Type ", NamedTextColor.RED))
                .append(Component.text("<amount> <price>", NamedTextColor.YELLOW))
                .append(Component.text(" — e.g. ", NamedTextColor.RED))
                .append(Component.text("64 1.5", NamedTextColor.YELLOW))
                .append(Component.text(". Type ", NamedTextColor.RED))
                .append(Component.text("cancel", NamedTextColor.RED))
                .append(Component.text(" to abort.", NamedTextColor.RED)));
            return;
        }

        int amount;
        double priceEach;
        try {
            amount    = Integer.parseInt(parts[0]);
            priceEach = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Orders] Invalid number. Try again or type cancel to abort.", NamedTextColor.RED));
            return;
        }

        String currency = pendingOrderCurrency.getOrDefault(player.getUniqueId(), "money");
        pendingOrderMaterial.remove(player.getUniqueId());
        pendingOrderCurrency.remove(player.getUniqueId());
        createOrderByMaterial(player, mat, amount, priceEach, currency);
    }

    // ── Orders GUI ─────────────────────────────────────────────────────────────

    public void openGui(Player player, GuiMode mode, int page) {
        List<BuyOrder> view = buildView(player, mode);
        int maxPage = view.isEmpty() ? 0 : (view.size() - 1) / ITEMS_PER_PAGE;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String title = switch (mode) {
            case MY_ORDERS -> "My Orders";
            case ADMIN     -> "Orders Admin";
            default        -> "Buy Orders";
        };
        if (maxPage > 0) title += "  (" + (page + 1) + "/" + (maxPage + 1) + ")";

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(title).decoration(TextDecoration.ITALIC, false));

        fillBorder(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, view.size());
        for (int i = start; i < end; i++) {
            inv.setItem(ITEM_SLOTS[i - start], buildOrderDisplay(view.get(i), player));
        }

        if (mode != GuiMode.ADMIN) {
            String myLabel = mode == GuiMode.MY_ORDERS ? "&e&lMy Orders" : "&fMy Orders";
            inv.setItem(SLOT_MY_ORDERS, makeButton(Material.BOOK, myLabel, List.of("&7View your buy orders", "&7Click to cancel")));
            inv.setItem(SLOT_NEW_ORDER, makeButton(Material.WRITABLE_BOOK, "&a+ New Order", List.of("&7Create a buy order", "&7without holding the item")));
            List<ItemStack> claimed = pendingDeliveries.get(player.getUniqueId());
            boolean hasClaimed = claimed != null && !claimed.isEmpty();
            int claimCount = hasClaimed ? claimed.stream().mapToInt(ItemStack::getAmount).sum() : 0;
            String claimLabel = hasClaimed ? "&a⬇ Claimed Items &e(" + claimCount + ")" : "&7Claimed Items";
            List<String> claimLore = hasClaimed
                ? List.of("&7Items from filled buy orders", "&aClick to collect")
                : List.of("&7Items from filled buy orders", "&8Nothing to collect yet");
            inv.setItem(SLOT_CLAIMED_ITEMS, makeButton(Material.CHEST, claimLabel, claimLore));
        }
        if (page > 0)       inv.setItem(SLOT_PREV,  makeButton(Material.ARROW,    "&7← Previous", List.of()));
        inv.setItem(SLOT_CLOSE,  makeButton(Material.OAK_DOOR, "&cClose",      List.of()));
        if (page < maxPage) inv.setItem(SLOT_NEXT,  makeButton(Material.ARROW,    "&7Next →",    List.of()));

        sessions.put(inv, new OrderSession(player.getUniqueId(), mode, page));
        player.openInventory(inv);
    }

    private List<BuyOrder> buildView(Player player, GuiMode mode) {
        return switch (mode) {
            case ALL, ADMIN -> orders.stream()
                .filter(BuyOrder::isActive)
                .sorted(Comparator.comparingLong(BuyOrder::getCreatedAt).reversed())
                .collect(Collectors.toList());
            case MY_ORDERS -> orders.stream()
                .filter(o -> o.isActive() && o.getBuyerUuid().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(BuyOrder::getCreatedAt).reversed())
                .collect(Collectors.toList());
        };
    }

    // ── Orders GUI click handling ──────────────────────────────────────────────

    public void handleClick(Player player, Inventory inv, int slot) {
        OrderSession session = sessions.get(inv);
        if (session == null) return;

        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_MY_ORDERS -> {
                if (session.mode() != GuiMode.ADMIN) {
                    player.closeInventory();
                    openGui(player, GuiMode.MY_ORDERS, 0);
                }
            }
            case SLOT_NEW_ORDER -> {
                if (session.mode() != GuiMode.ADMIN) {
                    player.closeInventory();
                    openItemPicker(player, 0);
                }
            }
            case SLOT_CLAIMED_ITEMS -> {
                if (session.mode() != GuiMode.ADMIN) {
                    player.closeInventory();
                    openClaimGui(player);
                }
            }
            case SLOT_PREV -> { player.closeInventory(); openGui(player, session.mode(), session.page() - 1); }
            case SLOT_NEXT -> { player.closeInventory(); openGui(player, session.mode(), session.page() + 1); }
            default -> {
                int idx = slotToIndex(slot);
                if (idx < 0) return;
                List<BuyOrder> view = buildView(player, session.mode());
                int orderIdx = session.page() * ITEMS_PER_PAGE + idx;
                if (orderIdx >= view.size()) return;
                BuyOrder order = view.get(orderIdx);

                switch (session.mode()) {
                    case ADMIN     -> adminForceCancel(player, order);
                    case MY_ORDERS -> {
                        player.closeInventory();
                        cancelOrder(player, order.getId());
                        openGui(player, GuiMode.MY_ORDERS, 0);
                    }
                    default        -> fillOrder(player, order, inv, session);
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

        ItemStack[] contents = player.getInventory().getContents();
        int available = 0;
        for (ItemStack s : contents) {
            if (s != null && s.getType() == order.getMaterial()) available += s.getAmount();
        }

        if (available == 0) {
            player.sendMessage(Component.text("[Orders] You don't have any " + prettify(order.getMaterial()) + ".", NamedTextColor.RED));
            return;
        }

        int toFill     = Math.min(available, order.getRemainingAmount());
        double payment = toFill * order.getPriceEach();

        int remaining = toFill;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null || contents[i].getType() != order.getMaterial()) continue;
            int take = Math.min(remaining, contents[i].getAmount());
            contents[i].setAmount(contents[i].getAmount() - take);
            if (contents[i].getAmount() == 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setContents(contents);

        bridge.deposit(player.getUniqueId(), payment, order.getCurrency());

        pendingDeliveries
            .computeIfAbsent(order.getBuyerUuid(), k -> new ArrayList<>())
            .add(new ItemStack(order.getMaterial(), toFill));
        Player buyer = Bukkit.getPlayer(order.getBuyerUuid());
        if (buyer != null && buyer.isOnline()) {
            buyer.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text(player.getName() + " filled ", NamedTextColor.GREEN))
                .append(Component.text(toFill + "x " + prettify(order.getMaterial()), NamedTextColor.WHITE))
                .append(Component.text(" from your order! Open ", NamedTextColor.GREEN))
                .append(Component.text("/orders", NamedTextColor.YELLOW))
                .append(Component.text(" → Claimed Items to collect.", NamedTextColor.GREEN)));
        }

        order.setFilledAmount(order.getFilledAmount() + toFill);
        save();
        historyLogger.log(player.getUniqueId(), player.getName(), "ORDER_FILL",
            "Filled " + toFill + "x " + prettify(order.getMaterial()) + " order for "
                + bridge.getSymbol(order.getCurrency()) + AuctionManager.fmt(payment), payment);
        historyLogger.log(order.getBuyerUuid(), order.getBuyerName(), "ORDER_RECEIVED",
            "Received " + toFill + "x " + prettify(order.getMaterial()) + " from " + player.getName(), 0);

        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Filled ", NamedTextColor.GREEN))
            .append(Component.text(toFill + "x " + prettify(order.getMaterial()), NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol(order.getCurrency()) + AuctionManager.fmt(payment) + ".", NamedTextColor.YELLOW)));

        if (order.isComplete()) {
            player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text("That order is now fully filled!", NamedTextColor.AQUA)));
        }

        player.closeInventory();
        openGui(player, GuiMode.ALL, session.page());
    }

    // ── Item picker GUI ────────────────────────────────────────────────────────

    public void openItemPicker(Player player, int page) {
        int maxPage = (TRADEABLE_MATERIALS.size() - 1) / ITEMS_PER_PAGE;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String title = "Pick Item — New Order  (" + (page + 1) + "/" + (maxPage + 1) + ")";
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(title).decoration(TextDecoration.ITALIC, false));

        fillBorder(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, TRADEABLE_MATERIALS.size());
        for (int i = start; i < end; i++) {
            Material mat = TRADEABLE_MATERIALS.get(i);
            ItemStack display = new ItemStack(mat);
            ItemMeta meta = display.getItemMeta();
            meta.displayName(comp("&f" + prettify(mat)));
            meta.lore(List.of(
                comp("&8" + mat.name()),
                Component.empty(),
                comp("&aClick to create a buy order")
            ));
            display.setItemMeta(meta);
            inv.setItem(ITEM_SLOTS[i - start], display);
        }

        if (page > 0)       inv.setItem(SLOT_PREV,  makeButton(Material.ARROW,    "&7← Previous", List.of()));
        inv.setItem(SLOT_CLOSE,  makeButton(Material.OAK_DOOR, "&cBack",       List.of("&7Return to orders board")));
        if (page < maxPage) inv.setItem(SLOT_NEXT,  makeButton(Material.ARROW,    "&7Next →",    List.of()));

        pickerSessions.put(inv, new PickerSession(player.getUniqueId(), page));
        player.openInventory(inv);
    }

    public boolean isPickerGui(Inventory inv) { return pickerSessions.containsKey(inv); }

    public void handlePickerClick(Player player, Inventory inv, int slot) {
        PickerSession session = pickerSessions.get(inv);
        if (session == null) return;

        if (slot == SLOT_CLOSE) { player.closeInventory(); openGui(player, GuiMode.ALL, 0); return; }
        if (slot == SLOT_PREV)  { player.closeInventory(); openItemPicker(player, session.page() - 1); return; }
        if (slot == SLOT_NEXT)  { player.closeInventory(); openItemPicker(player, session.page() + 1); return; }

        int idx = slotToIndex(slot);
        if (idx < 0) return;

        int matIdx = session.page() * ITEMS_PER_PAGE + idx;
        if (matIdx >= TRADEABLE_MATERIALS.size()) return;

        Material mat = TRADEABLE_MATERIALS.get(matIdx);
        player.closeInventory();
        openCurrencyPicker(player, mat);
    }

    // ── Currency picker GUI ────────────────────────────────────────────────────

    public void openCurrencyPicker(Player player, Material mat) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("Select Currency").decoration(TextDecoration.ITALIC, false));

        ItemStack glass = makeGlass();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(SLOT_CURRENCY_MONEY, makeButton(Material.GOLD_INGOT, "&6Money",
            List.of("&7Pay with money &e($)", "&aClick to select")));
        inv.setItem(SLOT_CURRENCY_BITS, makeButton(Material.AMETHYST_SHARD, "&dBits",
            List.of("&7Pay with bits &d(⬡)", "&aClick to select")));
        inv.setItem(SLOT_CURRENCY_BACK, makeButton(Material.OAK_DOOR, "&cBack",
            List.of("&7Return to item picker")));

        currencyPickerSessions.put(inv, new CurrencyPickerSession(player.getUniqueId(), mat));
        player.openInventory(inv);
    }

    public boolean isCurrencyPickerGui(Inventory inv) { return currencyPickerSessions.containsKey(inv); }

    public void handleCurrencyClick(Player player, Inventory inv, int slot) {
        CurrencyPickerSession session = currencyPickerSessions.get(inv);
        if (session == null) return;

        if (slot == SLOT_CURRENCY_BACK) {
            player.closeInventory();
            openItemPicker(player, 0);
            return;
        }

        String currency;
        if      (slot == SLOT_CURRENCY_MONEY) currency = "money";
        else if (slot == SLOT_CURRENCY_BITS)  currency = "bits";
        else return;

        Material mat = session.material();
        pendingOrderMaterial.put(player.getUniqueId(), mat);
        pendingOrderCurrency.put(player.getUniqueId(), currency);
        player.closeInventory();

        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("Selected: ", NamedTextColor.GREEN))
            .append(Component.text(prettify(mat), NamedTextColor.WHITE))
            .append(Component.text(" (paying in ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol(currency), NamedTextColor.YELLOW))
            .append(Component.text("). Type ", NamedTextColor.GREEN))
            .append(Component.text("<amount> <price>", NamedTextColor.YELLOW))
            .append(Component.text(" in chat (e.g. ", NamedTextColor.GREEN))
            .append(Component.text("64 1.5", NamedTextColor.YELLOW))
            .append(Component.text("). Type ", NamedTextColor.GREEN))
            .append(Component.text("cancel", NamedTextColor.RED))
            .append(Component.text(" to abort.", NamedTextColor.GREEN)));
    }

    public void onPickerClose(Inventory inv) { pickerSessions.remove(inv); }

    // ── Claim GUI ──────────────────────────────────────────────────────────────

    public void openClaimGui(Player player) {
        List<ItemStack> items = pendingDeliveries.get(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("Claimed Items").decoration(TextDecoration.ITALIC, false));

        ItemStack glass = makeGlass();
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        if (items != null && !items.isEmpty()) {
            int slot = 0;
            for (ItemStack item : items) {
                if (slot >= 45) break;
                inv.setItem(slot++, item.clone());
            }
            inv.setItem(CLAIM_TAKE_ALL, makeButton(Material.LIME_STAINED_GLASS_PANE,
                "&a✔ Take All", List.of("&7Move all items to your inventory")));
        } else {
            inv.setItem(CLAIM_TAKE_ALL, makeButton(Material.GRAY_STAINED_GLASS_PANE,
                "&8Nothing to claim", List.of("&7No filled orders yet")));
        }

        inv.setItem(CLAIM_BACK, makeButton(Material.OAK_DOOR, "&cBack", List.of("&7Return to orders")));

        claimSessions.put(inv, player.getUniqueId());
        player.openInventory(inv);
    }

    public boolean isClaimGui(Inventory inv) { return claimSessions.containsKey(inv); }

    public void handleClaimClick(Player player, Inventory inv, int slot) {
        if (slot == CLAIM_BACK) {
            player.closeInventory();
            openGui(player, GuiMode.ALL, 0);
            return;
        }
        if (slot == CLAIM_TAKE_ALL) {
            handleClaimTakeAll(player, inv);
        }
    }

    private void handleClaimTakeAll(Player player, Inventory inv) {
        List<ItemStack> leftover = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            if (!overflow.isEmpty()) {
                leftover.addAll(overflow.values());
            }
            inv.setItem(i, null);
        }
        // Put leftover back into the GUI
        int s = 0;
        for (ItemStack lo : leftover) {
            while (s < 45 && inv.getItem(s) != null && !inv.getItem(s).getType().isAir()) s++;
            if (s >= 45) break;
            inv.setItem(s++, lo);
        }
        player.updateInventory();
        if (leftover.isEmpty()) {
            player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text("All items collected!", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text("Inventory full — remaining items kept for later.", NamedTextColor.YELLOW)));
        }
    }

    public void onClaimClose(Inventory inv) {
        UUID uuid = claimSessions.remove(inv);
        if (uuid == null) return;
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) remaining.add(item.clone());
        }
        if (remaining.isEmpty()) {
            pendingDeliveries.remove(uuid);
        } else {
            pendingDeliveries.put(uuid, remaining);
        }
        save();
    }

    // ── Pending deliveries ─────────────────────────────────────────────────────

    public void deliverPending(Player player) {
        List<ItemStack> items = pendingDeliveries.get(player.getUniqueId());
        if (items == null || items.isEmpty()) return;
        int total = items.stream().mapToInt(ItemStack::getAmount).sum();
        player.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
            .append(Component.text("You have ", NamedTextColor.GREEN))
            .append(Component.text(total + " unclaimed item(s) ", NamedTextColor.WHITE))
            .append(Component.text("from filled buy orders! Open ", NamedTextColor.GREEN))
            .append(Component.text("/orders", NamedTextColor.YELLOW))
            .append(Component.text(" → Claimed Items to collect.", NamedTextColor.GREEN)));
    }

    // ── Admin wipe ─────────────────────────────────────────────────────────────

    /** Cancels all buy orders and drops any pending deliveries for the given player. */
    public void wipePlayer(UUID uuid) {
        orders.removeIf(o -> o.getBuyerUuid().equals(uuid));
        pendingDeliveries.remove(uuid);
        save();
    }

    /** Wipes all orders and pending deliveries for every player. */
    public void wipeAll() {
        orders.clear();
        pendingDeliveries.clear();
        save();
    }

    /** Force-cancels an order on behalf of an admin; refunds escrow to the buyer. */
    private void adminForceCancel(Player admin, BuyOrder order) {
        if (!order.isActive()) {
            admin.sendMessage(Component.text("[Orders Admin] That order is no longer active.", NamedTextColor.RED));
            return;
        }
        double refund = order.getRemainingEscrow();
        order.setCancelled(true);
        bridge.deposit(order.getBuyerUuid(), refund, order.getCurrency());

        Player buyer = Bukkit.getPlayer(order.getBuyerUuid());
        if (buyer != null && buyer.isOnline()) {
            buyer.sendMessage(Component.text("[Orders] ", NamedTextColor.GOLD)
                .append(Component.text("An admin cancelled your buy order for ", NamedTextColor.RED))
                .append(Component.text(prettify(order.getMaterial()), NamedTextColor.WHITE))
                .append(Component.text(". Escrow refunded: ", NamedTextColor.RED))
                .append(Component.text(bridge.getSymbol(order.getCurrency()) + AuctionManager.fmt(refund) + ".", NamedTextColor.YELLOW)));
        }

        save();
        admin.sendMessage(Component.text("[Orders Admin] ", NamedTextColor.GOLD)
            .append(Component.text("Force-cancelled order for ", NamedTextColor.GREEN))
            .append(Component.text(order.getBuyerName(), NamedTextColor.WHITE))
            .append(Component.text(". Refunded: ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol(order.getCurrency()) + AuctionManager.fmt(refund) + ".", NamedTextColor.YELLOW)));

        admin.closeInventory();
        openGui(admin, GuiMode.ADMIN, 0);
    }

    // ── Session management ─────────────────────────────────────────────────────

    public boolean isOrdersGui(Inventory inv) { return sessions.containsKey(inv); }

    public void onClose(Inventory inv) {
        sessions.remove(inv);
        pickerSessions.remove(inv);
        currencyPickerSessions.remove(inv);
        // Claim GUI has its own close handler; don't double-remove
    }

    // ── Item display builder ───────────────────────────────────────────────────

    private ItemStack buildOrderDisplay(BuyOrder o, Player viewer) {
        ItemStack display = new ItemStack(o.getMaterial());
        ItemMeta meta = display.getItemMeta();
        meta.displayName(comp("&f" + prettify(o.getMaterial()) + "  &8[" + o.getFilledAmount() + "/" + o.getTotalAmount() + "]"));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(comp("&7Buyer:    &f" + o.getBuyerName()));
        lore.add(comp("&7Wants:    &f" + o.getRemainingAmount() + " more"));
        lore.add(comp("&7Pays:     &e" + bridge.getSymbol(o.getCurrency()) + AuctionManager.fmt(o.getPriceEach()) + " each"));
        lore.add(comp("&7Total:    &e" + bridge.getSymbol(o.getCurrency()) + AuctionManager.fmt(o.getRemainingEscrow())));
        lore.add(Component.empty());

        OrderSession viewerSession = sessions.values().stream()
            .filter(s -> s.playerUuid().equals(viewer.getUniqueId()))
            .findFirst().orElse(null);
        if (viewerSession != null && viewerSession.mode() == GuiMode.ADMIN) {
            lore.add(comp("&4[Admin] &cClick to force-cancel order"));
        } else if (o.getBuyerUuid().equals(viewer.getUniqueId())) {
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

    // ── Static utilities (used by AhListener for sign handling) ───────────────

    public static String prettify(Material mat) {
        String raw = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }

    /** Tries to resolve a material from a player-typed string (case-insensitive, spaces=underscores). */
    public static Material parseMaterial(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = input.trim().toUpperCase().replace(' ', '_');
        try {
            Material m = Material.valueOf(normalized);
            return (m.isItem() && !m.isAir()) ? m : null;
        } catch (IllegalArgumentException e) {
            for (Material m : TRADEABLE_MATERIALS) {
                if (m.name().equalsIgnoreCase(normalized)
                        || prettify(m).equalsIgnoreCase(input.trim())) {
                    return m;
                }
            }
            return null;
        }
    }
}
