package com.mostlyvanilla.announcement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class AnnouncementCommand implements CommandExecutor, TabCompleter {

    // Long stay so players have time to read multi-line wrapping
    private static final Title.Times TIMES = Title.Times.times(
        Duration.ofMillis(200),
        Duration.ofSeconds(6),
        Duration.ofMillis(800)
    );

    // Auto-split threshold: messages longer than this get split into title + subtitle
    private static final int SPLIT_CHARS = 38;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canAnnounce(sender)) {
            sender.sendMessage(Component.text("You don't have permission to send announcements.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /announcement <message>", NamedTextColor.RED));
            sender.sendMessage(Component.text("  Long messages auto-split across two screen lines.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Use \\n to force a split: /announcement Line one \\n Line two", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Use & color codes or <gradient:gold:yellow>MiniMessage</gradient>", NamedTextColor.GRAY));
            return true;
        }

        String full = String.join(" ", args);
        String[] lines = splitLines(full);

        Component titleComp    = parse(lines[0]).decorate(TextDecoration.BOLD);
        Component subtitleComp = lines[1].isEmpty() ? Component.empty() : parse(lines[1]).decorate(TextDecoration.BOLD);

        Title title = Title.title(titleComp, subtitleComp, TIMES);

        // Chat fallback line shows the full raw text
        Component chatLine = Component.text()
            .append(Component.text("⚡ ", NamedTextColor.GOLD))
            .append(Component.text("[Announcement] ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(parse(lines[0]).decoration(TextDecoration.BOLD, TextDecoration.State.FALSE))
            .append(lines[1].isEmpty()
                ? Component.empty()
                : Component.text(" — ", NamedTextColor.DARK_GRAY)
                    .append(parse(lines[1]).decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)))
            .build();

        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.sendMessage(chatLine);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
            count++;
        }

        String senderName = (sender instanceof Player p) ? p.getName() : "Console";
        sender.sendMessage(Component.text("✔ Announcement sent to " + count + " player(s).", NamedTextColor.GREEN));
        Bukkit.getLogger().info("[Announcement] " + senderName + " broadcast: " + full);
        return true;
    }

    /** Splits the raw input into [titleText, subtitleText].
     *  Priority: explicit \n > explicit | > auto-split at midpoint word boundary. */
    private String[] splitLines(String full) {
        if (full.contains("\\n")) {
            String[] p = full.split("\\\\n", 2);
            return new String[]{p[0].trim(), p[1].trim()};
        }
        if (full.contains("|")) {
            String[] p = full.split("\\|", 2);
            return new String[]{p[0].trim(), p[1].trim()};
        }
        if (full.length() > SPLIT_CHARS) {
            int mid = full.length() / 2;
            for (int d = 0; d <= mid; d++) {
                if (mid + d < full.length() && full.charAt(mid + d) == ' ')
                    return new String[]{full.substring(0, mid + d).trim(), full.substring(mid + d).trim()};
                if (mid - d > 0 && full.charAt(mid - d) == ' ')
                    return new String[]{full.substring(0, mid - d).trim(), full.substring(mid - d).trim()};
            }
        }
        return new String[]{full, ""};
    }

    private Component parse(String text) {
        if (text.contains("<") && text.contains(">")) {
            return MiniMessage.miniMessage().deserialize(text);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private boolean canAnnounce(CommandSender sender) {
        if (!(sender instanceof Player player)) return sender.isOp();
        if (player.isOp()) return true;
        if (player.hasPermission("mv.announcement")) return true;
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            return (boolean) rm.getClass().getMethod("canUseAnnouncement", UUID.class)
                .invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
