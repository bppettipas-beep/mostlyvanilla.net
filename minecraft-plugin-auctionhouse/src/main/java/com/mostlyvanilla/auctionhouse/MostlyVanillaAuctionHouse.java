package com.mostlyvanilla.auctionhouse;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaAuctionHouse extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        EconomyBridge bridge = new EconomyBridge(this);
        AuctionManager auctionManager = new AuctionManager(this, bridge);
        OrderManager   orderManager   = new OrderManager(this, bridge);

        auctionManager.load();
        orderManager.load();

        var ahCmd = getCommand("ah");
        if (ahCmd != null) {
            var exec = new AhCommand(auctionManager);
            ahCmd.setExecutor(exec);
            ahCmd.setTabCompleter(exec);
        }

        var ordCmd = getCommand("orders");
        if (ordCmd != null) {
            var exec = new OrdersCommand(orderManager);
            ordCmd.setExecutor(exec);
            ordCmd.setTabCompleter(exec);
        }

        getServer().getPluginManager().registerEvents(
            new AhListener(auctionManager, orderManager), this);

        getLogger().info("MostlyVanillaAuctionHouse enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaAuctionHouse disabled.");
    }
}
