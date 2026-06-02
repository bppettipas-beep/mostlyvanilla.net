package com.mostlyvanilla.spawners;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpawnerConfig {

    private final DonutSpawners plugin;

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

    // ── Sell prices ──────────────────────────────────────────────────────────

    public double getSellPrice(Material mat) {
        return plugin.getConfig().getDouble("sell-prices." + mat.name(), 0.0);
    }

    // ── Reload ───────────────────────────────────────────────────────────────

    public void reload() {
        plugin.reloadConfig();
    }
}
