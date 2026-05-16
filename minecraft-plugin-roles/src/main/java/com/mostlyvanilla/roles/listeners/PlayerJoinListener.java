package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinListener implements Listener {

    private final MostlyVanillaRoles plugin;

    public PlayerJoinListener(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RoleManager rm = plugin.getRoleManager();
        var player = event.getPlayer();
        var uuid   = player.getUniqueId();

        if (rm.getPlayerRole(uuid) == null) {
            String joinRole = rm.getJoinRole();
            if (joinRole != null) rm.assignRole(uuid, joinRole);
        } else {
            // Sync scoreboard team immediately, then TAB after 1 tick
            rm.syncPlayerTeam(player);
        }

        // Re-sync TAB prefix after 1 tick to ensure TAB has finished its own join processing
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) rm.syncPlayerTeam(player);
            }
        }.runTaskLater(plugin, 1L);
    }
}
