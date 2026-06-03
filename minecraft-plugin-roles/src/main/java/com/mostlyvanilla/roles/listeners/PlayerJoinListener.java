package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
            rm.syncPlayerTeam(player);
        }

        // Re-sync TAB prefix after 1 tick
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) rm.syncPlayerTeam(player);
            }
        }.runTaskLater(plugin, 1L);

        // Async: query Discord roles and upgrade to highest linked role
        new BukkitRunnable() {
            @Override public void run() {
                rm.syncFromDiscord(uuid);
            }
        }.runTaskAsynchronously(plugin);

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
        plugin.getRoleManager().clearDutyStatus(event.getPlayer().getUniqueId());
    }
}
