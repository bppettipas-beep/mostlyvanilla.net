package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SpectatorVanishListener implements Listener {

    private final JavaPlugin plugin;

    public SpectatorVanishListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player             = event.getPlayer();
        boolean enteringSpectator = event.getNewGameMode() == GameMode.SPECTATOR;
        boolean leavingSpectator  = player.getGameMode() == GameMode.SPECTATOR && !enteringSpectator;

        if (enteringSpectator) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(player) && !canSeeVanished(other))
                        other.hidePlayer(plugin, player);
                }
                player.sendMessage(Component.text("You are now vanished.", NamedTextColor.DARK_GRAY));
            });
        } else if (leavingSpectator) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player other : Bukkit.getOnlinePlayers())
                    other.showPlayer(plugin, player);
                player.sendMessage(Component.text("You are no longer vanished.", NamedTextColor.GRAY));
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Hide any currently-spectating players from this player if they lack permission
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(joining)) continue;
                if (online.getGameMode() == GameMode.SPECTATOR && !canSeeVanished(joining))
                    joining.hidePlayer(plugin, online);
            }
            // If this player joined already in spectator (e.g. logged out in spec), hide them too
            if (joining.getGameMode() == GameMode.SPECTATOR) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(joining) && !canSeeVanished(other))
                        other.hidePlayer(plugin, joining);
                }
            }
        });
    }

    private boolean canSeeVanished(Player player) {
        return player.isOp()
            || player.hasPermission("mv.staff")
            || player.getGameMode() == GameMode.SPECTATOR;
    }
}
