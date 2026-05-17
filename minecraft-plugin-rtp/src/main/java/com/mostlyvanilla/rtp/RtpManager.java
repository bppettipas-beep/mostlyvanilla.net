package com.mostlyvanilla.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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

    public boolean isDisabled(World world) {
        return disabledWorlds.contains(world.getName());
    }

    /** Toggles the disabled state of a world. Returns true if it is now disabled. */
    public boolean toggleDisabled(String worldName) {
        boolean nowDisabled = disabledWorlds.add(worldName);
        if (!nowDisabled) disabledWorlds.remove(worldName);
        plugin.getConfig().set("disabled-worlds", new ArrayList<>(disabledWorlds));
        plugin.saveConfig();
        return nowDisabled;
    }

    // ── RTP entry point ───────────────────────────────────────────────────────

    public void startRtp(Player player, World world) {
        if (isDisabled(world)) {
            player.sendMessage(Component.text("That dimension is currently disabled.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Searching for a safe location in ", NamedTextColor.YELLOW)
            .append(Component.text(displayName(world), NamedTextColor.GOLD))
            .append(Component.text("…", NamedTextColor.YELLOW)));
        tryFind(player, world, 0);
    }

    private void tryFind(Player player, World world, int attempt) {
        if (!player.isOnline()) return;
        int maxAttempts = plugin.getConfig().getInt("max-attempts", 20);
        if (attempt >= maxAttempts) {
            player.sendMessage(Component.text("Could not find a safe location. Please try again.", NamedTextColor.RED));
            return;
        }

        int minR   = plugin.getConfig().getInt("min-radius", 1000);
        int maxR   = plugin.getConfig().getInt("max-radius", 10000);
        double angle  = random.nextDouble() * 2 * Math.PI;
        int    radius = minR + random.nextInt(Math.max(1, maxR - minR));
        int    x      = (int) (radius * Math.cos(angle));
        int    z      = (int) (radius * Math.sin(angle));

        // Load chunk async, then check safety on main thread
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || Bukkit.getWorld(world.getName()) == null) return;
                int y = findSafeY(world, x, z);
                if (y < 0) {
                    tryFind(player, world, attempt + 1);
                } else {
                    teleportManager.startTeleport(player, new Location(world, x + 0.5, y, z + 0.5));
                }
            })
        );
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
        if (isLiquid(ground)) return -1;
        if (!ground.getType().isSolid()) return -1;
        return y + 1;
    }

    private int findSafeYNether(World world, int x, int z) {
        // Scan upward from just above the lava sea (y=32) to below the bedrock ceiling
        for (int y = 32; y <= 118; y++) {
            Block floor = world.getBlockAt(x, y, z);
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
        if (y <= world.getMinHeight()) return -1; // nothing but void
        Block ground = world.getBlockAt(x, y, z);
        if (isLiquid(ground) || !ground.getType().isSolid()) return -1;
        return y + 1;
    }

    private static boolean isLiquid(Block block) {
        Material t = block.getType();
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

    public List<String> getDisabledWorlds() {
        return new ArrayList<>(disabledWorlds);
    }
}
