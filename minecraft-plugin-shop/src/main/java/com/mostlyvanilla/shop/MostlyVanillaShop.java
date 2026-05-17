package com.mostlyvanilla.shop;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaShop extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String currency = getConfig().getString("currency", "coins");
        EconomyBridge bridge  = new EconomyBridge(this, currency);
        ShopManager   manager = new ShopManager(this, bridge);

        ShopCommand shopCmd = new ShopCommand(manager);
        getCommand("shop").setExecutor(shopCmd);
        getCommand("shop").setTabCompleter(shopCmd);

        getServer().getPluginManager().registerEvents(new ShopListener(manager), this);

        getLogger().info("MostlyVanilla Shop enabled (currency: " + currency + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Shop disabled.");
    }
}
