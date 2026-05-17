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

    private static final Title.Times TIMES = Title.Times.times(
        Duration.ofMillis(300),
        Duration.ofSeconds(5),
        Duration.ofMillis(800)
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canAnnounce(sender)) {
            sender.sendMessage(Component.text("You don't have permission to send announcements.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /announcement <message> [| subtitle]", NamedTextColor.RED));
            sender.sendMessage(Component.text("  Colors: use & codes or <gradient:gold:yellow>MiniMessage</gradient>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Subtitle: /announcement Big News | More details here", NamedTextColor.GRAY));
            return true;
        }

        String full = String.join(" ", args);

        Component titleComp;
        Component subtitleComp;

        if (full.contains("|")) {
            String[] parts = full.split("\\|", 2);
            titleComp    = parse(parts[0].trim()).decorate(TextDecoration.BOLD);
            subtitleComp = parse(parts[1].trim());
        } else {
            titleComp    = parse(full).decorate(TextDecoration.BOLD);
            subtitleComp = Component.empty();
        }

        Title title = Title.title(titleComp, subtitleComp, TIMES);

        // Sender name for the chat line
        String senderName = (sender instanceof Player p) ? p.getName() : "Console";

        Component chatLine = Component.text()
            .append(Component.text("⚡ ", NamedTextColor.GOLD))
            .append(Component.text("[Announcement] ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(titleComp.decoration(TextDecoration.BOLD, TextDecoration.State.FALSE))
            .build();

        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.sendMessage(chatLine);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
            count++;
        }

        sender.sendMessage(Component.text("✔ Announcement sent to " + count + " player(s).", NamedTextColor.GREEN));
        Bukkit.getLogger().info("[Announcement] " + senderName + " broadcast: " + full);
        return true;
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
