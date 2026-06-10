package com.mostlyvanilla.settings;

import com.mostlyvanilla.settings.commands.SettingsCommand;
import com.mostlyvanilla.settings.gui.SettingsGuiListener;
import com.mostlyvanilla.settings.listeners.PlayerListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaSettings extends JavaPlugin {

    private SettingsManager settingsManager;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        settingsManager = new SettingsManager(this);
        settingsManager.load();

        playerListener = new PlayerListener(settingsManager);

        var pm = getServer().getPluginManager();
        pm.registerEvents(playerListener, this);
        pm.registerEvents(new SettingsGuiListener(this), this);

        var cmd = getCommand("settings");
        if (cmd != null) cmd.setExecutor(new SettingsCommand(this));

        // Night vision refresh every 10 seconds
        new BukkitRunnable() {
            @Override public void run() {
                playerListener.tickNightVision(getServer().getOnlinePlayers());
            }
        }.runTaskTimer(this, 200L, 200L);

        getLogger().info("MostlyVanillaSettings enabled.");
    }

    @Override
    public void onDisable() {
        if (settingsManager != null) settingsManager.save();
        getLogger().info("MostlyVanillaSettings disabled.");
    }

    public SettingsManager getSettingsManager() { return settingsManager; }
}
