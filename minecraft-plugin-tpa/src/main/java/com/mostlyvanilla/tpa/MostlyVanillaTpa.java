package com.mostlyvanilla.tpa;

import com.mostlyvanilla.tpa.commands.TpaCommand;
import com.mostlyvanilla.tpa.commands.TpaResponseCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class MostlyVanillaTpa extends JavaPlugin {

    private RequestManager requestManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        long   expireSeconds    = getConfig().getLong("request-timeout-seconds", 60);
        int    countdownSeconds = getConfig().getInt("countdown-seconds", 3);
        boolean cancelOnMove   = getConfig().getBoolean("cancel-on-move", true);
        double maxMoveDistance  = getConfig().getDouble("max-move-distance", 5.0);
        requestManager = new RequestManager(this, expireSeconds, countdownSeconds, cancelOnMove, maxMoveDistance);

        TpaCommand tpaCmd = new TpaCommand(requestManager);
        TpaResponseCommand respCmd = new TpaResponseCommand(requestManager);

        getCommand("tpa").setExecutor(tpaCmd);
        getCommand("tpa").setTabCompleter(tpaCmd);
        getCommand("tpahere").setExecutor(tpaCmd);
        getCommand("tpahere").setTabCompleter(tpaCmd);
        getCommand("tpaconfirm").setExecutor(respCmd);
        getCommand("tpaccept").setExecutor(respCmd);
        getCommand("tpdeny").setExecutor(respCmd);
        getCommand("tpacancel").setExecutor(respCmd);

        // Cleanup expired requests every 5 seconds
        new BukkitRunnable() {
            @Override public void run() { requestManager.cleanupExpired(); }
        }.runTaskTimer(this, 100L, 100L);

        getLogger().info("MostlyVanilla TPA enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla TPA disabled.");
    }
}
