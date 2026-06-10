package com.mostlyvanilla.rtp.commands;

import com.mostlyvanilla.rtp.MostlyVanillaRtp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class RtpRangeCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaRtp plugin;

    public RtpRangeCommand(MostlyVanillaRtp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mostlyvanilla.rtp.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        int currentMin = plugin.getConfig().getInt("min-radius", 1000);
        int currentMax = plugin.getConfig().getInt("max-radius", 10000);

        // No args — show current range
        if (args.length == 0) {
            sender.sendMessage(Component.text("RTP range: ", NamedTextColor.YELLOW)
                .append(Component.text(currentMin, NamedTextColor.WHITE))
                .append(Component.text(" – ", NamedTextColor.GRAY))
                .append(Component.text(currentMax, NamedTextColor.WHITE))
                .append(Component.text(" blocks", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Usage: /rtprange <min> <max>", NamedTextColor.GRAY));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /rtprange <min> <max>", NamedTextColor.RED));
            return true;
        }

        int newMin, newMax;
        try {
            newMin = Integer.parseInt(args[0]);
            newMax = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Both values must be whole numbers.", NamedTextColor.RED));
            return true;
        }

        if (newMin < 0 || newMax < 0) {
            sender.sendMessage(Component.text("Values must be positive.", NamedTextColor.RED));
            return true;
        }

        if (newMin >= newMax) {
            sender.sendMessage(Component.text("Min must be less than max.", NamedTextColor.RED));
            return true;
        }

        plugin.getConfig().set("min-radius", newMin);
        plugin.getConfig().set("max-radius", newMax);
        plugin.saveConfig();

        sender.sendMessage(Component.text("RTP range updated: ", NamedTextColor.GREEN)
            .append(Component.text(newMin, NamedTextColor.WHITE))
            .append(Component.text(" – ", NamedTextColor.GRAY))
            .append(Component.text(newMax, NamedTextColor.WHITE))
            .append(Component.text(" blocks", NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        int currentMin = plugin.getConfig().getInt("min-radius", 1000);
        int currentMax = plugin.getConfig().getInt("max-radius", 10000);
        if (args.length == 1) return List.of(String.valueOf(currentMin));
        if (args.length == 2) return List.of(String.valueOf(currentMax));
        return List.of();
    }
}
