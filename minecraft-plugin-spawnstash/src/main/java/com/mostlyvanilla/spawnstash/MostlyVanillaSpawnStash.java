package com.mostlyvanilla.spawnstash;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaSpawnStash extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();

        RolesBridge  roles = new RolesBridge(this);
        StashManager stash = new StashManager(this);

        getCommand("spawnstash").setExecutor(new SpawnStashCommand(stash, roles));
        getCommand("delstash").setExecutor(new DelStashCommand(stash));

        getLogger().info("MostlyVanilla SpawnStash enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla SpawnStash disabled.");
    }
}
