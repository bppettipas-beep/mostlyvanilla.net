package com.mostlyvanilla.scoreboard;

import com.mostlyvanilla.scoreboard.listeners.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MostlyVanillaScoreboard extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BoardManager boardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        boardManager = new BoardManager(this);
        boardManager.start();

        getServer().getPluginManager().registerEvents(new PlayerListener(boardManager), this);
        getCommand("sb").setExecutor(this);
        getCommand("sb").setTabCompleter(this);

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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        // /sb  →  toggle
        if (args.length == 0) {
            boardManager.toggle(player);
            return true;
        }

        // /sb currency <1|2> <name>
        if (args[0].equalsIgnoreCase("currency")) {
            if (!sender.hasPermission("scoreboard.admin")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: §e/sb currency <1|2> <currency name>");
                return true;
            }
            int slot;
            try {
                slot = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cSlot must be §e1 §cor §e2§c.");
                return true;
            }
            if (slot != 1 && slot != 2) {
                sender.sendMessage("§cSlot must be §e1 §cor §e2§c.");
                return true;
            }
            String name = args[2];
            boardManager.setCurrency(slot, name);
            sender.sendMessage("§aScoreboard currency §e" + slot + " §aset to §e" + name + "§a.");
            return true;
        }

        // /sb help  (or unknown subcommand)
        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(Arrays.asList("currency"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("currency")) {
            return filterPrefix(Arrays.asList("1", "2"), args[1]);
        }
        // arg 3 = currency name — we can't easily enumerate them here, let the player type it
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lScoreboard Commands:");
        sender.sendMessage("§e/sb §7— Toggle the scoreboard on/off");
        sender.sendMessage("§e/sb currency <1|2> <name> §7— Change a displayed currency §8(op)");
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        List<String> result = new java.util.ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }
}
