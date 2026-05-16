package com.mostlyvanilla.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.*;

import java.util.UUID;

public class PlayerBoard {

    private static final String[] ENTRIES = {
        "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r",
        "§7§r", "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r"
    };
    private static final int LINES = ENTRIES.length;

    private final Scoreboard scoreboard;
    @SuppressWarnings("deprecation")
    private final Objective objective;
    private final Team[] teams = new Team[LINES];

    @SuppressWarnings("deprecation")
    public PlayerBoard(Player player) {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective  = scoreboard.registerNewObjective("mv_sb", "dummy", "");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < LINES; i++) {
            Team team = scoreboard.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            objective.getScore(ENTRIES[i]).setScore(LINES - 1 - i);
            teams[i] = team;
        }
        player.setScoreboard(scoreboard);
    }

    @SuppressWarnings("deprecation")
    public void update(Player player, String title, String sep,
                       boolean fullUpdate, String currency1, String currency2,
                       String serverAddress) {
        objective.setDisplayName(title);
        setLine(0, sep);
        setLine(2, sep);

        if (!fullUpdate) return;

        setLine(1,  "   §a§lMostly Vanilla");
        setLine(3,  " ");

        setLine(4,  " §7Role");
        setLine(5,  "  " + fetchRole(player));
        setLine(6,  "  ");

        setLine(7,  " §e" + currency1 + " §8» §a" + fetchBalance(player, currency1));
        setLine(8,  " §2" + currency2 + " §8» §a" + fetchBalance(player, currency2));
        setLine(9,  "   ");

        int kills  = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        setLine(10, " §7Kills §a" + kills + "  §7Deaths §c" + deaths);
        setLine(11, " §7Playtime §f" + formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        setLine(12, "    ");
        setLine(13, " §7" + serverAddress);
    }

    public void remove(Player player) {
        if (player != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ── Data fetchers ─────────────────────────────────────

    private String fetchRole(Player player) {
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return "§7Member";
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            if (rm == null) return "§7Member";

            String roleName = (String) rm.getClass()
                    .getMethod("getPlayerRole", UUID.class)
                    .invoke(rm, player.getUniqueId());
            if (roleName == null) return "§7Member";

            // Get the color from the role's prefix, apply it to the role name
            String color = extractPrefixColor(rm, player.getUniqueId());
            return color + capitalize(roleName);
        } catch (Exception ignored) {
            return "§7Member";
        }
    }

    /** Pulls the first color code out of the role prefix (e.g. "&c[Admin]" → "§c"). */
    private String extractPrefixColor(Object rm, UUID uuid) {
        try {
            String prefix = (String) rm.getClass()
                    .getMethod("getPrefix", UUID.class)
                    .invoke(rm, uuid);
            if (prefix == null || prefix.isBlank()) return "§f";
            String t = ChatColor.translateAlternateColorCodes('&', prefix.trim());
            int i = 0;
            while (i + 1 < t.length() && t.charAt(i) == '§') {
                char code = Character.toLowerCase(t.charAt(i + 1));
                if ("0123456789abcdef".indexOf(code) >= 0) {
                    return "§" + code;
                }
                i += 2; // skip formatting codes (§l, §o, etc.) and keep looking
            }
        } catch (Exception ignored) {}
        return "§f";
    }

    private String fetchBalance(Player player, String currency) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String result = PlaceholderAPI.setPlaceholders(
                    player, "%economy_balance_" + currency.toLowerCase() + "%");
            if (result != null && !result.startsWith("%")) return result;
        }
        return "0";
    }

    // ── Helpers ───────────────────────────────────────────

    private void setLine(int index, String text) {
        teams[index].setPrefix(text.length() > 64 ? text.substring(0, 64) : text);
    }

    private String formatPlaytime(int ticks) {
        long seconds = ticks / 20L;
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
