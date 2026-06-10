package com.mostlyvanilla.unwipe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class UnwipeManager {

    private static final String ONLINE  = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final JavaPlugin plugin;
    private final File snapshotsFolder;
    // pending online-snapshot restores waiting for the player to join
    private final Map<UUID, YamlConfiguration> pendingRestores = new HashMap<>();

    public UnwipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.snapshotsFolder = new File(plugin.getDataFolder(), "snapshots");
        if (!snapshotsFolder.exists()) snapshotsFolder.mkdirs();
    }

    public boolean hasSnapshot(UUID uuid) {
        return new File(snapshotsFolder, uuid + "/snapshot.yml").exists();
    }

    // ── Called from WipeManager via reflection before any wipe ────────────────

    public void saveSnapshot(UUID uuid, Player online, File worldFolder) {
        File dir = new File(snapshotsFolder, uuid.toString());
        dir.mkdirs();

        YamlConfiguration snap = new YamlConfiguration();
        snap.set("timestamp", System.currentTimeMillis());

        saveCurrencies(snap, uuid);

        if (online != null) {
            snap.set("type", ONLINE);
            snap.set("xp-level",    online.getLevel());
            snap.set("xp-progress", (double) online.getExp());
            snap.set("statistics.playtime", online.getStatistic(Statistic.PLAY_ONE_MINUTE));
            snap.set("statistics.kills",    online.getStatistic(Statistic.PLAYER_KILLS));
            snap.set("statistics.deaths",   online.getStatistic(Statistic.DEATHS));

            saveItems(snap, "inventory",  online.getInventory().getContents());
            ItemStack helm  = online.getInventory().getHelmet();
            ItemStack chest = online.getInventory().getChestplate();
            ItemStack legs  = online.getInventory().getLeggings();
            ItemStack boots = online.getInventory().getBoots();
            if (helm  != null && helm.getType()  != Material.AIR) snap.set("armor.helmet",     helm);
            if (chest != null && chest.getType() != Material.AIR) snap.set("armor.chestplate", chest);
            if (legs  != null && legs.getType()  != Material.AIR) snap.set("armor.leggings",   legs);
            if (boots != null && boots.getType() != Material.AIR) snap.set("armor.boots",      boots);
            ItemStack offhand = online.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) snap.set("offhand", offhand);
            saveItems(snap, "enderchest", online.getEnderChest().getContents());
        } else {
            snap.set("type", OFFLINE);
            copyQuiet(new File(worldFolder, "playerdata/" + uuid + ".dat"),
                      new File(dir, "playerdata.dat"));
            copyQuiet(new File(worldFolder, "stats/" + uuid + ".json"),
                      new File(dir, "stats.json"));
        }

        try {
            snap.save(new File(dir, "snapshot.yml"));
            plugin.getLogger().info("[Unwipe] Saved " + (online != null ? "online" : "offline")
                + " snapshot for " + uuid);
        } catch (IOException e) {
            plugin.getLogger().warning("[Unwipe] Failed to save snapshot for " + uuid + ": " + e.getMessage());
        }
    }

    // ── Called from /unwipe command ───────────────────────────────────────────

    public boolean restoreSnapshot(CommandSender issuer, UUID uuid, String name) {
        File dir      = new File(snapshotsFolder, uuid.toString());
        File snapFile = new File(dir, "snapshot.yml");
        if (!snapFile.exists()) return false;

        YamlConfiguration snap = YamlConfiguration.loadConfiguration(snapFile);
        String type = snap.getString("type", OFFLINE);

        restoreCurrencies(snap, uuid);

        Player online = Bukkit.getPlayer(uuid);

        if (ONLINE.equals(type)) {
            if (online != null) {
                applyOnlineRestore(online, snap);
                msg(issuer, "✔ Restored all data for ", name, ".", NamedTextColor.GREEN);
            } else {
                pendingRestores.put(uuid, snap);
                msg(issuer, "✔ Currencies restored for ", name,
                    ". Inventory/stats will apply on their next login.", NamedTextColor.GREEN);
            }
        } else {
            // OFFLINE: put the .dat and stats files back
            World world = Bukkit.getWorlds().get(0);
            File worldFolder = world.getWorldFolder();

            File datSrc   = new File(dir, "playerdata.dat");
            File statsSrc = new File(dir, "stats.json");
            if (datSrc.exists())
                copyQuiet(datSrc,   new File(worldFolder, "playerdata/" + uuid + ".dat"));
            if (statsSrc.exists())
                copyQuiet(statsSrc, new File(worldFolder, "stats/" + uuid + ".json"));

            if (online != null) {
                online.sendMessage(Component.text(
                    "Your data has been restored. Please relog for inventory changes to take effect.",
                    NamedTextColor.GREEN));
                msg(issuer, "✔ Currencies restored for ", name,
                    ". They must relog for inventory to take effect.", NamedTextColor.GREEN);
            } else {
                msg(issuer, "✔ Restored all data for ", name, ".", NamedTextColor.GREEN);
            }
        }

        plugin.getLogger().info("[Unwipe] " + (issuer != null ? issuer.getName() : "SYSTEM")
            + " restored data for " + name + " (" + uuid + ")");
        return true;
    }

    // ── Called from UnwipeListener on PlayerJoinEvent ─────────────────────────

    public void applyPendingRestore(Player player) {
        YamlConfiguration snap = pendingRestores.remove(player.getUniqueId());
        if (snap == null) return;
        applyOnlineRestore(player, snap);
        player.sendMessage(Component.text("Your data has been restored.", NamedTextColor.GREEN));
        plugin.getLogger().info("[Unwipe] Applied pending restore for " + player.getName());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void applyOnlineRestore(Player player, YamlConfiguration snap) {
        player.setLevel((int) snap.getInt("xp-level", 0));
        player.setExp((float) snap.getDouble("xp-progress", 0.0));

        if (snap.isConfigurationSection("statistics")) {
            player.setStatistic(Statistic.PLAY_ONE_MINUTE, snap.getInt("statistics.playtime", 0));
            player.setStatistic(Statistic.PLAYER_KILLS,    snap.getInt("statistics.kills",    0));
            player.setStatistic(Statistic.DEATHS,          snap.getInt("statistics.deaths",   0));
        }

        player.getInventory().clear();
        restoreItems(snap, "inventory", player.getInventory().getContents().length,
            (slot, item) -> player.getInventory().setItem(slot, item));

        player.getInventory().setHelmet(snap.getItemStack("armor.helmet"));
        player.getInventory().setChestplate(snap.getItemStack("armor.chestplate"));
        player.getInventory().setLeggings(snap.getItemStack("armor.leggings"));
        player.getInventory().setBoots(snap.getItemStack("armor.boots"));

        ItemStack offhand = snap.getItemStack("offhand");
        player.getInventory().setItemInOffHand(offhand != null ? offhand : new ItemStack(Material.AIR));

        player.getEnderChest().clear();
        restoreItems(snap, "enderchest", player.getEnderChest().getSize(),
            (slot, item) -> player.getEnderChest().setItem(slot, item));

        player.saveData();
    }

    @FunctionalInterface
    private interface SlotSetter { void set(int slot, ItemStack item); }

    private void restoreItems(YamlConfiguration snap, String key, int maxSlots, SlotSetter setter) {
        ConfigurationSection sec = snap.getConfigurationSection(key);
        if (sec == null) return;
        for (String slotKey : sec.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotKey);
                if (slot < 0 || slot >= maxSlots) continue;
                ItemStack item = sec.getItemStack(slotKey);
                if (item != null) setter.set(slot, item);
            } catch (NumberFormatException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void saveCurrencies(YamlConfiguration snap, UUID uuid) {
        Plugin eco = Bukkit.getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (eco == null) return;
        try {
            Object em = eco.getClass().getMethod("getEconomyManager").invoke(eco);
            if (em == null) return;
            Collection<String> currencies = (Collection<String>)
                em.getClass().getMethod("getCurrencies").invoke(em);
            Method getBalance = em.getClass().getMethod("getBalance", UUID.class, String.class);
            for (String currency : currencies) {
                double balance = (double) getBalance.invoke(em, uuid, currency);
                snap.set("currencies." + currency, balance);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Unwipe] Failed to save currencies: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreCurrencies(YamlConfiguration snap, UUID uuid) {
        ConfigurationSection sec = snap.getConfigurationSection("currencies");
        if (sec == null) return;
        Plugin eco = Bukkit.getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (eco == null) return;
        try {
            Object em = eco.getClass().getMethod("getEconomyManager").invoke(eco);
            if (em == null) return;
            Method setBalance = em.getClass().getMethod("setBalance", UUID.class, String.class, double.class);
            for (String currency : sec.getKeys(false)) {
                setBalance.invoke(em, uuid, currency, sec.getDouble(currency, 0.0));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Unwipe] Failed to restore currencies: " + e.getMessage());
        }
    }

    private void saveItems(YamlConfiguration snap, String key, ItemStack[] items) {
        if (items == null) return;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR)
                snap.set(key + "." + i, items[i]);
        }
    }

    private void copyQuiet(File src, File dst) {
        if (!src.exists()) return;
        try {
            dst.getParentFile().mkdirs();
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("[Unwipe] Failed to copy " + src.getName() + ": " + e.getMessage());
        }
    }

    private void msg(CommandSender sender, String pre, String name, String post, NamedTextColor color) {
        if (sender == null) return;
        sender.sendMessage(Component.text(pre, color)
            .append(Component.text(name, NamedTextColor.YELLOW))
            .append(Component.text(post, color)));
    }
}
