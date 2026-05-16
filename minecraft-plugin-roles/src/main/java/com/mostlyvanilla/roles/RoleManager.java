package com.mostlyvanilla.roles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class RoleManager {

    private final MostlyVanillaRoles plugin;
    private final Map<String, String>  roles       = new LinkedHashMap<>();
    private final Map<String, Integer> roleWeights = new HashMap<>();
    private final Map<UUID, String>    playerRoles = new HashMap<>();
    private final Map<String, String>  roleLinks   = new HashMap<>(); // gameRole → discordRoleId
    private String joinRole = null;

    private File rolesFile;
    private File playersFile;
    private File linksFile;
    private Scoreboard scoreboard;

    private static final String TEAM_PREFIX = "mv_";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public RoleManager(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rolesFile   = new File(plugin.getDataFolder(), "roles.yml");
        playersFile = new File(plugin.getDataFolder(), "players.yml");
        linksFile   = new File(plugin.getDataFolder(), "links.yml");

        createIfAbsent(rolesFile);
        createIfAbsent(playersFile);
        createIfAbsent(linksFile);

        // Load roles
        YamlConfiguration rc = YamlConfiguration.loadConfiguration(rolesFile);
        joinRole = rc.getString("join-role", null);
        if (rc.isConfigurationSection("roles")) {
            for (String name : rc.getConfigurationSection("roles").getKeys(false)) {
                roles.put(name, rc.getString("roles." + name + ".prefix", ""));
                roleWeights.put(name, rc.getInt("roles." + name + ".weight", 50));
            }
        }

        // Load player assignments
        YamlConfiguration pc = YamlConfiguration.loadConfiguration(playersFile);
        if (pc.isConfigurationSection("players")) {
            for (String uuidStr : pc.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String role = pc.getString("players." + uuidStr);
                    if (role != null && roles.containsKey(role)) playerRoles.put(uuid, role);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Load role links
        YamlConfiguration lc = YamlConfiguration.loadConfiguration(linksFile);
        if (lc.isConfigurationSection("links")) {
            for (String gameRole : lc.getConfigurationSection("links").getKeys(false)) {
                roleLinks.put(gameRole, lc.getString("links." + gameRole));
            }
        }

        // Set up scoreboard teams
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Map.Entry<String, String> e : roles.entrySet()) setupTeam(e.getKey(), e.getValue());

        // Sync online players (e.g. after /reload)
        for (Player p : Bukkit.getOnlinePlayers()) syncPlayerTeam(p);

        plugin.getLogger().info("Loaded " + roles.size() + " role(s), " +
            playerRoles.size() + " assignment(s), " + roleLinks.size() + " link(s).");

        startPollTask();
    }

    private void startPollTask() {
        int interval = plugin.getConfig().getInt("poll-interval", 10);
        new BukkitRunnable() {
            @Override public void run() { pollPendingGameRoles(); }
        }.runTaskTimerAsynchronously(plugin, 200L, interval * 20L);
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void createIfAbsent(File f) {
        if (!f.exists()) try { f.createNewFile(); }
        catch (IOException e) { plugin.getLogger().warning("Could not create " + f.getName()); }
    }

    private void saveRoles() {
        YamlConfiguration c = new YamlConfiguration();
        if (joinRole != null) c.set("join-role", joinRole);
        for (Map.Entry<String, String> e : roles.entrySet()) {
            c.set("roles." + e.getKey() + ".prefix", e.getValue());
            c.set("roles." + e.getKey() + ".weight", roleWeights.getOrDefault(e.getKey(), 50));
        }
        save(c, rolesFile);
    }

    private void savePlayers() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : playerRoles.entrySet()) c.set("players." + e.getKey(), e.getValue());
        save(c, playersFile);
    }

    private void saveLinks() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<String, String> e : roleLinks.entrySet()) c.set("links." + e.getKey(), e.getValue());
        save(c, linksFile);
    }

    private void save(YamlConfiguration c, File f) {
        try { c.save(f); }
        catch (IOException e) { plugin.getLogger().warning("Could not save " + f.getName() + ": " + e.getMessage()); }
    }

    // ── Scoreboard teams ─────────────────────────────────────────────────────

    private String teamName(String roleName) {
        int w = roleWeights.getOrDefault(roleName, 50);
        String full = String.format("mv_%02d_%s", w, roleName);
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
        Team t = scoreboard.getTeam(teamName(roleName));
        if (t != null) t.unregister();
    }

    public void syncPlayerTeam(Player player) {
        String roleName = playerRoles.get(player.getUniqueId());

        Team current = scoreboard.getPlayerTeam(player);
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) current.removePlayer(player);

        if (roleName != null) {
            Team team = scoreboard.getTeam(teamName(roleName));
            if (team != null) team.addPlayer(player);
        }

        String prefix = roleName != null ? roles.get(roleName) : null;
        TabHook.setPrefix(player, prefix);

        TabManager tabMgr = plugin.getTabManager();
        if (tabMgr != null) tabMgr.updateTabName(player);
    }

    // ── Role CRUD ────────────────────────────────────────────────────────────

    public void createRole(String name, String prefix) {
        roles.put(name, prefix);
        roleWeights.putIfAbsent(name, 50);
        setupTeam(name, prefix);
        saveRoles();
    }

    public boolean deleteRole(String name) {
        if (!roles.containsKey(name)) return false;
        roles.remove(name);
        roleWeights.remove(name);
        playerRoles.values().removeIf(r -> r.equals(name));
        if (name.equals(joinRole)) joinRole = null;
        roleLinks.remove(name);
        removeTeam(name);
        saveRoles();
        savePlayers();
        saveLinks();
        return true;
    }

    public boolean setWeight(String roleName, int weight) {
        if (!roles.containsKey(roleName)) return false;
        removeTeam(roleName);                          // remove old team name (uses current weight)
        roleWeights.put(roleName, weight);             // update weight
        setupTeam(roleName, roles.get(roleName));      // register new team name
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roleName.equals(playerRoles.get(p.getUniqueId()))) syncPlayerTeam(p);
        }
        saveRoles();
        return true;
    }

    public boolean assignRole(UUID uuid, String roleName) {
        if (!roles.containsKey(roleName)) return false;
        playerRoles.put(uuid, roleName);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        if (roleLinks.containsKey(roleName)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> syncToDiscord(uuid, roleName, true));
        }
        return true;
    }

    public boolean removePlayerRole(UUID uuid) {
        String roleName = playerRoles.get(uuid);
        if (roleName == null) return false;
        playerRoles.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        if (roleLinks.containsKey(roleName)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> syncToDiscord(uuid, roleName, false));
        }
        return true;
    }

    // ── Role links ───────────────────────────────────────────────────────────

    public boolean linkRole(String gameRole, String discordRoleId) {
        if (!roles.containsKey(gameRole)) return false;
        roleLinks.put(gameRole, discordRoleId);
        saveLinks();
        // Register link with bot
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            postJson("/api/role-links",
                String.format("{\"game_role\":\"%s\",\"discord_role_id\":\"%s\"}", gameRole, discordRoleId))
        );
        return true;
    }

    public boolean unlinkRole(String gameRole) {
        if (!roleLinks.containsKey(gameRole)) return false;
        roleLinks.remove(gameRole);
        saveLinks();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            deleteRequest("/api/role-links/" + gameRole)
        );
        return true;
    }

    public Map<String, String> getRoleLinks() {
        return Collections.unmodifiableMap(roleLinks);
    }

    // ── Discord sync ─────────────────────────────────────────────────────────

    private void syncToDiscord(UUID uuid, String gameRole, boolean assign) {
        String discordRoleId = roleLinks.get(gameRole);
        if (discordRoleId == null) return;
        String body = getJson("/api/verified/" + uuid);
        if (body == null) return;
        try {
            JsonObject resp = JsonParser.parseString(body).getAsJsonObject();
            if (!resp.get("verified").getAsBoolean()) return;
            String discordUserId = resp.getAsJsonObject("data").get("discord_id").getAsString();
            postJson("/api/discord-role/assign",
                String.format("{\"discord_user_id\":\"%s\",\"discord_role_id\":\"%s\",\"assign\":%s}",
                    discordUserId, discordRoleId, assign));
        } catch (Exception e) {
            plugin.getLogger().warning("[RoleSync] Discord sync failed: " + e.getMessage());
        }
    }

    public void pollPendingGameRoles() {
        String body = getJson("/api/pending-game-roles");
        if (body == null || body.equals("[]")) return;
        try {
            JsonArray pending = JsonParser.parseString(body).getAsJsonArray();
            if (pending.isEmpty()) return;
            plugin.getLogger().info("[RoleSync] Processing " + pending.size() + " pending assignment(s)");
            for (var elem : pending) {
                JsonObject item = elem.getAsJsonObject();
                String uuidStr  = item.get("mc_uuid").getAsString();
                String gameRole = item.get("game_role").getAsString();
                String action   = item.get("action").getAsString();
                new BukkitRunnable() {
                    @Override public void run() {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            if ("assign".equals(action) && roleExists(gameRole)) {
                                // Update directly without triggering Discord sync (avoid loop)
                                playerRoles.put(uuid, gameRole);
                                Player online = Bukkit.getPlayer(uuid);
                                if (online != null) syncPlayerTeam(online);
                                savePlayers();
                            } else if ("remove".equals(action) && gameRole.equals(playerRoles.get(uuid))) {
                                playerRoles.remove(uuid);
                                Player online = Bukkit.getPlayer(uuid);
                                if (online != null) syncPlayerTeam(online);
                                savePlayers();
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("[RoleSync] Error applying pending role: " + e.getMessage());
                        }
                    }
                }.runTask(plugin);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[RoleSync] Failed to parse pending roles: " + e.getMessage());
        }
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private String apiUrl(String path) {
        return plugin.getConfig().getString("bot-api-url", "http://localhost:3000") + path;
    }

    private String apiSecret() {
        return plugin.getConfig().getString("api-secret", "");
    }

    private String getJson(String path) {
        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(path)))
                .header("x-api-secret", apiSecret())
                .timeout(Duration.ofSeconds(5))
                .GET().build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            plugin.getLogger().warning("[RoleSync] GET " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    private void postJson(String path, String body) {
        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(path)))
                .header("Content-Type", "application/json")
                .header("x-api-secret", apiSecret())
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getLogger().warning("[RoleSync] POST " + path + " failed: " + e.getMessage());
        }
    }

    private void deleteRequest(String path) {
        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(path)))
                .header("x-api-secret", apiSecret())
                .timeout(Duration.ofSeconds(5))
                .DELETE().build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getLogger().warning("[RoleSync] DELETE " + path + " failed: " + e.getMessage());
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getPrefix(UUID uuid) {
        String role = playerRoles.get(uuid);
        return role != null ? roles.get(role) : null;
    }

    public String getPlayerRole(UUID uuid)        { return playerRoles.get(uuid); }
    public String getJoinRole()                   { return joinRole; }
    public boolean roleExists(String name)        { return roles.containsKey(name); }
    public Set<String> getRoleNames()             { return Collections.unmodifiableSet(roles.keySet()); }
    public Map<String, String> getRoles()         { return Collections.unmodifiableMap(roles); }

    public boolean setJoinRole(String name) {
        if (!roles.containsKey(name)) return false;
        joinRole = name; saveRoles(); return true;
    }

    public void clearJoinRole() { joinRole = null; saveRoles(); }
}
