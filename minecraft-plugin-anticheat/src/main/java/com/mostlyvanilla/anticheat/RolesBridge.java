package com.mostlyvanilla.anticheat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class RolesBridge {

    private final MostlyVanillaAnticheat plugin;

    public RolesBridge(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public boolean canNotifySus(UUID uuid) {
        Plugin roles = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaRoles");
        if (roles == null) return false;
        try {
            Object rm = roles.getClass().getMethod("getRoleManager").invoke(roles);
            return (boolean) rm.getClass().getMethod("canNotifySus", UUID.class).invoke(rm, uuid);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canNotifySus(Player player) {
        if (player.isOp()) return true;
        return canNotifySus(player.getUniqueId());
    }
}
