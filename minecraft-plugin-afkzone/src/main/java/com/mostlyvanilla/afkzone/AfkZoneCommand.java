package com.mostlyvanilla.afkzone;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class AfkZoneCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaAfkZone plugin;
    private final AfkZoneManager manager;
    private final EconomyBridge economy;

    private static final List<String> SUBS = Arrays.asList(
        "set1", "set2", "enable", "disable",
        "setcurrency", "setamount", "setinterval",
        "info", "reload"
    );

    public AfkZoneCommand(MostlyVanillaAfkZone plugin, AfkZoneManager manager, EconomyBridge economy) {
        this.plugin  = plugin;
        this.manager = manager;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou must be OP to use this command.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "set1" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cOnly players can set corners."); return true; }
                manager.setCorner1(p.getLocation());
                sender.sendMessage("§aAFK Zone corner 1 set at §f" + fmt(p.getLocation()) + "§a.");
            }
            case "set2" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cOnly players can set corners."); return true; }
                manager.setCorner2(p.getLocation());
                sender.sendMessage("§aAFK Zone corner 2 set at §f" + fmt(p.getLocation()) + "§a.");
            }
            case "enable" -> {
                manager.setEnabled(true);
                sender.sendMessage("§aAFK Zone §2enabled§a.");
            }
            case "disable" -> {
                manager.setEnabled(false);
                sender.sendMessage("§cAFK Zone §4disabled§c.");
            }
            case "setcurrency" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /afkzone setcurrency <currency>"); return true; }
                plugin.getConfig().set("currency", args[1]);
                plugin.saveConfig();
                sender.sendMessage("§aCurrency set to §f" + args[1] + "§a.");
            }
            case "setamount" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /afkzone setamount <amount>"); return true; }
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (amount < 0) { sender.sendMessage("§cAmount must be non-negative."); return true; }
                    plugin.getConfig().set("amount", amount);
                    plugin.saveConfig();
                    sender.sendMessage("§aReward amount set to §f" + amount + " " + economy.getCurrency() + "§a per interval.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: §f" + args[1]);
                }
            }
            case "setinterval" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /afkzone setinterval <seconds>"); return true; }
                try {
                    long seconds = Long.parseLong(args[1]);
                    if (seconds < 1) { sender.sendMessage("§cInterval must be at least 1 second."); return true; }
                    plugin.getConfig().set("interval-seconds", seconds);
                    plugin.saveConfig();
                    plugin.startTasks();
                    sender.sendMessage("§aReward interval set to §f" + seconds + "s§a. Tasks restarted.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: §f" + args[1]);
                }
            }
            case "info" -> {
                sender.sendMessage("§5§l=== AFK Zone Info ===");
                sender.sendMessage("§7Enabled:   " + (manager.isEnabled() ? "§a✔ yes" : "§c✘ no"));
                sender.sendMessage("§7World:     §f" + manager.getWorld());
                sender.sendMessage("§7Corner 1:  §f" + coord(manager.getMinX(), manager.getMinY(), manager.getMinZ()));
                sender.sendMessage("§7Corner 2:  §f" + coord(manager.getMaxX(), manager.getMaxY(), manager.getMaxZ()));
                sender.sendMessage("§7Currency:  §f" + economy.getCurrency());
                sender.sendMessage("§7Amount:    §f" + plugin.getConfig().getDouble("amount", 10.0) + " " + economy.getCurrency());
                sender.sendMessage("§7Interval:  §f" + plugin.getConfig().getLong("interval-seconds", 60) + "s");
            }
            case "reload" -> {
                plugin.reloadConfig();
                manager.loadFromConfig();
                plugin.startTasks();
                sender.sendMessage("§aAFK Zone config reloaded.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§5§l=== AFK Zone Commands (OP only) ===");
        sender.sendMessage("§7/afkzone set1           §f— Set corner 1 to your position");
        sender.sendMessage("§7/afkzone set2           §f— Set corner 2 to your position");
        sender.sendMessage("§7/afkzone enable         §f— Enable the zone");
        sender.sendMessage("§7/afkzone disable        §f— Disable the zone");
        sender.sendMessage("§7/afkzone setcurrency <name>   §f— Set currency type");
        sender.sendMessage("§7/afkzone setamount <amount>   §f— Set reward per interval");
        sender.sendMessage("§7/afkzone setinterval <secs>   §f— Set reward interval");
        sender.sendMessage("§7/afkzone info           §f— Show current zone settings");
        sender.sendMessage("§7/afkzone reload         §f— Reload config.yml");
    }

    private String fmt(org.bukkit.Location loc) {
        return (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ();
    }

    private String coord(double x, double y, double z) {
        return (int) x + ", " + (int) y + ", " + (int) z;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.isOp()) return List.of();
        if (args.length == 1) return SUBS;
        return List.of();
    }
}
