package com.mostlyvanilla.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class VanillaSpawnManager {

    private final MostlyVanillaSpawn plugin;

    private String worldName;
    private int    minX, minY, minZ;
    private int    maxX, maxY, maxZ;
    private double tpX, tpY, tpZ;
    private float  tpYaw, tpPitch;
    private boolean configured = false;

    private File dataFile;

    public VanillaSpawnManager(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "vanilla-spawn.yml");
        if (!dataFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);
        if (!c.contains("world")) return;

        worldName = c.getString("world");
        minX  = c.getInt("min-x");
        minY  = c.getInt("min-y");
        minZ  = c.getInt("min-z");
        maxX  = c.getInt("max-x");
        maxY  = c.getInt("max-y");
        maxZ  = c.getInt("max-z");
        tpX   = c.getDouble("tp-x");
        tpY   = c.getDouble("tp-y");
        tpZ   = c.getDouble("tp-z");
        tpYaw   = (float) c.getDouble("tp-yaw");
        tpPitch = (float) c.getDouble("tp-pitch");
        configured = true;
    }

    public void setSpawn(Player player) {
        Location loc = player.getLocation();
        worldName = loc.getWorld().getName();

        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        // 10×10×10 zone centered on the op's block position
        minX = cx - 5; maxX = cx + 4;
        minY = cy - 5; maxY = cy + 4;
        minZ = cz - 5; maxZ = cz + 4;

        tpX     = loc.getX();
        tpY     = loc.getY();
        tpZ     = loc.getZ();
        tpYaw   = loc.getYaw();
        tpPitch = loc.getPitch();
        configured = true;

        save();
    }

    private void save() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "vanilla-spawn.yml");
        YamlConfiguration c = new YamlConfiguration();
        c.set("world",    worldName);
        c.set("min-x",    minX);
        c.set("min-y",    minY);
        c.set("min-z",    minZ);
        c.set("max-x",    maxX);
        c.set("max-y",    maxY);
        c.set("max-z",    maxZ);
        c.set("tp-x",     tpX);
        c.set("tp-y",     tpY);
        c.set("tp-z",     tpZ);
        c.set("tp-yaw",   (double) tpYaw);
        c.set("tp-pitch", (double) tpPitch);
        try { c.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save vanilla-spawn.yml: " + e.getMessage()); }
    }

    public boolean isConfigured() { return configured; }

    public boolean isInZone(Location loc) {
        if (!configured || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        return bx >= minX && bx <= maxX
            && by >= minY && by <= maxY
            && bz >= minZ && bz <= maxZ;
    }

    public Location getTeleportLocation() {
        if (!configured) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, tpX, tpY, tpZ, tpYaw, tpPitch);
    }
}
