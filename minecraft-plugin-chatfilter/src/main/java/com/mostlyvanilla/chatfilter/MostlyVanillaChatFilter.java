package com.mostlyvanilla.chatfilter;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaChatFilter extends JavaPlugin {

    private static MostlyVanillaChatFilter instance;
    private FilterManager filterManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        filterManager = new FilterManager();
        filterManager.loadConfig(getConfig());
        getDataFolder().mkdirs();
        filterManager.initData(getDataFolder());

        getServer().getPluginManager().registerEvents(
            new ChatFilterListener(this, filterManager), this);

        FilterReloadCommand reloadCmd = new FilterReloadCommand(this, filterManager);
        getCommand("chatfilter").setExecutor(reloadCmd);
        getCommand("chatfilter").setTabCompleter(reloadCmd);
        getCommand("cf").setExecutor(reloadCmd);
        getCommand("cf").setTabCompleter(reloadCmd);

        getLogger().info("MostlyVanilla ChatFilter enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla ChatFilter disabled.");
    }

    public static MostlyVanillaChatFilter getInstance() { return instance; }
    public FilterManager getFilterManager()             { return filterManager; }
}
