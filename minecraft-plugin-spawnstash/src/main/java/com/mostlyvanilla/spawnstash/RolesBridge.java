package com.mostlyvanilla.spawnstash;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class RolesBridge {

    private final JavaPlugin plugin;

    public RolesBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canUseStash(Player player) {
        if (player.isOp()) return true;

        Plugin roles = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaRoles");
        if (roles == null) return false;

        try {
            Object rm = roles.getClass().getMethod("getRoleManager").invoke(roles);
            return (boolean) rm.getClass()
                .getMethod("canUseStash", UUID.class)
                .invoke(rm, player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnStash] Role check failed: " + e.getMessage());
            return false;
        }
    }
}
