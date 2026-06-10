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

    private final JavaPlugin plugin;
    private final KeyBridge  keyBridge;

    private final Map<String, CrateType> crateTypes    = new LinkedHashMap<>();
    private final Map<String, String>    crateLocations = new LinkedHashMap<>();

    private File              dataFile;
    private YamlConfiguration dataCfg;

    private File              rewardsFile;
    private YamlConfiguration rewardsCfg;

    public CrateManager(JavaPlugin plugin, KeyBridge keyBridge) {
        this.plugin    = plugin;
        this.keyBridge = keyBridge;
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    public void load() {
        rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        rewardsCfg  = YamlConfiguration.loadConfiguration(rewardsFile);
        loadTypes();
        loadLocations();
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

    // ── Admin API ─────────────────────────────────────────────────────────────

    public boolean setCrate(Block block, String typeId) {
        if (!crateTypes.containsKey(typeId)) return false;
        crateLocations.put(locKey(block), typeId);
        saveLocations();
        if (block.getState() instanceof ShulkerBox shulker) {
            CrateType type = crateTypes.get(typeId);
            shulker.customName(LEGACY.deserialize(type.displayName()));
            shulker.update(true);
        }
        return true;
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

        // Always at least 3 rows so there's a top and bottom border
        int contentRows = Math.max(1, (int) Math.ceil((double) count / REWARDS_PER_ROW));
        int totalRows   = Math.min(6, contentRows + 2);
        int size        = totalRows * 9;

        String titleSuffix = preview ? " &8— Preview" : " &8— Pick a Reward";
        CrateGuiHolder holder = new CrateGuiHolder(type, preview);
        Inventory inv = Bukkit.createInventory(holder, size,
            LEGACY.deserialize(type.displayName() + titleSuffix)
                .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        // Fill everything with gray glass panes
        ItemStack filler = makeFiller();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        // Place rewards in interior slots (skip first and last row, skip cols 0 and 8)
        int rewardIndex = 0;
        for (int row = 1; row < totalRows - 1 && rewardIndex < count; row++) {
            int inRow = Math.min(REWARDS_PER_ROW, count - rewardIndex);
            // Center the rewards within the 7 interior columns
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

    private static ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
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
