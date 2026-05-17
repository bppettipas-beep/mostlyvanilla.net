package com.mostlyvanilla.staff;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaStaff extends JavaPlugin {

    @Override
    public void onEnable() {
        StaffManager manager     = new StaffManager(this);
        WipeManager  wipeManager = new WipeManager(this);

        StaffCommand cmd  = new StaffCommand(manager);
        WipeCommand  wipe = new WipeCommand(wipeManager);

        getCommand("staff").setExecutor(cmd);
        getCommand("staff").setTabCompleter(cmd);
        getCommand("wipe").setExecutor(wipe);
        getCommand("wipe").setTabCompleter(wipe);
        getServer().getPluginManager().registerEvents(new StaffListener(manager, wipeManager), this);

        getLogger().info("MostlyVanilla Staff enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Staff disabled.");
    }
}
