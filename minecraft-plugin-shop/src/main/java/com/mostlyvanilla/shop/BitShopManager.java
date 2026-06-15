package com.mostlyvanilla.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BitShopManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Row 0 (header): gold glass, title at 4, close at 8
    // Row 1: black, spawner label at 13
    // Row 2 (18-26): spawner items (up to 9)
    // Row 3 (27-35): orange divider
    // Row 4: black, crate key label at 40
    // Row 5 (45-53): crate keys centered
    private static final int SLOT_TITLE         = 4;
    private static final int SLOT_CLOSE         = 8;
    private static final int SLOT_SPAWNER_LABEL = 13;
    private static final int SPAWNER_ROW_START  = 18;
    private static final int SLOT_KEY_LABEL     = 40;
    private static final int KEY_ROW_BASE       = 45;

    private static final NamespacedKey SPAWNER_TYPE_KEY = new NamespacedKey("mvspawners", "type");

    private final JavaPlugin    plugin;
    private final EconomyBridge bits;
    private final KeyStore      keyStore;

    private final List<SpawnerEntry> spawners  = new ArrayList<>();
    private final List<CrateEntry>   crateKeys = new ArrayList<>();
    private final Set<Inventory>     sessions  = new HashSet<>();

    private record SpawnerEntry(String typeName, String displayName, double price) {}
    private record CrateEntry(String id, String displayName, Material material, double price, List<String> lore) {}

    public BitShopManager(JavaPlugin plugin, KeyStore keyStore) {
        this.plugin   = plugin;
        this.keyStore = keyStore;
        String currency = plugin.getConfig().getString("bitshop.bits-currency", "bits");
        this.bits = new EconomyBridge(plugin, currency);
        load();
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    public void load() {
        spawners.clear();
        crateKeys.clear();

        ConfigurationSection spawnSec = plugin.getConfig().getConfigurationSection("bitshop.spawners");
        if (spawnSec != null) {
            for (String key : spawnSec.getKeys(false)) {
                String displayName = spawnSec.getString(key + ".display-name", prettify(key) + " Spawner");
                double price       = spawnSec.getDouble(key + ".price", 5000);
                spawners.add(new SpawnerEntry(key.toUpperCase(), displayName, price));
            }
        }

        ConfigurationSection crateSec = plugin.getConfig().getConfigurationSection("bitshop.crate-keys");
        if (crateSec != null) {
            for (String id : crateSec.getKeys(false)) {
                String displayName = crateSec.getString(id + ".display-name", "&f" + prettify(id) + " Key");
                String matName     = crateSec.getString(id + ".material", "TRIPWIRE_HOOK");
                double price       = crateSec.getDouble(id + ".price", 100);
                List<String> lore  = crateSec.getStringList(id + ".lore");
                Material mat;
                try   { mat = Material.valueOf(matName.toUpperCase()); }
                catch (IllegalArgumentException e) { mat = Material.TRIPWIRE_HOOK; }
                crateKeys.add(new CrateEntry(id, displayName, mat, price, lore));
            }
        }
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public void addCrateKey(String id, String displayName, String material, double price, List<String> lore) {
        plugin.getConfig().set("bitshop.crate-keys." + id + ".display-name", displayName);
        plugin.getConfig().set("bitshop.crate-keys." + id + ".material", material);
        plugin.getConfig().set("bitshop.crate-keys." + id + ".price", price);
        plugin.getConfig().set("bitshop.crate-keys." + id + ".lore", lore);
        plugin.saveConfig();
        load();
    }

    public void removeCrateKey(String id) {
        plugin.getConfig().set("bitshop.crate-keys." + id, null);
        plugin.saveConfig();
        load();
    }

    public List<String> getKeyIds() {
        return crateKeys.stream().map(CrateEntry::id).toList();
    }

    // ── Glass helpers ──────────────────────────────────────────────────────────

    private ItemStack glass(Material mat) {
        ItemStack g = new ItemStack(mat);
        ItemMeta m = g.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        m.lore(List.of());
        g.setItemMeta(m);
        return g;
    }

    private ItemStack blackGlass()  { return glass(Material.BLACK_STAINED_GLASS_PANE);  }
    private ItemStack goldGlass()   { return glass(Material.YELLOW_STAINED_GLASS_PANE); }
    private ItemStack orangeGlass() { return glass(Material.ORANGE_STAINED_GLASS_PANE); }

    // ── Key slot helper ────────────────────────────────────────────────────────

    // startCol = 5 - N centers N items in 9 columns with spacing 2
    private int keySlot(int idx) {
        int n = Math.min(crateKeys.size(), 5);
        return KEY_ROW_BASE + (5 - n) + idx * 2;
    }

    // ── GUI ─────────────────────────────────────────────────────────────────────

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("Bit Shop").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Fill all with black glass
        ItemStack bg = blackGlass();
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // Header (row 0): gold glass
        ItemStack hdr = goldGlass();
        for (int i = 0; i < 9; i++) inv.setItem(i, hdr);

        // Title at slot 4
        ItemStack title = new ItemStack(Material.NETHER_STAR);
        ItemMeta tm = title.getItemMeta();
        tm.displayName(Component.text("Bit Shop")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false));
        tm.lore(List.of(
            Component.text("Spend your bits here")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        title.setItemMeta(tm);
        inv.setItem(SLOT_TITLE, title);

        // Close button at slot 8 (top-right corner)
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta clm = close.getItemMeta();
        clm.displayName(Component.text("Close")
            .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        clm.lore(List.of());
        close.setItemMeta(clm);
        inv.setItem(SLOT_CLOSE, close);

        // Spawner section label at slot 13
        ItemStack spawnLabel = new ItemStack(Material.SPAWNER);
        ItemMeta slm = spawnLabel.getItemMeta();
        slm.displayName(Component.text("Spawners")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false));
        slm.lore(List.of(
            Component.text("Purchase spawners with bits")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        spawnLabel.setItemMeta(slm);
        inv.setItem(SLOT_SPAWNER_LABEL, spawnLabel);

        // Spawner items (row 2, slots 18-26 — up to 9)
        for (int i = 0; i < Math.min(spawners.size(), 9); i++) {
            inv.setItem(SPAWNER_ROW_START + i, buildSpawnerDisplayItem(spawners.get(i)));
        }

        // Divider (row 3, slots 27-35): orange glass
        ItemStack div = orangeGlass();
        for (int i = 27; i < 36; i++) inv.setItem(i, div);

        // Crate key section label at slot 40
        ItemStack keyLabel = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta klm = keyLabel.getItemMeta();
        klm.displayName(Component.text("Crate Keys")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false));
        klm.lore(List.of(
            Component.text("Open crates at spawn")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        keyLabel.setItemMeta(klm);
        inv.setItem(SLOT_KEY_LABEL, keyLabel);

        // Crate keys centered in row 5 (slots 45-53)
        for (int i = 0; i < Math.min(crateKeys.size(), 5); i++) {
            inv.setItem(keySlot(i), buildCrateDisplayItem(crateKeys.get(i), player));
        }

        sessions.add(inv);
        player.openInventory(inv);
    }

    public boolean isBitShop(Inventory inv) { return sessions.contains(inv); }
    public void    onClose(Inventory inv)    { sessions.remove(inv); }

    // ── Click handler ─────────────────────────────────────────────────────────

    public void handleClick(Player player, Inventory inv, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        // Spawner row (slots 18-26)
        if (slot >= SPAWNER_ROW_START && slot < SPAWNER_ROW_START + 9) {
            int idx = slot - SPAWNER_ROW_START;
            if (idx < spawners.size()) purchaseSpawner(player, spawners.get(idx));
            return;
        }

        // Crate key row (centered in row 5)
        for (int i = 0; i < Math.min(crateKeys.size(), 5); i++) {
            if (slot == keySlot(i)) {
                purchaseCrateKey(player, crateKeys.get(i), inv);
                return;
            }
        }
    }

    private void purchaseSpawner(Player player, SpawnerEntry entry) {
        if (!hasFunds(player, entry.price())) return;
        if (!bits.withdraw(player.getUniqueId(), entry.price())) {
            player.sendMessage(LEGACY.deserialize("&c[BitShop] Transaction failed."));
            return;
        }
        giveOrDrop(player, buildSpawnerGiveItem(entry));
        player.sendMessage(LEGACY.deserialize(
            "&a[BitShop] &7Purchased &e" + entry.displayName()
            + " &7for &e" + fmt(entry.price()) + " bits&7."));
    }

    private void purchaseCrateKey(Player player, CrateEntry entry, Inventory inv) {
        if (!hasFunds(player, entry.price())) return;
        if (!bits.withdraw(player.getUniqueId(), entry.price())) {
            player.sendMessage(LEGACY.deserialize("&c[BitShop] Transaction failed."));
            return;
        }
        keyStore.addKeys(player.getUniqueId(), entry.id(), 1);

        // Refresh key display in open GUI
        int idx = crateKeys.indexOf(entry);
        inv.setItem(keySlot(idx), buildCrateDisplayItem(entry, player));

        player.sendMessage(LEGACY.deserialize(
            "&a[BitShop] &7Purchased "
            + LEGACY.serialize(LEGACY.deserialize(entry.displayName()))
            + " &7for &e" + fmt(entry.price()) + " bits&7. "
            + "&7You now have &e" + keyStore.getKeys(player.getUniqueId(), entry.id()) + "&7."));
    }

    private boolean hasFunds(Player player, double price) {
        double balance = bits.getBalance(player.getUniqueId());
        if (balance >= price) return true;
        player.sendMessage(LEGACY.deserialize(
            "&c[BitShop] Not enough bits! Need &e" + fmt(price)
            + " &cbut you have &e" + fmt(balance) + "&c."));
        return false;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildSpawnerDisplayItem(SpawnerEntry entry) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&6" + entry.displayName())
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.empty(),
            Component.text("Price: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(fmt(entry.price()) + " bits")
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)),
            Component.empty(),
            Component.text("Click to purchase").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSpawnerGiveItem(SpawnerEntry entry) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&6" + entry.displayName())
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Type: " + prettify(entry.typeName()))
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            Component.text("Right-click a placed spawner to stack")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Breaking destroys stored items!")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer()
            .set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, entry.typeName());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCrateDisplayItem(CrateEntry entry, Player player) {
        int owned = keyStore.getKeys(player.getUniqueId(), entry.id());
        ItemStack item = new ItemStack(entry.material());
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(entry.displayName())
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : entry.lore())
            lore.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Price: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            .append(Component.text(fmt(entry.price()) + " bits")
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Owned: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            .append(Component.text(String.valueOf(owned))
                .color(owned > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("Click to purchase").color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String prettify(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.replace('_', ' ').split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase()).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
