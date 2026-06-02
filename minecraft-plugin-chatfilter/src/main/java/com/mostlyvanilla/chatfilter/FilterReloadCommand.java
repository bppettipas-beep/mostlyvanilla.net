package com.mostlyvanilla.chatfilter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class FilterReloadCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaChatFilter plugin;
    private final FilterManager filterManager;

    public FilterReloadCommand(MostlyVanillaChatFilter plugin, FilterManager filterManager) {
        this.plugin        = plugin;
        this.filterManager = filterManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mv.chatfilter.admin")) {
            sender.sendMessage(FilterManager.colorize("&cNo permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(FilterManager.colorize("&7Usage: &f/" + label + " reload"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            filterManager.loadConfig(plugin.getConfig());
            sender.sendMessage(FilterManager.colorize("&aChatFilter config reloaded."));
            return true;
        }
        sender.sendMessage(FilterManager.colorize("&7Unknown subcommand. Usage: &f/" + label + " reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("reload");
        return List.of();
    }
}
