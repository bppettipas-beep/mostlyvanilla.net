package com.mostlyvanilla.spawners;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpawnerConfig {

    private final DonutSpawners plugin;

    private File              ratesFile;
    private YamlConfiguration ratesCfg;
    private final Map<SpawnerType, Double> rateMultipliers = new EnumMap<>(SpawnerType.class);

    public SpawnerConfig(DonutSpawners plugin) {
        this.plugin = plugin;
    }

    // ── Settings ────────────────────────────────────────────────────────────

    public boolean requireSilkTouch()    { return plugin.getConfig().getBoolean("settings.require-silk-touch", true); }
    public int     getProximityRadius()  { return plugin.getConfig().getInt("settings.proximity", 48); }
    public int     getIsolationRadius()  { return plugin.getConfig().getInt("settings.isolation-radius", 2); }
    public double  getIsolationBonus()   { return plugin.getConfig().getDouble("settings.isolation-bonus", 1.25); }
    public int     getSaveInterval()     { return plugin.getConfig().getInt("settings.save-interval", 300); }
    public String  getSellCurrency()     { return plugin.getConfig().getString("sell-currency", "money"); }

    // ── Production ───────────────────────────────────────────────────────────

    public int getInterval(SpawnerType type) {
        return plugin.getConfig().getInt("drops." + type.name() + ".ticks", 100);
    }

    public int getXpPerCycle(SpawnerType type) {
        return plugin.getConfig().getInt("drops." + type.name() + ".xp", 2);
    }

    /** Returns drop material → base amount per cycle. Skips "ticks" and "xp" keys. */
    public Map<Material, Integer> getDrops(SpawnerType type) {
        Map<Material, Integer> drops = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("drops." + type.name());
        if (sec == null) return drops;
        for (String key : sec.getKeys(false)) {
            if (key.equals("ticks") || key.equals("xp")) continue;
            try {
                drops.put(Material.valueOf(key.toUpperCase()), sec.getInt(key, 1));
            } catch (IllegalArgumentException ignored) {}
        }
        return drops;
    }

    // ── Rate multipliers ──────────────────────────────────────────────────────

    public void loadRates() {
        ratesFile = new File(plugin.getDataFolder(), "rates.yml");
        ratesCfg  = YamlConfiguration.loadConfiguration(ratesFile);
        rateMultipliers.clear();
        for (SpawnerType type : SpawnerType.values()) {
            double mult = ratesCfg.getDouble("rates." + type.name(), 1.0);
            if (mult != 1.0) rateMultipliers.put(type, mult);
        }
    }

    public double getRateMultiplier(SpawnerType type) {
        return rateMultipliers.getOrDefault(type, 1.0);
    }

    public void setRateMultiplier(SpawnerType type, double multiplier) {
        if (multiplier == 1.0) {
            rateMultipliers.remove(type);
            ratesCfg.set("rates." + type.name(), null);
        } else {
            rateMultipliers.put(type, multiplier);
            ratesCfg.set("rates." + type.name(), multiplier);
        }
        saveRates();
    }

    public void setAllRates(double multiplier) {
        rateMultipliers.clear();
        ratesCfg.set("rates", null);
        if (multiplier != 1.0) {
            for (SpawnerType type : SpawnerType.values()) {
                rateMultipliers.put(type, multiplier);
                ratesCfg.set("rates." + type.name(), multiplier);
            }
        }
        saveRates();
    }

    private void saveRates() {
        try { ratesCfg.save(ratesFile); }
        catch (IOException e) { plugin.getLogger().severe("[MVSpawners] Failed to save rates.yml: " + e.getMessage()); }
    }

    // ── Sell prices ──────────────────────────────────────────────────────────

    public double getSellPrice(Material mat) {
        return plugin.getConfig().getDouble("sell-prices." + mat.name(), 0.0);
    }

    // ── Reload ───────────────────────────────────────────────────────────────

    public void reload() {
        plugin.reloadConfig();
        loadRates();
    }
}
