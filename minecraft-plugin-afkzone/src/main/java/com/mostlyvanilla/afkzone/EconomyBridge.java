package com.mostlyvanilla.afkzone;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EconomyBridge {

    private final JavaPlugin plugin;

    public EconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getCurrency() {
        return plugin.getConfig().getString("currency", "coins");
    }

    public void deposit(UUID uuid, double amount) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) {
            plugin.getLogger().warning("[AfkZone] MostlyVanillaEconomy not found — cannot deposit.");
            return;
        }
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            em.getClass().getMethod("giveBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, getCurrency(), amount);
        } catch (Exception e) {
            plugin.getLogger().warning("[AfkZone] Failed to deposit: " + e.getMessage());
        }
    }
}
