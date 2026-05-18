package com.mostlyvanilla.shop;

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

    public double getBalance(UUID uuid) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return 0.0;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            Object result = em.getClass().getMethod("getBalance", UUID.class, String.class)
                .invoke(em, uuid, getCurrency());
            return ((Number) result).doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("[Shop] Failed to get balance: " + e.getMessage());
            return 0.0;
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return false;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            Object result = em.getClass().getMethod("takeBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, getCurrency(), amount);
            return (boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("[Shop] Failed to withdraw balance: " + e.getMessage());
            return false;
        }
    }
}
