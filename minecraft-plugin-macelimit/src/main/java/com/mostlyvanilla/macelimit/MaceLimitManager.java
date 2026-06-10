package com.mostlyvanilla.macelimit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class MaceLimitManager {

    private final JavaPlugin plugin;
    private File dataFile;

    private int limit;   // 0 = no limit
    private int crafted; // total crafted since server start / last reset

    public MaceLimitManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        limit = plugin.getConfig().getInt("limit", 10);

        dataFile = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        crafted = data.getInt("crafted", 0);
    }

    public boolean canCraft() {
        return limit <= 0 || crafted < limit;
    }

    public void recordCraft(int amount) {
        crafted += amount;
        saveData();
    }

    public void setLimit(int newLimit) {
        limit = newLimit;
        plugin.getConfig().set("limit", limit);
        plugin.saveConfig();
    }

    public void reset() {
        crafted = 0;
        saveData();
    }

    public int getLimit()   { return limit; }
    public int getCrafted() { return crafted; }
    public int getRemaining() { return limit <= 0 ? Integer.MAX_VALUE : Math.max(0, limit - crafted); }

    private void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("crafted", crafted);
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save mace data: " + e.getMessage()); }
    }
}
