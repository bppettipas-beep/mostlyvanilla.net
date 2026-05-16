package com.mostlyvanilla.verify.listeners;

import com.mostlyvanilla.verify.ApiClient;
import com.mostlyvanilla.verify.MostlyVanillaVerify;
import com.mostlyvanilla.verify.commands.DiscordCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinListener implements Listener {

    private final MostlyVanillaVerify plugin;

    public PlayerJoinListener(MostlyVanillaVerify plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Delay 40 ticks (2 seconds) so the player has fully loaded before showing messages
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                // Always show tips to everyone on join
                sendTips(player);

                // Then async-check if they need to link
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        boolean verified = plugin.getApiClient().isVerified(player.getUniqueId().toString());
                        if (verified) return;

                        ApiClient.CodeResult result = plugin.getApiClient()
                            .requestCode(player.getUniqueId().toString(), player.getName());

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!player.isOnline()) return;
                                if (result.success()) {
                                    DiscordCommand.sendVerificationMessage(player, result);
                                } else {
                                    player.sendMessage(Component.text(
                                        "Link your Discord account with /link",
                                        NamedTextColor.GREEN
                                    ));
                                }
                            }
                        }.runTask(plugin);
                    }
                }.runTaskAsynchronously(plugin);
            }
        }.runTaskLater(plugin, 40L);
    }

    private void sendTips(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━ Mostly Vanilla ━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(
            Component.text("  /tpa ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("<player>  ", NamedTextColor.AQUA))
                .append(Component.text("Teleport to another player", NamedTextColor.GRAY))
        );
        player.sendMessage(
            Component.text("  /rtp ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("            ", NamedTextColor.AQUA))
                .append(Component.text("Start your journey", NamedTextColor.GRAY))
        );
        player.sendMessage(
            Component.text("  /link ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("           ", NamedTextColor.AQUA))
                .append(Component.text("Link your Discord account", NamedTextColor.GRAY))
        );
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
    }
}
