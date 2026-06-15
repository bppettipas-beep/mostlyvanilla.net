package com.mostlyvanilla.spawn;

import com.mostlyvanilla.spawn.commands.BlockHereCommand;
import com.mostlyvanilla.spawn.commands.SpawnDropCommand;
import com.mostlyvanilla.spawn.commands.HologramCommand;
import com.mostlyvanilla.spawn.commands.NpcCommand;
import com.mostlyvanilla.spawn.commands.LeaveCommand;
import com.mostlyvanilla.spawn.commands.SpawnCommand;
import com.mostlyvanilla.spawn.commands.VanillaSpawnCommand;
import com.mostlyvanilla.spawn.commands.VanillaSpawnSetCommand;
import com.mostlyvanilla.spawn.listeners.NpcListener;
import com.mostlyvanilla.spawn.listeners.SpawnListener;
import com.mostlyvanilla.spawn.listeners.VanillaSpawnListener;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaSpawn extends JavaPlugin {

    private SpawnManager        spawnManager;
    private HologramManager     hologramManager;
    private NpcManager          npcManager;
    private VanillaSpawnManager vanillaSpawnManager;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();

        World spawnWorld = createSpawnWorld();

        spawnManager        = new SpawnManager(this, spawnWorld);
        hologramManager     = new HologramManager(this);
        vanillaSpawnManager = new VanillaSpawnManager(this);

        spawnManager.load();
        hologramManager.load(spawnWorld); // must load before NpcManager so it can create NPC holograms

        boolean citizensPresent = getServer().getPluginManager().getPlugin("Citizens") != null;
        if (citizensPresent) {
            npcManager = new NpcManager(this, hologramManager);
            // Delay one tick so Citizens has finished spawning NPCs into the world
            getServer().getScheduler().runTask(this, () -> npcManager.load());
            getServer().getPluginManager().registerEvents(new NpcListener(this), this);
            getLogger().info("Citizens found — NPCs enabled.");
        } else {
            getLogger().warning("Citizens not found — /npc will not work.");
        }

        CombatTracker combatTracker = new CombatTracker();

        SpawnCommand spawnCmd = new SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCmd);
        getCommand("setspawn").setExecutor(spawnCmd);
        getCommand("blockhere").setExecutor(new BlockHereCommand(this));
        getCommand("createnpc").setExecutor(new NpcCommand(this));
        getCommand("spawndrop1").setExecutor(new SpawnDropCommand(this, 1));
        getCommand("spawndrop2").setExecutor(new SpawnDropCommand(this, 2));
        getCommand("hologram").setExecutor(new HologramCommand(this));
        getCommand("leave").setExecutor(new LeaveCommand(this));
        getCommand("vanillaspawn").setExecutor(new VanillaSpawnCommand(this, combatTracker));
        getCommand("vanillaspawnset").setExecutor(new VanillaSpawnSetCommand(this));

        getServer().getPluginManager().registerEvents(combatTracker, this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new VanillaSpawnListener(this), this);

        getLogger().info("Spawn world ready: " + spawnWorld.getName()
            + (spawnManager.isSpawnSet() ? " (spawn point set)" : " (no spawn point — use /setspawn)"));
    }

    private World createSpawnWorld() {
        World existing = getServer().getWorld("mv_spawn");
        if (existing != null) return existing;

        WorldCreator creator = new WorldCreator("mv_spawn");
        creator.generator(new VoidGenerator());
        creator.environment(World.Environment.NORMAL);
        World world = creator.createWorld();

        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING,       false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,     false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE,      false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setTime(6000);
            world.setPVP(false);
        }
        return world;
    }

    public SpawnManager        getSpawnManager()        { return spawnManager; }
    public HologramManager     getHologramManager()     { return hologramManager; }
    public NpcManager          getNpcManager()          { return npcManager; }
    public VanillaSpawnManager getVanillaSpawnManager() { return vanillaSpawnManager; }
}
