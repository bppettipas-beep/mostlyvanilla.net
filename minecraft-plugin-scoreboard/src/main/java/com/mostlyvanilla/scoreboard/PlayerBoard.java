package com.mostlyvanilla.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerBoard {

    private static final String[] ENTRIES = {
        "§0§r", "§1§r", "§2§r", "§3§r", "§4§r",
        "§5§r", "§6§r", "§7§r", "§8§r"
    };
    private static final int LINES = ENTRIES.length;

    private static final String SEPARATOR = "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    private final Scoreboard scoreboard;
    @SuppressWarnings("deprecation")
    private final Objective objective;
    private final Team[] teams = new Team[LINES];

    @SuppressWarnings("deprecation")
    public PlayerBoard(Player player) {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective  = scoreboard.registerNewObjective("mv_sb", "dummy", "");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(BoardManager.TITLE);

        for (int i = 0; i < LINES; i++) {
            Team team = scoreboard.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            objective.getScore(ENTRIES[i]).setScore(LINES - 1 - i);
            teams[i] = team;
        }

        // Static separator
        setLine(0, SEPARATOR);

        player.setScoreboard(scoreboard);
    }

    public void update(Player player, String currency1, String currency2, String serverAddress) {
        syncRoleTeams();

        int kills  = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);

        setLine(1, " " + fetchRole(player));
        setLine(2, " §e" + currency1 + "  §f" + fetchBalance(player, currency1));
        setLine(3, " §b" + currency2 + "  §f" + fetchBalance(player, currency2));
        setLine(4, " §aKills  §f" + kills);
        setLine(5, " §cDeaths  §f" + deaths);
        setLine(6, " §dPlaytime  §f" + formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        setLine(7, " ");
        setLine(8, " §8" + serverAddress);
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

            try {
                String prefixLegacy = (String) rm.getClass()
                        .getMethod("getPrefixLegacy", UUID.class)
                        .invoke(rm, player.getUniqueId());
                if (prefixLegacy != null && !prefixLegacy.isBlank()) return prefixLegacy.trim();
            } catch (Exception ignored) {}

            String roleName = (String) rm.getClass()
                    .getMethod("getPlayerRole", UUID.class)
                    .invoke(rm, player.getUniqueId());
            if (roleName == null) return "§7Member";
            String color = extractPrefixColor(rm, player.getUniqueId());
            return color + capitalize(roleName);
        } catch (Exception ignored) {
            return "§7Member";
        }
    }

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
                if ("0123456789abcdef".indexOf(code) >= 0) return "§" + code;
                i += 2;
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
        try {
            Plugin eco = Bukkit.getPluginManager().getPlugin("MostlyVanillaEconomy");
            if (eco != null) {
                Object em = eco.getClass().getMethod("getEconomyManager").invoke(eco);
                if (em != null) {
                    double bal = (double) em.getClass()
                            .getMethod("getBalance", UUID.class, String.class)
                            .invoke(em, player.getUniqueId(), currency.toLowerCase());
                    return fmtBalance(bal);
                }
            }
        } catch (Exception ignored) {}
        return "0";
    }

    // ── Role team sync ────────────────────────────────────

    private void syncRoleTeams() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Set<String> seen = new HashSet<>();

        for (Team src : main.getTeams()) {
            String name = src.getName();
            if (!name.startsWith("mv_")) continue;
            seen.add(name);

            Team dst = scoreboard.getTeam(name);
            if (dst == null) dst = scoreboard.registerNewTeam(name);

            dst.prefix(src.prefix());
            dst.suffix(src.suffix());
            dst.setOption(Team.Option.NAME_TAG_VISIBILITY, src.getOption(Team.Option.NAME_TAG_VISIBILITY));
            dst.setOption(Team.Option.COLLISION_RULE, src.getOption(Team.Option.COLLISION_RULE));

            Set<String> srcEntries = src.getEntries();
            Set<String> dstEntries = new HashSet<>(dst.getEntries());
            for (String e : srcEntries) { if (!dstEntries.contains(e)) dst.addEntry(e); }
            for (String e : dstEntries) { if (!srcEntries.contains(e)) dst.removeEntry(e); }
        }

        for (Team t : new HashSet<>(scoreboard.getTeams())) {
            if (t.getName().startsWith("mv_") && !seen.contains(t.getName())) t.unregister();
        }
    }

    // ── Helpers ───────────────────────────────────────────

    private void setLine(int index, String text) {
        teams[index].prefix(LegacyComponentSerializer.legacySection().deserialize(text));
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

    private static String fmtBalance(double amount) {
        if (amount >= 1_000_000_000) return trimmed(amount / 1_000_000_000) + "B";
        if (amount >= 1_000_000)     return trimmed(amount / 1_000_000) + "M";
        if (amount >= 1_000)         return trimmed(amount / 1_000) + "K";
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
    }

    private static String trimmed(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
