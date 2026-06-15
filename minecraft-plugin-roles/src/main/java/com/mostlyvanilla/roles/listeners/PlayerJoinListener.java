package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinListener implements Listener {

    private final MostlyVanillaRoles plugin;

    public PlayerJoinListener(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode current = player.getGameMode();
        GameMode next    = event.getNewGameMode();

        if (next == GameMode.SPECTATOR) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) other.hidePlayer(plugin, player);
            }
        } else if (current == GameMode.SPECTATOR) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) other.showPlayer(plugin, player);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RoleManager rm = plugin.getRoleManager();
        var player = event.getPlayer();
        var uuid   = player.getUniqueId();

        // Hide all existing spectators from this player, and hide this player from
        // everyone if they themselves join in spectator (e.g. after a reload).
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.equals(player)) continue;
                    if (other.getGameMode() == GameMode.SPECTATOR) {
                        player.hidePlayer(plugin, other);
                    }
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        other.hidePlayer(plugin, player);
                    }
                }
            }
        }.runTaskLater(plugin, 1L);

        if (rm.getPlayerRole(uuid) == null) {
            String joinRole = rm.getJoinRole();
            if (joinRole != null) rm.assignRole(uuid, joinRole);
        } else {
            rm.syncPlayerTeam(player);
        }

        // Re-sync TAB prefix after 1 tick
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) rm.syncPlayerTeam(player);
            }
        }.runTaskLater(plugin, 1L);

        // Remind duty-required staff that they are off duty on join
        if (rm.isDutyRequired(uuid)) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("[Duty] ", NamedTextColor.GRAY)
                            .append(Component.text("OFF DUTY", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                            .append(Component.text(" — staff permissions are disabled. Use ", NamedTextColor.GRAY))
                            .append(Component.text("/duty", NamedTextColor.WHITE))
                            .append(Component.text(" to go on duty.", NamedTextColor.GRAY)));
                    }
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        RoleManager rm = plugin.getRoleManager();
        rm.clearDutyStatus(event.getPlayer().getUniqueId());
        rm.removePermissions(event.getPlayer());
    }
}
