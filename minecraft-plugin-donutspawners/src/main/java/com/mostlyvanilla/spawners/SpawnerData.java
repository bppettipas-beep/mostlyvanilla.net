package com.mostlyvanilla.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpawnerData {

    private final String        key;
    private final SpawnerType   type;
    private int                 stack;
    private final Map<Material, Integer> storage = new HashMap<>();
    private final Set<Material> disabledDrops    = new HashSet<>();
    private int                 xp;

    // in-memory only — not persisted
    transient int  tickCounter   = 0;
    transient Location cachedLoc = null;

    public SpawnerData(String key, SpawnerType type, int stack) {
        this.key   = key;
        this.type  = type;
        this.stack = stack;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String      getKey()   { return key; }
    public SpawnerType getType()  { return type; }
    public int         getStack() { return stack; }
    public int         getXp()    { return xp; }

    public Map<Material, Integer> getStorage() {
        return Collections.unmodifiableMap(storage);
    }

    public void setStack(int stack) { this.stack = stack; }

    // ── Drop filter ──────────────────────────────────────────────────────────

    public boolean isDropEnabled(Material mat) { return !disabledDrops.contains(mat); }
    public void toggleDrop(Material mat) {
        if (!disabledDrops.remove(mat)) disabledDrops.add(mat);
    }
    public void disableDrop(Material mat)  { disabledDrops.add(mat); }
    public Set<Material> getDisabledDrops() { return Collections.unmodifiableSet(disabledDrops); }

    // ── Storage mutations ────────────────────────────────────────────────────

    public void addStorage(Material mat, int amount) {
        storage.merge(mat, amount, Integer::sum);
    }

    public void addXp(int amount) { xp += amount; }

    /** Removes and returns all stored items. */
    public Map<Material, Integer> drainStorage() {
        Map<Material, Integer> out = new HashMap<>(storage);
        storage.clear();
        return out;
    }

    /** Returns all stored items WITHOUT clearing (for sell). */
    public Map<Material, Integer> peekStorage() {
        return new HashMap<>(storage);
    }

    /** Removes and returns all stored XP. */
    public int drainXp() {
        int out = xp;
        xp = 0;
        return out;
    }

    public boolean isEmpty() {
        return storage.isEmpty() && xp == 0;
    }

    // ── Location helper ──────────────────────────────────────────────────────

    public Location getLocation() {
        if (cachedLoc == null) cachedLoc = parseLocation(key);
        return cachedLoc;
    }

    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + ","
            + loc.getBlockX() + ","
            + loc.getBlockY() + ","
            + loc.getBlockZ();
    }

    public static Location parseLocation(String key) {
        String[] p = key.split(",");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w,
                Integer.parseInt(p[1]),
                Integer.parseInt(p[2]),
                Integer.parseInt(p[3]));
        } catch (NumberFormatException e) { return null; }
    }
}
