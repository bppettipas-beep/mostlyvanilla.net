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

    // ── Layout ────────────────────────────────────────────────────────────────
    //
    //  Row 0  (0 –  8)  glass
    //  Row 1  (9 – 17)  glass + "Spawners" label at slot 13 (center)
    //  Row 2  (18– 26)  9 spawner items  (fills row perfectly)
    //  Row 3  (27– 35)  glass + "Crate Keys" label at slot 31 (center)
    //  Row 4  (36– 44)  glass at 36 & 44, 7 keys at 37–43  (centered)
    //  Row 5  (45– 53)  glass + close at slot 49 (center)

    private static final int SPAWNER_ROW_START  = 18;   // first spawner slot
    private static final int KEY_ROW_START      = 37;   // first key slot (offset 1 for centering)
    private static final int SLOT_SPAWNER_LABEL = 13;
    private static final int SLOT_KEY_LABEL     = 31;
    private static final int SLOT_CLOSE         = 49;

    // PDC keys
    private static final NamespacedKey SPAWNER_TYPE_KEY = new NamespacedKey("mvspawners", "type");

    private final JavaPlugin    plugin;
    private final EconomyBridge bits;

    private final List<SpawnerEntry> spawners  = new ArrayList<>();
    private final List<CrateEntry>   crateKeys = new ArrayList<>();
    private final Set<Inventory>     sessions  = new HashSet<>();

    private record SpawnerEntry(String typeName, String displayName, double price) {}
    private record CrateEntry(String id, String displayName, Material material, double price, List<String> lore) {}

    public BitShopManager(JavaPlugin plugin) {
        this.plugin = plugin;
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

    // ── GUI ─────────────────────────────────────────────────────────────────────

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("⚡ Bit Shop").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        fillGlass(inv);

        // Section labels
        inv.setItem(SLOT_SPAWNER_LABEL, makeSectionLabel(Material.SPAWNER,       "&6&lSpawners"));
        inv.setItem(SLOT_KEY_LABEL,     makeSectionLabel(Material.TRIPWIRE_HOOK, "&b&lCrate Keys"));

        // Spawners — up to 9, fills row 2 perfectly
        for (int i = 0; i < Math.min(spawners.size(), 9); i++) {
            inv.setItem(SPAWNER_ROW_START + i, buildSpawnerDisplayItem(spawners.get(i)));
        }

        // Crate keys — up to 7, centered in row 4 (one glass on each side)
        for (int i = 0; i < Math.min(crateKeys.size(), 7); i++) {
            inv.setItem(KEY_ROW_START + i, buildCrateDisplayItem(crateKeys.get(i)));
        }

        inv.setItem(SLOT_CLOSE, makeCloseButton());

        sessions.add(inv);
        player.openInventory(inv);
    }

    public boolean isBitShop(Inventory inv) { return sessions.contains(inv); }
    public void    onClose(Inventory inv)    { sessions.remove(inv); }

    // ── Click handler ─────────────────────────────────────────────────────────

    public void handleClick(Player player, Inventory inv, int slot) {
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }

        if (slot >= SPAWNER_ROW_START && slot < SPAWNER_ROW_START + 9) {
            int idx = slot - SPAWNER_ROW_START;
            if (idx < spawners.size()) purchaseSpawner(player, spawners.get(idx));
            return;
        }

        if (slot >= KEY_ROW_START && slot < KEY_ROW_START + 7) {
            int idx = slot - KEY_ROW_START;
            if (idx < crateKeys.size()) purchaseCrateKey(player, crateKeys.get(idx));
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

    private void purchaseCrateKey(Player player, CrateEntry entry) {
        if (!hasFunds(player, entry.price())) return;
        if (!bits.withdraw(player.getUniqueId(), entry.price())) {
            player.sendMessage(LEGACY.deserialize("&c[BitShop] Transaction failed."));
            return;
        }
        giveOrDrop(player, buildCrateGiveItem(entry));
        player.sendMessage(LEGACY.deserialize(
            "&a[BitShop] &7Purchased "
            + LEGACY.serialize(LEGACY.deserialize(entry.displayName()))
            + " &7for &e" + fmt(entry.price()) + " bits&7."));
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
            Component.text("Price: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(fmt(entry.price()) + " bits")
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)),
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
            Component.text("⚠ Breaking destroys stored items!")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer()
            .set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, entry.typeName());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCrateDisplayItem(CrateEntry entry) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(entry.displayName())
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : entry.lore())
            lore.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Price: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            .append(Component.text(fmt(entry.price()) + " bits")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("Click to purchase").color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCrateGiveItem(CrateEntry entry) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(entry.displayName())
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : entry.lore())
            lore.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        NamespacedKey crateTag = new NamespacedKey(plugin, "crate_key");
        meta.getPersistentDataContainer().set(crateTag, PersistentDataType.STRING, entry.id());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSectionLabel(Material mat, String ampName) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(ampName).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeCloseButton() {
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&cClose").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    private void fillGlass(Inventory inv) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  m = g.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        g.setItemMeta(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, g);
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
