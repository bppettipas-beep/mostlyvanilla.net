package com.mostlyvanilla.spawners;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {

    private final DonutSpawners plugin;
    private final SpawnerConfig  cfg;

    private final Map<String, SpawnerData> spawners = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File dataFile;

    public SpawnerManager(DonutSpawners plugin, SpawnerConfig cfg) {
        this.plugin  = plugin;
        this.cfg     = cfg;
    }

    // ── Startup / shutdown ────────────────────────────────────────────────────

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "spawners.json");
        if (!dataFile.exists()) return;

        try (Reader r = new FileReader(dataFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                SpawnerType type = SpawnerType.fromString(obj.get("type").getAsString());
                if (type == null) continue;
                int stack = obj.get("stack").getAsInt();
                SpawnerData data = new SpawnerData(e.getKey(), type, stack);
                if (obj.has("xp")) data.addXp(obj.get("xp").getAsInt());
                if (obj.has("storage")) {
                    for (Map.Entry<String, JsonElement> s : obj.getAsJsonObject("storage").entrySet()) {
                        try {
                            Material mat = Material.valueOf(s.getKey());
                            data.addStorage(mat, s.getValue().getAsInt());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                if (obj.has("disabledDrops")) {
                    for (JsonElement el : obj.getAsJsonArray("disabledDrops")) {
                        try { data.disableDrop(Material.valueOf(el.getAsString())); }
                        catch (IllegalArgumentException ignored) {}
                    }
                }
                spawners.put(e.getKey(), data);
            }
            plugin.getLogger().info("[MVSpawners] Loaded " + spawners.size() + " spawner(s).");
        } catch (Exception e) {
            plugin.getLogger().severe("[MVSpawners] Failed to load spawners.json: " + e.getMessage());
        }

        // Re-apply PDC to all currently loaded spawner blocks
        for (SpawnerData data : spawners.values()) {
            Location loc = data.getLocation();
            if (loc != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) applyPdc(loc, data);
        }
    }

    public void save() {
        if (!dirty && dataFile != null && dataFile.exists()) return;
        forceSave();
    }

    public void forceSave() {
        JsonObject root = new JsonObject();
        for (SpawnerData data : spawners.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", data.getType().name());
            obj.addProperty("stack", data.getStack());
            obj.addProperty("xp", data.getXp());
            JsonObject storage = new JsonObject();
            data.getStorage().forEach((mat, amt) -> storage.addProperty(mat.name(), amt));
            obj.add("storage", storage);
            com.google.gson.JsonArray disabled = new com.google.gson.JsonArray();
            data.getDisabledDrops().forEach(mat -> disabled.add(mat.name()));
            obj.add("disabledDrops", disabled);
            root.add(data.getKey(), obj);
        }
        try (Writer w = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
            gson.toJson(root, w);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("[MVSpawners] Failed to save spawners.json: " + e.getMessage());
        }
    }

    // ── Placement / removal ───────────────────────────────────────────────────

    public void placeSpawner(Location loc, SpawnerType type, int stack) {
        String key = SpawnerData.locationKey(loc);
        SpawnerData data = new SpawnerData(key, type, stack);
        spawners.put(key, data);
        applyPdc(loc, data);
        dirty = true;
    }

    public void removeSpawner(Location loc) {
        String key = SpawnerData.locationKey(loc);
        spawners.remove(key);
        dirty = true;
    }

    public boolean addStack(Location loc, int amount) {
        SpawnerData data = getSpawner(loc);
        if (data == null) return false;
        long newStack = (long) data.getStack() + amount;
        if (newStack > Integer.MAX_VALUE) newStack = Integer.MAX_VALUE;
        data.setStack((int) newStack);
        // Keep PDC in sync
        Block block = loc.getBlock();
        if (block.getState() instanceof org.bukkit.block.TileState ts) {
            ts.getPersistentDataContainer().set(DonutSpawners.KEY_STACK, PersistentDataType.INTEGER, data.getStack());
            ts.update();
        }
        dirty = true;
        return true;
    }

    public SpawnerData getSpawner(Location loc) {
        return spawners.get(SpawnerData.locationKey(loc));
    }

    public SpawnerData getNearestSpawner(Location origin, double maxDist) {
        SpawnerData nearest = null;
        double nearestDist  = maxDist * maxDist;
        for (SpawnerData data : spawners.values()) {
            Location l = data.getLocation();
            if (l == null || !l.getWorld().equals(origin.getWorld())) continue;
            double d = l.distanceSquared(origin);
            if (d < nearestDist) { nearestDist = d; nearest = data; }
        }
        return nearest;
    }

    public Collection<SpawnerData> getAllSpawners() {
        return Collections.unmodifiableCollection(spawners.values());
    }

    public int removeAll() {
        int count = spawners.size();
        for (SpawnerData data : spawners.values()) {
            Location loc = data.getLocation();
            if (loc != null && loc.getBlock().getType() == Material.SPAWNER) {
                loc.getBlock().setType(Material.AIR, false);
            }
        }
        spawners.clear();
        dirty = true;
        return count;
    }

    public void markDirty() { dirty = true; }

    // ── Production tick ───────────────────────────────────────────────────────

    /** Called every tick from the global scheduler. */
    public void tick() {
        if (spawners.isEmpty()) return;

        // Build player location snapshot once per tick
        double radius = cfg.getProximityRadius();
        double radiusSq = radius * radius;
        Map<World, List<Location>> playerLocs = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            playerLocs.computeIfAbsent(p.getWorld(), k -> new ArrayList<>())
                .add(p.getLocation());
        }

        for (SpawnerData data : spawners.values()) {
            Location loc = data.getLocation();
            if (loc == null) continue;
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

            // Proximity check
            List<Location> players = playerLocs.get(loc.getWorld());
            if (players == null) continue;
            boolean nearby = false;
            for (Location pl : players) {
                if (pl.distanceSquared(loc) <= radiusSq) { nearby = true; break; }
            }
            if (!nearby) continue;

            data.tickCounter += 4;
            if (data.tickCounter < cfg.getInterval(data.getType())) continue;
            data.tickCounter = 0;
            produce(data, loc);
            dirty = true;
        }
    }

    private void produce(SpawnerData data, Location loc) {
        double mult = isIsolated(loc) ? cfg.getIsolationBonus() : 1.0;
        double sqrtStack = Math.sqrt(data.getStack());

        // Items
        cfg.getDrops(data.getType()).forEach((mat, base) -> {
            if (!data.isDropEnabled(mat)) return;
            int amount = (int) Math.max(1, Math.floor(base * sqrtStack * mult));
            data.addStorage(mat, amount);
        });

        // XP
        int xpBase = cfg.getXpPerCycle(data.getType());
        data.addXp((int) Math.max(1, Math.floor(xpBase * sqrtStack * mult)));
    }

    private boolean isIsolated(Location loc) {
        int r = cfg.getIsolationRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Location check = loc.clone().add(dx, dy, dz);
                    if (spawners.containsKey(SpawnerData.locationKey(check))) return false;
                }
            }
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyPdc(Location loc, SpawnerData data) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof org.bukkit.block.TileState ts)) return;
        ts.getPersistentDataContainer().set(DonutSpawners.KEY_TYPE,  PersistentDataType.STRING,  data.getType().name());
        ts.getPersistentDataContainer().set(DonutSpawners.KEY_STACK, PersistentDataType.INTEGER, data.getStack());
        if (ts instanceof CreatureSpawner cs) {
            cs.setSpawnedType(data.getType().getEntityType());
        }
        ts.update(true);
    }

    public boolean isPluginSpawner(Location loc) {
        Block block = loc.getBlock();
        if (block.getType() != Material.SPAWNER) return false;
        if (!(block.getState() instanceof org.bukkit.block.TileState ts)) return false;
        return ts.getPersistentDataContainer().has(DonutSpawners.KEY_TYPE, PersistentDataType.STRING);
    }

    public SpawnerType getBlockType(Location loc) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof org.bukkit.block.TileState ts)) return null;
        return SpawnerType.fromString(
            ts.getPersistentDataContainer().get(DonutSpawners.KEY_TYPE, PersistentDataType.STRING));
    }
}
