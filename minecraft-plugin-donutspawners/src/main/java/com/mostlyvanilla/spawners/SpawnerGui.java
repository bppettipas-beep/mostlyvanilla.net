package com.mostlyvanilla.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SpawnerGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Main GUI button slots (27-slot chest)
    private static final int SLOT_INFO    = 4;
    private static final int SLOT_COLLECT = 10;
    private static final int SLOT_VIEW    = 12;
    private static final int SLOT_XP      = 14;
    private static final int SLOT_SELL    = 16;
    private static final int SLOT_DROP    = 20;
    private static final int SLOT_FILTER  = 24;

    // Filter GUI back button slot (27-slot chest, center of bottom row)
    private static final int FILTER_BACK  = 22;

    private final SpawnerManager manager;
    private final SpawnerConfig  cfg;
    private final EconomyBridge  economy;
    private final HistoryLogger  historyLogger;

    private enum GuiType { MAIN, STORAGE, FILTER }
    private record Session(SpawnerData data, GuiType type, List<Material> filterOrder) {
        Session(SpawnerData data, GuiType type) { this(data, type, List.of()); }
    }
    private final Map<Inventory, Session> sessions = new HashMap<>();

    public SpawnerGui(SpawnerManager manager, SpawnerConfig cfg, EconomyBridge economy, HistoryLogger historyLogger) {
        this.manager       = manager;
        this.cfg           = cfg;
        this.economy       = economy;
        this.historyLogger = historyLogger;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void openMain(Player player, SpawnerData data) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text(data.getType().getTitle(data.getStack()))
                .decoration(TextDecoration.ITALIC, false));
        fillGlass(inv, 27);
        inv.setItem(SLOT_INFO,    buildInfoItem(data));
        inv.setItem(SLOT_COLLECT, btn(Material.CHEST,             "&a✦ Collect All",    "&7Move stored items to your inventory"));
        inv.setItem(SLOT_VIEW,    btn(Material.BARREL,            "&e⊞ View Storage",   "&7Browse stored items"));
        inv.setItem(SLOT_XP,      btn(Material.EXPERIENCE_BOTTLE, "&b✦ Collect XP",     "&7Claim &e" + data.getXp() + " XP"));
        inv.setItem(SLOT_SELL,    btn(Material.GOLD_INGOT,        "&6$ Sell All",
            economy.isAvailable() ? "&7Sell all stored items for money" : "&8Economy plugin not loaded"));
        inv.setItem(SLOT_DROP,    btn(Material.DROPPER,           "&e⬇ Drop All",       "&7Drop all stored items on the ground"));
        inv.setItem(SLOT_FILTER,  btn(Material.HOPPER,            "&d⚙ Filter Drops",   "&7Choose which items this spawner produces"));
        sessions.put(inv, new Session(data, GuiType.MAIN));
        player.openInventory(inv);
    }

    public void openStorage(Player player, SpawnerData data) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("Storage — " + data.getType().getDisplayName())
                .decoration(TextDecoration.ITALIC, false));
        fillGlass(inv, 54);

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : data.getStorage().entrySet()) {
            if (slot >= 45) break;
            ItemStack item = new ItemStack(entry.getKey());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(prettify(entry.getKey()))
                .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Stored: " + entry.getValue())
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        inv.setItem(49, btn(Material.OAK_DOOR, "&cBack", "&7Return to spawner menu"));
        sessions.put(inv, new Session(data, GuiType.STORAGE));
        player.openInventory(inv);
    }

    public void openFilter(Player player, SpawnerData data) {
        List<Material> order = new ArrayList<>(cfg.getDrops(data.getType()).keySet());

        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("Drop Filter — " + data.getType().getDisplayName())
                .decoration(TextDecoration.ITALIC, false));
        fillGlass(inv, 27);

        for (int i = 0; i < order.size() && i < 18; i++) {
            inv.setItem(i, buildFilterItem(order.get(i), data.isDropEnabled(order.get(i))));
        }
        inv.setItem(FILTER_BACK, btn(Material.OAK_DOOR, "&cBack", "&7Return to spawner menu"));

        sessions.put(inv, new Session(data, GuiType.FILTER, order));
        player.openInventory(inv);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Inventory top = e.getView().getTopInventory();
        Session session = sessions.get(top);
        if (session == null) return;

        e.setCancelled(true);
        player.updateInventory();
        if (e.getClickedInventory() != top) return;

        int slot = e.getSlot();
        SpawnerData data = session.data();

        if (session.type() == GuiType.STORAGE) {
            if (slot == 49) { player.closeInventory(); openMain(player, data); }
            return;
        }

        if (session.type() == GuiType.FILTER) {
            if (slot == FILTER_BACK) { player.closeInventory(); openMain(player, data); return; }
            List<Material> order = session.filterOrder();
            if (slot < order.size()) {
                Material mat = order.get(slot);
                data.toggleDrop(mat);
                manager.markDirty();
                top.setItem(slot, buildFilterItem(mat, data.isDropEnabled(mat)));
            }
            return;
        }

        switch (slot) {
            case SLOT_COLLECT -> handleCollect(player, top, data);
            case SLOT_VIEW    -> { player.closeInventory(); openStorage(player, data); }
            case SLOT_XP      -> handleXp(player, top, data);
            case SLOT_SELL    -> handleSell(player, top, data);
            case SLOT_DROP    -> handleDropAll(player, top, data);
            case SLOT_FILTER  -> { player.closeInventory(); openFilter(player, data); }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        sessions.remove(e.getInventory());
    }

    // ── Button actions ────────────────────────────────────────────────────────

    private void handleCollect(Player player, Inventory inv, SpawnerData data) {
        Map<Material, Integer> toGive = data.drainStorage();
        if (toGive.isEmpty()) { player.sendMessage(msg("&e[Spawners] &7Nothing stored yet.")); return; }

        Map<Material, Integer> leftover = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : toGive.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int batch = Math.min(entry.getKey().getMaxStackSize(), remaining);
                Map<Integer, ItemStack> overflow = player.getInventory()
                    .addItem(new ItemStack(entry.getKey(), batch));
                if (!overflow.isEmpty()) {
                    int unfit = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                    leftover.merge(entry.getKey(), unfit, Integer::sum);
                    remaining -= batch - unfit;
                    break;
                }
                remaining -= batch;
            }
        }
        leftover.forEach(data::addStorage);
        manager.markDirty();
        refreshInfo(inv, data);
        player.sendMessage(msg("&a[Spawners] &7Items moved to your inventory."));
    }

    private void handleXp(Player player, Inventory inv, SpawnerData data) {
        int xp = data.drainXp();
        if (xp == 0) { player.sendMessage(msg("&e[Spawners] &7No XP stored yet.")); return; }
        player.giveExp(xp);
        manager.markDirty();
        refreshInfo(inv, data);
        inv.setItem(SLOT_XP, btn(Material.EXPERIENCE_BOTTLE, "&b✦ Collect XP", "&7Claim &e0 XP"));
        player.sendMessage(msg("&a[Spawners] &7Granted &e" + xp + " XP&7."));
    }

    private void handleSell(Player player, Inventory inv, SpawnerData data) {
        if (!economy.isAvailable()) {
            player.sendMessage(msg("&c[Spawners] Economy plugin is not loaded.")); return;
        }
        if (data.getStorage().isEmpty()) {
            player.sendMessage(msg("&e[Spawners] &7Nothing to sell.")); return;
        }
        double total = data.getStorage().entrySet().stream()
            .mapToDouble(e -> cfg.getSellPrice(e.getKey()) * e.getValue())
            .sum();
        if (total <= 0) {
            player.sendMessage(msg("&e[Spawners] &7None of the stored items have a sell price.")); return;
        }
        data.drainStorage();
        economy.deposit(player.getUniqueId(), total);
        historyLogger.log(player.getUniqueId(), player.getName(), "SELL",
            "Sold spawner drops for $" + fmt(total), total);
        manager.markDirty();
        refreshInfo(inv, data);
        player.sendMessage(msg("&a[Spawners] &7Sold everything for &e$" + fmt(total) + "&7."));
    }

    private void handleDropAll(Player player, Inventory inv, SpawnerData data) {
        Map<Material, Integer> items = data.drainStorage();
        if (items.isEmpty()) { player.sendMessage(msg("&e[Spawners] &7Nothing stored to drop.")); return; }
        Location dropLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize());
        items.forEach((mat, amount) -> {
            int remaining = amount;
            while (remaining > 0) {
                int batch = Math.min(mat.getMaxStackSize(), remaining);
                dropLoc.getWorld().dropItem(dropLoc, new ItemStack(mat, batch));
                remaining -= batch;
            }
        });
        manager.markDirty();
        refreshInfo(inv, data);
        player.sendMessage(msg("&a[Spawners] &7Items dropped in front of you."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshInfo(Inventory inv, SpawnerData data) {
        inv.setItem(SLOT_INFO, buildInfoItem(data));
    }

    private ItemStack buildInfoItem(SpawnerData data) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(data.getType().getTitle(data.getStack()))
            .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Stack: ×" + data.getStack())
            .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (!data.getStorage().isEmpty()) {
            lore.add(Component.text("Stored:").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            data.getStorage().forEach((mat, amt) -> lore.add(
                Component.text("  " + prettify(mat) + ": " + amt)
                    .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        } else {
            lore.add(Component.text("Storage empty").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (data.getXp() > 0) {
            lore.add(Component.text("XP: " + data.getXp())
                .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildFilterItem(Material mat, boolean enabled) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(prettify(mat))
            .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(enabled ? "✔ Enabled — click to disable" : "✘ Disabled — click to enable")
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack btn(Material mat, String name, String loreLine) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(Collections.singletonList(
            LEGACY.deserialize(loreLine).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private void fillGlass(Inventory inv, int size) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  m = g.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        g.setItemMeta(m);
        for (int i = 0; i < size; i++) inv.setItem(i, g);
    }

    private String prettify(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String w : mat.name().split("_")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                sb.append(w.substring(1).toLowerCase()).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private Component msg(String s) { return LEGACY.deserialize(s); }

    public boolean isGuiInventory(Inventory inv) { return sessions.containsKey(inv); }
}
