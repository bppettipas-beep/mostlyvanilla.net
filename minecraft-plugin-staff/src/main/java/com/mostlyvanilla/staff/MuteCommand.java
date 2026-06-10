package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private final MuteManager muteManager;

    public MuteCommand(MuteManager muteManager) {
        this.muteManager = muteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isUnmute = command.getName().equalsIgnoreCase("unmute");

        if (!canMute(sender)) {
            sender.sendMessage(Component.text("You don't have permission to mute players.", NamedTextColor.RED));
            return true;
        }

        if (isUnmute) {
            if (args.length < 1) {
                sender.sendMessage(Component.text("Usage: /unmute <player>", NamedTextColor.RED));
                return true;
            }
            doUnmute(sender, args[0]);
            return true;
        }

        // /mute <player> <duration> [reason...]
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mute <player> <duration> [reason]", NamedTextColor.RED));
            sender.sendMessage(Component.text("  Duration: 30s, 5m, 2h, 7d, perm", NamedTextColor.GRAY));
            return true;
        }

        long durationMs = MuteManager.parseDuration(args[1]);
        if (durationMs == 0L) {
            sender.sendMessage(Component.text("Invalid duration. Use: 30s, 5m, 2h, 7d, perm", NamedTextColor.RED));
            return true;
        }

        String reason = args.length > 2
            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
            : "No reason given.";

        doMute(sender, args[0], durationMs, reason);
        return true;
    }

    private void doMute(CommandSender sender, String targetName, long durationMs, String reason) {
        Player target = Bukkit.getPlayerExact(targetName);
        UUID uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId();
            name = target.getName();
        } else {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(Component.text("Player '" + targetName + "' not found.", NamedTextColor.RED));
                return;
            }
            uuid = offline.getUniqueId();
            name = offline.getName() != null ? offline.getName() : targetName;
        }

        long expiresAt = durationMs == -1L ? -1L : System.currentTimeMillis() + durationMs;
        String byName  = (sender instanceof Player p) ? p.getName() : "Console";
        muteManager.mute(uuid, expiresAt, reason, byName);

        String durationStr = MuteManager.formatDuration(durationMs);
        sender.sendMessage(Component.text("✔ Muted ", NamedTextColor.GREEN)
            .append(Component.text(name, NamedTextColor.WHITE))
            .append(Component.text(" for " + durationStr + ". Reason: " + reason, NamedTextColor.GREEN)));

        if (target != null) {
            target.sendMessage(
                Component.text("You have been muted.", NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text("Duration: ", NamedTextColor.GRAY))
                    .append(Component.text(durationStr, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Reason: ", NamedTextColor.GRAY))
                    .append(Component.text(reason, NamedTextColor.WHITE))
            );
        }

        Bukkit.getLogger().info("[Mute] " + byName + " muted " + name + " for " + durationStr + ". Reason: " + reason);
    }

    private void doUnmute(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        UUID uuid;
        String name;

        if (target != null) {
            uuid = target.getUniqueId();
            name = target.getName();
        } else {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(Component.text("Player '" + targetName + "' not found.", NamedTextColor.RED));
                return;
            }
            uuid = offline.getUniqueId();
            name = offline.getName() != null ? offline.getName() : targetName;
        }

        boolean staffMuted  = muteManager.isMuted(uuid);
        boolean filterMuted = isFilterMuted(uuid);

        if (!staffMuted && !filterMuted) {
            sender.sendMessage(Component.text(name + " is not muted.", NamedTextColor.YELLOW));
            return;
        }

        if (staffMuted)  muteManager.unmute(uuid);
        if (filterMuted) clearFilterMute(uuid);

        sender.sendMessage(Component.text("✔ Unmuted ", NamedTextColor.GREEN)
            .append(Component.text(name, NamedTextColor.WHITE))
            .append(Component.text(".", NamedTextColor.GREEN)));

        if (target != null)
            target.sendMessage(Component.text("You have been unmuted.", NamedTextColor.GREEN));
    }

    private boolean isFilterMuted(UUID uuid) {
        Plugin fp = Bukkit.getPluginManager().getPlugin("MostlyVanillaChatFilter");
        if (fp == null) return false;
        try {
            Object fm = fp.getClass().getMethod("getFilterManager").invoke(fp);
            return (boolean) fm.getClass().getMethod("isFilterMuted", UUID.class).invoke(fm, uuid);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[Mute] Could not check filter mute", e);
            return false;
        }
    }

    private void clearFilterMute(UUID uuid) {
        Plugin fp = Bukkit.getPluginManager().getPlugin("MostlyVanillaChatFilter");
        if (fp == null) return;
        try {
            Object fm = fp.getClass().getMethod("getFilterManager").invoke(fp);
            fm.getClass().getMethod("removeFilterMute", UUID.class).invoke(fm, uuid);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[Mute] Could not clear filter mute", e);
        }
    }

    private boolean canMute(CommandSender sender) {
        if (!(sender instanceof Player player)) return sender.isOp();
        if (player.isOp()) return true;
        if (player.hasPermission("mv.mute")) return true;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            return (boolean) rm.getClass().getMethod("canUseMute", UUID.class).invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean isUnmute = command.getName().equalsIgnoreCase("unmute");
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        if (!isUnmute && args.length == 2) {
            return List.of("30s", "5m", "1h", "12h", "1d", "7d", "perm");
        }
        return List.of();
    }
}
