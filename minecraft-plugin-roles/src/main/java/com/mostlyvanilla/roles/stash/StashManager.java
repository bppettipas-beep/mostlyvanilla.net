package com.mostlyvanilla.roles.stash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StashManager {

    private final JavaPlugin plugin;

    public StashManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── /spawnstash ───────────────────────────────────────────────────────────

    public void createStash(Player player) {
        if (hasStash(player.getUniqueId())) {
            player.sendMessage(Component.text(
                "You already have a stash. Use /delstash to remove it first.", NamedTextColor.RED));
            return;
        }

        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY();
        int pz = player.getLocation().getBlockZ();
        World world = player.getWorld();

        // Save region: outer box x px-2→px+3, y py-1→py+3, z pz-2→pz+3
        List<String> savedBlocks = new ArrayList<>();
        for (int x = px - 2; x <= px + 3; x++) {
            for (int y = py - 1; y <= py + 3; y++) {
                for (int z = pz - 2; z <= pz + 3; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    savedBlocks.add(x + " " + y + " " + z + " " + b.getBlockData().getAsString());
                }
            }
        }

        // ── Build structure ───────────────────────────────────────────────────

        Material wall = Material.OAK_PLANKS;

        // Floor (py-1) and ceiling (py+3)
        for (int x = px - 2; x <= px + 3; x++) {
            for (int z = pz - 2; z <= pz + 3; z++) {
                world.getBlockAt(x, py - 1, z).setType(wall, false);
                world.getBlockAt(x, py + 3, z).setType(wall, false);
            }
        }

        // Walls + interior for y = py → py+2
        for (int y = py; y <= py + 2; y++) {
            for (int x = px - 2; x <= px + 3; x++) {
                for (int z = pz - 2; z <= pz + 3; z++) {
                    boolean isWall = x == px - 2 || x == px + 3 || z == pz - 2 || z == pz + 3;
                    world.getBlockAt(x, y, z).setType(isWall ? wall : Material.AIR, false);
                }
            }
        }

        // ── Furniture ─────────────────────────────────────────────────────────

        // Two double chests against the north wall (z = pz-1), facing south
        placeChest(world, px - 1, py, pz - 1, BlockFace.SOUTH, Chest.Type.LEFT);
        placeChest(world, px,     py, pz - 1, BlockFace.SOUTH, Chest.Type.RIGHT);
        placeChest(world, px + 1, py, pz - 1, BlockFace.SOUTH, Chest.Type.LEFT);
        placeChest(world, px + 2, py, pz - 1, BlockFace.SOUTH, Chest.Type.RIGHT);

        // Crafting table (west side of middle row)
        world.getBlockAt(px - 1, py, pz).setType(Material.CRAFTING_TABLE, false);

        // Furnace (east side of middle row), facing south
        Block furnaceBlock = world.getBlockAt(px + 2, py, pz);
        furnaceBlock.setType(Material.FURNACE, false);
        Furnace furnaceData = (Furnace) furnaceBlock.getBlockData();
        furnaceData.setFacing(BlockFace.SOUTH);
        furnaceBlock.setBlockData(furnaceData, false);

        // Persist
        saveStashFile(player.getUniqueId(), world.getName(), px, py, pz, savedBlocks);

        player.sendMessage(Component.text("Stash spawned! Use /delstash to remove it.", NamedTextColor.GREEN));
    }

    private void placeChest(World world, int x, int y, int z, BlockFace facing, Chest.Type type) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        Chest data = (Chest) block.getBlockData();
        data.setFacing(facing);
        data.setType(type);
        block.setBlockData(data, false);
    }

    // ── /delstash ─────────────────────────────────────────────────────────────

    public void deleteStash(Player player) {
        File file = stashFile(player.getUniqueId());
        if (!file.exists()) {
            player.sendMessage(Component.text("You don't have an active stash.", NamedTextColor.RED));
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String worldName = cfg.getString("world");
        World world = Bukkit.getWorld(worldName != null ? worldName : "");
        if (world == null) {
            player.sendMessage(Component.text(
                "Could not restore stash — world '" + worldName + "' is not loaded.", NamedTextColor.RED));
            return;
        }

        for (String entry : cfg.getStringList("blocks")) {
            String[] parts = entry.split(" ", 4);
            if (parts.length < 4) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                BlockData bd = Bukkit.createBlockData(parts[3]);
                world.getBlockAt(x, y, z).setBlockData(bd, false);
            } catch (Exception ignored) {}
        }

        file.delete();
        player.sendMessage(Component.text("Stash removed and original blocks restored.", NamedTextColor.GREEN));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public boolean hasStash(UUID uuid) {
        return stashFile(uuid).exists();
    }

    private void saveStashFile(UUID uuid, String world, int px, int py, int pz, List<String> blocks) {
        File file = stashFile(uuid);
        file.getParentFile().mkdirs();
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("world", world);
        cfg.set("px", px);
        cfg.set("py", py);
        cfg.set("pz", pz);
        cfg.set("blocks", blocks);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[SpawnStash] Failed to save stash for " + uuid + ": " + e.getMessage());
        }
    }

    private File stashFile(UUID uuid) {
        return new File(plugin.getDataFolder(), "stashes/" + uuid + ".yml");
    }
}
