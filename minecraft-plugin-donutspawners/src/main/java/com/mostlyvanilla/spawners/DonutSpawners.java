package com.mostlyvanilla.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class DonutSpawners extends JavaPlugin {

    // PDC keys shared across the plugin
    public static NamespacedKey KEY_TYPE;
    public static NamespacedKey KEY_STACK;

    private SpawnerConfig  spawnerConfig;
    private EconomyBridge  economyBridge;
    private SpawnerManager spawnerManager;
    private SpawnerGui     spawnerGui;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        KEY_TYPE  = new NamespacedKey(this, "type");
        KEY_STACK = new NamespacedKey(this, "stack");

        spawnerConfig  = new SpawnerConfig(this);
        economyBridge  = new EconomyBridge(this);
        spawnerManager = new SpawnerManager(this, spawnerConfig);
        spawnerGui     = new SpawnerGui(spawnerManager, spawnerConfig, economyBridge);

        spawnerManager.load();

        // Register listeners
        SpawnerListener spawnerListener = new SpawnerListener(this, spawnerManager, spawnerConfig, spawnerGui);
        getServer().getPluginManager().registerEvents(spawnerListener, this);

        // Convert any vanilla spawners already in loaded chunks at startup
        getServer().getScheduler().runTask(this, () -> {
            int count = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    int before = count;
                    spawnerListener.convertChunk(chunk);
                    // count is approximate — just log that it ran
                }
            }
            getLogger().info("[MVSpawners] Startup vanilla-spawner scan complete.");
        });
        getServer().getPluginManager().registerEvents(spawnerGui, this);


        // Register command (/ds, /spawner, /donutspawner all map here)
        var cmd = new SpawnerCommand(this, spawnerManager, spawnerConfig);
        getCommand("ds").setExecutor(cmd);
        getCommand("ds").setTabCompleter(cmd);

        // Global production tick — runs every 4 ticks; SpawnerManager.tick() compensates counters
        getServer().getScheduler().runTaskTimer(this, spawnerManager::tick, 4L, 4L);

        // Periodic async save
        long saveInterval = (long) spawnerConfig.getSaveInterval() * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this,
            spawnerManager::forceSave, saveInterval, saveInterval);

        getLogger().info("MVSpawners enabled!");
    }

    @Override
    public void onDisable() {
        spawnerManager.forceSave();
        getLogger().info("MVSpawners disabled.");
    }

    public SpawnerConfig  getSpawnerConfig()  { return spawnerConfig; }
    public SpawnerManager getSpawnerManager() { return spawnerManager; }
    public EconomyBridge  getEconomyBridge()  { return economyBridge; }
}
