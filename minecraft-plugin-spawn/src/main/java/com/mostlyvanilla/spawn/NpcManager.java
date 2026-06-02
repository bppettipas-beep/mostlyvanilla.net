package com.mostlyvanilla.spawn;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcManager {

    private final MostlyVanillaSpawn plugin;
    private final HologramManager holoManager;

    private final Map<Integer, String>  commands       = new HashMap<>(); // Citizens NPC id → command
    private final Map<Integer, String>  holoTexts      = new HashMap<>(); // Citizens NPC id → hologram text
    private final Map<Integer, UUID>    holoEntities   = new HashMap<>(); // Citizens NPC id → hologram entity UUID
    private File dataFile;

    public NpcManager(MostlyVanillaSpawn plugin, HologramManager holoManager) {
        this.plugin      = plugin;
        this.holoManager = holoManager;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "npc-data.yml");
        if (!dataFile.exists()) return;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);
        if (!c.isConfigurationSection("npcs")) return;

        for (String key : c.getConfigurationSection("npcs").getKeys(false)) {
            int id = Integer.parseInt(key);
            commands.put(id, c.getString("npcs." + key + ".command", ""));
            String holoText = c.getString("npcs." + key + ".hologram", null);
            if (holoText != null) holoTexts.put(id, holoText);
        }

        // Recreate hologram entities above each NPC using Citizens' stored positions
        for (Map.Entry<Integer, String> entry : holoTexts.entrySet()) {
            int id = entry.getKey();
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc == null) continue;
            Location npcLoc = npc.getStoredLocation();
            if (npcLoc == null) continue;
            UUID holoId = holoManager.createNpcHologram(npcLoc.clone().add(0, 2.4, 0), entry.getValue());
            holoEntities.put(id, holoId);
        }
    }

    public NPC create(Location loc, String skinName, String command, String hologramText) {
        // Empty name so no nameplate floats above — skin is set separately via SkinTrait
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, " ");

        npc.getOrAddTrait(SkinTrait.class).setSkinName(skinName, false);
        npc.getOrAddTrait(LookClose.class).lookClose(true);
        npc.spawn(loc);

        commands.put(npc.getId(), command);

        if (hologramText != null && !hologramText.isBlank()) {
            holoTexts.put(npc.getId(), hologramText);
            UUID holoId = holoManager.createNpcHologram(loc.clone().add(0, 2.4, 0), hologramText);
            holoEntities.put(npc.getId(), holoId);
        }

        save();
        return npc;
    }

    public boolean deleteNearest(Location loc, double radius) {
        NPC nearest = null;
        double nearestDist = radius * radius;

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!commands.containsKey(npc.getId())) continue;
            Location npcLoc = npc.getStoredLocation();
            if (npcLoc == null || !npcLoc.getWorld().equals(loc.getWorld())) continue;
            double d = npcLoc.distanceSquared(loc);
            if (d < nearestDist) { nearestDist = d; nearest = npc; }
        }

        if (nearest == null) return false;

        int id = nearest.getId();
        UUID holoId = holoEntities.remove(id);
        if (holoId != null) holoManager.removeNpcHologram(holoId);

        commands.remove(id);
        holoTexts.remove(id);
        nearest.destroy();
        save();
        return true;
    }

    public String getCommand(int npcId) {
        return commands.get(npcId);
    }

    private void save() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "npc-data.yml");
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<Integer, String> e : commands.entrySet()) {
            int id = e.getKey();
            c.set("npcs." + id + ".command", e.getValue());
            if (holoTexts.containsKey(id)) c.set("npcs." + id + ".hologram", holoTexts.get(id));
        }
        try { c.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save npc-data.yml"); }
    }
}
