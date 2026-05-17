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
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public class BanCommand implements CommandExecutor, TabCompleter {

    private final BanReasonManager banReasonManager;
    private final WipeManager      wipeManager;

    public BanCommand(BanReasonManager banReasonManager, WipeManager wipeManager) {
        this.banReasonManager = banReasonManager;
        this.wipeManager      = wipeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!canUseBan(staff)) {
            staff.sendMessage(Component.text("You don't have permission to ban players.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            staff.sendMessage(Component.text("Usage: /ban <player> <reason>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        String reasonArg  = args[1].toLowerCase();

        BanReasonManager.BanReason preset = banReasonManager.getReason(reasonArg);

        String  banReason;
        long    durationMs;
        boolean doWipe;

        if (preset != null) {
            banReason  = preset.id();
            durationMs = preset.durationMs();
            doWipe     = preset.wipe();
        } else {
            // Free-text reason — permanent, no wipe
            banReason  = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            durationMs = -1L;
            doWipe     = false;
        }

        Date expiry = durationMs == -1L ? null : new Date(System.currentTimeMillis() + durationMs);

        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
            .addBan(targetName, banReason, expiry, staff.getName());

        Player online = Bukkit.getPlayer(targetName);
        String durationStr = durationMs == -1L
            ? "permanently"
            : "for " + BanReasonManager.formatDuration(durationMs);

        if (online != null) {
            String kickMsg = durationMs == -1L
                ? "You have been permanently banned.\nReason: " + banReason
                : "You have been banned for " + BanReasonManager.formatDuration(durationMs) + ".\nReason: " + banReason;
            online.kick(Component.text(kickMsg, NamedTextColor.DARK_RED));

            if (doWipe) {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(online.getUniqueId());
                wipeManager.directWipe(staff, offlineTarget);
            }
        }

        staff.sendMessage(Component.text("✔ Banned ", NamedTextColor.GREEN)
            .append(Component.text(targetName, NamedTextColor.WHITE))
            .append(Component.text(" " + durationStr + ". Reason: " + banReason
                + (doWipe ? " (data wiped)" : ""), NamedTextColor.GREEN)));

        // Broadcast to anyone with mv.staff permission
        Component broadcast = Component.text("[Staff] ", NamedTextColor.DARK_RED)
            .append(Component.text(staff.getName(), NamedTextColor.RED))
            .append(Component.text(" banned ", NamedTextColor.GRAY))
            .append(Component.text(targetName, NamedTextColor.WHITE))
            .append(Component.text(" " + durationStr + " — " + banReason, NamedTextColor.GRAY));
        Bukkit.broadcast(broadcast, "mv.staff");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return banReasonManager.getReasons().keySet().stream()
                .filter(r -> r.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private boolean canUseBan(Player player) {
        if (player.isOp()) return true;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            String banRole = (String) rm.getClass().getMethod("getBanRole").invoke(rm);
            if (banRole == null) return false; // no role configured = op-only
            return (boolean) rm.getClass().getMethod("canUseBan", UUID.class).invoke(rm, player.getUniqueId());
        } catch (Exception e) { return false; }
    }
}
