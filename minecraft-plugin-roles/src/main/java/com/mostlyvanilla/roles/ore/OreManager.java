package com.mostlyvanilla.roles.ore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

public class OreManager {

    private record BlockRecord(Location loc, Material original) {}

    // Per-player list of blocks placed by /spawnore (and what was there before)
    private final Map<UUID, List<BlockRecord>> placed = new HashMap<>();

    private static final Set<Material> STONE_HOSTS = EnumSet.of(
        Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE
    );
    private static final Set<Material> DEEPSLATE_HOSTS = EnumSet.of(
        Material.DEEPSLATE, Material.TUFF
    );
    private static final Set<Material> NETHER_HOSTS = EnumSet.of(
        Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE
    );

    public record OreSpec(Material stoneOre, Material deepslateOre, Material netherOre, int veinSize) {}

    public static final Map<String, OreSpec> ORES = new LinkedHashMap<>();
    static {
        ORES.put("coal",           new OreSpec(Material.COAL_ORE,     Material.DEEPSLATE_COAL_ORE,     null,                      8));
        ORES.put("iron",           new OreSpec(Material.IRON_ORE,     Material.DEEPSLATE_IRON_ORE,     null,                      6));
        ORES.put("copper",         new OreSpec(Material.COPPER_ORE,   Material.DEEPSLATE_COPPER_ORE,   null,                      7));
        ORES.put("gold",           new OreSpec(Material.GOLD_ORE,     Material.DEEPSLATE_GOLD_ORE,     Material.NETHER_GOLD_ORE,  4));
        ORES.put("lapis",          new OreSpec(Material.LAPIS_ORE,    Material.DEEPSLATE_LAPIS_ORE,    null,                      5));
        ORES.put("redstone",       new OreSpec(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, null,                      6));
        ORES.put("emerald",        new OreSpec(Material.EMERALD_ORE,  Material.DEEPSLATE_EMERALD_ORE,  null,                      3));
        ORES.put("diamond",        new OreSpec(Material.DIAMOND_ORE,  Material.DEEPSLATE_DIAMOND_ORE,  null,                      4));
        ORES.put("quartz",         new OreSpec(null,                  null,                            Material.NETHER_QUARTZ_ORE,6));
        ORES.put("nether_gold",    new OreSpec(null,                  null,                            Material.NETHER_GOLD_ORE,  5));
        ORES.put("ancient_debris", new OreSpec(null,                  null,                            Material.ANCIENT_DEBRIS,   2));
    }

    private final Random random = new Random();

    /**
     * Spawns a vein for the given player, records all placed blocks for later removal.
     * Returns the number of blocks placed (0 if no valid hosts found).
     */
    public int spawnVein(UUID playerId, Location center, OreSpec spec) {
        World world = center.getWorld();
        int radius = 3;
        List<Block> candidates = new ArrayList<>();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material mat = b.getType();
                    if      (STONE_HOSTS.contains(mat)     && spec.stoneOre()    != null) candidates.add(b);
                    else if (DEEPSLATE_HOSTS.contains(mat) && spec.deepslateOre() != null) candidates.add(b);
                    else if (NETHER_HOSTS.contains(mat)    && spec.netherOre()   != null) candidates.add(b);
                }
            }
        }

        Collections.shuffle(candidates, random);
        List<BlockRecord> records = placed.computeIfAbsent(playerId, k -> new ArrayList<>());
        int placed = 0;
        for (int i = 0; i < Math.min(spec.veinSize(), candidates.size()); i++) {
            Block b = candidates.get(i);
            Material original = b.getType();
            Material ore = oreFor(b.getType(), spec);
            b.setType(ore, false);
            records.add(new BlockRecord(b.getLocation().clone(), original));
            placed++;
        }
        return placed;
    }

    /**
     * Restores all blocks placed by this player's /spawnore calls.
     * Returns the number of blocks restored (0 if none were tracked).
     */
    public int removeOres(UUID playerId) {
        List<BlockRecord> records = placed.remove(playerId);
        if (records == null || records.isEmpty()) return 0;
        for (BlockRecord r : records) {
            r.loc().getBlock().setType(r.original(), false);
        }
        return records.size();
    }

    public boolean hasOres(UUID playerId) {
        List<BlockRecord> records = placed.get(playerId);
        return records != null && !records.isEmpty();
    }

    private static Material oreFor(Material host, OreSpec spec) {
        if (STONE_HOSTS.contains(host))     return spec.stoneOre();
        if (DEEPSLATE_HOSTS.contains(host)) return spec.deepslateOre();
        return spec.netherOre();
    }
}
