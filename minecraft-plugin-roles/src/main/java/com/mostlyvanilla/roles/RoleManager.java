package com.mostlyvanilla.roles;

import org.bukkit.configuration.file.YamlConfiguration;

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
                    UUID uuid     = UUID.fromString(uuidStr);
                    String role   = playersConfig.getString("players." + uuidStr);
                    if (role != null && roles.containsKey(role)) {
                        playerRoles.put(uuid, role);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("Loaded " + roles.size() + " role(s), " + playerRoles.size() + " assignment(s).");
    }

    private void createIfAbsent(File file) {
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("Could not create " + file.getName()); }
        }
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
        saveRoles();
    }

    public boolean deleteRole(String name) {
        if (!roles.containsKey(name)) return false;
        roles.remove(name);
        playerRoles.values().removeIf(r -> r.equals(name));
        if (name.equals(joinRole)) joinRole = null;
        saveRoles();
        savePlayers();
        return true;
    }

    public boolean assignRole(UUID uuid, String roleName) {
        if (!roles.containsKey(roleName)) return false;
        playerRoles.put(uuid, roleName);
        savePlayers();
        return true;
    }

    public boolean removePlayerRole(UUID uuid) {
        if (!playerRoles.containsKey(uuid)) return false;
        playerRoles.remove(uuid);
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
