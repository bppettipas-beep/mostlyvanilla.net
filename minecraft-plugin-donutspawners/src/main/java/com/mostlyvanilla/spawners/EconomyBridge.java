package com.mostlyvanilla.spawners;

import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class EconomyBridge {

    private final DonutSpawners plugin;

    public EconomyBridge(DonutSpawners plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy") != null;
    }

    public void deposit(UUID uuid, double amount) {
        Plugin econ = plugin.getServer().getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (econ == null) return;
        try {
            Object em = econ.getClass().getMethod("getEconomyManager").invoke(econ);
            em.getClass()
                .getMethod("giveBalance", UUID.class, String.class, double.class)
                .invoke(em, uuid, plugin.getSpawnerConfig().getSellCurrency(), amount);
        } catch (Exception e) {
            plugin.getLogger().warning("[MVSpawners] Economy deposit failed: " + e.getMessage());
        }
    }
}
