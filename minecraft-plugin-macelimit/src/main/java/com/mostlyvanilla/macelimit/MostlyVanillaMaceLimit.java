package com.mostlyvanilla.macelimit;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaMaceLimit extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        MaceLimitManager manager = new MaceLimitManager(this);
        manager.load();

        MaceLimitCommand cmd = new MaceLimitCommand(manager);
        getCommand("macelimit").setExecutor(cmd);
        getCommand("macelimit").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new MaceLimitListener(manager), this);

        getLogger().info("MaceLimit enabled — limit: "
            + (manager.getLimit() <= 0 ? "unlimited" : manager.getLimit())
            + ", crafted so far: " + manager.getCrafted());
    }
}
