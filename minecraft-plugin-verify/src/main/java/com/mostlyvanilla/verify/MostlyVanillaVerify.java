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

        var linkCmd = getCommand("link");
        if (linkCmd != null) linkCmd.setExecutor(new DiscordCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        getLogger().info("MostlyVanillaVerify enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaVerify disabled.");
    }

    public static MostlyVanillaVerify getInstance() { return instance; }
    public ApiClient getApiClient() { return apiClient; }
}
