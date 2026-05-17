package com.mostlyvanilla.rtp.commands;

import com.mostlyvanilla.rtp.RtpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.stream.Collectors;

public class DimensionDisableCommand implements CommandExecutor, TabCompleter {

    private final RtpManager rtpManager;

    public DimensionDisableCommand(RtpManager rtpManager) { this.rtpManager = rtpManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.rtp.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /dimensiondisable <world>", NamedTextColor.RED));
            return true;
        }
        String worldName = args[0];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(Component.text("World \"" + worldName + "\" not found.", NamedTextColor.RED));
            return true;
        }
        boolean nowDisabled = rtpManager.toggleDisabled(worldName);
        if (nowDisabled) {
            sender.sendMessage(Component.text("RTP disabled for ", NamedTextColor.RED)
                .append(Component.text(RtpManager.displayName(world), NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.RED)));
        } else {
            sender.sendMessage(Component.text("RTP enabled for ", NamedTextColor.GREEN)
                .append(Component.text(RtpManager.displayName(world), NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GREEN)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        String partial = args[0].toLowerCase();
        return Bukkit.getWorlds().stream()
            .map(World::getName)
            .filter(n -> n.toLowerCase().startsWith(partial))
            .collect(Collectors.toList());
    }
}
