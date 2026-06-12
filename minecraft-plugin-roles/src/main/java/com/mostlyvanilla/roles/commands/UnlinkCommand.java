package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.ApiClient;
import com.mostlyvanilla.roles.MostlyVanillaRoles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.stream.Collectors;

public class UnlinkCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaRoles plugin;

    public UnlinkCommand(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mostlyvanilla.roles.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /unlink <player>", NamedTextColor.RED));
            return true;
        }

        ApiClient api = plugin.getApiClient();
        if (api == null) {
            sender.sendMessage(Component.text("Discord linking is not configured on this server.", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        @SuppressWarnings("deprecation")
        var offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            sender.sendMessage(Component.text("Player '" + targetName + "' has never joined this server.", NamedTextColor.RED));
            return true;
        }

        String uuid = offlinePlayer.getUniqueId().toString();
        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean ok = api.unlinkPlayer(uuid);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            sender.sendMessage(Component.text("Unlinked " + name + " from Discord.", NamedTextColor.GREEN));
                            var online = Bukkit.getPlayer(offlinePlayer.getUniqueId());
                            if (online != null) {
                                online.sendMessage(Component.text("Your Discord account has been unlinked by a staff member.", NamedTextColor.YELLOW));
                            }
                        } else {
                            sender.sendMessage(Component.text(name + " is not linked to Discord.", NamedTextColor.RED));
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mostlyvanilla.roles.admin")) return List.of();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .filter(n -> n.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
