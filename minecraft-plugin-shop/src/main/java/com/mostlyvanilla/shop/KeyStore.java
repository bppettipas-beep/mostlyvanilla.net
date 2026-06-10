package com.mostlyvanilla.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class KeyStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public KeyStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "keys.yml");
        load();
    }

    public void load() {
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public int getKeys(UUID uuid, String keyId) {
        return cfg.getInt(uuid + "." + keyId, 0);
    }

    public void addKeys(UUID uuid, String keyId, int amount) {
        int current = getKeys(uuid, keyId);
        cfg.set(uuid + "." + keyId, current + amount);
        save();
    }

    public boolean takeKey(UUID uuid, String keyId) {
        int current = getKeys(uuid, keyId);
        if (current <= 0) return false;
        cfg.set(uuid + "." + keyId, current - 1);
        save();
        return true;
    }

    public void setKeys(UUID uuid, String keyId, int amount) {
        cfg.set(uuid + "." + keyId, Math.max(0, amount));
        save();
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Shop] Failed to save keys.yml: " + e.getMessage());
        }
    }
}
