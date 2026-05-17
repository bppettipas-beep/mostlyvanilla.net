package com.mostlyvanilla.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MuteManager {

    public record MuteEntry(long expiresAt, String reason, String mutedBy) {
        public boolean isPermanent() { return expiresAt == -1L; }
        public boolean isExpired()   { return !isPermanent() && System.currentTimeMillis() > expiresAt; }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, MuteEntry> mutes = new HashMap<>();
    private File mutesFile;

    public MuteManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!mutesFile.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(mutesFile);
        if (!c.isConfigurationSection("mutes")) return;
        for (String uuidStr : c.getConfigurationSection("mutes").getKeys(false)) {
            try {
                UUID uuid      = UUID.fromString(uuidStr);
                long expiresAt = c.getLong("mutes."   + uuidStr + ".expires", -1L);
                String reason  = c.getString("mutes." + uuidStr + ".reason",  "No reason given.");
                String by      = c.getString("mutes." + uuidStr + ".by",      "Staff");
                MuteEntry e    = new MuteEntry(expiresAt, reason, by);
                if (!e.isExpired()) mutes.put(uuid, e);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        if (mutesFile == null) mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, MuteEntry> e : mutes.entrySet()) {
            String k = "mutes." + e.getKey();
            c.set(k + ".expires", e.getValue().expiresAt());
            c.set(k + ".reason",  e.getValue().reason());
            c.set(k + ".by",      e.getValue().mutedBy());
        }
        try { c.save(mutesFile); }
        catch (IOException ex) { plugin.getLogger().warning("[Mute] Could not save: " + ex.getMessage()); }
    }

    /** expiresAt = -1 for permanent, otherwise System.currentTimeMillis() + durationMs */
    public void mute(UUID uuid, long expiresAt, String reason, String mutedBy) {
        mutes.put(uuid, new MuteEntry(expiresAt, reason, mutedBy));
        save();
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        save();
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry e = mutes.get(uuid);
        if (e == null) return false;
        if (e.isExpired()) { mutes.remove(uuid); save(); return false; }
        return true;
    }

    public MuteEntry getMute(UUID uuid) {
        return isMuted(uuid) ? mutes.get(uuid) : null;
    }

    // ── Duration helpers ──────────────────────────────────────────────────────

    /** Parses "30s" "5m" "2h" "7d" "perm". Returns durationMs, -1 for permanent, 0 for invalid. */
    public static long parseDuration(String s) {
        if (s.equalsIgnoreCase("perm") || s.equalsIgnoreCase("permanent")) return -1L;
        if (s.length() < 2) return 0L;
        char unit = Character.toLowerCase(s.charAt(s.length() - 1));
        long amount;
        try { amount = Long.parseLong(s.substring(0, s.length() - 1)); }
        catch (NumberFormatException e) { return 0L; }
        if (amount <= 0) return 0L;
        return switch (unit) {
            case 's' -> amount * 1_000L;
            case 'm' -> amount * 60_000L;
            case 'h' -> amount * 3_600_000L;
            case 'd' -> amount * 86_400_000L;
            default  -> 0L;
        };
    }

    /** Formats remaining time: "2h 15m", "Permanent", etc. */
    public static String formatRemaining(long expiresAt) {
        if (expiresAt == -1L) return "Permanent";
        long ms = expiresAt - System.currentTimeMillis();
        if (ms <= 0) return "Expired";
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0)  return d + "d " + (h % 24) + "h";
        if (h > 0)  return h + "h " + (m % 60) + "m";
        if (m > 0)  return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    /** Formats a duration in ms back to a readable string (for confirmations). */
    public static String formatDuration(long durationMs) {
        if (durationMs == -1L) return "Permanent";
        long s = durationMs / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0)  return d + "d " + (h % 24) + "h";
        if (h > 0)  return h + "h " + (m % 60) + "m";
        if (m > 0)  return m + "m " + (s % 60) + "s";
        return s + "s";
    }
}
