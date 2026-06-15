package com.mostlyvanilla.roles;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class HistoryReader {

    public record Entry(UUID uuid, String name, String type, String description, double amount, long timestamp, String material) {}

    public static List<Entry> forPlayer(UUID target, String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) return List.of();
        File file = new File(plugin.getDataFolder(), "history.yml");
        if (!file.exists()) return List.of();

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Entry> result = new ArrayList<>();
        for (Map<?,?> raw : cfg.getMapList("history")) {
            try {
                UUID uuid = UUID.fromString((String) raw.get("uuid"));
                if (!uuid.equals(target)) continue;
                String name     = (String) raw.get("name");
                String type     = (String) raw.get("type");
                String desc     = (String) raw.get("description");
                double amount   = ((Number) raw.get("amount")).doubleValue();
                long   ts       = ((Number) raw.get("timestamp")).longValue();
                String material = raw.get("material") != null ? (String) raw.get("material") : null;
                result.add(new Entry(uuid, name, type, desc, amount, ts, material));
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static List<Entry> forPlayerMerged(UUID target, String... pluginNames) {
        List<Entry> merged = new ArrayList<>();
        for (String name : pluginNames) {
            merged.addAll(forPlayer(target, name));
        }
        merged.sort(Comparator.comparingLong(Entry::timestamp).reversed());
        return merged;
    }
}
