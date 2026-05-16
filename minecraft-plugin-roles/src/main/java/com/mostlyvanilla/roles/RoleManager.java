package com.mostlyvanilla.roles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RoleManager {

    private final MostlyVanillaRoles plugin;
    private final Map<String, String> roles = new LinkedHashMap<>();
    private final Map<UUID, String> playerRoles = new HashMap<>();
    private String joinRole = null;

    private File rolesFile;
    private File playersFile;
    private Scoreboard scoreboard;

    // Team names are limited to 16 chars; prefix with "mv_" to avoid conflicts
    private static final String TEAM_PREFIX = "mv_";

    public RoleManager(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rolesFile   = new File(plugin.getDataFolder(), "roles.yml");
        playersFile = new File(plugin.getDataFolder(), "players.yml");

        createIfAbsent(rolesFile);
        createIfAbsent(playersFile);

        YamlConfiguration rolesConfig = YamlConfiguration.loadConfiguration(rolesFile);
        joinRole = rolesConfig.getString("join-role", null);
        if (rolesConfig.isConfigurationSection("roles")) {
            for (String name : rolesConfig.getConfigurationSection("roles").getKeys(false)) {
                String prefix = rolesConfig.getString("roles." + name + ".prefix", "");
                roles.put(name, prefix);
            }
        }

        YamlConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        if (playersConfig.isConfigurationSection("players")) {
            for (String uuidStr : playersConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid   = UUID.fromString(uuidStr);
                    String role = playersConfig.getString("players." + uuidStr);
                    if (role != null && roles.containsKey(role)) {
                        playerRoles.put(uuid, role);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Set up scoreboard teams for all roles
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Map.Entry<String, String> entry : roles.entrySet()) {
            setupTeam(entry.getKey(), entry.getValue());
        }

        // Sync any online players (e.g. after a reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayerTeam(player);
        }

        plugin.getLogger().info("Loaded " + roles.size() + " role(s), " + playerRoles.size() + " assignment(s).");
    }

    private void createIfAbsent(File file) {
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("Could not create " + file.getName()); }
        }
    }

    private String teamName(String roleName) {
        String full = TEAM_PREFIX + roleName;
        return full.length() > 16 ? full.substring(0, 16) : full;
    }

    private Component parsePrefix(String prefix) {
        return prefix.contains("<")
            ? MiniMessage.miniMessage().deserialize(prefix)
            : LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
    }

    private void setupTeam(String roleName, String prefix) {
        String tName = teamName(roleName);
        Team team = scoreboard.getTeam(tName);
        if (team == null) team = scoreboard.registerNewTeam(tName);
        team.prefix(parsePrefix(prefix).append(Component.text(" ")));
    }

    private void removeTeam(String roleName) {
        Team team = scoreboard.getTeam(teamName(roleName));
        if (team != null) team.unregister();
    }

    public void syncPlayerTeam(Player player) {
        String roleName = playerRoles.get(player.getUniqueId());

        // Remove from any existing mv_ team first
        Team current = scoreboard.getPlayerTeam(player);
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            current.removePlayer(player);
        }

        if (roleName != null) {
            Team team = scoreboard.getTeam(teamName(roleName));
            if (team != null) team.addPlayer(player);
        }

        // Also update TAB if installed
        String prefix = roleName != null ? roles.get(roleName) : null;
        TabHook.setPrefix(player, prefix);
    }

    private void saveRoles() {
        YamlConfiguration config = new YamlConfiguration();
        if (joinRole != null) config.set("join-role", joinRole);
        for (Map.Entry<String, String> e : roles.entrySet()) {
            config.set("roles." + e.getKey() + ".prefix", e.getValue());
        }
        save(config, rolesFile);
    }

    private void savePlayers() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : playerRoles.entrySet()) {
            config.set("players." + e.getKey().toString(), e.getValue());
        }
        save(config, playersFile);
    }

    private void save(YamlConfiguration config, File file) {
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save " + file.getName() + ": " + e.getMessage()); }
    }

    public void createRole(String name, String prefix) {
        roles.put(name, prefix);
        setupTeam(name, prefix);
        saveRoles();
    }

    public boolean deleteRole(String name) {
        if (!roles.containsKey(name)) return false;
        roles.remove(name);
        playerRoles.values().removeIf(r -> r.equals(name));
        if (name.equals(joinRole)) joinRole = null;
        removeTeam(name);
        saveRoles();
        savePlayers();
        return true;
    }

    public boolean assignRole(UUID uuid, String roleName) {
        if (!roles.containsKey(roleName)) return false;
        playerRoles.put(uuid, roleName);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        return true;
    }

    public boolean removePlayerRole(UUID uuid) {
        if (!playerRoles.containsKey(uuid)) return false;
        playerRoles.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        return true;
    }

    public String getPrefix(UUID uuid) {
        String role = playerRoles.get(uuid);
        return role != null ? roles.get(role) : null;
    }

    public String getPlayerRole(UUID uuid) {
        return playerRoles.get(uuid);
    }

    public String getJoinRole() { return joinRole; }

    public boolean setJoinRole(String name) {
        if (!roles.containsKey(name)) return false;
        joinRole = name;
        saveRoles();
        return true;
    }

    public void clearJoinRole() {
        joinRole = null;
        saveRoles();
    }

    public boolean roleExists(String name) {
        return roles.containsKey(name);
    }

    public Set<String> getRoleNames() {
        return Collections.unmodifiableSet(roles.keySet());
    }

    public Map<String, String> getRoles() {
        return Collections.unmodifiableMap(roles);
    }
}
