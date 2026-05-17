package com.mostlyvanilla.home.commands;

import com.mostlyvanilla.home.Home;
import com.mostlyvanilla.home.HomeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homeManager;

    public DelHomeCommand(HomeManager homeManager) { this.homeManager = homeManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /delhome <name>", NamedTextColor.RED));
            return true;
        }
        if (homeManager.deleteHome(player.getUniqueId(), args[0])) {
            player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
                .append(Component.text(args[0], NamedTextColor.GOLD))
                .append(Component.text(" deleted.", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("No home named \"" + args[0] + "\" was found.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        String partial = args[0].toLowerCase();
        return homeManager.getHomes(player.getUniqueId()).stream()
            .map(Home::getName)
            .filter(n -> n.toLowerCase().startsWith(partial))
            .collect(Collectors.toList());
    }
}
