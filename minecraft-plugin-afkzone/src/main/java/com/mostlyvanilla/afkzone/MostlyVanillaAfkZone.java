package com.mostlyvanilla.afkzone;

import com.mostlyvanilla.afkzone.listeners.ZoneListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class MostlyVanillaAfkZone extends JavaPlugin {

    private AfkZoneManager zoneManager;
    private EconomyBridge  economyBridge;
    private BukkitTask     rewardTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economyBridge    = new EconomyBridge(this);
        RolesBridge roles = new RolesBridge();
        zoneManager      = new AfkZoneManager(this, economyBridge, roles);

        AfkZoneCommand afkZoneCmd = new AfkZoneCommand(this, zoneManager, economyBridge, roles);
        getCommand("afkzone").setExecutor(afkZoneCmd);
        getCommand("afkzone").setTabCompleter(afkZoneCmd);
        getCommand("afk").setExecutor(new AfkCommand(zoneManager));
        getServer().getPluginManager().registerEvents(new ZoneListener(zoneManager), this);

        startTasks();
        getLogger().info("MostlyVanillaAfkZone enabled.");
    }

    /** Starts (or restarts) the reward task. */
    public void startTasks() {
        if (rewardTask != null) rewardTask.cancel();

        long intervalTicks = getConfig().getLong("interval-seconds", 60) * 20L;
        rewardTask = new BukkitRunnable() {
            @Override public void run() { zoneManager.rewardPlayersInZone(); }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaAfkZone disabled.");
    }
}
