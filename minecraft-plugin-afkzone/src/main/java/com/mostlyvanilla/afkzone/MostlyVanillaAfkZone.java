package com.mostlyvanilla.afkzone;

import com.mostlyvanilla.afkzone.listeners.ZoneListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class MostlyVanillaAfkZone extends JavaPlugin {

    private AfkZoneManager zoneManager;
    private EconomyBridge  economyBridge;
    private BukkitTask     rewardTask;
    private BukkitTask     titleTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economyBridge = new EconomyBridge(this);
        zoneManager   = new AfkZoneManager(this, economyBridge);

        getCommand("afkzone").setExecutor(new AfkZoneCommand(this, zoneManager, economyBridge));
        getCommand("afkzone").setTabCompleter(new AfkZoneCommand(this, zoneManager, economyBridge));
        getServer().getPluginManager().registerEvents(new ZoneListener(zoneManager), this);

        startTasks();
        getLogger().info("MostlyVanillaAfkZone enabled.");
    }

    /** Starts (or restarts) the reward and title-refresh tasks. */
    public void startTasks() {
        if (rewardTask != null) rewardTask.cancel();
        if (titleTask  != null) titleTask.cancel();

        long intervalTicks = getConfig().getLong("interval-seconds", 60) * 20L;
        rewardTask = new BukkitRunnable() {
            @Override public void run() { zoneManager.rewardPlayersInZone(); }
        }.runTaskTimer(this, intervalTicks, intervalTicks);

        // Refresh title every 2 seconds so it stays on screen while inside the zone
        titleTask = new BukkitRunnable() {
            @Override public void run() { zoneManager.sendTitlesToZonePlayers(); }
        }.runTaskTimer(this, 20L, 40L);
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaAfkZone disabled.");
    }
}
