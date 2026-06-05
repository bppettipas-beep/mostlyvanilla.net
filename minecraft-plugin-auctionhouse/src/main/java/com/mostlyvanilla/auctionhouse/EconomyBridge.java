package com.mostlyvanilla.auctionhouse;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EconomyBridge {

    private final JavaPlugin plugin;

    public EconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getCurrency() {
        return plugin.getConfig().getString("currency", "money");
    }

    public String getSymbol() {
        return plugin.getConfig().getString("currency-symbol", "$");
    }

    public double getBalance(UUID uuid) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return 0.0;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            return ((Number) em.getClass()
                .getMethod("getBalance", UUID.class, String.class)
                .invoke(em, uuid, getCurrency())).doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("[AH] getBalance failed: " + e.getMessage());
            return 0.0;
        }
    }

    public void deposit(UUID uuid, double amount) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            em.getClass()
                .getMethod("giveBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, getCurrency(), amount);
        } catch (Exception e) {
            plugin.getLogger().warning("[AH] deposit failed: " + e.getMessage());
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        org.bukkit.plugin.Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return false;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            return (boolean) em.getClass()
                .getMethod("takeBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, getCurrency(), amount);
        } catch (Exception e) {
            plugin.getLogger().warning("[AH] withdraw failed: " + e.getMessage());
            return false;
        }
    }
}
