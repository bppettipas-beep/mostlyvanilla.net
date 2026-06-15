package com.mostlyvanilla.auctionhouse;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaAuctionHouse extends JavaPlugin {

    private AuctionManager auctionManager;
    private OrderManager   orderManager;

    public AuctionManager getAuctionManager() { return auctionManager; }
    public OrderManager   getOrderManager()   { return orderManager; }

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        EconomyBridge bridge        = new EconomyBridge(this);
        HistoryLogger historyLogger = new HistoryLogger(getLogger());
        historyLogger.init(getDataFolder());
        auctionManager = new AuctionManager(this, bridge, historyLogger);
        orderManager   = new OrderManager(this, bridge, historyLogger);

        auctionManager.load();
        orderManager  .load();

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
            new AhListener(this, auctionManager, orderManager), this);

        getLogger().info("MostlyVanillaAuctionHouse enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaAuctionHouse disabled.");
    }
}
