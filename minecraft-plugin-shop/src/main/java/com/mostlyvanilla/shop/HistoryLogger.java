package com.mostlyvanilla.shop;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class HistoryLogger {

    private static final int MAX_ENTRIES = 500;

    private File file;
    private final Logger logger;

    public HistoryLogger(Logger logger) {
        this.logger = logger;
    }

    public void init(File dataFolder) {
        this.file = new File(dataFolder, "history.yml");
    }

    public void log(UUID uuid, String playerName, String type, String description, double amount) {
        log(uuid, playerName, type, description, amount, null);
    }

    public void log(UUID uuid, String playerName, String type, String description, double amount, String material) {
        if (file == null) return;
        YamlConfiguration cfg = file.exists()
            ? YamlConfiguration.loadConfiguration(file)
            : new YamlConfiguration();

        List<Map<?, ?>> entries = new ArrayList<>(cfg.getMapList("history"));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("uuid",        uuid.toString());
        entry.put("name",        playerName);
        entry.put("type",        type);
        entry.put("description", description);
        entry.put("amount",      amount);
        entry.put("timestamp",   System.currentTimeMillis());
        if (material != null) entry.put("material", material);

        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) entries = entries.subList(0, MAX_ENTRIES);

        cfg.set("history", entries);
        try { cfg.save(file); }
        catch (IOException e) { logger.warning("[History] Failed to save history.yml: " + e.getMessage()); }
    }
}
