package com.mostlyvanilla.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BoardManager {

    private static final String[] TITLE_FRAMES = {
        "§a§lM§2§lO§a§lS§2§lT§a§lL§2§lY §a§lV§2§lA§a§lN§2§lI§a§lL§2§lL§a§lA",
        "§2§lM§a§lO§2§lS§a§lT§2§lL§a§lY §2§lV§a§lA§2§lN§a§lI§2§lL§a§lL§2§lA",
        "§f§lMOSTLY VANILLA",
        "§a§lM§2§lO§a§lS§2§lT§a§lL§2§lY §a§lV§2§lA§a§lN§2§lI§a§lL§2§lL§a§lA",
        "§2§lM§a§lO§2§lS§a§lT§2§lL§a§lY §2§lV§a§lA§2§lN§a§lI§2§lL§a§lL§2§lA",
        "§f§lMOSTLY VANILLA"
    };

    private static final String[] SEP_FRAMES = {
        "§2▬▬▬▬▬§a▬▬▬▬▬▬▬▬▬▬▬",
        "§a▬▬§2▬▬▬▬▬§a▬▬▬▬▬▬▬▬",
        "§a▬▬▬▬▬§2▬▬▬▬▬§a▬▬▬▬▬",
        "§a▬▬▬▬▬▬▬▬▬▬§2▬▬▬▬▬§a▬",
        "§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§2▬▬",
        "§a▬▬▬▬▬▬▬▬▬▬§2▬▬▬▬▬§a▬",
        "§a▬▬▬▬▬§2▬▬▬▬▬§a▬▬▬▬▬",
        "§a▬▬§2▬▬▬▬▬§a▬▬▬▬▬▬▬▬"
    };

    private final Plugin plugin;
    private String currency1;
    private String currency2;
    private final String serverAddress;
    private final int sepAnimInterval;
    private final int titleAnimInterval;
    private final int dataRefreshInterval;

    private final Map<UUID, PlayerBoard> boards = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();

    private BukkitTask task;
    private int titleFrame = 0;
    private int sepFrame   = 0;
    private int titleTick  = 0;
    private int sepTick    = 0;
    private int dataTick   = 0;

    public BoardManager(Plugin plugin) {
        this.plugin              = plugin;
        this.currency1           = plugin.getConfig().getString("currency-1", "diamonds");
        this.currency2           = plugin.getConfig().getString("currency-2", "emeralds");
        this.serverAddress       = plugin.getConfig().getString("server-address", "mostlyvanilla.net");
        this.sepAnimInterval     = plugin.getConfig().getInt("separator-anim-interval", 3);
        this.titleAnimInterval   = plugin.getConfig().getInt("title-anim-interval", 8);
        this.dataRefreshInterval = plugin.getConfig().getInt("data-refresh-interval", 40);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Map.Entry<UUID, PlayerBoard> entry : boards.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            entry.getValue().remove(p);
        }
        boards.clear();
    }

    private void tick() {
        sepTick++;
        titleTick++;
        dataTick++;

        boolean advanceSep   = sepTick   >= sepAnimInterval;
        boolean advanceTitle = titleTick >= titleAnimInterval;
        boolean refreshData  = dataTick  >= dataRefreshInterval;

        if (advanceSep)   { sepTick   = 0; sepFrame   = (sepFrame   + 1) % SEP_FRAMES.length; }
        if (advanceTitle) { titleTick = 0; titleFrame = (titleFrame + 1) % TITLE_FRAMES.length; }
        if (refreshData)  { dataTick  = 0; }

        if (!advanceSep && !advanceTitle && !refreshData) return;

        String title = TITLE_FRAMES[titleFrame];
        String sep   = SEP_FRAMES[sepFrame];

        for (Map.Entry<UUID, PlayerBoard> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            entry.getValue().update(player, title, sep, refreshData, currency1, currency2, serverAddress);
        }
    }

    public void addPlayer(Player player) {
        if (hidden.contains(player.getUniqueId())) return;
        PlayerBoard board = new PlayerBoard(player);
        boards.put(player.getUniqueId(), board);
        board.update(player, TITLE_FRAMES[titleFrame], SEP_FRAMES[sepFrame],
                true, currency1, currency2, serverAddress);
    }

    public void removePlayer(Player player) {
        PlayerBoard board = boards.remove(player.getUniqueId());
        if (board != null) board.remove(player);
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (hidden.contains(uuid)) {
            hidden.remove(uuid);
            addPlayer(player);
            player.sendMessage("§aScoreboard §2enabled§a.");
        } else {
            hidden.add(uuid);
            removePlayer(player);
            player.sendMessage("§cScoreboard disabled. Run §e/sb §cto bring it back.");
        }
    }

    /** Changes a currency slot (1 or 2), saves to config, and forces an immediate refresh. */
    public void setCurrency(int slot, String name) {
        if (slot == 1) {
            currency1 = name;
            plugin.getConfig().set("currency-1", name);
        } else {
            currency2 = name;
            plugin.getConfig().set("currency-2", name);
        }
        plugin.saveConfig();
        // Trigger a data refresh on the very next tick
        dataTick = dataRefreshInterval;
    }

    public String getCurrency1() { return currency1; }
    public String getCurrency2() { return currency2; }
}
