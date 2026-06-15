package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TransferCommand implements CommandExecutor, TabCompleter {

    private final TransferManager manager;

    public TransferCommand(TransferManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("You must be an op to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /transfer <from> <to>", NamedTextColor.RED));
            return true;
        }

        @SuppressWarnings("deprecation") OfflinePlayer from = Bukkit.getOfflinePlayer(args[0]);
        @SuppressWarnings("deprecation") OfflinePlayer to   = Bukkit.getOfflinePlayer(args[1]);

        if (!from.hasPlayedBefore()) {
            player.sendMessage(Component.text("Player '" + args[0] + "' has never joined this server.", NamedTextColor.RED));
            return true;
        }
        if (!to.hasPlayedBefore()) {
            player.sendMessage(Component.text("Player '" + args[1] + "' has never joined this server.", NamedTextColor.RED));
            return true;
        }
        if (from.getUniqueId().equals(to.getUniqueId())) {
            player.sendMessage(Component.text("Cannot transfer to the same player.", NamedTextColor.RED));
            return true;
        }

        manager.openStep1(player, from, to);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 || args.length == 2) {
            String prefix = args[args.length - 1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
