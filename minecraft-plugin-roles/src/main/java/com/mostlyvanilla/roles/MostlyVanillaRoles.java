package com.mostlyvanilla.roles;

import com.mostlyvanilla.roles.commands.RoleCommand;
import com.mostlyvanilla.roles.listeners.ChatListener;
import com.mostlyvanilla.roles.listeners.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaRoles extends JavaPlugin {

    private static MostlyVanillaRoles instance;
    private RoleManager roleManager;
    private TabManager tabManager;

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        saveDefaultConfig();

        roleManager = new RoleManager(this);
        roleManager.load();

        tabManager = new TabManager(this);
        tabManager.setupPingObjective();
        tabManager.start();

        var roleCmd = getCommand("role");
        if (roleCmd != null) {
            var executor = new RoleCommand(this);
            roleCmd.setExecutor(executor);
            roleCmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(tabManager, this);

        getLogger().info("MostlyVanillaRoles enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaRoles disabled.");
    }

    public static MostlyVanillaRoles getInstance() { return instance; }
    public RoleManager getRoleManager() { return roleManager; }
    public TabManager getTabManager() { return tabManager; }
}
