package com.mostlyvanilla.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CrateManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin    plugin;
    private final KeyBridge     keyBridge;
    private final BitShopBridge bitShopBridge;

    private final Map<String, CrateType> crateTypes    = new LinkedHashMap<>();
    private final Map<String, String>    crateLocations = new LinkedHashMap<>();

    private File              dataFile;
    private YamlConfiguration dataCfg;

    private File              rewardsFile;
    private YamlConfiguration rewardsCfg;

    public CrateManager(JavaPlugin plugin, KeyBridge keyBridge, BitShopBridge bitShopBridge) {
        this.plugin        = plugin;
        this.keyBridge     = keyBridge;
        this.bitShopBridge = bitShopBridge;
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    public void load() {
        rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        rewardsCfg  = YamlConfiguration.loadConfiguration(rewardsFile);
        seedDefaultTypes();
        loadTypes();
        loadLocations();
    }

    private void seedDefaultTypes() {
        plugin.getConfig().addDefault("crate-types.iron.display-name", "&fIron Crate");
        plugin.getConfig().addDefault("crate-types.iron.key-type", "iron");
        plugin.getConfig().addDefault("crate-types.iron.rewards", List.of(
            Map.of("weight", 40, "material", "IRON_INGOT",   "amount", 32, "name", "&7x32 Iron Ingots"),
            Map.of("weight", 30, "material", "COAL",          "amount", 64, "name", "&8x64 Coal"),
            Map.of("weight", 15, "material", "GOLD_INGOT",    "amount",  8, "name", "&6x8 Gold Ingots"),
            Map.of("weight", 10, "material", "DIAMOND",       "amount",  1, "name", "&bDiamond"),
            Map.of("weight",  5, "material", "EMERALD",       "amount",  1, "name", "&aEmerald")
        ));

        plugin.getConfig().addDefault("crate-types.gold.display-name", "&6Gold Crate");
        plugin.getConfig().addDefault("crate-types.gold.key-type", "gold");
        plugin.getConfig().addDefault("crate-types.gold.rewards", List.of(
            Map.of("weight", 35, "material", "GOLD_INGOT",   "amount", 32, "name", "&6x32 Gold Ingots"),
            Map.of("weight", 25, "material", "IRON_INGOT",   "amount", 64, "name", "&7x64 Iron Ingots"),
            Map.of("weight", 20, "material", "DIAMOND",      "amount",  3, "name", "&bx3 Diamonds"),
            Map.of("weight", 15, "material", "EMERALD",      "amount",  3, "name", "&ax3 Emeralds"),
            Map.of("weight",  5, "material", "GOLDEN_APPLE", "amount",  2, "name", "&6x2 Golden Apples")
        ));

        plugin.getConfig().addDefault("crate-types.diamond.display-name", "&bDiamond Crate");
        plugin.getConfig().addDefault("crate-types.diamond.key-type", "diamond");
        plugin.getConfig().addDefault("crate-types.diamond.rewards", List.of(
            Map.of("weight", 30, "material", "DIAMOND",            "amount",  5, "name", "&bx5 Diamonds"),
            Map.of("weight", 25, "material", "EMERALD",            "amount",  5, "name", "&ax5 Emeralds"),
            Map.of("weight", 20, "material", "GOLDEN_APPLE",       "amount",  3, "name", "&6x3 Golden Apples"),
            Map.of("weight", 15, "material", "NETHERITE_SCRAP",    "amount",  1, "name", "&8Netherite Scrap"),
            Map.of("weight", 10, "material", "TOTEM_OF_UNDYING",   "amount",  1, "name", "&6Totem of Undying")
        ));

        plugin.getConfig().addDefault("crate-types.netherite.display-name", "&8Netherite Crate");
        plugin.getConfig().addDefault("crate-types.netherite.key-type", "netherite");
        plugin.getConfig().addDefault("crate-types.netherite.rewards", List.of(
            Map.of("weight", 30, "material", "NETHERITE_SCRAP",  "amount",  2, "name", "&8x2 Netherite Scrap"),
            Map.of("weight", 25, "material", "DIAMOND",          "amount", 10, "name", "&bx10 Diamonds"),
            Map.of("weight", 20, "material", "TOTEM_OF_UNDYING", "amount",  1, "name", "&6Totem of Undying"),
            Map.of("weight", 15, "material", "NETHERITE_INGOT",  "amount",  1, "name", "&8Netherite Ingot"),
            Map.of("weight", 10, "material", "ELYTRA",           "amount",  1, "name", "&7Elytra")
        ));

        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void loadTypes() {
        crateTypes.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crate-types");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            String displayName = sec.getString(id + ".display-name", "&f" + id + " Crate");
            String keyType     = sec.getString(id + ".key-type", id);
            List<CrateReward> rewards;
            if (rewardsCfg.contains(id + ".rewards")) {
                rewards = parseRewardList(rewardsCfg.getMapList(id + ".rewards"));
            } else {
                rewards = parseRewardList(sec.getMapList(id + ".rewards"));
            }
            crateTypes.put(id, new CrateType(id, displayName, keyType, rewards));
        }
        plugin.getLogger().info("[Crates] Loaded " + crateTypes.size() + " crate type(s).");
    }

    private List<CrateReward> parseRewardList(List<Map<?, ?>> raw) {
        List<CrateReward> list = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) entry;
            String matName     = String.valueOf(e.getOrDefault("material", "CHEST"));
            int    amount      = Integer.parseInt(String.valueOf(e.getOrDefault("amount", 1)));
            String name        = String.valueOf(e.getOrDefault("name", matName));
            int    weight      = Integer.parseInt(String.valueOf(e.getOrDefault("weight", 10)));
            String spawnerType = e.containsKey("spawner-type") ? String.valueOf(e.get("spawner-type")) : null;

            Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
            if (e.containsKey("enchantments")) {
                Object rawEnch = e.get("enchantments");
                if (rawEnch instanceof Map<?, ?> enchMap) {
                    for (Map.Entry<?, ?> ench : enchMap.entrySet()) {
                        enchants.put(String.valueOf(ench.getKey()), Integer.parseInt(String.valueOf(ench.getValue())));
                    }
                }
            }

            try {
                list.add(new CrateReward(name, Material.valueOf(matName.toUpperCase()), amount, weight, spawnerType, enchants));
            } catch (IllegalArgumentException ignored) {}
        }
        return list;
    }

    private void loadLocations() {
        crateLocations.clear();
        dataFile = new File(plugin.getDataFolder(), "crates.yml");
        dataCfg  = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = dataCfg.getConfigurationSection("locations");
        if (sec != null) {
            for (String key : sec.getKeys(false)) crateLocations.put(key, sec.getString(key));
        }
        plugin.getLogger().info("[Crates] Loaded " + crateLocations.size() + " placed crate(s).");
    }

    private void saveLocations() {
        dataCfg.set("locations", null);
        crateLocations.forEach((k, v) -> dataCfg.set("locations." + k, v));
        try { dataCfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("[Crates] Failed to save crates.yml: " + e.getMessage()); }
    }

    private void saveRewards(String typeId) {
        CrateType type = crateTypes.get(typeId);
        if (type == null) return;
        List<Map<String, Object>> list = new ArrayList<>();
        for (CrateReward r : type.rewards()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("material", r.material().name());
            map.put("amount", r.amount());
            map.put("name", r.name());
            map.put("weight", r.weight());
            if (r.spawnerType() != null) map.put("spawner-type", r.spawnerType());
            if (!r.enchantments().isEmpty()) map.put("enchantments", r.enchantments());
            list.add(map);
        }
        rewardsCfg.set(typeId + ".rewards", list);
        try { rewardsCfg.save(rewardsFile); }
        catch (IOException e) { plugin.getLogger().severe("[Crates] Failed to save rewards.yml: " + e.getMessage()); }
    }

    // ── Reward editing API ────────────────────────────────────────────────────

    public boolean addReward(String typeId, CrateReward reward) {
        CrateType type = crateTypes.get(typeId);
        if (type == null) return false;
        List<CrateReward> rewards = new ArrayList<>(type.rewards());
        rewards.add(reward);
        crateTypes.put(typeId, new CrateType(type.id(), type.displayName(), type.keyType(), rewards));
        saveRewards(typeId);
        return true;
    }

    public boolean removeReward(String typeId, int index) {
        CrateType type = crateTypes.get(typeId);
        if (type == null) return false;
        List<CrateReward> rewards = new ArrayList<>(type.rewards());
        if (index < 0 || index >= rewards.size()) return false;
        rewards.remove(index);
        crateTypes.put(typeId, new CrateType(type.id(), type.displayName(), type.keyType(), rewards));
        saveRewards(typeId);
        return true;
    }

    // ── Type creation / deletion ──────────────────────────────────────────────

    public boolean createType(String id, String displayName) {
        if (crateTypes.containsKey(id)) return false;
        plugin.getConfig().set("crate-types." + id + ".display-name", displayName);
        plugin.getConfig().set("crate-types." + id + ".key-type", id);
        plugin.saveConfig();
        crateTypes.put(id, new CrateType(id, displayName, id, new ArrayList<>()));
        bitShopBridge.addCrateKey(id, keyDisplayName(displayName), "TRIPWIRE_HOOK", 100.0,
            List.of("&7A " + id + " crate key"));
        return true;
    }

    public boolean deleteType(String id) {
        if (!crateTypes.containsKey(id)) return false;
        plugin.getConfig().set("crate-types." + id, null);
        plugin.saveConfig();
        rewardsCfg.set(id, null);
        try { rewardsCfg.save(rewardsFile); }
        catch (IOException e) { plugin.getLogger().severe("[Crates] Failed to save rewards.yml: " + e.getMessage()); }
        crateTypes.remove(id);
        bitShopBridge.removeCrateKey(id);
        return true;
    }

    private static String keyDisplayName(String crateDisplayName) {
        if (crateDisplayName.endsWith(" Crate")) {
            return crateDisplayName.substring(0, crateDisplayName.length() - 6) + " Key";
        }
        return crateDisplayName + " Key";
    }

    // ── Color picker GUI ──────────────────────────────────────────────────────

    private record ColorEntry(char code, String name, Material pane) {}
    private record PendingCreate(String id, String rawName) {}

    private static final List<ColorEntry> CHAT_COLORS = List.of(
        new ColorEntry('0', "Black",        Material.BLACK_STAINED_GLASS_PANE),
        new ColorEntry('1', "Dark Blue",    Material.BLUE_STAINED_GLASS_PANE),
        new ColorEntry('2', "Dark Green",   Material.GREEN_STAINED_GLASS_PANE),
        new ColorEntry('3', "Dark Aqua",    Material.CYAN_STAINED_GLASS_PANE),
        new ColorEntry('4', "Dark Red",     Material.RED_STAINED_GLASS_PANE),
        new ColorEntry('5', "Dark Purple",  Material.PURPLE_STAINED_GLASS_PANE),
        new ColorEntry('6', "Gold",         Material.ORANGE_STAINED_GLASS_PANE),
        new ColorEntry('7', "Gray",         Material.LIGHT_GRAY_STAINED_GLASS_PANE),
        new ColorEntry('8', "Dark Gray",    Material.GRAY_STAINED_GLASS_PANE),
        new ColorEntry('9', "Blue",         Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        new ColorEntry('a', "Green",        Material.LIME_STAINED_GLASS_PANE),
        new ColorEntry('b', "Aqua",         Material.CYAN_STAINED_GLASS_PANE),
        new ColorEntry('c', "Red",          Material.RED_STAINED_GLASS_PANE),
        new ColorEntry('d', "Light Purple", Material.MAGENTA_STAINED_GLASS_PANE),
        new ColorEntry('e', "Yellow",       Material.YELLOW_STAINED_GLASS_PANE),
        new ColorEntry('f', "White",        Material.WHITE_STAINED_GLASS_PANE)
    );

    private final Map<UUID, PendingCreate> pendingColorPick  = new HashMap<>();
    private final Set<Inventory>           colorPickSessions = new HashSet<>();

    public void openColorPicker(Player player, String id, String rawName) {
        pendingColorPick.put(player.getUniqueId(), new PendingCreate(id, rawName));

        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("Pick a color — " + rawName)
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Header row: gold glass + title at slot 4
        ItemStack hdr = makeFiller(Material.YELLOW_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, hdr);
        ItemStack titleItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta  tm        = titleItem.getItemMeta();
        tm.displayName(Component.text("Pick a Color")
            .color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false));
        tm.lore(List.of(
            Component.text("Choosing color for: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(rawName).color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
        ));
        titleItem.setItemMeta(tm);
        inv.setItem(4, titleItem);

        // Colors in slots 9-24 (rows 1-2)
        for (int i = 0; i < CHAT_COLORS.size(); i++) {
            inv.setItem(9 + i, buildColorItem(CHAT_COLORS.get(i), rawName));
        }

        // Filler at slot 25
        inv.setItem(25, makeFiller(Material.BLACK_STAINED_GLASS_PANE));

        // Cancel at slot 26
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta  cm     = cancel.getItemMeta();
        cm.displayName(Component.text("Cancel").color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        cm.lore(List.of());
        cancel.setItemMeta(cm);
        inv.setItem(26, cancel);

        colorPickSessions.add(inv);
        player.openInventory(inv);
    }

    private ItemStack buildColorItem(ColorEntry color, String rawName) {
        String prefix    = "&" + color.code();
        String crateName = prefix + rawName;
        String keyName   = keyDisplayName(crateName);

        ItemStack item = new ItemStack(color.pane());
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(prefix + color.name())
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.empty(),
            Component.text("Crate: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(LEGACY.deserialize(crateName).decoration(TextDecoration.ITALIC, false)),
            Component.text("Key:   ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(LEGACY.deserialize(keyName).decoration(TextDecoration.ITALIC, false)),
            Component.empty(),
            Component.text("Click to select").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isColorPickerGui(Inventory inv) {
        return colorPickSessions.contains(inv);
    }

    public void onColorPickerClose(Inventory inv) {
        colorPickSessions.remove(inv);
    }

    public void handleColorPickClick(Player player, Inventory inv, int slot) {
        player.closeInventory();
        PendingCreate pending = pendingColorPick.remove(player.getUniqueId());
        if (pending == null) return;

        if (slot == 26) { // cancel button
            player.sendMessage(Component.text("Crate creation cancelled.", NamedTextColor.GRAY));
            return;
        }

        int colorIndex = slot - 9;
        if (colorIndex < 0 || colorIndex >= CHAT_COLORS.size()) return; // header or filler click

        ColorEntry color   = CHAT_COLORS.get(colorIndex);
        String displayName = "&" + color.code() + pending.rawName();

        if (createType(pending.id(), displayName)) {
            player.sendMessage(Component.text("Created crate type \"" + pending.id() + "\" (", NamedTextColor.GREEN)
                .append(LEGACY.deserialize(displayName))
                .append(Component.text(") and registered its key in /bitshop.", NamedTextColor.GREEN)));
            player.sendMessage(Component.text(
                "Add rewards with /crate reward add " + pending.id()
                + ", then place it with /crate set " + pending.id() + ".",
                NamedTextColor.GRAY));
        }
    }

    // ── Admin API ─────────────────────────────────────────────────────────────

    public boolean setCrate(Block block, String typeId) {
        if (!crateTypes.containsKey(typeId)) return false;
        crateLocations.put(locKey(block), typeId);
        saveLocations();
        if (block.getState() instanceof ShulkerBox shulker) {
            List<CrateReward> imported = new ArrayList<>();
            for (ItemStack item : shulker.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) imported.add(itemToReward(item));
            }
            if (!imported.isEmpty()) {
                CrateType old = crateTypes.get(typeId);
                crateTypes.put(typeId, new CrateType(old.id(), old.displayName(), old.keyType(), imported));
                saveRewards(typeId);
                shulker.getInventory().clear();
            }
            CrateType type = crateTypes.get(typeId);
            shulker.customName(LEGACY.deserialize(type.displayName()));
            shulker.update(true);
        }
        return true;
    }

    private CrateReward itemToReward(ItemStack item) {
        ItemMeta meta       = item.getItemMeta();
        String spawnerType  = readSpawnerType(item);
        String name;
        if (meta != null && meta.hasDisplayName()) {
            name = LEGACY.serialize(meta.displayName());
        } else if (spawnerType != null) {
            name = "&6" + formatMaterialName(Material.valueOf(spawnerType.toUpperCase())) + " Spawner";
        } else {
            name = "&f" + formatMaterialName(item.getType());
        }
        Map<String, Integer> enchants = new LinkedHashMap<>();
        Map<Enchantment, Integer> raw = (item.getType() == Material.ENCHANTED_BOOK
                && meta instanceof EnchantmentStorageMeta esm)
            ? esm.getStoredEnchants() : item.getEnchantments();
        for (Map.Entry<Enchantment, Integer> e : raw.entrySet())
            enchants.put(e.getKey().getKey().getKey(), e.getValue());
        return new CrateReward(name, item.getType(), item.getAmount(), 10, spawnerType, enchants);
    }

    public boolean removeCrate(Block block) {
        boolean removed = crateLocations.remove(locKey(block)) != null;
        if (removed) saveLocations();
        return removed;
    }

    public int wipeAll() {
        // Clear the contents of each registered shulker box in the world
        for (String locKey : crateLocations.keySet()) {
            String[] parts = locKey.split(",");
            if (parts.length == 4) {
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                        if (block.getState() instanceof ShulkerBox shulker) {
                            shulker.getInventory().clear();
                            shulker.update();
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        int count = crateLocations.size();

        // Wipe locations
        crateLocations.clear();
        saveLocations();

        // Clear rewards from every crate type (keep the type itself)
        for (String typeId : new ArrayList<>(crateTypes.keySet())) {
            CrateType type = crateTypes.get(typeId);
            crateTypes.put(typeId, new CrateType(type.id(), type.displayName(), type.keyType(), new ArrayList<>()));
            saveRewards(typeId);
        }

        return count;
    }

    public Map<String, String> getAll()             { return Collections.unmodifiableMap(crateLocations); }
    public Set<String>         getTypeIds()          { return crateTypes.keySet(); }
    public CrateType           getType(String id)    { return crateTypes.get(id); }

    // ── Player interaction ────────────────────────────────────────────────────

    public boolean isCrate(Block block) {
        return crateLocations.containsKey(locKey(block));
    }

    public void tryOpen(Player player, Block block) {
        String typeId = crateLocations.get(locKey(block));
        if (typeId == null) return;

        CrateType type = crateTypes.get(typeId);
        if (type == null) {
            player.sendMessage(Component.text("This crate type no longer exists.", NamedTextColor.RED));
            return;
        }

        int keys = keyBridge.getKeys(player.getUniqueId(), type.keyType());
        if (keys <= 0) {
            player.sendMessage(
                LEGACY.deserialize(type.displayName())
                    .append(Component.text(" requires a ", NamedTextColor.RED))
                    .append(Component.text(capitalize(type.keyType()) + " Key", NamedTextColor.YELLOW))
                    .append(Component.text(". You have none.", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }

        openSelectionGui(player, type);
    }

    // ── Preview GUI ───────────────────────────────────────────────────────────

    public void openPreview(Player player, Block block) {
        String typeId = crateLocations.get(locKey(block));
        if (typeId == null) return;
        CrateType type = crateTypes.get(typeId);
        if (type == null) return;
        openGui(player, type, true);
    }

    // ── Selection GUI ─────────────────────────────────────────────────────────

    private static final int REWARDS_PER_ROW = 7; // cols 1–7, leaving 0 and 8 as border

    private void openSelectionGui(Player player, CrateType type) {
        openGui(player, type, false);
    }

    private void openGui(Player player, CrateType type, boolean preview) {
        List<CrateReward> rewards = type.rewards();
        int count = rewards.size();

        int contentRows = Math.max(1, (int) Math.ceil((double) count / REWARDS_PER_ROW));
        int totalRows   = Math.min(6, contentRows + 2);
        int size        = totalRows * 9;

        String titleSuffix = preview ? " &8— Preview" : " &8— Pick a Reward";
        CrateGuiHolder holder = new CrateGuiHolder(type, preview);
        Inventory inv = Bukkit.createInventory(holder, size,
            LEGACY.deserialize(type.displayName() + titleSuffix)
                .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack border = makeFiller(borderMaterial(type.id()));
        ItemStack gray   = makeFiller(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            boolean isBorder = row == 0 || row == totalRows - 1 || col == 0 || col == 8;
            inv.setItem(i, isBorder ? border : gray);
        }

        // Crate icon centered in the top border row
        inv.setItem(4, makeCrateIcon(type, preview));

        // Place rewards centered in each content row
        int rewardIndex = 0;
        for (int row = 1; row < totalRows - 1 && rewardIndex < count; row++) {
            int inRow    = Math.min(REWARDS_PER_ROW, count - rewardIndex);
            int startCol = 1 + (REWARDS_PER_ROW - inRow) / 2;
            for (int c = 0; c < inRow; c++) {
                int slot = row * 9 + startCol + c;
                inv.setItem(slot, makeRewardItem(rewards.get(rewardIndex), preview));
                holder.mapSlot(slot, rewardIndex);
                rewardIndex++;
            }
        }

        player.openInventory(inv);
    }

    public void handleGuiClick(Player player, CrateGuiHolder holder, int slot) {
        if (holder.isPreview()) return; // preview — nothing to claim

        int rewardIndex = holder.getRewardIndex(slot);
        if (rewardIndex < 0) return; // clicked filler

        CrateType type = holder.getCrateType();
        List<CrateReward> rewards = type.rewards();
        if (rewardIndex >= rewards.size()) return;

        CrateReward reward = rewards.get(rewardIndex);

        if (!keyBridge.takeKey(player.getUniqueId(), type.keyType())) {
            player.closeInventory();
            player.sendMessage(Component.text("Could not use your key — do you still have one?", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        giveReward(player, reward);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(
            Component.text("You claimed ", NamedTextColor.GREEN)
                .append(LEGACY.deserialize(reward.name()).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" from the ", NamedTextColor.GREEN))
                .append(LEGACY.deserialize(type.displayName()).decoration(TextDecoration.ITALIC, false))
                .append(Component.text("!", NamedTextColor.GREEN)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static Material borderMaterial(String crateId) {
        return switch (crateId) {
            case "iron"      -> Material.WHITE_STAINED_GLASS_PANE;
            case "gold"      -> Material.YELLOW_STAINED_GLASS_PANE;
            case "diamond"   -> Material.CYAN_STAINED_GLASS_PANE;
            case "netherite" -> Material.BLACK_STAINED_GLASS_PANE;
            default          -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }

    private ItemStack makeCrateIcon(CrateType type, boolean preview) {
        Material mat = switch (type.id()) {
            case "iron"      -> Material.IRON_INGOT;
            case "gold"      -> Material.GOLD_INGOT;
            case "diamond"   -> Material.DIAMOND;
            case "netherite" -> Material.NETHERITE_INGOT;
            default          -> Material.CHEST;
        };
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta  = icon.getItemMeta();
        meta.displayName(LEGACY.deserialize(type.displayName()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add((preview
            ? Component.text("Right-click with a key to open.", NamedTextColor.YELLOW)
            : Component.text("Select a reward below.", NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack makeRewardItem(CrateReward reward, boolean preview) {
        ItemStack item = new ItemStack(reward.material(), reward.amount());
        applySpawnerType(item, reward.spawnerType());
        applyEnchantments(item, reward.enchantments());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(reward.name()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (!reward.enchantments().isEmpty()) {
            lore.add(Component.empty());
            for (Map.Entry<String, Integer> ench : reward.enchantments().entrySet()) {
                lore.add(Component.text("  " + prettifyEnchant(ench.getKey()) + " " + ench.getValue(),
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        lore.add(preview
            ? Component.text("Right-click the crate with a key to open.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.text("Click to claim this reward.", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void giveReward(Player player, CrateReward reward) {
        ItemStack item = new ItemStack(reward.material(), reward.amount());
        applySpawnerType(item, reward.spawnerType());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(reward.name()).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        applyEnchantments(item, reward.enchantments());
        player.getInventory().addItem(item).values()
            .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }

    static void applyEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey().toLowerCase()));
            if (ench == null) continue;
            int level = entry.getValue();
            if (item.getType() == Material.ENCHANTED_BOOK) {
                if (item.getItemMeta() instanceof EnchantmentStorageMeta esm) {
                    esm.addStoredEnchant(ench, level, true);
                    item.setItemMeta(esm);
                }
            } else {
                item.addUnsafeEnchantment(ench, level);
            }
        }
    }

    private static String prettifyEnchant(String key) {
        return java.util.Arrays.stream(key.split("_"))
            .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private static void applySpawnerType(ItemStack item, String spawnerType) {
        if (spawnerType == null || item.getType() != Material.SPAWNER) return;
        if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof CreatureSpawner cs)) return;
        try {
            cs.setSpawnedType(EntityType.valueOf(spawnerType.toUpperCase()));
            bsm.setBlockState(cs);
            item.setItemMeta(bsm);
        } catch (IllegalArgumentException ignored) {}
    }

    /** Reads the mob type from a spawner item held by a player. Returns null if not a spawner or type unknown. */
    public static String readSpawnerType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return null;
        if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return null;
        if (!(bsm.getBlockState() instanceof CreatureSpawner cs)) return null;
        EntityType et = cs.getSpawnedType();
        return et != null ? et.name() : null;
    }

    private static String locKey(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String formatMaterialName(Material mat) {
        String[] parts = mat.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            sb.append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
