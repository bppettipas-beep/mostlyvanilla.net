package com.mostlyvanilla.shop;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaShop extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        EconomyBridge bridge  = new EconomyBridge(this);
        ShopManager   manager = new ShopManager(this, bridge);

        ShopCommand shopCmd = new ShopCommand(manager);
        getCommand("shop").setExecutor(shopCmd);
        getCommand("shop").setTabCompleter(shopCmd);

        getServer().getPluginManager().registerEvents(new ShopListener(manager), this);

        getLogger().info("MostlyVanilla Shop enabled (currency: " + bridge.getCurrency() + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Shop disabled.");
    }
}
