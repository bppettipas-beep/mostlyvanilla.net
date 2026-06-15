package com.mostlyvanilla.staff;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaStaff extends JavaPlugin {

    @Override
    public void onEnable() {
        MuteManager      muteManager      = new MuteManager(this);
        muteManager.load();

        BanReasonManager banReasonManager = new BanReasonManager(this);
        banReasonManager.load();

        StaffManager      manager          = new StaffManager(this, muteManager);
        WipeManager       wipeManager      = new WipeManager(this);
        WorldRegenManager worldRegenManager = new WorldRegenManager(this);
        GlobalWipeManager globalWipeManager = new GlobalWipeManager(this);
        TransferManager   transferManager   = new TransferManager(this);

        StaffCommand     staffCmd    = new StaffCommand(manager);
        WipeCommand      wipe        = new WipeCommand(wipeManager);
        MuteCommand      mute        = new MuteCommand(muteManager);
        BanCommand       ban         = new BanCommand(banReasonManager, wipeManager);
        BanReasonCommand banReason   = new BanReasonCommand(banReasonManager);
        BanManageCommand banManage   = new BanManageCommand();
        InvSeeCommand    invSee      = new InvSeeCommand();

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
        getCommand("banlist").setExecutor(banManage);
        getCommand("banlist").setTabCompleter(banManage);
        getCommand("bancheck").setExecutor(banManage);
        getCommand("bancheck").setTabCompleter(banManage);
        getCommand("unban").setExecutor(banManage);
        getCommand("unban").setTabCompleter(banManage);
        getCommand("invsee").setExecutor(invSee);
        getCommand("invsee").setTabCompleter(invSee);
        getCommand("checkec").setExecutor(invSee);
        getCommand("checkec").setTabCompleter(invSee);
        getCommand("regenworld").setExecutor(new WorldRegenCommand(worldRegenManager));
        getCommand("globalwipe").setExecutor(new GlobalWipeCommand(globalWipeManager));
        TransferCommand transferCmd = new TransferCommand(transferManager);
        getCommand("transfer").setExecutor(transferCmd);
        getCommand("transfer").setTabCompleter(transferCmd);
        getServer().getPluginManager().registerEvents(
            new StaffListener(manager, wipeManager, muteManager, worldRegenManager, globalWipeManager, transferManager), this);
        getServer().getPluginManager().registerEvents(new SpectatorVanishListener(this), this);

        getLogger().info("MostlyVanilla Staff enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Staff disabled.");
    }
}
