package com.mostlyvanilla.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class VanillaSpawnManager {

    private final MostlyVanillaHome plugin;

    private String worldName;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private double tpX, tpY, tpZ;
    private float  tpYaw, tpPitch;
    private boolean configured = false;

    public VanillaSpawnManager(MostlyVanillaHome plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        if (!plugin.getConfig().isSet("vanilla-spawn.world")) return;
        worldName = plugin.getConfig().getString("vanilla-spawn.world");
        minX  = plugin.getConfig().getInt("vanilla-spawn.min-x");
        minY  = plugin.getConfig().getInt("vanilla-spawn.min-y");
        minZ  = plugin.getConfig().getInt("vanilla-spawn.min-z");
        maxX  = plugin.getConfig().getInt("vanilla-spawn.max-x");
        maxY  = plugin.getConfig().getInt("vanilla-spawn.max-y");
        maxZ  = plugin.getConfig().getInt("vanilla-spawn.max-z");
        tpX   = plugin.getConfig().getDouble("vanilla-spawn.tp-x");
        tpY   = plugin.getConfig().getDouble("vanilla-spawn.tp-y");
        tpZ   = plugin.getConfig().getDouble("vanilla-spawn.tp-z");
        tpYaw   = (float) plugin.getConfig().getDouble("vanilla-spawn.tp-yaw");
        tpPitch = (float) plugin.getConfig().getDouble("vanilla-spawn.tp-pitch");
        configured = true;
    }

    public void setSpawn(Player player) {
        Location loc = player.getLocation();
        worldName = loc.getWorld().getName();

        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        // 10×10×10 zone centered on op's block position
        minX = cx - 5; maxX = cx + 4;
        minY = cy - 5; maxY = cy + 4;
        minZ = cz - 5; maxZ = cz + 4;

        tpX     = loc.getX();
        tpY     = loc.getY();
        tpZ     = loc.getZ();
        tpYaw   = loc.getYaw();
        tpPitch = loc.getPitch();
        configured = true;

        plugin.getConfig().set("vanilla-spawn.world",    worldName);
        plugin.getConfig().set("vanilla-spawn.min-x",    minX);
        plugin.getConfig().set("vanilla-spawn.min-y",    minY);
        plugin.getConfig().set("vanilla-spawn.min-z",    minZ);
        plugin.getConfig().set("vanilla-spawn.max-x",    maxX);
        plugin.getConfig().set("vanilla-spawn.max-y",    maxY);
        plugin.getConfig().set("vanilla-spawn.max-z",    maxZ);
        plugin.getConfig().set("vanilla-spawn.tp-x",     tpX);
        plugin.getConfig().set("vanilla-spawn.tp-y",     tpY);
        plugin.getConfig().set("vanilla-spawn.tp-z",     tpZ);
        plugin.getConfig().set("vanilla-spawn.tp-yaw",   (double) tpYaw);
        plugin.getConfig().set("vanilla-spawn.tp-pitch", (double) tpPitch);
        plugin.saveConfig();
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
