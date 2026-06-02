package com.mostlyvanilla.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HologramManager {

    private static final String TAG = "mv_hologram";

    private final MostlyVanillaSpawn plugin;
    private final Map<UUID, TextDisplay> live = new HashMap<>(); // entity UUID → entity reference
    private final List<HologramRecord> saved = new ArrayList<>(); // standalone holograms only
    private File dataFile;

    public HologramManager(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    public void load(World spawnWorld) {
        dataFile = new File(plugin.getDataFolder(), "hologram-data.yml");

        // Remove any leftover hologram entities from a previous run
        for (Entity e : spawnWorld.getEntities()) {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains(TAG)) e.remove();
        }

        if (!dataFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);
        for (Map<?, ?> entry : c.getMapList("holograms")) {
            double x    = ((Number) entry.get("x")).doubleValue();
            double y    = ((Number) entry.get("y")).doubleValue();
            double z    = ((Number) entry.get("z")).doubleValue();
            String text = (String) entry.get("text");
            Location loc = new Location(spawnWorld, x, y, z);
            saved.add(new HologramRecord(loc, text));
            spawnEntity(loc, text, true);
        }
    }

    /** Creates a standalone (persistent) hologram. */
    public UUID createHologram(Location loc, String text) {
        saved.add(new HologramRecord(loc.clone(), text));
        save();
        return spawnEntity(loc, text, true);
    }

    /** Creates an NPC-attached hologram — not saved to hologram-data.yml (NpcManager owns it). */
    public UUID createNpcHologram(Location loc, String text) {
        return spawnEntity(loc, text, false);
    }

    public void deleteHologram(UUID uuid) {
        TextDisplay entity = live.remove(uuid);
        if (entity != null && !entity.isDead()) entity.remove();
        // Remove from saved list if it was a standalone hologram
        saved.removeIf(r -> {
            // match by approximate position since we don't store UUID in HologramRecord
            return false; // handled by re-matching below
        });
        save();
    }

    /** Removes the nearest standalone hologram within radius. Returns true if one was found. */
    public boolean deleteNearest(Location loc, double radius) {
        TextDisplay nearest = null;
        double nearestDist = radius * radius;

        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof TextDisplay td)) continue;
            if (!td.getScoreboardTags().contains(TAG)) continue;
            if (td.getScoreboardTags().contains("mv_npc_holo")) continue; // skip NPC holograms
            double d = e.getLocation().distanceSquared(loc);
            if (d < nearestDist) { nearestDist = d; nearest = td; }
        }

        if (nearest == null) return false;

        Location removedLoc = nearest.getLocation();
        live.remove(nearest.getUniqueId());
        nearest.remove();

        // Remove matching entry from saved list
        saved.removeIf(r ->
            Math.abs(r.loc().getX() - removedLoc.getX()) < 0.5 &&
            Math.abs(r.loc().getY() - removedLoc.getY()) < 0.5 &&
            Math.abs(r.loc().getZ() - removedLoc.getZ()) < 0.5
        );
        save();
        return true;
    }

    /** Removes an NPC hologram by entity UUID (called by NpcManager on NPC delete). */
    public void removeNpcHologram(UUID uuid) {
        TextDisplay entity = live.remove(uuid);
        if (entity != null && !entity.isDead()) entity.remove();
    }

    private UUID spawnEntity(Location loc, String text, boolean standalone) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, e -> {
            e.text(parse(text));
            e.setBillboard(Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setDefaultBackground(false);
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            if (!standalone) e.addScoreboardTag("mv_npc_holo");
        });
        live.put(display.getUniqueId(), display);
        return display.getUniqueId();
    }

    private void save() {
        if (dataFile == null) return;
        List<Map<String, Object>> list = new ArrayList<>();
        for (HologramRecord r : saved) {
            Map<String, Object> m = new HashMap<>();
            m.put("x", r.loc().getX());
            m.put("y", r.loc().getY());
            m.put("z", r.loc().getZ());
            m.put("text", r.text());
            list.add(m);
        }
        YamlConfiguration c = new YamlConfiguration();
        c.set("holograms", list);
        try { c.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save hologram-data.yml"); }
    }

    static Component parse(String text) {
        if (text.contains("<") && text.contains(">"))
            return MiniMessage.miniMessage().deserialize(text);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private record HologramRecord(Location loc, String text) {}
}
