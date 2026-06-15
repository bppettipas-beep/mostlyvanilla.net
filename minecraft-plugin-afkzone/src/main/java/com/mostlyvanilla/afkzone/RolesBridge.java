package com.mostlyvanilla.afkzone;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class RolesBridge {

    private Object roleManager = null;
    private boolean resolved = false;

    private Object getRoleManager() {
        if (resolved) return roleManager;
        resolved = true;
        Plugin roles = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (roles == null) return null;
        try {
            roleManager = roles.getClass().getMethod("getRoleManager").invoke(roles);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AfkZone] Could not connect to MostlyVanillaRoles: " + e.getMessage());
        }
        return roleManager;
    }

    /** Returns the player's role id, or null if roles plugin is absent or player has no role. */
    public String getPlayerRole(UUID uuid) {
        Object rm = getRoleManager();
        if (rm == null) return null;
        try {
            return (String) rm.getClass().getMethod("getPlayerRole", UUID.class).invoke(rm, uuid);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns all configured role names, for tab-completion. */
    @SuppressWarnings("unchecked")
    public Set<String> getRoleNames() {
        Object rm = getRoleManager();
        if (rm == null) return Collections.emptySet();
        try {
            return (Set<String>) rm.getClass().getMethod("getRoleNames").invoke(rm);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
