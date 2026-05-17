package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanManageCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label.toLowerCase()) {
            case "banlist" -> handleBanList(sender, args);
            case "bancheck" -> handleBanCheck(sender, args);
            case "unban" -> handleUnban(sender, args);
            default -> sender.sendMessage(Component.text("Unknown command.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleBanList(CommandSender sender, String[] args) {
        if (!isOpOrCanBan(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid page number.", NamedTextColor.RED));
                return;
            }
        }

        @SuppressWarnings("unchecked")
        Set<BanEntry<String>> entries = (Set<BanEntry<String>>) (Set<?>) Bukkit.getBanList(BanList.Type.NAME).getBanEntries();
        List<BanEntry<String>> list = new ArrayList<>(entries);

        int total = list.size();
        if (total == 0) {
            sender.sendMessage(Component.text("No banned players.", NamedTextColor.YELLOW));
            return;
        }

        int totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, total);

        sender.sendMessage(Component.text("━━━ Ban List (Page " + page + "/" + totalPages + ") ━━━", NamedTextColor.RED));
        for (int i = start; i < end; i++) {
            BanEntry<String> entry = list.get(i);
            String expiry = entry.getExpiration() == null
                ? "permanent"
                : DATE_FORMAT.format(entry.getExpiration());
            String reason = entry.getReason() != null ? entry.getReason() : "No reason";
            String banner = entry.getSource() != null ? entry.getSource() : "Unknown";

            sender.sendMessage(
                Component.text((i + 1) + ". ", NamedTextColor.GRAY)
                    .append(Component.text(entry.getBanTarget(), NamedTextColor.WHITE))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(reason, NamedTextColor.YELLOW))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Until: " + expiry, NamedTextColor.AQUA))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("By: " + banner, NamedTextColor.GRAY))
            );
        }
        if (totalPages > 1) {
            sender.sendMessage(Component.text("Use /banlist <page> to see more.", NamedTextColor.GRAY));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBanCheck(CommandSender sender, String[] args) {
        if (!isOpOrCanBan(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /bancheck <player>", NamedTextColor.RED));
            return;
        }
        String targetName = args[0];
        BanEntry<String> entry = (BanEntry<String>) Bukkit.getBanList(BanList.Type.NAME).getBanEntry(targetName);
        if (entry == null) {
            sender.sendMessage(Component.text(targetName + " is not banned.", NamedTextColor.GREEN));
            return;
        }
        String expiry = entry.getExpiration() == null
            ? "permanent"
            : DATE_FORMAT.format(entry.getExpiration());
        String reason = entry.getReason() != null ? entry.getReason() : "No reason";
        String banner = entry.getSource() != null ? entry.getSource() : "Unknown";
        String created = entry.getCreated() != null ? DATE_FORMAT.format(entry.getCreated()) : "Unknown";

        sender.sendMessage(Component.text("━━━ Ban Info: " + targetName + " ━━━", NamedTextColor.RED));
        sender.sendMessage(Component.text("  Reason: ", NamedTextColor.GRAY)
            .append(Component.text(reason, NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("  Banned by: ", NamedTextColor.GRAY)
            .append(Component.text(banner, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Date: ", NamedTextColor.GRAY)
            .append(Component.text(created, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Expires: ", NamedTextColor.GRAY)
            .append(Component.text(expiry, NamedTextColor.AQUA)));
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (!isOpOrCanBan(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /unban <player>", NamedTextColor.RED));
            return;
        }
        String targetName = args[0];
        if (!Bukkit.getBanList(BanList.Type.NAME).isBanned(targetName)) {
            sender.sendMessage(Component.text(targetName + " is not banned.", NamedTextColor.YELLOW));
            return;
        }
        Bukkit.getBanList(BanList.Type.NAME).pardon(targetName);
        sender.sendMessage(Component.text("Unbanned " + targetName + ".", NamedTextColor.GREEN));

        String staffName = (sender instanceof Player p) ? p.getName() : "Console";
        Component broadcast = Component.text("[Staff] ", NamedTextColor.DARK_GREEN)
            .append(Component.text(staffName, NamedTextColor.GREEN))
            .append(Component.text(" unbanned ", NamedTextColor.GRAY))
            .append(Component.text(targetName, NamedTextColor.WHITE));
        Bukkit.broadcast(broadcast, "mv.staff");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("bancheck") || label.equalsIgnoreCase("unban")) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private boolean isOpOrCanBan(CommandSender sender) {
        if (sender.isOp()) return true;
        if (!(sender instanceof Player player)) return false;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            String banRole = (String) rm.getClass().getMethod("getBanRole").invoke(rm);
            if (banRole == null) return false;
            return (boolean) rm.getClass().getMethod("canUseBan", UUID.class).invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}
