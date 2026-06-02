package com.mostlyvanilla.spawnstash;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class RolesBridge {

    private final JavaPlugin plugin;

    public RolesBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getAllowedRole() {
        return plugin.getConfig().getString("allowed-role", null);
    }

    public void setAllowedRole(String role) {
        plugin.getConfig().set("allowed-role", role);
        plugin.saveConfig();
    }

    public boolean canUseStash(Player player) {
        if (player.isOp()) return true;

        String allowedRole = getAllowedRole();
        if (allowedRole == null) return false;

        Plugin roles = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaRoles");
        if (roles == null) return false;

        try {
            Object rm = roles.getClass().getMethod("getRoleManager").invoke(roles);

            String playerRole = (String) rm.getClass()
                .getMethod("getPlayerRole", UUID.class)
                .invoke(rm, player.getUniqueId());
            if (playerRole == null) return false;

            @SuppressWarnings("unchecked")
            Map<String, Integer> weights = (Map<String, Integer>) rm.getClass()
                .getMethod("getRoleWeights")
                .invoke(rm);

            Integer allowedWeight = weights.get(allowedRole.toLowerCase());
            Integer playerWeight  = weights.get(playerRole.toLowerCase());
            if (allowedWeight == null || playerWeight == null) return false;

            return playerWeight <= allowedWeight;
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnStash] Role check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean roleExists(String role) {
        Plugin roles = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaRoles");
        if (roles == null) return false;
        try {
            Object rm = roles.getClass().getMethod("getRoleManager").invoke(roles);
            return (boolean) rm.getClass()
                .getMethod("roleExists", String.class)
                .invoke(rm, role.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }
}
