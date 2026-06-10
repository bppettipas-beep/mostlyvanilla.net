package com.mostlyvanilla.unwipe;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaUnwipe extends JavaPlugin {

    private UnwipeManager unwipeManager;

    public UnwipeManager getUnwipeManager() { return unwipeManager; }

    @Override
    public void onEnable() {
        unwipeManager = new UnwipeManager(this);

        UnwipeCommand cmd = new UnwipeCommand(unwipeManager);
        getCommand("unwipe").setExecutor(cmd);
        getCommand("unwipe").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new UnwipeListener(unwipeManager), this);

        getLogger().info("MostlyVanillaUnwipe enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaUnwipe disabled.");
    }
}
