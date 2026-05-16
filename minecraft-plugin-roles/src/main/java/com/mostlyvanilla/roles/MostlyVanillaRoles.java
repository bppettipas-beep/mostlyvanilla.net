package com.mostlyvanilla.roles;

import com.mostlyvanilla.roles.commands.RoleCommand;
import com.mostlyvanilla.roles.listeners.ChatListener;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaRoles extends JavaPlugin {

    private static MostlyVanillaRoles instance;
    private RoleManager roleManager;

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();

        roleManager = new RoleManager(this);
        roleManager.load();

        var roleCmd = getCommand("role");
        if (roleCmd != null) {
            var executor = new RoleCommand(this);
            roleCmd.setExecutor(executor);
            roleCmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("MostlyVanillaRoles enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaRoles disabled.");
    }

    public static MostlyVanillaRoles getInstance() { return instance; }
    public RoleManager getRoleManager() { return roleManager; }
}
