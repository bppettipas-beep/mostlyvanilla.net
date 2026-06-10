package com.mostlyvanilla.joinleave;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaJoinLeave extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new JoinLeaveListener(), this);
        getLogger().info("MostlyVanillaJoinLeave enabled.");
    }
}
