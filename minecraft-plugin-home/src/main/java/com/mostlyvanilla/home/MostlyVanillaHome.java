package com.mostlyvanilla.home;

import com.mostlyvanilla.home.commands.*;
import com.mostlyvanilla.home.gui.HomeGui;
import com.mostlyvanilla.home.listeners.ChatListener;
import com.mostlyvanilla.home.listeners.GuiListener;
import com.mostlyvanilla.home.listeners.PlayerListener;
import com.mostlyvanilla.home.listeners.VanillaSpawnListener;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaHome extends JavaPlugin {

    private HomeManager         homeManager;
    private TeleportManager     teleportManager;
    private VanillaSpawnManager vanillaSpawnManager;

    public HomeManager         getHomeManager()         { return homeManager; }
    public VanillaSpawnManager getVanillaSpawnManager() { return vanillaSpawnManager; }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        homeManager         = new HomeManager(this);
        teleportManager     = new TeleportManager(this);
        vanillaSpawnManager = new VanillaSpawnManager(this);
        homeManager.load();

        HomeGui     homeGui     = new HomeGui(this, homeManager);
        GuiListener guiListener = new GuiListener(this, homeManager, teleportManager, homeGui);

        SetHomeCommand setHomeCmd = new SetHomeCommand(homeManager);
        DelHomeCommand delHomeCmd = new DelHomeCommand(homeManager);

        getCommand("home").setExecutor(new HomeCommand(homeGui));
        getCommand("sethome").setExecutor(setHomeCmd);
        getCommand("sethome").setTabCompleter(setHomeCmd);
        getCommand("delhome").setExecutor(delHomeCmd);
        getCommand("delhome").setTabCompleter(delHomeCmd);
        getCommand("homerole").setExecutor(new HomeRoleCommand(homeManager));
        getCommand("vanillaspawn").setExecutor(new VanillaSpawnCommand(vanillaSpawnManager, teleportManager));
        getCommand("vanillaspawnset").setExecutor(new VanillaSpawnSetCommand(vanillaSpawnManager));

        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, homeManager, guiListener), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(teleportManager), this);
        getServer().getPluginManager().registerEvents(new VanillaSpawnListener(vanillaSpawnManager), this);

        getLogger().info("MostlyVanilla Home enabled.");
    }

    @Override
    public void onDisable() {
        if (homeManager != null) homeManager.save();
        getLogger().info("MostlyVanilla Home disabled.");
    }
}
