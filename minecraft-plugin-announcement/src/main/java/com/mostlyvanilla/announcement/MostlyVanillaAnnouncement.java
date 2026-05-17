package com.mostlyvanilla.announcement;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaAnnouncement extends JavaPlugin {

    @Override
    public void onEnable() {
        AnnouncementCommand cmd = new AnnouncementCommand();
        getCommand("announcement").setExecutor(cmd);
        getCommand("announcement").setTabCompleter(cmd);
        getLogger().info("MostlyVanilla Announcement enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla Announcement disabled.");
    }
}
