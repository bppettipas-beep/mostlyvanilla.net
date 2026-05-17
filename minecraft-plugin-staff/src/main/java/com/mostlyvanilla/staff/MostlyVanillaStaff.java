package com.mostlyvanilla.staff;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaStaff extends JavaPlugin {

    @Override
    public void onEnable() {
        MuteManager      muteManager      = new MuteManager(this);
        muteManager.load();

        BanReasonManager banReasonManager = new BanReasonManager(this);
        banReasonManager.load();

        StaffManager manager     = new StaffManager(this, muteManager);
        WipeManager  wipeManager = new WipeManager(this);

        StaffCommand    staffCmd     = new StaffCommand(manager);
        WipeCommand     wipe         = new WipeCommand(wipeManager);
        MuteCommand     mute         = new MuteCommand(muteManager);
        BanCommand      ban          = new BanCommand(banReasonManager, wipeManager);
        BanReasonCommand banReason   = new BanReasonCommand(banReasonManager);

        getCommand("staff").setExecutor(staffCmd);
        getCommand("staff").setTabCompleter(staffCmd);
        getCommand("wipe").setExecutor(wipe);
        getCommand("wipe").setTabCompleter(wipe);
        getCommand("mute").setExecutor(mute);
        getCommand("mute").setTabCompleter(mute);
        getCommand("unmute").setExecutor(mute);
        getCommand("unmute").setTabCompleter(mute);
        getCommand("ban").setExecutor(ban);
        getCommand("ban").setTabCompleter(ban);
        getCommand("banreason").setExecutor(banReason);
        getCommand("banreason").setTabCompleter(banReason);
        getServer().getPluginManager().registerEvents(new StaffListener(manager, wipeManager, muteManager), this);
        getServer().getPluginManager().registerEvents(new SpectatorVanishListener(this), this);

        getLogger().info("MostlyVanilla Staff enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Staff disabled.");
    }
}
