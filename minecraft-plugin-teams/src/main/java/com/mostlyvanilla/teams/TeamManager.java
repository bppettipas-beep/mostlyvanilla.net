package com.mostlyvanilla.teams;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final JavaPlugin plugin;

    private final Map<UUID, TeamData> teams  = new LinkedHashMap<>(); // team id → team
    private final Map<UUID, UUID>     member = new HashMap<>();       // player uuid → team id
    private final Set<UUID>           tchat  = new HashSet<>();       // players with team chat on

    private File              dataFile;
    private YamlConfiguration dataCfg;

    public TeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "teams.yml");
        dataCfg  = YamlConfiguration.loadConfiguration(dataFile);
        teams.clear();
        member.clear();

        ConfigurationSection root = dataCfg.getConfigurationSection("teams");
        if (root == null) return;

        for (String idStr : root.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(idStr); } catch (Exception e) { continue; }
            String name   = root.getString(idStr + ".name", "Team");
            String color  = root.getString(idStr + ".color", "white");
            UUID   leader;
            try { leader = UUID.fromString(root.getString(idStr + ".leader", "")); }
            catch (Exception e) { continue; }

            TeamData team = new TeamData(id, name, color, leader);
            team.getMembers().clear();

            for (String ms : root.getStringList(idStr + ".members")) {
                try {
                    UUID mu = UUID.fromString(ms);
                    team.getMembers().add(mu);
                    member.put(mu, id);
                } catch (Exception ignored) {}
            }

            team.setFriendlyFire(root.getBoolean(idStr + ".friendly-fire", false));
            team.setOpen(root.getBoolean(idStr + ".open", false));

            if (root.contains(idStr + ".home.world")) {
                String wn = root.getString(idStr + ".home.world");
                org.bukkit.World w = Bukkit.getWorld(wn != null ? wn : "");
                if (w != null) {
                    team.setHome(new Location(w,
                        root.getDouble(idStr + ".home.x"),
                        root.getDouble(idStr + ".home.y"),
                        root.getDouble(idStr + ".home.z"),
                        (float) root.getDouble(idStr + ".home.yaw"),
                        (float) root.getDouble(idStr + ".home.pitch")));
                }
            }

            teams.put(id, team);
            syncScoreboard(team);
        }
        plugin.getLogger().info("[Teams] Loaded " + teams.size() + " team(s).");
    }

    public void save() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "teams.yml");
        YamlConfiguration c = new YamlConfiguration();
        for (TeamData t : teams.values()) {
            String base = "teams." + t.getId();
            c.set(base + ".name",           t.getName());
            c.set(base + ".color",          t.getColor());
            c.set(base + ".leader",         t.getLeader().toString());
            c.set(base + ".friendly-fire",  t.isFriendlyFire());
            c.set(base + ".open",           t.isOpen());
            List<String> ms = new ArrayList<>();
            t.getMembers().forEach(u -> ms.add(u.toString()));
            c.set(base + ".members", ms);
            if (t.getHome() != null) {
                Location h = t.getHome();
                c.set(base + ".home.world", h.getWorld().getName());
                c.set(base + ".home.x",     h.getX());
                c.set(base + ".home.y",     h.getY());
                c.set(base + ".home.z",     h.getZ());
                c.set(base + ".home.yaw",   (double) h.getYaw());
                c.set(base + ".home.pitch", (double) h.getPitch());
            }
        }
        try { c.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("[Teams] Could not save teams.yml: " + e.getMessage()); }
    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    public void syncScoreboard(TeamData team) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String sid = team.scoreboardId();
        Team st = board.getTeam(sid);
        if (st == null) st = board.registerNewTeam(sid);

        st.prefix(Component.empty());
        st.setAllowFriendlyFire(team.isFriendlyFire());
        st.setCanSeeFriendlyInvisibles(true);

        for (UUID uuid : team.getMembers()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null && !st.hasEntry(name)) st.addEntry(name);
        }
    }

    public void removeFromScoreboard(TeamData team, String playerName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team st = board.getTeam(team.scoreboardId());
        if (st != null) st.removeEntry(playerName);
    }

    public void unregisterScoreboard(TeamData team) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team st = board.getTeam(team.scoreboardId());
        if (st != null) st.unregister();
    }

    // ── Team CRUD ─────────────────────────────────────────────────────────────

    public TeamData createTeam(String name, Player leader) {
        UUID id     = UUID.randomUUID();
        String color = plugin.getConfig().getString("default-color", "white");
        TeamData team = new TeamData(id, name, color, leader.getUniqueId());
        teams.put(id, team);
        member.put(leader.getUniqueId(), id);
        syncScoreboard(team);
        save();
        return team;
    }

    public void disbandTeam(TeamData team) {
        List<UUID> allMembers = new ArrayList<>(team.getMembers());
        for (UUID uuid : allMembers) {
            member.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text("Your team \"" + team.getName() + "\" was disbanded.")
                .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            tchat.remove(uuid);
        }
        unregisterScoreboard(team);
        teams.remove(team.getId());
        save();
        // Restore roles-plugin tab position for all online former members
        for (UUID uuid : allMembers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) notifyRolesSync(p);
        }
    }

    public void addMember(TeamData team, Player player) {
        team.getMembers().add(player.getUniqueId());
        member.put(player.getUniqueId(), team.getId());
        String name = player.getName();
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team st = board.getTeam(team.scoreboardId());
        if (st != null && !st.hasEntry(name)) st.addEntry(name);
        save();
    }

    public void removeMember(TeamData team, UUID uuid) {
        team.getMembers().remove(uuid);
        member.remove(uuid);
        tchat.remove(uuid);
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name != null) removeFromScoreboard(team, name);
        save();
        // Restore roles-plugin tab position so player doesn't end up teamless (top of tab)
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) notifyRolesSync(p);
    }

    // ── Roles plugin bridge ───────────────────────────────────────────────────

    private void notifyRolesSync(Player player) {
        org.bukkit.plugin.Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            if (rm != null) rm.getClass().getMethod("syncPlayerTeam", Player.class).invoke(rm, player);
        } catch (Exception ignored) {}
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public TeamData getTeamByPlayer(UUID uuid) {
        UUID tid = member.get(uuid);
        return tid == null ? null : teams.get(tid);
    }

    public TeamData getTeamByName(String name) {
        for (TeamData t : teams.values())
            if (t.getName().equalsIgnoreCase(name)) return t;
        return null;
    }

    public Collection<TeamData> getAllTeams() { return teams.values(); }

    // ── Team chat ─────────────────────────────────────────────────────────────

    public boolean hasTeamChat(UUID uuid)         { return tchat.contains(uuid); }
    public void    toggleTeamChat(UUID uuid)       { if (!tchat.remove(uuid)) tchat.add(uuid); }
    public void    setTeamChat(UUID uuid, boolean b) { if (b) tchat.add(uuid); else tchat.remove(uuid); }

    // ── Config ────────────────────────────────────────────────────────────────

    public int maxTeamSize() { return plugin.getConfig().getInt("max-team-size", 10); }
}
