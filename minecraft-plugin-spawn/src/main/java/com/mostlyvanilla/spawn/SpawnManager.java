package com.mostlyvanilla.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpawnManager {

    private final MostlyVanillaSpawn plugin;
    private final World spawnWorld;
    private Location spawnPoint;
    private File dataFile;

    private final Set<UUID>             pendingTeleports = new HashSet<>();
    private final Map<UUID, BukkitTask> pendingSpawns    = new HashMap<>();

    // Drop zone — the rectangle below spawn that catches falling players
    private Location dropPoint1      = null; // temporary until both points set
    private boolean  dropZoneActive  = false;
    private double   dropY, dropMinX, dropMaxX, dropMinZ, dropMaxZ;

    public SpawnManager(MostlyVanillaSpawn plugin, World spawnWorld) {
        this.plugin = plugin;
        this.spawnWorld = spawnWorld;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "spawn.yml");
        if (!dataFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);
        if (c.contains("spawn.x")) {
            spawnPoint = new Location(
                spawnWorld,
                c.getDouble("spawn.x"),
                c.getDouble("spawn.y"),
                c.getDouble("spawn.z"),
                (float) c.getDouble("spawn.yaw"),
                (float) c.getDouble("spawn.pitch")
            );
        }
        if (c.getBoolean("drop-zone.active", false)) {
            dropZoneActive = true;
            dropY    = c.getDouble("drop-zone.y");
            dropMinX = c.getDouble("drop-zone.min-x");
            dropMaxX = c.getDouble("drop-zone.max-x");
            dropMinZ = c.getDouble("drop-zone.min-z");
            dropMaxZ = c.getDouble("drop-zone.max-z");
        }
    }

    public void setSpawn(Location loc) {
        spawnPoint = loc.clone();
        save();
    }

    // ── Drop zone ─────────────────────────────────────────────────────────────

    /** Sets the first corner. Returns false if not in the spawn world. */
    public boolean setDropPoint1(Location loc) {
        if (!isInSpawnWorld(loc)) return false;
        dropPoint1 = loc.clone();
        return true;
    }

    /**
     * Sets the second corner and activates the drop zone.
     * Returns false if point 1 hasn't been set yet or loc isn't in the spawn world.
     */
    public boolean setDropPoint2(Location loc) {
        if (!isInSpawnWorld(loc)) return false;
        if (dropPoint1 == null) return false;
        dropY    = Math.min(dropPoint1.getY(), loc.getY());
        dropMinX = Math.min(dropPoint1.getX(), loc.getX());
        dropMaxX = Math.max(dropPoint1.getX(), loc.getX());
        dropMinZ = Math.min(dropPoint1.getZ(), loc.getZ());
        dropMaxZ = Math.max(dropPoint1.getZ(), loc.getZ());
        dropZoneActive = true;
        dropPoint1 = null;
        save();
        return true;
    }

    public boolean hasDropPoint1()    { return dropPoint1 != null; }
    public boolean isDropZoneActive() { return dropZoneActive; }

    /** Returns true if this location is below and within the drop zone rectangle. */
    public boolean isBelowDropZone(Location loc) {
        if (!dropZoneActive) return false;
        return loc.getY() < dropY
            && loc.getX() >= dropMinX && loc.getX() <= dropMaxX
            && loc.getZ() >= dropMinZ && loc.getZ() <= dropMaxZ;
    }

    public String dropZoneDescription() {
        return String.format("Y<%.1f, X[%.1f–%.1f], Z[%.1f–%.1f]", dropY, dropMinX, dropMaxX, dropMinZ, dropMaxZ);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void save() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "spawn.yml");
        YamlConfiguration c = new YamlConfiguration();
        if (spawnPoint != null) {
            c.set("spawn.x",     spawnPoint.getX());
            c.set("spawn.y",     spawnPoint.getY());
            c.set("spawn.z",     spawnPoint.getZ());
            c.set("spawn.yaw",   spawnPoint.getYaw());
            c.set("spawn.pitch", spawnPoint.getPitch());
        }
        c.set("drop-zone.active", dropZoneActive);
        if (dropZoneActive) {
            c.set("drop-zone.y",     dropY);
            c.set("drop-zone.min-x", dropMinX);
            c.set("drop-zone.max-x", dropMaxX);
            c.set("drop-zone.min-z", dropMinZ);
            c.set("drop-zone.max-z", dropMaxZ);
        }
        try { c.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save spawn.yml: " + e.getMessage()); }
    }

    // ── Teleport flags ────────────────────────────────────────────────────────

    public void flagTeleport(UUID uuid)             { pendingTeleports.add(uuid); }
    public boolean consumeTeleportFlag(UUID uuid)   { return pendingTeleports.remove(uuid); }

    public void addPendingSpawn(UUID uuid, BukkitTask task) {
        BukkitTask existing = pendingSpawns.put(uuid, task);
        if (existing != null) existing.cancel();
    }

    public BukkitTask removePendingSpawn(UUID uuid) { return pendingSpawns.remove(uuid); }
    public boolean    hasPendingSpawn(UUID uuid)     { return pendingSpawns.containsKey(uuid); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Location getSpawnPoint()               { return spawnPoint != null ? spawnPoint.clone() : null; }
    public World    getSpawnWorld()               { return spawnWorld; }
    public boolean  isSpawnSet()                  { return spawnPoint != null; }
    public boolean  isInSpawnWorld(Location loc)  { return loc != null && spawnWorld.equals(loc.getWorld()); }
}
