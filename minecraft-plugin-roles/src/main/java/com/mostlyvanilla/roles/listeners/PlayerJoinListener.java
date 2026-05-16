package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final MostlyVanillaRoles plugin;

    public PlayerJoinListener(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RoleManager rm = plugin.getRoleManager();
        var uuid = event.getPlayer().getUniqueId();

        if (rm.getPlayerRole(uuid) == null) {
            // New player — assign join role if configured
            String joinRole = rm.getJoinRole();
            if (joinRole != null) rm.assignRole(uuid, joinRole); // also syncs team
        } else {
            // Returning player — sync them to their scoreboard team
            rm.syncPlayerTeam(event.getPlayer());
        }
    }
}
