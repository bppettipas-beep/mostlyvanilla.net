package com.mostlyvanilla.roles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
    private String  joinRole          = null;
    private String  staffRole         = null;
    private String  flyRole           = null;
    private String  announcementRole  = null;
    private String  muteRole          = null;
    private boolean nameColorMatch    = false;

    private final Map<String, Set<String>> blockedCmds  = new HashMap<>(); // role → blocked prefixes
    private final Map<String, Set<String>> allowedCmds  = new HashMap<>(); // role → allowed prefixes (block-all exceptions)
    private final Set<String>              blockAllRoles = new HashSet<>(); // roles with all commands blocked

    private File rolesFile;
    private File playersFile;
    private File linksFile;
    private File cmdBlocksFile;
    private Scoreboard scoreboard;

    private static final String TEAM_PREFIX = "mv_";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public RoleManager(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rolesFile     = new File(plugin.getDataFolder(), "roles.yml");
        playersFile   = new File(plugin.getDataFolder(), "players.yml");
        linksFile     = new File(plugin.getDataFolder(), "links.yml");
        cmdBlocksFile = new File(plugin.getDataFolder(), "command-blocks.yml");

        createIfAbsent(rolesFile);
        createIfAbsent(playersFile);
        createIfAbsent(linksFile);
        createIfAbsent(cmdBlocksFile);

        // Load roles
        YamlConfiguration rc = YamlConfiguration.loadConfiguration(rolesFile);
        joinRole         = rc.getString("join-role",         null);
        staffRole        = rc.getString("staff-role",        null);
        flyRole          = rc.getString("fly-role",          null);
        announcementRole = rc.getString("announcement-role", null);
        muteRole         = rc.getString("mute-role",         null);
        nameColorMatch   = rc.getBoolean("name-color-match", false);
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

        // Load command blocks
        YamlConfiguration cc = YamlConfiguration.loadConfiguration(cmdBlocksFile);
        if (cc.isConfigurationSection("roles")) {
            for (String roleName : cc.getConfigurationSection("roles").getKeys(false)) {
                if (cc.getBoolean("roles." + roleName + ".block-all", false)) blockAllRoles.add(roleName);
                List<String> blocked = cc.getStringList("roles." + roleName + ".blocked");
                if (!blocked.isEmpty()) blockedCmds.put(roleName, new HashSet<>(blocked));
                List<String> allowed = cc.getStringList("roles." + roleName + ".allowed");
                if (!allowed.isEmpty()) allowedCmds.put(roleName, new HashSet<>(allowed));
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
        if (joinRole         != null) c.set("join-role",         joinRole);
        if (staffRole        != null) c.set("staff-role",        staffRole);
        if (flyRole          != null) c.set("fly-role",          flyRole);
        if (announcementRole != null) c.set("announcement-role", announcementRole);
        if (muteRole         != null) c.set("mute-role",         muteRole);
        c.set("name-color-match", nameColorMatch);
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

    private void saveCmdBlocks() {
        YamlConfiguration c = new YamlConfiguration();
        Set<String> allRoles = new HashSet<>(blockedCmds.keySet());
        allRoles.addAll(allowedCmds.keySet());
        allRoles.addAll(blockAllRoles);
        for (String role : allRoles) {
            c.set("roles." + role + ".block-all", blockAllRoles.contains(role));
            c.set("roles." + role + ".blocked", new ArrayList<>(blockedCmds.getOrDefault(role, Set.of())));
            c.set("roles." + role + ".allowed", new ArrayList<>(allowedCmds.getOrDefault(role, Set.of())));
        }
        save(c, cmdBlocksFile);
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
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    private void removeTeam(String roleName) {
        Team t = scoreboard.getTeam(teamName(roleName));
        if (t != null) t.unregister();
    }

    public void syncPlayerTeam(Player player) {
        String roleName = playerRoles.get(player.getUniqueId());
        String playerName = player.getName();

        // Remove from any existing mv_ team by entry name (more reliable than getPlayerTeam)
        for (Team t : scoreboard.getTeams()) {
            if (t.getName().startsWith(TEAM_PREFIX) && t.hasEntry(playerName)) {
                t.removeEntry(playerName);
            }
        }

        if (roleName != null) {
            Team team = scoreboard.getTeam(teamName(roleName));
            if (team != null) team.addEntry(playerName);
        }

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
        blockedCmds.remove(name);
        allowedCmds.remove(name);
        blockAllRoles.remove(name);
        removeTeam(name);
        saveRoles();
        savePlayers();
        saveLinks();
        saveCmdBlocks();
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

    // ── Command blocking ─────────────────────────────────────────────────────

    public boolean blockCommand(String roleName, String cmd) {
        if (!roles.containsKey(roleName)) return false;
        blockedCmds.computeIfAbsent(roleName, k -> new HashSet<>()).add(cmd.toLowerCase());
        saveCmdBlocks();
        return true;
    }

    public boolean setBlockAll(String roleName) {
        if (!roles.containsKey(roleName)) return false;
        blockAllRoles.add(roleName);
        saveCmdBlocks();
        return true;
    }

    public boolean unblockAllCommands(String roleName) {
        if (!roles.containsKey(roleName)) return false;
        blockAllRoles.remove(roleName);
        blockedCmds.remove(roleName);
        allowedCmds.remove(roleName);
        saveCmdBlocks();
        return true;
    }

    public void allowCommandGlobal(String cmd) {
        String c = cmd.toLowerCase();
        for (String roleName : roles.keySet()) {
            if (blockAllRoles.contains(roleName)) {
                allowedCmds.computeIfAbsent(roleName, k -> new HashSet<>()).add(c);
            } else {
                Set<String> blocked = blockedCmds.get(roleName);
                if (blocked != null) blocked.remove(c);
            }
        }
        saveCmdBlocks();
    }

    public void blockCommandGlobal(String cmd) {
        String c = cmd.toLowerCase();
        for (String roleName : roles.keySet()) {
            if (isLowPriorityRole(roleName))
                blockedCmds.computeIfAbsent(roleName, k -> new HashSet<>()).add(c);
        }
        saveCmdBlocks();
    }

    public void blockAllCommandsGlobal() {
        for (String roleName : roles.keySet()) {
            if (isLowPriorityRole(roleName))
                blockAllRoles.add(roleName);
        }
        saveCmdBlocks();
    }

    /** Returns true for roles at or below the join role's weight (i.e. regular players, not staff). */
    private boolean isLowPriorityRole(String roleName) {
        if (joinRole == null) return true;
        int joinWeight = roleWeights.getOrDefault(joinRole, 50);
        int roleWeight = roleWeights.getOrDefault(roleName, 50);
        return roleWeight >= joinWeight;
    }

    public void unblockAllCommandsGlobal() {
        blockAllRoles.clear();
        blockedCmds.clear();
        allowedCmds.clear();
        saveCmdBlocks();
    }

    public boolean allowCommand(String roleName, String cmd) {
        if (!roles.containsKey(roleName)) return false;
        String c = cmd.toLowerCase();
        if (blockAllRoles.contains(roleName)) {
            allowedCmds.computeIfAbsent(roleName, k -> new HashSet<>()).add(c);
        } else {
            Set<String> blocked = blockedCmds.get(roleName);
            if (blocked != null) blocked.remove(c);
        }
        saveCmdBlocks();
        return true;
    }

    public boolean isCommandBlocked(String roleName, String input) {
        if (roleName == null || input == null) return false;
        String cmd = input.toLowerCase();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        int playerWeight = roleWeights.getOrDefault(roleName, 50);

        // Check the player's role AND every higher-priority role (lower weight).
        // A block at any higher priority cascades down; an allow at any lower priority cascades up.
        for (Map.Entry<String, Integer> e : roleWeights.entrySet()) {
            if (e.getValue() <= playerWeight && isNetBlockedForRole(e.getKey(), cmd)) return true;
        }
        return false;
    }

    private boolean isNetBlockedForRole(String role, String cmd) {
        if (blockAllRoles.contains(role)) {
            for (String a : allowedCmds.getOrDefault(role, Set.of())) {
                if (cmdMatches(a, cmd)) return false;
            }
            return true;
        }
        for (String b : blockedCmds.getOrDefault(role, Set.of())) {
            if (cmdMatches(b, cmd)) return true;
        }
        return false;
    }

    private boolean cmdMatches(String prefix, String input) {
        return input.equals(prefix) || input.startsWith(prefix + " ");
    }

    // ── Role links ───────────────────────────────────────────────────────────

    public boolean linkRole(String gameRole, String discordRoleId) {
        if (!roles.containsKey(gameRole)) return false;
        roleLinks.put(gameRole, discordRoleId);
        saveLinks();
        // Register link with bot
        int weight = roleWeights.getOrDefault(gameRole, 50);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            postJson("/api/role-links",
                String.format("{\"game_role\":\"%s\",\"discord_role_id\":\"%s\",\"weight\":%d}",
                    gameRole, discordRoleId, weight))
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
                                playerRoles.put(uuid, gameRole);
                                Player online = Bukkit.getPlayer(uuid);
                                if (online != null) syncPlayerTeam(online);
                                savePlayers();
                                // After assigning, upgrade to highest Discord role if one exists
                                new BukkitRunnable() {
                                    @Override public void run() { syncFromDiscord(uuid); }
                                }.runTaskAsynchronously(plugin);
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

    /**
     * Queries the bot for all Discord roles the player currently holds, finds the
     * highest-priority (lowest weight) linked game role among them, and assigns it.
     * Must be called from an async thread.
     */
    public void syncFromDiscord(UUID mcUuid) {
        String body = getJson("/api/discord-roles/" + mcUuid);
        if (body == null || body.isBlank() || body.equals("[]")) return;

        List<String> discordRoleIds = parseJsonStringArray(body);
        if (discordRoleIds.isEmpty()) return;

        String bestRole  = null;
        int    bestWeight = Integer.MAX_VALUE;
        for (Map.Entry<String, String> link : roleLinks.entrySet()) {
            if (discordRoleIds.contains(link.getValue())) {
                int w = roleWeights.getOrDefault(link.getKey(), 50);
                if (w < bestWeight) { bestWeight = w; bestRole = link.getKey(); }
            }
        }

        if (bestRole == null) return;

        final String roleToAssign = bestRole;
        new BukkitRunnable() {
            @Override public void run() {
                if (roleToAssign.equals(playerRoles.get(mcUuid))) return;
                playerRoles.put(mcUuid, roleToAssign);
                Player online = Bukkit.getPlayer(mcUuid);
                if (online != null) syncPlayerTeam(online);
                savePlayers();
                plugin.getLogger().info("[RoleSync] Assigned '" + roleToAssign + "' to " + mcUuid + " via Discord role sync.");
            }
        }.runTask(plugin);
    }

    /** Parses a JSON array of strings: ["a","b","c"] → List<String>. */
    private static List<String> parseJsonStringArray(String json) {
        List<String> result = new ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) return result;
        json = json.substring(1, json.length() - 1).trim();
        if (json.isBlank()) return result;
        for (String part : json.split(",")) {
            String s = part.trim().replaceAll("^\"|\"$", "");
            if (!s.isBlank()) result.add(s);
        }
        return result;
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

    /** Returns the role prefix serialized to §-format legacy string (handles gradients). */
    public String getPrefixLegacy(UUID uuid) {
        String role = playerRoles.get(uuid);
        if (role == null) return null;
        String prefix = roles.get(role);
        if (prefix == null || prefix.isBlank()) return null;
        return LegacyComponentSerializer.legacySection().serialize(parsePrefix(prefix));
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

    public boolean setMuteRole(String name) {
        if (!roles.containsKey(name)) return false;
        muteRole = name; saveRoles(); return true;
    }

    public void clearMuteRole() { muteRole = null; saveRoles(); }

    public String getMuteRole() { return muteRole; }

    public boolean canUseMute(UUID uuid) {
        if (muteRole == null) return false;
        Integer threshold = roleWeights.get(muteRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setAnnouncementRole(String name) {
        if (!roles.containsKey(name)) return false;
        announcementRole = name; saveRoles(); return true;
    }

    public void clearAnnouncementRole() { announcementRole = null; saveRoles(); }

    public String getAnnouncementRole() { return announcementRole; }

    public boolean canUseAnnouncement(UUID uuid) {
        if (announcementRole == null) return false;
        Integer threshold = roleWeights.get(announcementRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setFlyRole(String name) {
        if (!roles.containsKey(name)) return false;
        flyRole = name; saveRoles(); return true;
    }

    public void clearFlyRole() { flyRole = null; saveRoles(); }

    public String getFlyRole() { return flyRole; }

    public boolean canUseFly(UUID uuid) {
        if (flyRole == null) return false;
        Integer threshold = roleWeights.get(flyRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setStaffRole(String name) {
        if (!roles.containsKey(name)) return false;
        staffRole = name; saveRoles(); return true;
    }

    public void clearStaffRole() { staffRole = null; saveRoles(); }

    public String getStaffRole() { return staffRole; }

    /** Returns true if the player is allowed to use the staff panel.
     *  If no staff-role is set, everyone is allowed. */
    public boolean canUseStaff(UUID uuid) {
        if (staffRole == null) return true;
        Integer threshold = roleWeights.get(staffRole);
        if (threshold == null) return true;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public Map<String, Integer> getRoleWeights() { return Collections.unmodifiableMap(roleWeights); }

    // ── Name color match ─────────────────────────────────────────────────────

    public boolean isNameColorMatch() { return nameColorMatch; }

    public boolean toggleNameColorMatch() {
        nameColorMatch = !nameColorMatch;
        saveRoles();
        return nameColorMatch;
    }

    /** Returns the first color found in the player's role prefix, or null. */
    public TextColor extractRoleColor(UUID uuid) {
        String legacy = getPrefixLegacy(uuid);
        if (legacy == null) return null;
        for (int i = 0; i < legacy.length() - 1; i++) {
            if (legacy.charAt(i) == '§') {
                NamedTextColor color = legacyCodeToColor(Character.toLowerCase(legacy.charAt(i + 1)));
                if (color != null) return color;
            }
        }
        return null;
    }

    private static NamedTextColor legacyCodeToColor(char c) {
        return switch (c) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default  -> null;
        };
    }
}
