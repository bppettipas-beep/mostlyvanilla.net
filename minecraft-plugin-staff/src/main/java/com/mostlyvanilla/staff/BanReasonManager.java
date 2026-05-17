package com.mostlyvanilla.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BanReasonManager {

    private final JavaPlugin plugin;
    private final Map<String, BanReason> reasons = new LinkedHashMap<>();
    private File file;

    public record BanReason(String id, long durationMs, boolean wipe) {
        public boolean isPermanent() { return durationMs == -1L; }
    }

    public BanReasonManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "ban-reasons.yml");
        if (!file.exists()) try { file.createNewFile(); } catch (IOException ignored) {}
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        reasons.clear();
        if (c.isConfigurationSection("reasons")) {
            for (String id : c.getConfigurationSection("reasons").getKeys(false)) {
                long    duration = c.getLong("reasons." + id + ".duration", -1L);
                boolean wipe     = c.getBoolean("reasons." + id + ".wipe", false);
                reasons.put(id.toLowerCase(), new BanReason(id.toLowerCase(), duration, wipe));
            }
        }
    }

    public void addReason(String id, long durationMs, boolean wipe) {
        reasons.put(id.toLowerCase(), new BanReason(id.toLowerCase(), durationMs, wipe));
        save();
    }

    public boolean removeReason(String id) {
        if (reasons.remove(id.toLowerCase()) == null) return false;
        save();
        return true;
    }

    public BanReason getReason(String id)          { return id != null ? reasons.get(id.toLowerCase()) : null; }
    public Map<String, BanReason> getReasons()     { return Collections.unmodifiableMap(reasons); }

    private void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (BanReason r : reasons.values()) {
            c.set("reasons." + r.id() + ".duration", r.durationMs());
            c.set("reasons." + r.id() + ".wipe",     r.wipe());
        }
        try { c.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save ban-reasons.yml: " + e.getMessage()); }
    }

    // ── Duration helpers ──────────────────────────────────────────────────────

    public static long parseDuration(String s) {
        if (s == null || s.isBlank()) return 0L;
        if (s.equalsIgnoreCase("perm") || s.equalsIgnoreCase("permanent")) return -1L;
        try {
            char unit  = Character.toLowerCase(s.charAt(s.length() - 1));
            long value = Long.parseLong(s.substring(0, s.length() - 1));
            return switch (unit) {
                case 's' -> value * 1_000L;
                case 'm' -> value * 60_000L;
                case 'h' -> value * 3_600_000L;
                case 'd' -> value * 86_400_000L;
                default  -> 0L;
            };
        } catch (NumberFormatException e) { return 0L; }
    }

    public static String formatDuration(long ms) {
        if (ms == -1L) return "Permanent";
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d";
        if (h > 0) return h + "h";
        if (m > 0) return m + "m";
        return s + "s";
    }
}
