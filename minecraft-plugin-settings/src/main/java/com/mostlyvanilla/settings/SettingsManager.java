package com.mostlyvanilla.settings;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SettingsManager {

    private final MostlyVanillaSettings plugin;
    private final Map<UUID, Map<Setting, Boolean>> data = new HashMap<>();
    private File file;

    public SettingsManager(MostlyVanillaSettings plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "settings.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("players")) return;
        for (String uuidStr : cfg.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<Setting, Boolean> map = new EnumMap<>(Setting.class);
                for (Setting s : Setting.values()) {
                    String key = "players." + uuidStr + "." + s.name();
                    if (cfg.contains(key)) map.put(s, cfg.getBoolean(key));
                }
                data.put(uuid, map);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        if (file == null) file = new File(plugin.getDataFolder(), "settings.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<Setting, Boolean>> entry : data.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<Setting, Boolean> se : entry.getValue().entrySet()) {
                cfg.set("players." + uuidStr + "." + se.getKey().name(), se.getValue());
            }
        }
        try { cfg.save(file); } catch (IOException e) { plugin.getLogger().warning("Could not save settings: " + e.getMessage()); }
    }

    /** Returns the current value, falling back to the setting's default. */
    public boolean isEnabled(UUID uuid, Setting setting) {
        Map<Setting, Boolean> map = data.get(uuid);
        if (map == null) return setting.defaultValue;
        return map.getOrDefault(setting, setting.defaultValue);
    }

    /** Reflection-friendly overload used by other plugins: key is the enum name, e.g. "TPA_AUTO_ACCEPT". */
    public boolean isEnabled(UUID uuid, String settingKey) {
        try {
            return isEnabled(uuid, Setting.valueOf(settingKey));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean toggle(UUID uuid, Setting setting) {
        boolean current = isEnabled(uuid, setting);
        boolean next = !current;
        data.computeIfAbsent(uuid, k -> new EnumMap<>(Setting.class)).put(setting, next);
        save();
        return next;
    }

    public void set(UUID uuid, Setting setting, boolean value) {
        data.computeIfAbsent(uuid, k -> new EnumMap<>(Setting.class)).put(setting, value);
        save();
    }

    /** Returns true if this player has never had any settings saved (i.e. first join). */
    public boolean isNewPlayer(UUID uuid) {
        return !data.containsKey(uuid);
    }
}
