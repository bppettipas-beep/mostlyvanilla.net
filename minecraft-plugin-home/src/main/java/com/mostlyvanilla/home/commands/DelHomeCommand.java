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
            player.sendMessage(Component.text("Usage: /delhome <name|number>", NamedTextColor.RED));
            return true;
        }

        String deletedName;
        try {
            int index = Integer.parseInt(args[0]);
            deletedName = homeManager.deleteHomeByIndex(player.getUniqueId(), index);
            if (deletedName == null) {
                player.sendMessage(Component.text("No home at position " + index + ".", NamedTextColor.RED));
                return true;
            }
        } catch (NumberFormatException e) {
            if (!homeManager.deleteHome(player.getUniqueId(), args[0])) {
                player.sendMessage(Component.text("No home named \"" + args[0] + "\" was found.", NamedTextColor.RED));
                return true;
            }
            deletedName = args[0];
        }

        player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
            .append(Component.text(deletedName, NamedTextColor.GOLD))
            .append(Component.text(" deleted.", NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        String partial = args[0].toLowerCase();
        List<Home> playerHomes = homeManager.getHomes(player.getUniqueId());
        List<String> completions = new java.util.ArrayList<>();
        for (int i = 0; i < playerHomes.size(); i++) {
            String num = String.valueOf(i + 1);
            if (num.startsWith(partial)) completions.add(num);
        }
        playerHomes.stream()
            .map(Home::getName)
            .filter(n -> n.toLowerCase().startsWith(partial))
            .forEach(completions::add);
        return completions;
    }
}
