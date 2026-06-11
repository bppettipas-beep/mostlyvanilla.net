package com.mostlyvanilla.roles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
    private ApiClient apiClient;

    private final Map<String, String>  roles       = new LinkedHashMap<>();
    private final Map<String, Integer> roleWeights = new HashMap<>();
    private final Map<UUID, String>    playerRoles = new HashMap<>();
    private String  joinRole          = null;
    private String  staffRole         = null;
    private String  flyRole           = null;
    private String  allowTpRole       = null;
    private String  announcementRole  = null;
    private String  muteRole          = null;
    private String  banRole           = null;
    private String  ecSeeRole         = null;
    private String  invSeeRole        = null;
    private String  stashRole         = null;
    private String  spawnoreRole      = null;
    private String  susRole           = null;
    private boolean nameColorMatch    = false;
    private String  dutyRole          = null;

    private final Set<UUID>                onDutyPlayers = new HashSet<>();
    private final Map<String, Set<String>> blockedCmds  = new HashMap<>();
    private final Map<String, Set<String>> allowedCmds  = new HashMap<>();
    private final Set<String>             blockAllRoles = new HashSet<>();

    private File rolesFile;
    private File playersFile;
    private File cmdBlocksFile;
    private Scoreboard scoreboard;

    private static final String TEAM_PREFIX = "mv_";

    public RoleManager(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rolesFile     = new File(plugin.getDataFolder(), "roles.yml");
        playersFile   = new File(plugin.getDataFolder(), "players.yml");
        cmdBlocksFile = new File(plugin.getDataFolder(), "command-blocks.yml");

        createIfAbsent(rolesFile);
        createIfAbsent(playersFile);
        createIfAbsent(cmdBlocksFile);

        // Load roles
        YamlConfiguration rc = YamlConfiguration.loadConfiguration(rolesFile);
        joinRole         = rc.getString("join-role",         null);
        staffRole        = rc.getString("staff-role",        null);
        flyRole          = rc.getString("fly-role",          null);
        allowTpRole      = rc.getString("allowtp-role",      null);
        announcementRole = rc.getString("announcement-role", null);
        muteRole         = rc.getString("mute-role",         null);
        banRole          = rc.getString("ban-role",          null);
        ecSeeRole        = rc.getString("ecsee-role",        null);
        invSeeRole       = rc.getString("invsee-role",       null);
        stashRole        = rc.getString("stash-role",        null);
        spawnoreRole     = rc.getString("spawnore-role",     null);
        susRole          = rc.getString("sus-role",          null);
        nameColorMatch   = rc.getBoolean("name-color-match", false);
        dutyRole         = rc.getString("duty-role",         null);
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
            playerRoles.size() + " assignment(s).");
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
        if (allowTpRole      != null) c.set("allowtp-role",      allowTpRole);
        if (announcementRole != null) c.set("announcement-role", announcementRole);
        if (muteRole         != null) c.set("mute-role",         muteRole);
        if (banRole          != null) c.set("ban-role",          banRole);
        if (ecSeeRole        != null) c.set("ecsee-role",        ecSeeRole);
        if (invSeeRole       != null) c.set("invsee-role",       invSeeRole);
        if (stashRole        != null) c.set("stash-role",        stashRole);
        if (spawnoreRole     != null) c.set("spawnore-role",     spawnoreRole);
        if (susRole          != null) c.set("sus-role",          susRole);
        c.set("name-color-match", nameColorMatch);
        if (dutyRole         != null) c.set("duty-role",         dutyRole);
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

    public void syncPlayerTeamIfNeeded(Player player) {
        String roleName = playerRoles.get(player.getUniqueId());
        if (roleName == null) return;
        Team expected = scoreboard.getTeam(teamName(roleName));
        if (expected == null) return;
        if (!expected.hasEntry(player.getName())) syncPlayerTeam(player);
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
        if (name.equals(joinRole))         joinRole         = null;
        if (name.equals(allowTpRole))      allowTpRole      = null;
        if (name.equals(dutyRole))         dutyRole         = null;
        if (name.equals(staffRole))        staffRole        = null;
        if (name.equals(flyRole))          flyRole          = null;
        if (name.equals(announcementRole)) announcementRole = null;
        if (name.equals(muteRole))         muteRole         = null;
        if (name.equals(banRole))          banRole          = null;
        if (name.equals(ecSeeRole))        ecSeeRole        = null;
        if (name.equals(invSeeRole))       invSeeRole       = null;
        if (name.equals(stashRole))        stashRole        = null;
        if (name.equals(spawnoreRole))     spawnoreRole     = null;
        if (name.equals(susRole))          susRole          = null;
        blockedCmds.remove(name);
        allowedCmds.remove(name);
        blockAllRoles.remove(name);
        removeTeam(name);
        saveRoles();
        savePlayers();
        saveCmdBlocks();
        return true;
    }

    public boolean setWeight(String roleName, int weight) {
        if (!roles.containsKey(roleName)) return false;
        removeTeam(roleName);
        roleWeights.put(roleName, weight);
        setupTeam(roleName, roles.get(roleName));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roleName.equals(playerRoles.get(p.getUniqueId()))) syncPlayerTeam(p);
        }
        saveRoles();
        return true;
    }

    public void setApiClient(ApiClient client) { this.apiClient = client; }

    public boolean assignRole(UUID uuid, String roleName) {
        if (!roles.containsKey(roleName)) return false;
        String oldRole = playerRoles.get(uuid);
        playerRoles.put(uuid, roleName);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        if (apiClient != null) {
            final String mcUuid = uuid.toString();
            final String old    = oldRole;
            final String newR   = roleName;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (old != null && !old.equals(newR)) apiClient.notifyRoleChange(mcUuid, old, false);
                apiClient.notifyRoleChange(mcUuid, newR, true);
            });
        }
        return true;
    }

    /** Assign a role coming from Discord sync — removes old Discord role if changed, never pings back. */
    public void assignRoleFromDiscord(UUID uuid, String roleName) {
        if (!roles.containsKey(roleName)) return;
        String oldRole = playerRoles.get(uuid);
        if (roleName.equals(oldRole)) return;
        playerRoles.put(uuid, roleName);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        if (apiClient != null && oldRole != null) {
            final String mcUuid = uuid.toString();
            final String old    = oldRole;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                apiClient.notifyRoleChange(mcUuid, old, false));
        }
    }

    public boolean removePlayerRole(UUID uuid) {
        String roleName = playerRoles.remove(uuid);
        if (roleName == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
        if (apiClient != null) {
            final String mcUuid = uuid.toString();
            final String role   = roleName;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                apiClient.notifyRoleChange(mcUuid, role, false));
        }
        return true;
    }

    /** Remove a role only if it matches the player's current role (used by Discord→MC sync). */
    public void removePlayerRoleIfMatches(UUID uuid, String roleName) {
        if (!roleName.equals(playerRoles.get(uuid))) return;
        playerRoles.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) syncPlayerTeam(player);
        savePlayers();
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
        allowedCmds.computeIfAbsent(roleName, k -> new HashSet<>()).add(c);
        Set<String> blocked = blockedCmds.get(roleName);
        if (blocked != null) blocked.remove(c);
        saveCmdBlocks();
        return true;
    }

    public boolean isCommandBlocked(String roleName, String input) {
        if (roleName == null || input == null) return false;
        String cmd = input.toLowerCase();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        for (String a : allowedCmds.getOrDefault(roleName, Set.of())) {
            if (cmdMatches(a, cmd)) return false;
        }

        int playerWeight = roleWeights.getOrDefault(roleName, 50);

        for (Map.Entry<String, Integer> e : roleWeights.entrySet()) {
            if (e.getValue() <= playerWeight && isNetBlockedForRole(e.getKey(), cmd)) return true;
        }
        return false;
    }

    private boolean isNetBlockedForRole(String role, String cmd) {
        for (String a : allowedCmds.getOrDefault(role, Set.of())) {
            if (cmdMatches(a, cmd)) return false;
        }
        if (blockAllRoles.contains(role)) return true;
        for (String b : blockedCmds.getOrDefault(role, Set.of())) {
            if (cmdMatches(b, cmd)) return true;
        }
        return false;
    }

    private boolean cmdMatches(String prefix, String input) {
        return input.equals(prefix) || input.startsWith(prefix + " ");
    }

    public boolean hasRolePermission(UUID uuid, String input) {
        String cmd = input.toLowerCase();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if ((cmdMatches("fly", cmd) || cmdMatches("allowfly", cmd)) && canUseFly(uuid)) return true;
        if ((cmdMatches("tp", cmd) || cmdMatches("teleport", cmd)) && canUseTp(uuid)) return true;
        if ((cmdMatches("mute", cmd) || cmdMatches("unmute", cmd) || cmdMatches("tempmute", cmd)) && canUseMute(uuid)) return true;
        if ((cmdMatches("announcement", cmd) || cmdMatches("announce", cmd)) && canUseAnnouncement(uuid)) return true;
        if (banRole != null && (cmdMatches("ban", cmd) || cmdMatches("tempban", cmd) || cmdMatches("banip", cmd)) && canUseBan(uuid)) return true;
        if (cmdMatches("checkec", cmd) && canUseEcSee(uuid)) return true;
        if (cmdMatches("invsee", cmd) && canUseInvSee(uuid)) return true;
        if (staffRole != null && cmdMatches("staff", cmd) && canUseStaff(uuid)) return true;
        if (cmdMatches("sus", cmd) && canNotifySus(uuid) && isOnDuty(uuid)) return true;
        if ((cmdMatches("spawnstash", cmd) || cmdMatches("delstash", cmd)) && canUseStash(uuid)) return true;
        if ((cmdMatches("spawnore", cmd) || cmdMatches("delore", cmd)) && canUseSpawnore(uuid)) return true;
        return false;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getPrefix(UUID uuid) {
        String role = playerRoles.get(uuid);
        return role != null ? roles.get(role) : null;
    }

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
    public Map<String, Integer> getRoleWeights()  { return Collections.unmodifiableMap(roleWeights); }

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

    public boolean setBanRole(String name) {
        if (!roles.containsKey(name)) return false;
        banRole = name; saveRoles(); return true;
    }
    public void clearBanRole() { banRole = null; saveRoles(); }
    public String getBanRole() { return banRole; }
    public boolean canUseBan(UUID uuid) {
        if (banRole == null) return false;
        Integer threshold = roleWeights.get(banRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setEcSeeRole(String name) {
        if (!roles.containsKey(name)) return false;
        ecSeeRole = name; saveRoles(); return true;
    }
    public void clearEcSeeRole() { ecSeeRole = null; saveRoles(); }
    public String getEcSeeRole() { return ecSeeRole; }
    public boolean canUseEcSee(UUID uuid) {
        if (ecSeeRole == null) return false;
        Integer threshold = roleWeights.get(ecSeeRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setInvSeeRole(String name) {
        if (!roles.containsKey(name)) return false;
        invSeeRole = name; saveRoles(); return true;
    }
    public void clearInvSeeRole() { invSeeRole = null; saveRoles(); }
    public String getInvSeeRole() { return invSeeRole; }
    public boolean canUseInvSee(UUID uuid) {
        if (invSeeRole == null) return false;
        Integer threshold = roleWeights.get(invSeeRole);
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

    public boolean setAllowTpRole(String name) {
        if (!roles.containsKey(name)) return false;
        allowTpRole = name; saveRoles(); return true;
    }
    public void clearAllowTpRole() { allowTpRole = null; saveRoles(); }
    public String getAllowTpRole() { return allowTpRole; }
    public boolean canUseTp(UUID uuid) {
        if (allowTpRole == null) return false;
        Integer threshold = roleWeights.get(allowTpRole);
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
    public boolean canUseStaff(UUID uuid) {
        if (staffRole == null) return true;
        Integer threshold = roleWeights.get(staffRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setStashRole(String name) {
        if (!roles.containsKey(name)) return false;
        stashRole = name; saveRoles(); return true;
    }
    public void clearStashRole() { stashRole = null; saveRoles(); }
    public String getStashRole() { return stashRole; }
    public boolean canUseStash(UUID uuid) {
        if (stashRole == null) return false;
        Integer threshold = roleWeights.get(stashRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setSpawnoreRole(String name) {
        if (!roles.containsKey(name)) return false;
        spawnoreRole = name; saveRoles(); return true;
    }
    public void clearSpawnoreRole() { spawnoreRole = null; saveRoles(); }
    public String getSpawnoreRole() { return spawnoreRole; }
    public boolean canUseSpawnore(UUID uuid) {
        if (spawnoreRole == null) return false;
        Integer threshold = roleWeights.get(spawnoreRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean setSusRole(String name) {
        if (!roles.containsKey(name)) return false;
        susRole = name; saveRoles(); return true;
    }
    public void clearSusRole() { susRole = null; saveRoles(); }
    public String getSusRole() { return susRole; }
    public boolean canNotifySus(UUID uuid) {
        if (susRole == null) return false;
        Integer threshold = roleWeights.get(susRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    // ── Duty system ──────────────────────────────────────────────────────────

    public boolean setDutyRole(String name) {
        if (!roles.containsKey(name)) return false;
        dutyRole = name; saveRoles(); return true;
    }
    public void clearDutyRole() { dutyRole = null; saveRoles(); }
    public String getDutyRole() { return dutyRole; }

    public boolean isDutyRequired(UUID uuid) {
        if (dutyRole == null) return false;
        Integer threshold = roleWeights.get(dutyRole);
        if (threshold == null) return false;
        String playerRole = playerRoles.get(uuid);
        if (playerRole == null) return false;
        Integer playerWeight = roleWeights.get(playerRole);
        return playerWeight != null && playerWeight <= threshold;
    }

    public boolean isOnDuty(UUID uuid) { return onDutyPlayers.contains(uuid); }

    public boolean toggleDuty(UUID uuid) {
        if (onDutyPlayers.remove(uuid)) return false;
        onDutyPlayers.add(uuid);
        return true;
    }

    public void clearDutyStatus(UUID uuid) { onDutyPlayers.remove(uuid); }

    public boolean isDutyGatedCommand(String input) {
        String cmd = input.toLowerCase();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmdMatches("gmc", cmd) || cmdMatches("gms", cmd) ||
            cmdMatches("gmsp", cmd) || cmdMatches("gma", cmd)) return true;
        if (staffRole != null && cmdMatches("staff", cmd)) return true;
        if (muteRole != null && (cmdMatches("mute", cmd) || cmdMatches("unmute", cmd) ||
            cmdMatches("tempmute", cmd))) return true;
        if (banRole != null && (cmdMatches("ban", cmd) || cmdMatches("tempban", cmd) ||
            cmdMatches("banip", cmd) || cmdMatches("unban", cmd))) return true;
        if (flyRole != null && (cmdMatches("fly", cmd) || cmdMatches("allowfly", cmd))) return true;
        if (allowTpRole != null && (cmdMatches("tp", cmd) || cmdMatches("teleport", cmd))) return true;
        if (announcementRole != null && (cmdMatches("announcement", cmd) || cmdMatches("announce", cmd))) return true;
        if (ecSeeRole != null && cmdMatches("checkec", cmd)) return true;
        if (invSeeRole != null && cmdMatches("invsee", cmd)) return true;
        if (susRole != null && cmdMatches("sus", cmd)) return true;
        return false;
    }

    // ── Name color match ─────────────────────────────────────────────────────

    public boolean isNameColorMatch() { return nameColorMatch; }

    public boolean toggleNameColorMatch() {
        nameColorMatch = !nameColorMatch;
        saveRoles();
        return nameColorMatch;
    }

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
