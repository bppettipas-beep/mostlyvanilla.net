package com.mostlyvanilla.auctionhouse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

public class AuctionManager {

    // ── GUI constants ──────────────────────────────────────────────────────────

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = 28;

    // Main GUI nav (row 6)
    private static final int SLOT_MY_LISTINGS = 45;
    private static final int SLOT_EXPIRED     = 46;
    private static final int SLOT_PREV        = 48;
    private static final int SLOT_CLOSE       = 49;
    private static final int SLOT_NEXT        = 50;
    private static final int SLOT_SORT        = 52;

    // Confirm dialog (27 slots)
    private static final int CONFIRM_YES  = 11;
    private static final int CONFIRM_ITEM = 13;
    private static final int CONFIRM_NO   = 15;

    // ── Inner types ────────────────────────────────────────────────────────────

    public enum GuiMode  { ALL, MY_LISTINGS, EXPIRED }
    public enum SortMode {
        NEWEST, PRICE_ASC, PRICE_DESC;
        public SortMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private record AhSession(UUID playerUuid, GuiMode mode, SortMode sort, int page) {}
    private record ConfirmSession(UUID playerUuid, AuctionListing listing) {}

    // ── Fields ─────────────────────────────────────────────────────────────────

    private final MostlyVanillaAuctionHouse plugin;
    private final EconomyBridge bridge;
    private final List<AuctionListing> listings = new ArrayList<>();
    private final Map<Inventory, AhSession>      sessions        = new HashMap<>();
    private final Map<Inventory, ConfirmSession> confirmSessions = new HashMap<>();
    private File listingsFile;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // ── Constructor ────────────────────────────────────────────────────────────

    public AuctionManager(MostlyVanillaAuctionHouse plugin, EconomyBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    // ── Config helpers ─────────────────────────────────────────────────────────

    private int    getMaxListings()      { return plugin.getConfig().getInt("max-listings-per-player", 10); }
    private int    getDurationDays()     { return plugin.getConfig().getInt("listing-duration-days", 7); }
    private double getSaleTaxPercent()   { return plugin.getConfig().getDouble("sale-tax-percent", 5.0); }

    // ── Persistence ────────────────────────────────────────────────────────────

    public void load() {
        listingsFile = new File(plugin.getDataFolder(), "auction-listings.yml");
        if (!listingsFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(listingsFile);
        ConfigurationSection sec = c.getConfigurationSection("listings");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            try {
                String base        = "listings." + id + ".";
                UUID   sellerUuid  = UUID.fromString(c.getString(base + "seller-uuid"));
                String sellerName  = c.getString(base + "seller-name", "Unknown");
                ItemStack item     = c.getItemStack(base + "item");
                if (item == null) continue;
                double price       = c.getDouble(base + "price");
                long   listedAt    = c.getLong(base + "listed-at");
                long   expiresAt   = c.getLong(base + "expires-at");

                listings.add(new AuctionListing(id, sellerUuid, sellerName, item, price, listedAt, expiresAt));
            } catch (Exception e) {
                plugin.getLogger().warning("[AH] Could not load listing " + id + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[AH] Loaded " + listings.size() + " listing(s).");
    }

    public void save() {
        if (listingsFile == null) listingsFile = new File(plugin.getDataFolder(), "auction-listings.yml");
        YamlConfiguration c = new YamlConfiguration();
        for (AuctionListing l : listings) {
            if (l.isSold() || l.isCollected()) continue; // prune dead listings
            String base = "listings." + l.getId() + ".";
            c.set(base + "seller-uuid",  l.getSellerUuid().toString());
            c.set(base + "seller-name",  l.getSellerName());
            c.set(base + "item",         l.getItem());
            c.set(base + "price",        l.getPrice());
            c.set(base + "listed-at",    l.getListedAt());
            c.set(base + "expires-at",   l.getExpiresAt());
        }
        try { c.save(listingsFile); }
        catch (IOException e) { plugin.getLogger().warning("[AH] Could not save listings: " + e.getMessage()); }
    }

    // ── Listing management ─────────────────────────────────────────────────────

    /** Called by /ah sell. Takes item from player hand, creates listing. */
    public void createListing(Player player, double price) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Component.text("Hold the item you want to sell.", NamedTextColor.RED));
            return;
        }
        if (price <= 0) {
            player.sendMessage(Component.text("Price must be greater than zero.", NamedTextColor.RED));
            return;
        }

        long activeCount = listings.stream()
            .filter(l -> l.getSellerUuid().equals(player.getUniqueId()) && l.isActive())
            .count();
        if (activeCount >= getMaxListings()) {
            player.sendMessage(Component.text("You have reached your listing limit (" + getMaxListings() + "). Cancel or wait for listings to expire.", NamedTextColor.RED));
            return;
        }

        long now      = System.currentTimeMillis();
        long expiry   = now + (long) getDurationDays() * 86_400_000L;
        String id     = UUID.randomUUID().toString();
        AuctionListing listing = new AuctionListing(id, player.getUniqueId(), player.getName(), hand, price, now, expiry);
        listings.add(listing);

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        save();

        player.sendMessage(Component.text("[AH] ", NamedTextColor.GOLD)
            .append(Component.text("Listed ", NamedTextColor.GREEN))
            .append(Component.text(itemName(hand), NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + fmt(price), NamedTextColor.YELLOW))
            .append(Component.text(" (" + getDurationDays() + "d listing).", NamedTextColor.GREEN)));
    }

    // ── GUI opening ────────────────────────────────────────────────────────────

    public void openGui(Player player, GuiMode mode, SortMode sort, int page) {
        List<AuctionListing> view = buildView(player, mode, sort);
        int maxPage = view.isEmpty() ? 0 : (view.size() - 1) / ITEMS_PER_PAGE;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        String title = switch (mode) {
            case ALL         -> "Auction House";
            case MY_LISTINGS -> "My Listings";
            case EXPIRED     -> "Expired Listings";
        };
        if (maxPage > 0) title += "  (" + (page + 1) + "/" + (maxPage + 1) + ")";

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(title).decoration(TextDecoration.ITALIC, false));

        fillBorder(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, view.size());
        for (int i = start; i < end; i++) {
            inv.setItem(ITEM_SLOTS[i - start], buildListingDisplay(view.get(i), mode, player));
        }

        // Navigation
        buildNav(inv, mode, sort, page, maxPage, player);

        sessions.put(inv, new AhSession(player.getUniqueId(), mode, sort, page));
        player.openInventory(inv);
    }

    private void buildNav(Inventory inv, GuiMode mode, SortMode sort, int page, int maxPage, Player player) {
        // My Listings button
        String myLabel = mode == GuiMode.MY_LISTINGS ? "&e&lMy Listings" : "&fMy Listings";
        inv.setItem(SLOT_MY_LISTINGS, makeButton(Material.BOOK, myLabel,
            List.of("&7View your active listings")));

        // Expired button
        String exLabel = mode == GuiMode.EXPIRED ? "&e&lExpired" : "&fExpired";
        inv.setItem(SLOT_EXPIRED, makeButton(Material.CHEST, exLabel,
            List.of("&7Collect unsold items")));

        // Prev / Next
        if (page > 0)
            inv.setItem(SLOT_PREV, makeButton(Material.ARROW, "&7← Previous", List.of()));
        if (page < maxPage)
            inv.setItem(SLOT_NEXT, makeButton(Material.ARROW, "&7Next →", List.of()));

        // Close
        inv.setItem(SLOT_CLOSE, makeButton(Material.OAK_DOOR, "&cClose", List.of()));

        // Sort (only meaningful in ALL mode)
        String sortLabel = switch (sort) {
            case NEWEST    -> "&fSort: &eNewest";
            case PRICE_ASC -> "&fSort: &ePrice ↑";
            case PRICE_DESC -> "&fSort: &ePrice ↓";
        };
        String sortHint = switch (sort) {
            case NEWEST    -> "&7Next: Price ↑";
            case PRICE_ASC -> "&7Next: Price ↓";
            case PRICE_DESC -> "&7Next: Newest";
        };
        inv.setItem(SLOT_SORT, makeButton(Material.HOPPER, sortLabel,
            List.of(sortHint, "&7(applies to All view)")));
    }

    private List<AuctionListing> buildView(Player player, GuiMode mode, SortMode sort) {
        return switch (mode) {
            case ALL -> listings.stream()
                .filter(AuctionListing::isActive)
                .sorted(sortComparator(sort))
                .collect(Collectors.toList());
            case MY_LISTINGS -> listings.stream()
                .filter(l -> l.isActive() && l.getSellerUuid().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(AuctionListing::getListedAt).reversed())
                .collect(Collectors.toList());
            case EXPIRED -> listings.stream()
                .filter(l -> l.isExpired() && !l.isCollected() && l.getSellerUuid().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(AuctionListing::getExpiresAt).reversed())
                .collect(Collectors.toList());
        };
    }

    private Comparator<AuctionListing> sortComparator(SortMode sort) {
        return switch (sort) {
            case NEWEST    -> Comparator.comparingLong(AuctionListing::getListedAt).reversed();
            case PRICE_ASC -> Comparator.comparingDouble(AuctionListing::getPrice);
            case PRICE_DESC -> Comparator.comparingDouble(AuctionListing::getPrice).reversed();
        };
    }

    // ── Confirm dialog ─────────────────────────────────────────────────────────

    public void openConfirm(Player player, AuctionListing listing) {
        if (!listing.isActive()) {
            player.sendMessage(Component.text("[AH] That listing is no longer available.", NamedTextColor.RED));
            openGui(player, GuiMode.ALL, SortMode.NEWEST, 0);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("Confirm Purchase").decoration(TextDecoration.ITALIC, false));

        ItemStack glass = makeGlass();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Item preview in center
        ItemStack preview = listing.getItem();
        ItemMeta pm = preview.getItemMeta();
        List<Component> lore = new ArrayList<>(pm.hasLore() ? pm.lore() : List.of());
        lore.add(Component.empty());
        lore.add(comp("&7Seller: &f" + listing.getSellerName()));
        lore.add(comp("&7Price:  &e" + bridge.getSymbol() + fmt(listing.getPrice())));
        pm.lore(lore);
        preview.setItemMeta(pm);
        inv.setItem(CONFIRM_ITEM, preview);

        // Confirm (green)
        inv.setItem(CONFIRM_YES, makeButton(Material.LIME_STAINED_GLASS_PANE, "&a&lConfirm Purchase",
            List.of("&7Pay &e" + bridge.getSymbol() + fmt(listing.getPrice()),
                    "&7Click to buy")));

        // Cancel (red)
        inv.setItem(CONFIRM_NO, makeButton(Material.RED_STAINED_GLASS_PANE, "&c&lCancel",
            List.of("&7Go back")));

        confirmSessions.put(inv, new ConfirmSession(player.getUniqueId(), listing));
        player.openInventory(inv);
    }

    // ── Click handling ─────────────────────────────────────────────────────────

    public void handleClick(Player player, Inventory inv, int slot) {
        // Confirm dialog?
        ConfirmSession cs = confirmSessions.get(inv);
        if (cs != null) {
            handleConfirmClick(player, cs, slot);
            return;
        }

        AhSession session = sessions.get(inv);
        if (session == null) return;

        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_MY_LISTINGS -> { player.closeInventory(); openGui(player, GuiMode.MY_LISTINGS, session.sort(), 0); }
            case SLOT_EXPIRED     -> { player.closeInventory(); openGui(player, GuiMode.EXPIRED, session.sort(), 0); }
            case SLOT_SORT        -> { player.closeInventory(); openGui(player, session.mode(), session.sort().next(), 0); }
            case SLOT_PREV        -> { player.closeInventory(); openGui(player, session.mode(), session.sort(), session.page() - 1); }
            case SLOT_NEXT        -> { player.closeInventory(); openGui(player, session.mode(), session.sort(), session.page() + 1); }
            default -> {
                int idx = slotToIndex(slot);
                if (idx < 0) return;
                List<AuctionListing> view = buildView(player, session.mode(), session.sort());
                int listingIdx = session.page() * ITEMS_PER_PAGE + idx;
                if (listingIdx >= view.size()) return;
                AuctionListing listing = view.get(listingIdx);

                switch (session.mode()) {
                    case ALL -> {
                        if (listing.getSellerUuid().equals(player.getUniqueId())) {
                            player.sendMessage(Component.text("[AH] You cannot buy your own listing.", NamedTextColor.RED));
                            return;
                        }
                        player.closeInventory();
                        openConfirm(player, listing);
                    }
                    case MY_LISTINGS -> cancelListing(player, listing);
                    case EXPIRED     -> collectExpired(player, listing);
                }
            }
        }
    }

    private void handleConfirmClick(Player player, ConfirmSession cs, int slot) {
        if (slot == CONFIRM_NO) {
            player.closeInventory();
            openGui(player, GuiMode.ALL, SortMode.NEWEST, 0);
            return;
        }
        if (slot != CONFIRM_YES) return;

        AuctionListing listing = cs.listing();
        player.closeInventory();

        // Re-validate
        if (!listing.isActive()) {
            player.sendMessage(Component.text("[AH] That listing is no longer available.", NamedTextColor.RED));
            openGui(player, GuiMode.ALL, SortMode.NEWEST, 0);
            return;
        }
        if (listing.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("[AH] You cannot buy your own listing.", NamedTextColor.RED));
            openGui(player, GuiMode.ALL, SortMode.NEWEST, 0);
            return;
        }

        double price = listing.getPrice();
        if (!bridge.withdraw(player.getUniqueId(), price)) {
            player.sendMessage(Component.text("[AH] You cannot afford this (" + bridge.getSymbol() + fmt(price) + ").", NamedTextColor.RED));
            openGui(player, GuiMode.ALL, SortMode.NEWEST, 0);
            return;
        }

        // Tax & payout
        double tax     = price * (getSaleTaxPercent() / 100.0);
        double payout  = price - tax;
        bridge.deposit(listing.getSellerUuid(), payout);

        // Give item to buyer
        giveOrDrop(player, listing.getItem());

        // Notify seller if online
        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
        if (seller != null) {
            seller.sendMessage(Component.text("[AH] ", NamedTextColor.GOLD)
                .append(Component.text(player.getName() + " bought ", NamedTextColor.GRAY))
                .append(Component.text(itemName(listing.getItem()), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GRAY))
                .append(Component.text(bridge.getSymbol() + fmt(payout), NamedTextColor.YELLOW))
                .append(Component.text("  (−" + bridge.getSymbol() + fmt(tax) + " tax)", NamedTextColor.DARK_GRAY)));
        }

        player.sendMessage(Component.text("[AH] ", NamedTextColor.GOLD)
            .append(Component.text("Purchased ", NamedTextColor.GREEN))
            .append(Component.text(itemName(listing.getItem()), NamedTextColor.WHITE))
            .append(Component.text(" for ", NamedTextColor.GREEN))
            .append(Component.text(bridge.getSymbol() + fmt(price) + ".", NamedTextColor.YELLOW)));

        listing.setSold(true);
        save();
        openGui(player, GuiMode.ALL, SortMode.NEWEST, 0);
    }

    // ── Cancel / collect ───────────────────────────────────────────────────────

    private void cancelListing(Player player, AuctionListing listing) {
        if (!listing.getSellerUuid().equals(player.getUniqueId()) || !listing.isActive()) return;
        listing.setCollected(true);
        giveOrDrop(player, listing.getItem());
        save();
        player.sendMessage(Component.text("[AH] ", NamedTextColor.GOLD)
            .append(Component.text("Listing cancelled. ", NamedTextColor.YELLOW))
            .append(Component.text(itemName(listing.getItem()), NamedTextColor.WHITE))
            .append(Component.text(" returned.", NamedTextColor.YELLOW)));
        player.closeInventory();
        openGui(player, GuiMode.MY_LISTINGS, SortMode.NEWEST, 0);
    }

    private void collectExpired(Player player, AuctionListing listing) {
        if (!listing.getSellerUuid().equals(player.getUniqueId()) || !listing.isExpired() || listing.isCollected()) return;
        listing.setCollected(true);
        giveOrDrop(player, listing.getItem());
        save();
        player.sendMessage(Component.text("[AH] ", NamedTextColor.GOLD)
            .append(Component.text("Collected ", NamedTextColor.YELLOW))
            .append(Component.text(itemName(listing.getItem()), NamedTextColor.WHITE))
            .append(Component.text(".", NamedTextColor.YELLOW)));
        player.closeInventory();
        openGui(player, GuiMode.EXPIRED, SortMode.NEWEST, 0);
    }

    // ── Session management ─────────────────────────────────────────────────────

    public boolean isAhGui(Inventory inv)   { return sessions.containsKey(inv) || confirmSessions.containsKey(inv); }
    public void    onClose(Inventory inv)   { sessions.remove(inv); confirmSessions.remove(inv); }

    // ── Item display builder ───────────────────────────────────────────────────

    private ItemStack buildListingDisplay(AuctionListing l, GuiMode mode, Player viewer) {
        ItemStack display = l.getItem();
        ItemMeta meta = display.getItemMeta();

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        lore.add(Component.empty());
        lore.add(comp("&7Seller:  &f" + l.getSellerName()));
        lore.add(comp("&7Price:   &e" + bridge.getSymbol() + fmt(l.getPrice())));

        if (l.isActive()) {
            lore.add(comp("&7Expires: &a" + formatDuration(l.getExpiresAt())));
        } else {
            lore.add(comp("&cExpired"));
        }

        lore.add(Component.empty());
        switch (mode) {
            case ALL -> {
                if (!l.getSellerUuid().equals(viewer.getUniqueId()))
                    lore.add(comp("&bClick to purchase"));
                else
                    lore.add(comp("&7Your listing"));
            }
            case MY_LISTINGS -> lore.add(comp("&cClick to cancel listing"));
            case EXPIRED     -> lore.add(comp("&eClick to collect item"));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    // ── GUI helpers ────────────────────────────────────────────────────────────

    private void fillBorder(Inventory inv) {
        ItemStack glass = makeGlass();
        for (int i = 0; i < 9; i++)  inv.setItem(i, glass);   // top row
        for (int r = 1; r <= 4; r++) { inv.setItem(r * 9, glass); inv.setItem(r * 9 + 8, glass); } // sides
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);  // bottom row (nav resets over this)
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
        ItemMeta meta  = item.getItemMeta();
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

    // ── Static helpers ─────────────────────────────────────────────────────────

    public static String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        String raw = item.getType().name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
        }
        String name = sb.toString().trim();
        return item.getAmount() > 1 ? item.getAmount() + "x " + name : name;
    }

    public static String fmt(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.format("%.2f", d);
    }

    public static String formatDuration(long epochMs) {
        long ms = epochMs - System.currentTimeMillis();
        if (ms <= 0) return "Expired";
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        return m + "m " + (s % 60) + "s";
    }
}
