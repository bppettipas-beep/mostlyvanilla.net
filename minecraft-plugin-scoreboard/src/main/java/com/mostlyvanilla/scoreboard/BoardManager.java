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

    static final String TITLE = "§a§l✦ §f§lMOSTLY §a§lVANILLA §a§l✦";

    private final Plugin plugin;
    private String currency1;
    private String currency2;
    private final String serverAddress;
    private final int dataRefreshInterval;

    private final Map<UUID, PlayerBoard> boards = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();

    private BukkitTask task;
    private int dataTick = 0;

    public BoardManager(Plugin plugin) {
        this.plugin              = plugin;
        this.currency1           = plugin.getConfig().getString("currency-1", "diamonds");
        this.currency2           = plugin.getConfig().getString("currency-2", "emeralds");
        this.serverAddress       = plugin.getConfig().getString("server-address", "mostlyvanilla.net");
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
        dataTick++;
        if (dataTick < dataRefreshInterval) return;
        dataTick = 0;

        for (Map.Entry<UUID, PlayerBoard> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            entry.getValue().update(player, currency1, currency2, serverAddress);
        }
    }

    public void addPlayer(Player player) {
        if (hidden.contains(player.getUniqueId())) return;
        PlayerBoard board = new PlayerBoard(player);
        boards.put(player.getUniqueId(), board);
        board.update(player, currency1, currency2, serverAddress);
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
        dataTick = dataRefreshInterval;
    }

    public String getCurrency1() { return currency1; }
    public String getCurrency2() { return currency2; }
}
