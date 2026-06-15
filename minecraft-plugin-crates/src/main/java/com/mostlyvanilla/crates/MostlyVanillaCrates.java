package com.mostlyvanilla.crates;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaCrates extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        KeyBridge    keyBridge     = new KeyBridge();
        BitShopBridge bitShopBridge = new BitShopBridge();
        CrateManager manager      = new CrateManager(this, keyBridge, bitShopBridge);
        manager.load();

        CrateCommand cmd = new CrateCommand(this, manager);
        getCommand("crate").setExecutor(cmd);
        getCommand("crate").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new CrateListener(manager), this);

        getLogger().info("MostlyVanilla Crates enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Crates disabled.");
    }
}
