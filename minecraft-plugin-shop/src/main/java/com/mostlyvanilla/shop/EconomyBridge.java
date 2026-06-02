package com.mostlyvanilla.shop;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EconomyBridge {

    private final JavaPlugin plugin;
    private final String     currencyOverride; // null = read from config

    public EconomyBridge(JavaPlugin plugin) {
        this.plugin           = plugin;
        this.currencyOverride = null;
    }

    /** Use this constructor to hard-wire a specific currency (e.g. "bits"). */
    public EconomyBridge(JavaPlugin plugin, String currency) {
        this.plugin           = plugin;
        this.currencyOverride = currency;
    }

    public String getCurrency() {
        return currencyOverride != null ? currencyOverride
            : plugin.getConfig().getString("currency", "coins");
    }

    public String getSymbol() {
        return plugin.getConfig().getString("currency-symbol", "$");
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

    public void deposit(UUID uuid, double amount) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            em.getClass().getMethod("giveBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, getCurrency(), amount);
        } catch (Exception e) {
            plugin.getLogger().warning("[Shop] Failed to deposit balance: " + e.getMessage());
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
