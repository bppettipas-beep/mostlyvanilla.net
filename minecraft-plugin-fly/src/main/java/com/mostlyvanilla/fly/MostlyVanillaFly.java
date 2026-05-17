package com.mostlyvanilla.fly;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaFly extends JavaPlugin {

    @Override
    public void onEnable() {
        FlyCommand cmd = new FlyCommand();
        getCommand("fly").setExecutor(cmd);
        getCommand("fly").setTabCompleter(cmd);
        getLogger().info("MostlyVanilla Fly enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Fly disabled.");
    }
}
