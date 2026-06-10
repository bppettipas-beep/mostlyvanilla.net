package com.mostlyvanilla.joinleave;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinLeaveListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        event.setJoinMessage(null);

        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            Component welcome = Component.empty()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("  ★  Welcome to MostlyVanilla, ", NamedTextColor.YELLOW)
                    .append(Component.text(player.getName(), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("!  ★", NamedTextColor.YELLOW)))
                .append(Component.newline())
                .append(Component.text("  It's your first time here — say hi and enjoy your stay!", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

            Bukkit.broadcast(welcome);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        event.setQuitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }
}
