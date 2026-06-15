package com.mostlyvanilla.macelimit;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class MaceLimitManager {

    private final JavaPlugin plugin;
    private File dataFile;

    private int limit;   // 0 = no limit
    private int crafted; // total crafted since server start / last reset
    private int found;   // total found in containers

    public MaceLimitManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        limit = plugin.getConfig().getInt("limit", 10);

        dataFile = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        crafted = data.getInt("crafted", 0);
        found   = data.getInt("found", 0);
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

    public void broadcastFind(String playerName) {
        found++;
        saveData();
        if (!plugin.getConfig().getBoolean("find-announcement.enabled", true)) return;
        String limitStr = limit <= 0 ? "∞" : String.valueOf(limit);
        String msg = plugin.getConfig()
            .getString("find-announcement.message",
                "&6[MostlyVanilla] &e{player} &6has discovered a mace in the world! &7({found}/{limit} maces found)")
            .replace("{player}", playerName)
            .replace("{found}",  String.valueOf(found))
            .replace("{limit}",  limitStr);
        Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }

    public int getLimit()   { return limit; }
    public int getCrafted() { return crafted; }
    public int getFound()   { return found; }
    public int getRemaining() { return limit <= 0 ? Integer.MAX_VALUE : Math.max(0, limit - crafted); }

    private void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("crafted", crafted);
        data.set("found",   found);
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save mace data: " + e.getMessage()); }
    }
}
