package com.mostlyvanilla.shop;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaShop extends JavaPlugin {

    private KeyStore keyStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        keyStore = new KeyStore(this);

        EconomyBridge  bridge         = new EconomyBridge(this);
        ShopManager    shopManager    = new ShopManager(this, bridge);
        SellManager    sellManager    = new SellManager(this, bridge);
        BitShopManager bitShopManager = new BitShopManager(this, keyStore);

        ShopCommand shopCmd = new ShopCommand(shopManager, sellManager);
        getCommand("shop").setExecutor(shopCmd);
        getCommand("shop").setTabCompleter(shopCmd);

        SellCommand sellCmd = new SellCommand(sellManager);
        getCommand("sell").setExecutor(sellCmd);
        getCommand("sell").setTabCompleter(sellCmd);

        getCommand("worth").setExecutor(new WorthCommand(sellManager));

        BitShopCommand bitShopCmd = new BitShopCommand(bitShopManager);
        getCommand("bitshop").setExecutor(bitShopCmd);
        getCommand("bitshop").setTabCompleter(bitShopCmd);

        KeysCommand keysCmd = new KeysCommand(keyStore, bitShopManager);
        getCommand("keys").setExecutor(keysCmd);
        getCommand("keys").setTabCompleter(keysCmd);

        getServer().getPluginManager().registerEvents(
            new ShopListener(shopManager, sellManager, bitShopManager), this);

        getLogger().info("MostlyVanilla Shop enabled (currency: " + bridge.getCurrency() + ", symbol: " + bridge.getSymbol() + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Shop disabled.");
    }

    public KeyStore getKeyStore() { return keyStore; }
}
