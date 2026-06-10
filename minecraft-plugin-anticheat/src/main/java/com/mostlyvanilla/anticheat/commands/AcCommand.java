package com.mostlyvanilla.anticheat.commands;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AcCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaAnticheat plugin;

    public AcCommand(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.ac.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendUsage(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "violations" -> {
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                        return true;
                    }
                    showViolations(sender, target.getUniqueId(), target.getName());
                } else {
                    // Show all players with violations
                    boolean any = false;
                    for (Map.Entry<UUID, PlayerData> entry : plugin.getAllData().entrySet()) {
                        PlayerData data = entry.getValue();
                        if (data.totalViolations() > 0) {
                            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                            if (name == null) name = entry.getKey().toString();
                            showViolations(sender, entry.getKey(), name);
                            any = true;
                        }
                    }
                    if (!any) sender.sendMessage(Component.text("No active violations.", NamedTextColor.GREEN));
                }
            }
            case "reset" -> {
                if (args.length < 2) { sendUsage(sender); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found (must be online).", NamedTextColor.RED));
                    return true;
                }
                plugin.getData(target.getUniqueId()).resetViolations();
                sender.sendMessage(Component.text("Violations reset for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "status" -> {
                sender.sendMessage(Component.text("[AC] Status:", NamedTextColor.GOLD));
                sender.sendMessage(Component.text(" Anti-xray: " +
                        (plugin.getConfig().getBoolean("anti-xray.enabled") ? "ON" : "OFF"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(" Speed: " +
                        (plugin.getConfig().getBoolean("checks.speed.enabled") ? "ON" : "OFF"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(" Fly: " +
                        (plugin.getConfig().getBoolean("checks.fly.enabled") ? "ON" : "OFF"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(" Reach: " +
                        (plugin.getConfig().getBoolean("checks.reach.enabled") ? "ON" : "OFF"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(" KillAura: " +
                        (plugin.getConfig().getBoolean("checks.killaura.enabled") ? "ON" : "OFF"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(" XrayDetector: " +
                        (plugin.getConfig().getBoolean("xray-detector.enabled") ? "ON" : "OFF"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(" Tracked players: " +
                        plugin.getAllData().size(), NamedTextColor.GRAY));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getAntiXrayEngine().reload();
                sender.sendMessage(Component.text("[AC] Config reloaded.", NamedTextColor.GREEN));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void showViolations(CommandSender sender, UUID uuid, String name) {
        PlayerData data = plugin.getData(uuid);
        sender.sendMessage(Component.text("[AC] " + name + " violations:", NamedTextColor.GOLD));
        if (data.violations.isEmpty()) {
            sender.sendMessage(Component.text("  None", NamedTextColor.GRAY));
            return;
        }
        for (Map.Entry<String, Integer> e : data.violations.entrySet()) {
            sender.sendMessage(Component.text("  " + e.getKey() + ": " + e.getValue(), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("  Total: " + data.totalViolations(), NamedTextColor.YELLOW));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("/ac violations [player]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/ac reset <player>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/ac status", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/ac reload", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List.of("violations", "reset", "status", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .forEach(completions::add);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("violations") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
            }
        }
        return completions;
    }
}
