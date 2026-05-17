package com.mostlyvanilla.home;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HomeManager {

    private final MostlyVanillaHome plugin;
    private final Map<UUID, Map<String, Home>> homes = new HashMap<>();
    private File homesFile;

    public HomeManager(MostlyVanillaHome plugin) {
        this.plugin = plugin;
    }

    public void load() {
        homesFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!homesFile.exists()) {
            try { homesFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("Could not create homes.yml"); }
        }

        YamlConfiguration c = YamlConfiguration.loadConfiguration(homesFile);
        if (!c.isConfigurationSection("players")) return;

        for (String uuidStr : c.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Home> playerHomes = new LinkedHashMap<>();
                String base = "players." + uuidStr + ".homes";
                if (c.isConfigurationSection(base)) {
                    for (String name : c.getConfigurationSection(base).getKeys(false)) {
                        String p = base + "." + name;
                        playerHomes.put(name.toLowerCase(), new Home(
                            name,
                            c.getString(p + ".world", "world"),
                            c.getDouble(p + ".x"),
                            c.getDouble(p + ".y"),
                            c.getDouble(p + ".z"),
                            (float) c.getDouble(p + ".yaw"),
                            (float) c.getDouble(p + ".pitch")
                        ));
                    }
                }
                if (!playerHomes.isEmpty()) homes.put(uuid, playerHomes);
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("Loaded homes for " + homes.size() + " player(s).");
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> entry : homes.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Home h : entry.getValue().values()) {
                String p = "players." + uuidStr + ".homes." + h.getName();
                c.set(p + ".world", h.getWorld());
                c.set(p + ".x", h.getX());
                c.set(p + ".y", h.getY());
                c.set(p + ".z", h.getZ());
                c.set(p + ".yaw", (double) h.getYaw());
                c.set(p + ".pitch", (double) h.getPitch());
            }
        }
        try { c.save(homesFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save homes.yml: " + e.getMessage()); }
    }

    public List<Home> getHomes(UUID uuid) {
        return new ArrayList<>(homes.getOrDefault(uuid, Collections.emptyMap()).values());
    }

    public Home getHome(UUID uuid, String name) {
        Map<String, Home> m = homes.get(uuid);
        return m == null ? null : m.get(name.toLowerCase());
    }

    /** Returns false if the player is at their limit and this is a new (not update) home. */
    public boolean setHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> m = homes.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        int limit = getHomeLimit(player);
        boolean isNew = !m.containsKey(name.toLowerCase());
        if (isNew && limit >= 0 && m.size() >= limit) return false;
        m.put(name.toLowerCase(), new Home(name, player.getLocation()));
        save();
        return true;
    }

    public boolean deleteHome(UUID uuid, String name) {
        Map<String, Home> m = homes.get(uuid);
        if (m == null || !m.containsKey(name.toLowerCase())) return false;
        m.remove(name.toLowerCase());
        if (m.isEmpty()) homes.remove(uuid);
        save();
        return true;
    }

    public boolean renameHome(UUID uuid, String oldName, String newName) {
        Map<String, Home> m = homes.get(uuid);
        if (m == null) return false;
        Home h = m.remove(oldName.toLowerCase());
        if (h == null) return false;
        h.setName(newName);
        m.put(newName.toLowerCase(), h);
        save();
        return true;
    }

    public int getHomeLimit(Player player) {
        String role = getPlayerRole(player);
        if (role != null) {
            int limit = plugin.getConfig().getInt("role-limits." + role, -1);
            if (limit >= 0) return limit;
        }
        return plugin.getConfig().getInt("default-homes", 3);
    }

    public void setHomeLimitForRole(String role, int limit) {
        plugin.getConfig().set("role-limits." + role, limit);
        plugin.saveConfig();
    }

    // Reads the player's role from the scoreboard teams set by MostlyVanillaRoles.
    // Team name format: mv_<weight>_<rolename>  (e.g. mv_50_member)
    private String getPlayerRole(Player player) {
        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (team.getName().startsWith("mv_") && team.hasEntry(player.getName())) {
                String rest = team.getName().substring(3); // "<weight>_<role>"
                int sep = rest.indexOf('_');
                if (sep >= 0) return rest.substring(sep + 1);
            }
        }
        return null;
    }
}
