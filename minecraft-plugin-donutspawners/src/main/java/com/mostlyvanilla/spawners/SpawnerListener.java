package com.mostlyvanilla.spawners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class SpawnerListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DonutSpawners plugin;
    private final SpawnerManager manager;
    private final SpawnerConfig  cfg;
    private final SpawnerGui     gui;

    public SpawnerListener(DonutSpawners plugin, SpawnerManager manager, SpawnerConfig cfg, SpawnerGui gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.cfg     = cfg;
        this.gui     = gui;
    }

    // ── Auto-convert on chunk load ────────────────────────────────────────────
    // Every time any chunk loads, all vanilla spawners inside it are immediately
    // converted to plugin spawners so players can never interact with a vanilla one.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        convertChunk(e.getChunk());
    }

    /** Converts every non-plugin CreatureSpawner in the chunk. */
    public void convertChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof CreatureSpawner cs)) continue;
            if (manager.isPluginSpawner(cs.getLocation())) continue;

            SpawnerType type = SpawnerType.fromEntityType(cs.getSpawnedType());
            if (type != null) {
                manager.placeSpawner(cs.getLocation(), type, 1);
            } else {
                // Mob type not supported by this plugin — remove the spawner silently
                cs.getLocation().getBlock().setType(Material.AIR, false);
            }
        }
    }

    // ── Block right-click ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        SpawnerType heldType = SpawnerItems.getType(hand);

        boolean isPluginSpawner = manager.isPluginSpawner(block.getLocation());

        // Vanilla spawner that somehow wasn't converted — convert it now
        if (!isPluginSpawner) {
            e.setCancelled(true);
            if (heldType != null) {
                manager.placeSpawner(block.getLocation(), heldType, 1);
                consumeOne(player, hand);
            } else {
                if (block.getState() instanceof CreatureSpawner cs) {
                    SpawnerType detected = SpawnerType.fromEntityType(cs.getSpawnedType());
                    if (detected != null) {
                        manager.placeSpawner(block.getLocation(), detected, 1);
                    }
                }
            }
            return;
        }

        // ── Plugin spawner ────────────────────────────────────────────────────
        e.setCancelled(true);
        SpawnerData data = manager.getSpawner(block.getLocation());
        if (data == null) return;

        if (heldType != null) {
            // Holding a spawner item — try to stack
            if (heldType != data.getType()) {
                player.sendMessage(LEGACY.deserialize(
                    "&c[Spawners] Wrong type — this is a " + data.getType().getDisplayName() + " Spawner."));
                return;
            }
            int amount = player.isSneaking() ? hand.getAmount() : 1;
            manager.addStack(block.getLocation(), amount);
            hand.setAmount(hand.getAmount() - amount);
            SpawnerData updated = manager.getSpawner(block.getLocation());
            if (updated != null) {
                player.sendActionBar(LEGACY.deserialize("&aStack: &e×" + updated.getStack()));
            }
        } else {
            // Empty / non-spawner hand → open GUI
            gui.openMain(player, data);
        }
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        // Vanilla spawner that wasn't converted yet — convert it and stop the break
        if (!manager.isPluginSpawner(block.getLocation())) {
            e.setCancelled(true);
            e.setExpToDrop(0);
            if (block.getState() instanceof CreatureSpawner cs) {
                SpawnerType type = SpawnerType.fromEntityType(cs.getSpawnedType());
                if (type != null) {
                    manager.placeSpawner(block.getLocation(), type, 1);
                } else {
                    // Unsupported mob type — just remove it
                    block.setType(Material.AIR, false);
                }
            }
            return;
        }

        e.setCancelled(true);
        e.setDropItems(false);
        e.setExpToDrop(0);

        Player player = e.getPlayer();

        // Silk Touch check
        if (cfg.requireSilkTouch()) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (!hasSilkTouch(tool)) {
                player.sendMessage(LEGACY.deserialize("&c[Spawners] You need Silk Touch to mine this spawner!"));
                return;
            }
        }

        SpawnerData data = manager.getSpawner(block.getLocation());
        if (data == null) return;

        int dropCount = player.isSneaking() ? Math.min(64, data.getStack()) : 1;
        int newStack  = data.getStack() - dropCount;

        if (newStack <= 0) {
            if (!data.isEmpty()) {
                player.sendMessage(LEGACY.deserialize("&c[Spawners] ⚠ All stored items and XP were lost!"));
            }
            manager.removeSpawner(block.getLocation());
            block.setType(Material.AIR, false);
        } else {
            data.setStack(newStack);
            if (block.getState() instanceof org.bukkit.block.TileState ts) {
                ts.getPersistentDataContainer()
                    .set(DonutSpawners.KEY_STACK, PersistentDataType.INTEGER, newStack);
                ts.update();
            }
            manager.markDirty();
            player.sendActionBar(LEGACY.deserialize("&aStack: &e×" + newStack));
        }

        var dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        int remaining = dropCount;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            block.getWorld().dropItemNaturally(dropLoc, SpawnerItems.create(data.getType(), batch));
            remaining -= batch;
        }
    }

    // ── Block place ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.SPAWNER) return;
        ItemStack item = e.getItemInHand();
        SpawnerType type = SpawnerItems.getType(item);

        if (type != null) {
            // Plugin spawner item — register normally
            manager.placeSpawner(e.getBlockPlaced().getLocation(), type, 1);
        } else {
            // Vanilla spawner block placed (creative, etc.) — auto-convert on next tick
            // so the block state is fully set before we read it
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var loc = e.getBlockPlaced().getLocation();
                if (loc.getBlock().getType() != Material.SPAWNER) return;
                if (manager.isPluginSpawner(loc)) return;
                if (loc.getBlock().getState() instanceof CreatureSpawner cs) {
                    SpawnerType detected = SpawnerType.fromEntityType(cs.getSpawnedType());
                    manager.placeSpawner(loc, detected != null ? detected : SpawnerType.ZOMBIE, 1);
                }
            });
        }
    }

    // ── No vanilla spawning ───────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent e) {
        if (manager.isPluginSpawner(e.getSpawner().getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasSilkTouch(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return false;
        return tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private void consumeOne(Player player, ItemStack hand) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        hand.setAmount(hand.getAmount() - 1);
    }
}
