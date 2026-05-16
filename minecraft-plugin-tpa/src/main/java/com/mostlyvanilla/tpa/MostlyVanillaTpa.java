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
        long expireSeconds = getConfig().getLong("request-timeout-seconds", 60);
        requestManager = new RequestManager(expireSeconds);

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
