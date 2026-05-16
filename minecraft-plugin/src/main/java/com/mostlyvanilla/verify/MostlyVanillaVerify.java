package com.mostlyvanilla.verify;

import com.mostlyvanilla.verify.commands.DiscordCommand;
import com.mostlyvanilla.verify.listeners.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaVerify extends JavaPlugin {

    private static MostlyVanillaVerify instance;
    private ApiClient apiClient;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        apiClient = new ApiClient(
            getConfig().getString("bot-api-url", "http://localhost:3000"),
            getConfig().getString("api-secret", "")
        );

        var discordCmd = getCommand("discord");
        if (discordCmd != null) discordCmd.setExecutor(new DiscordCommand(this));

        if (getConfig().getBoolean("remind-on-join", true)) {
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        }

        getLogger().info("MostlyVanillaVerify enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaVerify disabled.");
    }

    public static MostlyVanillaVerify getInstance() { return instance; }
    public ApiClient getApiClient() { return apiClient; }
}
