package com.mostlyvanilla.teams;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaTeams extends JavaPlugin {

    private TeamManager manager;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        manager = new TeamManager(this);
        manager.load();

        TeamCommand teamCmd = new TeamCommand(manager);
        getCommand("team").setExecutor(teamCmd);
        getCommand("team").setTabCompleter(teamCmd);

        TeamAdminCommand adminCmd = new TeamAdminCommand(manager);
        getCommand("teamadmin").setExecutor(adminCmd);
        getCommand("teamadmin").setTabCompleter(adminCmd);

        getServer().getPluginManager().registerEvents(new TeamListener(this, manager), this);

        getLogger().info("MostlyVanilla Teams enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            // Clean up all scoreboard teams
            for (TeamData t : manager.getAllTeams()) manager.unregisterScoreboard(t);
            manager.save();
        }
    }
}
