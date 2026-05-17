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

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homeManager;

    public SetHomeCommand(HomeManager homeManager) { this.homeManager = homeManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        String name = args.length > 0 ? args[0] : "home";
        if (!name.matches("[a-zA-Z0-9_\\-]+")) {
            player.sendMessage(Component.text("Home name can only contain letters, numbers, underscores and hyphens.", NamedTextColor.RED));
            return true;
        }
        if (name.length() > 16) {
            player.sendMessage(Component.text("Home name cannot exceed 16 characters.", NamedTextColor.RED));
            return true;
        }
        if (homeManager.setHome(player, name)) {
            player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
                .append(Component.text(name, NamedTextColor.GOLD))
                .append(Component.text(" set!", NamedTextColor.GREEN)));
        } else {
            int limit = homeManager.getHomeLimit(player);
            player.sendMessage(Component.text("You have reached your home limit (" + limit + ").", NamedTextColor.RED));
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
