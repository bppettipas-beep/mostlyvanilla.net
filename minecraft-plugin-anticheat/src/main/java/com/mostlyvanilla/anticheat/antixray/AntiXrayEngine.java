package com.mostlyvanilla.anticheat.antixray;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class AntiXrayEngine {

    private final MostlyVanillaAnticheat plugin;
    private final Set<Material> hiddenOres = EnumSet.noneOf(Material.class);
    private final BlockData fakeStone;
    private final BlockData fakeDeepslate;
    private final BlockData fakeNetherrack;
    private int revealRadius;
    private int maxHideY;

    public AntiXrayEngine(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
        fakeStone      = Material.STONE.createBlockData();
        fakeDeepslate  = Material.DEEPSLATE.createBlockData();
        fakeNetherrack = Material.NETHERRACK.createBlockData();
        reload();
    }

    public void reload() {
        hiddenOres.clear();
        revealRadius = plugin.getConfig().getInt("anti-xray.reveal-radius", 2);
        maxHideY     = plugin.getConfig().getInt("anti-xray.max-hide-y", 64);
        for (String name : plugin.getConfig().getStringList("anti-xray.hidden-ores")) {
            try { hiddenOres.add(Material.valueOf(name)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public boolean isHiddenOre(Material m) {
        return hiddenOres.contains(m);
    }

    /** Async-safe: runs on chunk-load scheduler call, operates only on ChunkSnapshot via sendBlockChange. */
    public void obfuscateChunk(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        var snapshot = chunk.getChunkSnapshot(true, false, false);
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int minY = world.getMinHeight();
            int maxY = Math.min(maxHideY, world.getMaxHeight());

            // Collect all ores for this chunk first (async, safe via ChunkSnapshot)
            List<long[]> found = new ArrayList<>();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int y = minY; y < maxY; y++) {
                        Material mat = snapshot.getBlockType(lx, y, lz);
                        if (!hiddenOres.contains(mat)) continue;
                        int wx = baseX + lx;
                        int wz = baseZ + lz;
                        long key = PlayerData.encodePos(wx, y, wz);
                        found.add(new long[]{wx, y, wz, key});
                    }
                }
            }
            if (found.isEmpty()) return;

            // ONE sync task for the entire chunk instead of one per ore
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                PlayerData data = plugin.getData(player.getUniqueId());
                for (long[] pos : found) {
                    long key = pos[3];
                    if (data.fakeBlocks.contains(key)) continue;
                    int wx = (int) pos[0];
                    int wy = (int) pos[1];
                    int wz = (int) pos[2];
                    player.sendBlockChange(new Location(world, wx, wy, wz), getFakeBlock(world, wy));
                    data.fakeBlocks.add(key);
                }
            });
        });
    }

    /** Called on player movement; reveals real blocks close to player. */
    public void revealAround(Player player) {
        PlayerData data = plugin.getData(player.getUniqueId());
        Location loc = player.getLocation();

        // Only re-run when player moved at least 1 block
        if (data.lastRevealedAt != null
                && data.lastRevealedAt.getBlockX() == loc.getBlockX()
                && data.lastRevealedAt.getBlockY() == loc.getBlockY()
                && data.lastRevealedAt.getBlockZ() == loc.getBlockZ()) {
            return;
        }
        data.lastRevealedAt = loc.clone();

        World world = loc.getWorld();
        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();

        for (int dx = -revealRadius; dx <= revealRadius; dx++) {
            for (int dy = -revealRadius; dy <= revealRadius; dy++) {
                for (int dz = -revealRadius; dz <= revealRadius; dz++) {
                    int wx = px + dx;
                    int wy = py + dy;
                    int wz = pz + dz;
                    long key = PlayerData.encodePos(wx, wy, wz);
                    if (!data.fakeBlocks.remove(key)) continue;
                    Location blockLoc = new Location(world, wx, wy, wz);
                    player.sendBlockChange(blockLoc, world.getBlockAt(blockLoc).getBlockData());
                }
            }
        }
    }

    public void revealBlock(Player player, Location loc) {
        PlayerData data = plugin.getData(player.getUniqueId());
        long key = PlayerData.encodePos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (data.fakeBlocks.remove(key)) {
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    public void clearPlayer(Player player) {
        plugin.getData(player.getUniqueId()).fakeBlocks.clear();
    }

    private BlockData getFakeBlock(World world, int y) {
        return switch (world.getEnvironment()) {
            case NETHER -> fakeNetherrack;
            default     -> y < 0 ? fakeDeepslate : fakeStone;
        };
    }
}
