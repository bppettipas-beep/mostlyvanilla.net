package com.mostlyvanilla.antidupe;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MostlyVanillaAntiDupe extends JavaPlugin {

    private final Map<UUID, PlayerDupeData> dataMap = new ConcurrentHashMap<>();
    private DupeDetector detector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        detector = new DupeDetector(this);

        getServer().getPluginManager().registerEvents(new DupeListener(this), this);

        var cmd = getCommand("antidupe");
        if (cmd != null) {
            var handler = new AntiDupeCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("MostlyVanillaAntiDupe enabled.");
    }

    @Override
    public void onDisable() {
        dataMap.clear();
        getLogger().info("MostlyVanillaAntiDupe disabled.");
    }

    public PlayerDupeData getData(UUID uuid) {
        return dataMap.computeIfAbsent(uuid, k -> new PlayerDupeData());
    }

    public void removeData(UUID uuid) {
        dataMap.remove(uuid);
    }

    public DupeDetector getDetector() {
        return detector;
    }
}
