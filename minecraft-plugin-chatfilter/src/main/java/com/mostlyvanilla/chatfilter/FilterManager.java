package com.mostlyvanilla.chatfilter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class FilterManager {

    // Category name constants — must match keys in config.yml
    public static final String CAT_NIGGER = "nigger_slur";
    public static final String CAT_FUCK   = "fuck";
    public static final String CAT_SHIT   = "shit";
    public static final String CAT_BITCH  = "bitch";
    public static final String CAT_CUNT   = "cunt";

    // player UUID → (category name → violation count)
    private final Map<UUID, Map<String, Integer>> violations = new HashMap<>();
    // player UUID → mute expiry epoch ms (-1 = permanent)
    private final Map<UUID, Long> filterMutes = new HashMap<>();

    private File dataFile;

    private final List<WordCategory> categories;

    // Config-driven messages with defaults
    private String msgWarned      = "&cWarning &f{warning}&c: Watch your language! &7(category: {word})";
    private String msgMuted       = "&cYou have been muted for &f{duration} &cfor using prohibited language.";
    private String msgStillMuted  = "&cYou are muted by the chat filter. &7({remaining} remaining)";
    private String msgKicked      = "Kicked: Prohibited language.";
    private String msgBanned      = "Banned: Repeated language violations.";
    private String msgStaff       = "&8[&cFilter&8] &7{player} &8» &c{word} &7(action: {action}, violation #{warning})";

    public FilterManager() {
        categories = buildCategories();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  WORD PATTERNS
    //
    //  Two-pass design:
    //    Pass 1 — leet normalization only (spaces/separators preserved).
    //             Patterns use \b word boundaries. Catches standard text and
    //             single-char leet substitutions.
    //    Pass 2 — full normalization: leet + separator removal + q/x→g.
    //             Simple substring checks. Catches spacing/punctuation bypasses
    //             like "n i g g e r", "n.i.g.g.e.r", "n-1-g-g-e-r", "niqqer".
    //
    //  Pass 1 patterns are applied AFTER leet normalization so they only need
    //  to match the base ASCII letters (no need to embed leet in the regex).
    //  They DO need to handle q/x since those are not leet-normalized in pass 1.
    // ─────────────────────────────────────────────────────────────────────────
    private List<WordCategory> buildCategories() {
        List<WordCategory> list = new ArrayList<>();

        // ── N-WORD ────────────────────────────────────────────────────────────
        // Catches: nigger, n1gger, n!gger, n|gger, n166er, ni99er, ni66er,
        //          niiigger, niggger, n1gg3r, nigg3r, ni9g3r, n1g6er,
        //          niqqer, niqq3r, n1qqer, niqger, nixxer, nixxer,
        //          nigga, n1gga, niiga, niqqah, niqqa, nixxah,
        //          n-i-g-g-e-r, n.i.g.g.e.r, n i g g e r, n_i_g_g_e_r
        list.add(new WordCategory(CAT_NIGGER,
            List.of(
                // Standard + char repetition (no q/x): nigger, niigger, niggger, nigg3r→nigger after leet
                Pattern.compile("\\bni+g+e?r+s?\\b"),
                // Nigga form + char repetition
                Pattern.compile("\\bni+g+a+h?\\b"),
                // Q-bypass: niqqer, niqger, niqqah, niqqa, niqq3r (q not normalized in pass 1)
                Pattern.compile("\\bni+[gq]+[gq]*e?r+s?\\b"),
                Pattern.compile("\\bni+[gq]+[gq]*a+h?\\b"),
                // X-bypass: nixxer, nixger, nixxa, nixxah
                Pattern.compile("\\bni+[gx]+[gx]*e?r+s?\\b"),
                Pattern.compile("\\bni+[gx]+[gx]*a+h?\\b"),
                // Mixed q+x bypass
                Pattern.compile("\\bni+[gqx]+[gqx]*e?r+s?\\b"),
                Pattern.compile("\\bni+[gqx]+[gqx]*a+h?\\b")
            ),
            // Pass 2 (fully normalized: q/x→g, separators removed)
            // "n i g g e r" → "nigger", "niqqer" → "nigger", "nixxer" → "nigger"
            List.of("nigger", "nigga", "niggah")
        ));

        // ── F-WORD ────────────────────────────────────────────────────────────
        // Catches: fuck, f*ck, f-u-c-k, fuuck, fuk, f4ck (4→a→fack), f@ck,
        //          phuck, ph*ck, fcuk, fck, fvck, f_u_c_k,
        //          fucking, fuking, f*cking, fvcking
        list.add(new WordCategory(CAT_FUCK,
            List.of(
                // Standard + "a" from 4→a substitution (f4ck→fack), fuuck, fuk
                Pattern.compile("\\bf+[ua]+c?k+[a-z]*\\b"),
                // fvck variant (v used as u-bypass)
                Pattern.compile("\\bf+v+c?k+[a-z]*\\b"),
                // fcuk (letters scrambled)
                Pattern.compile("\\bf+c+u+k+[a-z]*\\b"),
                // ficking, fcking (c before k, no vowel)
                Pattern.compile("\\bf+c+k+[a-z]*\\b")
            ),
            // Pass 2: separator-bypass forms after full normalization
            List.of("fuck", "fuk", "fck", "fcuk", "fvck", "phuck", "phuk")
        ));

        // ── S-WORD ────────────────────────────────────────────────────────────
        // Catches: shit, sh1t, sh!t, sh|t, shitt, shiiit, shyt,
        //          sh*t, s.h.i.t, s h i t, shiet, sheit
        list.add(new WordCategory(CAT_SHIT,
            List.of(
                // Standard + char repetition + "y" variant (shyt after leet)
                Pattern.compile("\\bsh+[iy]+t+[a-z]*\\b"),
                // sheit / shiet bypass
                Pattern.compile("\\bsh+[e]+[i]+t+[a-z]*\\b")
            ),
            // Pass 2
            List.of("shit", "sht", "shyt", "shiet", "sheit")
        ));

        // ── B-WORD ────────────────────────────────────────────────────────────
        // Catches: bitch, b1tch, b!tch, biitch, biotch, biatch,
        //          b*tch, b-i-t-c-h, b.i.t.c.h, bytch
        list.add(new WordCategory(CAT_BITCH,
            List.of(
                // Standard + char repetition, optional 'c' before h
                Pattern.compile("\\bb+[iy]+tc?h+[a-z]*\\b"),
                // biotch / biatch forms
                Pattern.compile("\\bb+[iy]+[ao]+tc?h+[a-z]*\\b"),
                // bytch (y used as i-bypass, already caught above but explicit)
                Pattern.compile("\\bb+y+tc?h+[a-z]*\\b")
            ),
            // Pass 2
            List.of("bitch", "btch", "bich", "biatch", "biotch", "bytch")
        ));

        // ── C-WORD ────────────────────────────────────────────────────────────
        // Catches: cunt, kunt, cvnt, c*nt, c-u-n-t, kuunt, cunts
        // NOTE: [ua] intentionally NOT used — 4→a would turn c4nt into "cant" (false positive)
        list.add(new WordCategory(CAT_CUNT,
            List.of(
                // c or k variant, u only (not 'a' — avoids matching the real word "cant")
                Pattern.compile("\\b[ck]+u+n+t+s?\\b"),
                // cvnt (v used as u-bypass)
                Pattern.compile("\\b[ck]+v+n+t+s?\\b")
            ),
            // Pass 2
            List.of("cunt", "kunt", "cvnt", "cnt")
        ));

        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONFIG LOADING
    // ─────────────────────────────────────────────────────────────────────────

    public void loadConfig(FileConfiguration config) {
        ConfigurationSection words = config.getConfigurationSection("words");
        if (words != null) {
            for (WordCategory cat : categories) {
                ConfigurationSection cs = words.getConfigurationSection(cat.name);
                if (cs == null) continue;
                cat.setEnabled(cs.getBoolean("enabled", true));
                List<String> actions = cs.getStringList("actions");
                if (!actions.isEmpty()) cat.setActions(actions);
            }
        }

        ConfigurationSection msgs = config.getConfigurationSection("messages");
        if (msgs != null) {
            msgWarned     = msgs.getString("warned",         msgWarned);
            msgMuted      = msgs.getString("muted",          msgMuted);
            msgStillMuted = msgs.getString("still_muted",    msgStillMuted);
            msgKicked     = msgs.getString("kicked_message", msgKicked);
            msgBanned     = msgs.getString("banned_message", msgBanned);
            msgStaff      = msgs.getString("staff_notify",   msgStaff);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DATA PERSISTENCE
    // ─────────────────────────────────────────────────────────────────────────

    public void initData(File dataFolder) {
        dataFile = new File(dataFolder, "filter-data.yml");
        loadData();
    }

    private void loadData() {
        if (dataFile == null || !dataFile.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection vs = c.getConfigurationSection("violations");
        if (vs != null) {
            for (String uuidStr : vs.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection cats = vs.getConfigurationSection(uuidStr);
                    if (cats == null) continue;
                    Map<String, Integer> counts = new HashMap<>();
                    for (String cat : cats.getKeys(false)) counts.put(cat, cats.getInt(cat));
                    if (!counts.isEmpty()) violations.put(uuid, counts);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection ms = c.getConfigurationSection("mutes");
        if (ms != null) {
            for (String uuidStr : ms.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long expiry = ms.getLong(uuidStr);
                    if (expiry == -1L || expiry > System.currentTimeMillis()) filterMutes.put(uuid, expiry);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void saveData() {
        if (dataFile == null) return;
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Integer>> e : violations.entrySet()) {
            String base = "violations." + e.getKey();
            for (Map.Entry<String, Integer> cat : e.getValue().entrySet()) c.set(base + "." + cat.getKey(), cat.getValue());
        }
        for (Map.Entry<UUID, Long> e : filterMutes.entrySet()) c.set("mutes." + e.getKey(), e.getValue());
        try { c.save(dataFile); }
        catch (IOException ex) { Bukkit.getLogger().warning("[ChatFilter] Could not save filter-data.yml: " + ex.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NORMALIZATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pass 1: replace leet characters → plain ASCII letters.
     * Separators (spaces, punctuation) are preserved so that \b word boundaries
     * work correctly in the regex patterns.
     * q and x are NOT substituted here — the pass-1 patterns handle them explicitly.
     */
    private String normalizeLeet(String input) {
        String s = input.toLowerCase();
        s = s.replace("ph", "f");   // phuck → fuck
        s = s.replace("0", "o")
             .replace("1", "i")
             .replace("3", "e")
             .replace("4", "a")
             .replace("5", "s")
             .replace("6", "g")
             .replace("7", "t")
             .replace("8", "b")
             .replace("9", "g")
             .replace("@", "a")
             .replace("$", "s")
             .replace("!", "i")
             .replace("|", "i");
        return s;
    }

    /**
     * Pass 2: full normalization.
     * Leet substitution + q/x → g (handles niqqer, nixxer etc.) +
     * strip all non-letter characters (catches spacing/punctuation bypasses).
     * Result is a continuous lowercase string with no separators.
     */
    private String normalizeFull(String input) {
        String s = normalizeLeet(input);
        s = s.replace("q", "g")  // niqqer → nigger
             .replace("x", "g"); // nixxer → nigger, fxck → fgck (caught by pass 1 anyway)
        s = s.replaceAll("[^a-z]", ""); // remove all non-alpha
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CHECK
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the first WordCategory the message matches, or null if clean. */
    public WordCategory check(String message) {
        String p1 = normalizeLeet(message);
        String p2 = normalizeFull(message);
        for (WordCategory cat : categories) {
            if (cat.matches(p1, p2)) return cat;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIOLATION TRACKING
    // ─────────────────────────────────────────────────────────────────────────

    private int incrementViolations(UUID uuid, String category) {
        Map<String, Integer> counts = violations.computeIfAbsent(uuid, k -> new HashMap<>());
        int count = counts.getOrDefault(category, 0) + 1;
        counts.put(category, count);
        return count;
    }

    public int getViolations(UUID uuid, String category) {
        Map<String, Integer> m = violations.get(uuid);
        return m == null ? 0 : m.getOrDefault(category, 0);
    }

    public void resetViolations(UUID uuid) {
        violations.remove(uuid);
        saveData();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILTER MUTES  (separate from the staff plugin mutes)
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isFilterMuted(UUID uuid) {
        Long expiry = filterMutes.get(uuid);
        if (expiry == null) return false;
        if (expiry != -1L && System.currentTimeMillis() > expiry) {
            filterMutes.remove(uuid);
            return false;
        }
        return true;
    }

    public long getMuteExpiry(UUID uuid) {
        return filterMutes.getOrDefault(uuid, 0L);
    }

    private void applyFilterMute(UUID uuid, long durationMs) {
        long expiry = (durationMs == -1L) ? -1L : System.currentTimeMillis() + durationMs;
        filterMutes.put(uuid, expiry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ACTION APPLICATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Increments the violation counter for the player/category, then applies
     * the configured action for that violation level.
     * Must be called on the main server thread (kick/ban require it).
     */
    public void applyAction(Player player, WordCategory cat) {
        int violationNum = incrementViolations(player.getUniqueId(), cat.name);
        List<String> actions = cat.getActions();

        String action;
        if (violationNum <= actions.size()) {
            action = actions.get(violationNum - 1).trim().toLowerCase();
        } else {
            action = actions.get(actions.size() - 1).trim().toLowerCase();
        }

        notifyStaff(player, cat.name, action, violationNum);

        if (action.equals("warn")) {
            String msg = msgWarned
                .replace("{warning}", String.valueOf(violationNum))
                .replace("{word}", cat.name);
            player.sendMessage(colorize(msg));

        } else if (action.startsWith("mute:") || action.startsWith("mute ")) {
            String durStr = action.substring(5).trim();
            long durMs = parseDuration(durStr);
            if (durMs == 0L) durMs = 10 * 60_000L; // default 10 minutes if invalid
            applyFilterMute(player.getUniqueId(), durMs);
            String formatted = formatDuration(durMs);
            player.sendMessage(colorize(msgMuted.replace("{duration}", formatted)));

        } else if (action.equals("kick")) {
            player.kick(colorize(msgKicked));

        } else if (action.equals("ban")) {
            Bukkit.getBanList(BanList.Type.NAME)
                  .addBan(player.getName(), msgBanned, null, "ChatFilter");
            player.kick(colorize("&cYou have been banned.\n&7" + msgBanned));

        } else {
            // Unknown action → default to warn
            player.sendMessage(colorize(msgWarned
                .replace("{warning}", String.valueOf(violationNum))
                .replace("{word}", cat.name)));
        }
        saveData();
    }

    private void notifyStaff(Player player, String category, String action, int violationNum) {
        Component msg = colorize(msgStaff
            .replace("{player}", player.getName())
            .replace("{word}", category)
            .replace("{action}", action)
            .replace("{warning}", String.valueOf(violationNum)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("mv.chatfilter.admin")) p.sendMessage(msg);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Parses "30s" "5m" "2h" "7d" "perm". Returns duration ms, -1 for permanent, 0 for invalid. */
    public static long parseDuration(String s) {
        if (s == null || s.isEmpty()) return 0L;
        if (s.equalsIgnoreCase("perm") || s.equalsIgnoreCase("permanent")) return -1L;
        if (s.length() < 2) return 0L;
        char unit = Character.toLowerCase(s.charAt(s.length() - 1));
        long amount;
        try { amount = Long.parseLong(s.substring(0, s.length() - 1)); }
        catch (NumberFormatException e) { return 0L; }
        if (amount <= 0) return 0L;
        return switch (unit) {
            case 's' -> amount * 1_000L;
            case 'm' -> amount * 60_000L;
            case 'h' -> amount * 3_600_000L;
            case 'd' -> amount * 86_400_000L;
            default  -> 0L;
        };
    }

    /** Formats a remaining-time expiry epoch into a human-readable string. */
    public static String formatRemaining(long expiryEpoch) {
        if (expiryEpoch == -1L) return "Permanent";
        long ms = expiryEpoch - System.currentTimeMillis();
        if (ms <= 0) return "Expired";
        return formatDuration(ms);
    }

    /** Formats a duration in ms to a human-readable string. */
    public static String formatDuration(long durationMs) {
        if (durationMs == -1L) return "Permanent";
        long s = durationMs / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    public static Component colorize(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    public String getStillMutedMessage() { return msgStillMuted; }
}
