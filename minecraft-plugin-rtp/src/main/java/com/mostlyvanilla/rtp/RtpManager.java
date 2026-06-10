package com.mostlyvanilla.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RtpManager {

    private final MostlyVanillaRtp plugin;
    private final TeleportManager  teleportManager;
    private final Set<String>      disabledWorlds = new HashSet<>();
    private final Random           random         = new Random();

    public RtpManager(MostlyVanillaRtp plugin, TeleportManager teleportManager) {
        this.plugin          = plugin;
        this.teleportManager = teleportManager;
        disabledWorlds.addAll(plugin.getConfig().getStringList("disabled-worlds"));
    }

    // ── Disabled world management ─────────────────────────────────────────────

    public boolean isDisabled(World world) { return disabledWorlds.contains(world.getName()); }

    public boolean toggleDisabled(String worldName) {
        boolean nowDisabled = disabledWorlds.add(worldName);
        if (!nowDisabled) disabledWorlds.remove(worldName);
        plugin.getConfig().set("disabled-worlds", new ArrayList<>(disabledWorlds));
        plugin.saveConfig();
        return nowDisabled;
    }

    public List<String> getDisabledWorlds() { return new ArrayList<>(disabledWorlds); }

    // ── RTP entry point ───────────────────────────────────────────────────────

    public void startRtp(Player player, World world) {
        if (isDisabled(world)) {
            player.sendMessage(Component.text("That dimension is currently disabled.", NamedTextColor.RED));
            return;
        }

        // Start countdown immediately — search runs in parallel
        teleportManager.startCountdown(player);

        // Build candidate list on the main thread (isChunkGenerated is safe here)
        int maxAttempts = plugin.getConfig().getInt("max-attempts", 20);
        int minR        = plugin.getConfig().getInt("min-radius",   1000);
        int maxR        = plugin.getConfig().getInt("max-radius",   10000);

        List<int[]> generated   = new ArrayList<>();
        List<int[]> ungenerated = new ArrayList<>();

        int originX = player.getLocation().getBlockX();
        int originZ = player.getLocation().getBlockZ();

        for (int i = 0; i < maxAttempts; i++) {
            double angle  = random.nextDouble() * 2 * Math.PI;
            int    radius = minR + random.nextInt(Math.max(1, maxR - minR));
            int    x      = originX + (int) (radius * Math.cos(angle));
            int    z      = originZ + (int) (radius * Math.sin(angle));
            int[]  coord  = {x, z};
            if (world.isChunkGenerated(x >> 4, z >> 4)) generated.add(coord);
            else                                         ungenerated.add(coord);
        }

        // Try generated chunks first (fast, no server-side generation), then fall back
        List<int[]> candidates = new ArrayList<>(generated);
        candidates.addAll(ungenerated);

        tryCoord(player, world, candidates, 0);
    }

    // ── Sequential safe-spot search ───────────────────────────────────────────

    private void tryCoord(Player player, World world, List<int[]> candidates, int index) {
        if (!player.isOnline() || !teleportManager.isPending(player.getUniqueId())) return;
        if (index >= candidates.size()) return; // exhausted — countdown will time out

        int[] coord = candidates.get(index);
        int x = coord[0], z = coord[1];

        // Load this chunk async (no main-thread block)
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !teleportManager.isPending(player.getUniqueId())) return;

                int y = findSafeY(world, x, z);
                if (y < 0) {
                    tryCoord(player, world, candidates, index + 1);
                    return;
                }

                Location dest = new Location(world, x + 0.5, y, z + 0.5);

                // Pre-load the 3×3 surrounding chunks so the player doesn't see a
                // black screen on arrival. This happens while the countdown is still
                // ticking, so it's essentially free time.
                preloadSurroundingChunks(world, x >> 4, z >> 4, () ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && teleportManager.isPending(player.getUniqueId())) {
                            teleportManager.setDestination(player.getUniqueId(), dest);
                        }
                    })
                );
            })
        );
    }

    /** Loads the 3×3 chunk region around the destination, then fires onComplete. */
    private void preloadSurroundingChunks(World world, int cX, int cZ, Runnable onComplete) {
        CompletableFuture<?>[] futures = new CompletableFuture[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                futures[i++] = world.getChunkAtAsync(cX + dx, cZ + dz);
            }
        }
        CompletableFuture.allOf(futures).thenRun(onComplete);
    }

    // ── Safe-Y finders ────────────────────────────────────────────────────────

    private int findSafeY(World world, int x, int z) {
        return switch (world.getEnvironment()) {
            case NETHER  -> findSafeYNether(world, x, z);
            case THE_END -> findSafeYEnd(world, x, z);
            default      -> findSafeYOverworld(world, x, z);
        };
    }

    private int findSafeYOverworld(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (y <= world.getMinHeight()) return -1;
        Block ground = world.getBlockAt(x, y, z);
        if (isLiquid(ground) || !ground.getType().isSolid()) return -1;
        return y + 1;
    }

    private int findSafeYNether(World world, int x, int z) {
        for (int y = 32; y <= 118; y++) {
            Block floor = world.getBlockAt(x, y,     z);
            Block feet  = world.getBlockAt(x, y + 1, z);
            Block head  = world.getBlockAt(x, y + 2, z);
            if (floor.getType().isSolid() && !isLiquid(floor)
                    && feet.getType().isAir()
                    && head.getType().isAir()) {
                return y + 1;
            }
        }
        return -1;
    }

    private int findSafeYEnd(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (y <= world.getMinHeight()) return -1;
        Block ground = world.getBlockAt(x, y, z);
        if (isLiquid(ground) || !ground.getType().isSolid()) return -1;
        return y + 1;
    }

    private static boolean isLiquid(Block b) {
        Material t = b.getType();
        return t == Material.WATER || t == Material.LAVA || t == Material.BUBBLE_COLUMN;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String displayName(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL  -> "The Overworld";
            case NETHER  -> "The Nether";
            case THE_END -> "The End";
            default      -> world.getName();
        };
    }
}
