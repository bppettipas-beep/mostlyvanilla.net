package com.mostlyvanilla.staff;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaStaff extends JavaPlugin {

    @Override
    public void onEnable() {
        MuteManager  muteManager = new MuteManager(this);
        muteManager.load();

        StaffManager manager     = new StaffManager(this, muteManager);
        WipeManager  wipeManager = new WipeManager(this);

        StaffCommand cmd   = new StaffCommand(manager);
        WipeCommand  wipe  = new WipeCommand(wipeManager);
        MuteCommand  mute  = new MuteCommand(muteManager);

        getCommand("staff").setExecutor(cmd);
        getCommand("staff").setTabCompleter(cmd);
        getCommand("wipe").setExecutor(wipe);
        getCommand("wipe").setTabCompleter(wipe);
        getCommand("mute").setExecutor(mute);
        getCommand("mute").setTabCompleter(mute);
        getCommand("unmute").setExecutor(mute);
        getCommand("unmute").setTabCompleter(mute);
        getServer().getPluginManager().registerEvents(new StaffListener(manager, wipeManager, muteManager), this);

        getLogger().info("MostlyVanilla Staff enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Staff disabled.");
    }
}
