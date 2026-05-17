package com.mostlyvanilla.shop;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EconomyBridge {

    private final JavaPlugin plugin;
    private final String currency;

    public EconomyBridge(JavaPlugin plugin, String currency) {
        this.plugin   = plugin;
        this.currency = currency;
    }

    public String getCurrency() {
        return currency;
    }

    public double getBalance(UUID uuid) {
        Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return 0.0;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            Object result = em.getClass().getMethod("getBalance", UUID.class, String.class)
                .invoke(em, uuid, currency);
            return ((Number) result).doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("[Shop] Failed to get balance: " + e.getMessage());
            return 0.0;
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return false;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            Object result = em.getClass().getMethod("takeBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, currency, amount);
            return (boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("[Shop] Failed to withdraw balance: " + e.getMessage());
            return false;
        }
    }
}
