package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.ApiClient;
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

        // Remind unverified players to link their Discord (async so we don't block the join tick)
        ApiClient api = plugin.getApiClient();
        if (api != null) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    if (api.isVerified(uuid.toString())) return;
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!player.isOnline()) return;
                            player.sendMessage(Component.text("Link your Discord with ", NamedTextColor.GRAY)
                                .append(Component.text("/link", NamedTextColor.GREEN, TextDecoration.BOLD))
                                .append(Component.text(" to get the verified role.", NamedTextColor.GRAY)));
                        }
                    }.runTask(plugin);
                }
            }.runTaskLaterAsynchronously(plugin, 60L);
        }

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
