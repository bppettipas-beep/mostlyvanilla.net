package com.mostlyvanilla.roles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

public class TabManager implements Listener {

    private final MostlyVanillaRoles plugin;
    private int tick = 0;
    private Objective pingObjective;

    // ── Animations (replicating animations.yml) ──────────────────────────────

    private static final String[] SEP = {
        "&2◆&2▬▬▬▬&a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬&2▬▬▬▬&2◆",
        "&2◆&a▬▬▬▬▬&2▬▬▬▬▬&a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬&2▬&2◆",
        "&2◆&a▬▬▬▬▬▬▬▬▬▬&2▬▬▬▬▬&a▬▬▬▬▬▬▬▬▬▬&2▬▬&2◆",
        "&2◆&a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬&2▬▬▬▬▬&a▬▬▬▬▬&2▬▬&2◆",
        "&2◆&a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬&2▬▬▬▬▬▬▬▬&2◆",
        "&2◆&a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬&2▬▬▬▬▬&a▬▬▬▬▬&2▬▬&2◆",
        "&2◆&a▬▬▬▬▬▬▬▬▬▬&2▬▬▬▬▬&a▬▬▬▬▬▬▬▬▬▬&2▬▬&2◆",
        "&2◆&a▬▬▬▬▬&2▬▬▬▬▬&a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬&2▬&2◆"
    };

    private static final String[] TITLE = {
        "&a&l✦ &a&lM&2&lO&a&lS&2&lT&a&lL&2&lY &a&lV&2&lA&a&lN&2&lI&a&lL&2&lL&a&lA &a&l✦",
        "&2&l✦ &2&lM&a&lO&2&lS&a&lT&2&lL&a&lY &2&lV&a&lA&2&lN&a&lI&2&lL&a&lL&2&lA &2&l✦",
        "&a&l✦ &a&lM&2&lO&a&lS&2&lT&a&lL&2&lY &a&lV&2&lA&a&lN&2&lI&a&lL&2&lL&a&lA &a&l✦",
        "&2&l✦ &2&lM&a&lO&2&lS&a&lT&2&lL&a&lY &2&lV&a&lA&2&lN&a&lI&2&lL&a&lL&2&lA &2&l✦",
        "&f&l★ MOSTLY VANILLA ★",
        "&a&l❯ &a&lM&2&lO&a&lS&2&lT&a&lL&2&lY &a&lV&2&lA&a&lN&2&lI&a&lL&2&lL&a&lA &a&l❮",
        "&2&l❯ &2&lM&a&lO&2&lS&a&lT&2&lL&a&lY &2&lV&a&lA&2&lN&a&lI&2&lL&a&lL&2&lA &2&l❮",
        "&f&l★ MOSTLY VANILLA ★"
    };

    private static final String[] TAGLINES = {
        "&7&o✦ Survival &8• &7&oCommunity &8• &7&oVanilla &7&o✦",
        "&7&o★ No nonsense. Just Minecraft. ★",
        "&7&o♦ Where every block matters. ♦",
        "&7&o⚔ Build. Explore. Survive. ⚔",
        "&7&o♥ The best vanilla experience. ♥",
        "&7&o★ Mostly vanilla, fully adventure. ★",
        "&7&o◆ Join the community today! ◆",
        "&7&o✦ Every block tells a story. ✦"
    };

    private static final String[] DOT = { "&a★", "&2✦", "&a◆", "&2★" };

    // Intervals in ticks (1 tick ≈ 50 ms at 20 TPS)
    private static final int SEP_INT     = 1;   // 50 ms
    private static final int TITLE_INT   = 6;   // 300 ms
    private static final int TAGLINE_INT = 80;  // 4 000 ms
    private static final int DOT_INT     = 14;  // 700 ms

    public TabManager(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Header/footer animation every tick
        new BukkitRunnable() {
            @Override public void run() {
                tick++;
                int online = Bukkit.getOnlinePlayers().size();
                Component header = buildHeader();
                Component footer = buildFooter(online);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendPlayerListHeaderAndFooter(header, footer);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Ping objective update every second
        new BukkitRunnable() {
            @Override public void run() { updatePing(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void setupPingObjective() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        pingObjective = sb.getObjective("mvping");
        if (pingObjective == null) {
            pingObjective = sb.registerNewObjective(
                "mvping", "dummy",
                Component.text("Ping").color(NamedTextColor.GRAY),
                RenderType.INTEGER
            );
        }
        pingObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
    }

    private void updatePing() {
        if (pingObjective == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            pingObjective.getScore(p.getName()).setScore(p.getPing());
        }
    }

    public void updateTabName(Player player) {
        String prefix = plugin.getRoleManager().getPrefix(player.getUniqueId());
        Component name;
        if (prefix != null) {
            Component prefixComp = prefix.contains("<")
                ? MiniMessage.miniMessage().deserialize(prefix)
                : LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
            name = Component.empty()
                .append(prefixComp)
                .append(Component.text(" ").decoration(TextDecoration.BOLD, TextDecoration.State.FALSE))
                .append(Component.text(player.getName())
                    .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)
                    .color(NamedTextColor.WHITE));
        } else {
            name = Component.text(player.getName()).color(NamedTextColor.WHITE);
        }
        player.playerListName(name);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override public void run() {
                Player p = event.getPlayer();
                if (!p.isOnline()) return;
                updateTabName(p);
                if (pingObjective != null) pingObjective.getScore(p.getName()).setScore(p.getPing());
            }
        }.runTaskLater(plugin, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (pingObjective != null) pingObjective.getScore(event.getPlayer().getName()).resetScore();
    }

    // ── Header / footer builders ─────────────────────────────────────────────

    private Component buildHeader() {
        String sep     = SEP    [(tick / SEP_INT)     % SEP.length];
        String title   = TITLE  [(tick / TITLE_INT)   % TITLE.length];
        String tagline = TAGLINES[(tick / TAGLINE_INT) % TAGLINES.length];
        return l(sep)
            .append(Component.newline()).append(Component.space())
            .append(Component.newline()).append(l(title))
            .append(Component.newline()).append(l(tagline))
            .append(Component.newline()).append(Component.space())
            .append(Component.newline()).append(l(sep));
    }

    private Component buildFooter(int online) {
        String sep = SEP[(tick / SEP_INT) % SEP.length];
        String dot = DOT[(tick / DOT_INT) % DOT.length];
        return l(sep)
            .append(Component.newline()).append(Component.space())
            .append(Component.newline()).append(l(" " + dot + " &7Players Online: &a" + online + " &8/ &f45"))
            .append(Component.newline()).append(l(" &8▸ &7mostlyvanilla.net"))
            .append(Component.newline()).append(Component.space())
            .append(Component.newline()).append(l(sep));
    }

    private Component l(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
