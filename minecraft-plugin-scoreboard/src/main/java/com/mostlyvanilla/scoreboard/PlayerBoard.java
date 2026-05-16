package com.mostlyvanilla.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class PlayerBoard {

    // Unique invisible entries for each sidebar line (§0§r through §d§r = 14 entries)
    private static final String[] ENTRIES = {
        "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r",
        "§7§r", "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r"
    };

    private static final int LINES = ENTRIES.length; // 14

    private final Scoreboard scoreboard;
    @SuppressWarnings("deprecation")
    private final Objective objective;
    private final Team[] teams = new Team[LINES];

    @SuppressWarnings("deprecation")
    public PlayerBoard(Player player) {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("mv_sb", "dummy", "");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < LINES; i++) {
            Team team = scoreboard.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            // Score controls vertical position — higher score = higher on sidebar
            objective.getScore(ENTRIES[i]).setScore(LINES - 1 - i);
            teams[i] = team;
        }

        player.setScoreboard(scoreboard);
    }

    /**
     * Called every animation tick. Updates animated lines and, when fullUpdate is true,
     * re-fetches all live player data.
     */
    @SuppressWarnings("deprecation")
    public void update(Player player, String title, String sep,
                       boolean fullUpdate, String currency1, String currency2,
                       String serverAddress) {
        objective.setDisplayName(title);

        // Animated separator lines (lines 0 and 2)
        setLine(0, sep);
        setLine(2, sep);

        if (!fullUpdate) return;

        // Static header
        setLine(1, "   §a§lMostly Vanilla");
        setLine(3, " ");

        // Role
        setLine(4, " §7Role");
        setLine(5, "  §f" + fetchRole(player));
        setLine(6, "  ");

        // Currencies
        String c1Name = capitalize(currency1);
        String c2Name = capitalize(currency2);
        String bal1   = fetchBalance(player, currency1);
        String bal2   = fetchBalance(player, currency2);
        setLine(7, " §e" + c1Name + " §8» §a" + bal1);
        setLine(8, " §2" + c2Name + " §8» §a" + bal2);
        setLine(9, "   ");

        // Stats
        int kills  = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        setLine(10, " §7Kills §a" + kills + "  §7Deaths §c" + deaths);
        setLine(11, " §7Playtime §f" + formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        setLine(12, "    ");

        // Footer
        setLine(13, " §7" + serverAddress);
    }

    public void remove(Player player) {
        if (player != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private void setLine(int index, String text) {
        // Team prefix supports up to 64 chars in 1.20+
        teams[index].setPrefix(text.length() > 64 ? text.substring(0, 64) : text);
    }

    private String fetchRole(Player player) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            return fetchLuckPermsRole(player);
        }
        return "Member";
    }

    private String fetchLuckPermsRole(Player player) {
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
            if (user == null) return "Member";
            String prefix = user.getCachedData().getMetaData().getPrefix();
            if (prefix != null && !prefix.isBlank()) return prefix;
            return capitalize(user.getPrimaryGroup());
        } catch (Exception e) {
            return "Member";
        }
    }

    private String fetchBalance(Player player, String currency) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String result = PlaceholderAPI.setPlaceholders(player, "%economy_balance_" + currency + "%");
            if (result != null && !result.startsWith("%")) return result;
        }
        return "0";
    }

    private String formatPlaytime(int ticks) {
        long seconds = ticks / 20L;
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
