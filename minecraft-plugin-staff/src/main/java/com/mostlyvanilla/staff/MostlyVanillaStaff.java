package com.mostlyvanilla.staff;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaStaff extends JavaPlugin {

    @Override
    public void onEnable() {
        StaffManager manager = new StaffManager(this);
        StaffCommand cmd     = new StaffCommand(manager);

        getCommand("staff").setExecutor(cmd);
        getCommand("staff").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new StaffListener(manager), this);

        getLogger().info("MostlyVanilla Staff enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Staff disabled.");
    }
}
