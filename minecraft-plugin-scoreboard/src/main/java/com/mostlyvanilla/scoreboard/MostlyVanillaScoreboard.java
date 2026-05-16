package com.mostlyvanilla.scoreboard;

import com.mostlyvanilla.scoreboard.listeners.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaScoreboard extends JavaPlugin {

    private BoardManager boardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        boardManager = new BoardManager(this);
        boardManager.start();

        getServer().getPluginManager().registerEvents(new PlayerListener(boardManager), this);

        getCommand("sb").setExecutor((CommandSender sender, Command cmd, String label, String[] args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            boardManager.toggle((Player) sender);
            return true;
        });

        // Handle reloads — add boards for players already online
        for (Player p : Bukkit.getOnlinePlayers()) {
            boardManager.addPlayer(p);
        }

        getLogger().info("MostlyVanillaScoreboard enabled!");
    }

    @Override
    public void onDisable() {
        if (boardManager != null) boardManager.stop();
        getLogger().info("MostlyVanillaScoreboard disabled!");
    }
}
