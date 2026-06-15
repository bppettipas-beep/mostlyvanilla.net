package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

public class CommandListener implements Listener {

    private final MostlyVanillaRoles plugin;

    public CommandListener(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("mostlyvanilla.roles.admin")) return;

        RoleManager rm = plugin.getRoleManager();
        java.util.UUID uuid = player.getUniqueId();

        String msg = event.getMessage(); // e.g. "/tpa overworld"
        String cmd = msg.startsWith("/") ? msg.substring(1) : msg;

        if (rm.isDutyRequired(uuid) && !rm.isOnDuty(uuid)
                && rm.isDutyGatedCommand(cmd)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You must be on duty to use staff commands. Type /duty to go on duty.", NamedTextColor.RED));
            return;
        }

        if (!cmd.equals("duty") && !cmd.equals("dutyrequire")
                && rm.isCommandBlockedForPlayer(uuid, cmd) && !rm.hasRolePermission(uuid, cmd)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Unknown command.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("mostlyvanilla.roles.admin")) return;

        RoleManager rm = plugin.getRoleManager();
        java.util.UUID uuid = player.getUniqueId();
        if (rm.getPlayerRole(uuid) == null) return;

        event.getCommands().removeIf(cmd ->
            (rm.isDutyRequired(uuid) && !rm.isOnDuty(uuid) && rm.isDutyGatedCommand(cmd))
            || (!cmd.equals("duty") && !cmd.equals("dutyrequire")
                && rm.isCommandBlockedForPlayer(uuid, cmd) && !rm.hasRolePermission(uuid, cmd)));
    }
}
