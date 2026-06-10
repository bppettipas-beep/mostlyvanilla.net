package com.mostlyvanilla.antidupe;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Set;

public class DupeListener implements Listener {

    // Inventory types where items can only be moved (not created or transformed).
    // Crafting, smelting, enchanting etc. are excluded because they legitimately
    // change item counts in ways that would produce false positives.
    private static final Set<InventoryType> MONITORED = Set.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.DISPENSER,
            InventoryType.DROPPER
    );

    private final MostlyVanillaAntiDupe plugin;

    public DupeListener(MostlyVanillaAntiDupe plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!MONITORED.contains(event.getInventory().getType())) return;

        PlayerDupeData data = plugin.getData(player.getUniqueId());
        data.sessionOpenTotal = countItems(player.getInventory()) + countItems(event.getInventory());
        data.sessionPickups = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!MONITORED.contains(event.getInventory().getType())) return;

        PlayerDupeData data = plugin.getData(player.getUniqueId());
        if (data.sessionOpenTotal < 0) return; // no session tracked (e.g. opened before plugin loaded)

        int expectedTotal = data.sessionOpenTotal + data.sessionPickups;
        int actualTotal   = countItems(player.getInventory()) + countItems(event.getInventory());

        data.sessionOpenTotal = -1;
        data.sessionPickups   = 0;

        int excess = actualTotal - expectedTotal;
        int threshold = plugin.getConfig().getInt("detection-threshold", 32);

        if (excess >= threshold) {
            plugin.getDetector().flagDupe(player, "ContainerDupe",
                    "unexplained_gain=" + excess + " items (threshold=" + threshold + ")");
        }
    }

    // Track items picked up from the ground during an open session so they
    // don't count as an unexplained gain when we compare totals on close.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        PlayerDupeData data = plugin.getData(player.getUniqueId());
        if (data.sessionOpenTotal >= 0) {
            data.sessionPickups += event.getItem().getItemStack().getAmount();
        }
    }

    // ── Bundle prevention ─────────────────────────────────────────────────────
    // Bundles can be used for inventory-manipulation dupes. Block all ways of
    // obtaining them: crafting, ground pickup, creative menu placement, and
    // existing ones in inventories are cleared on join.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.BUNDLE) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BUNDLE) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§cBundles are disabled on this server.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBundlePickup(EntityPickupItemEvent event) {
        if (event.getItem().getItemStack().getType() != Material.BUNDLE) return;
        event.setCancelled(true);
        event.getItem().remove(); // destroy the entity so nobody can pick it up
    }

    // Block moving a bundle into any inventory via click (covers creative menu grabs).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBundleClick(InventoryClickEvent event) {
        ItemStack cursor  = event.getCursor();
        ItemStack current = event.getCurrentItem();
        boolean cursorIsBundle  = cursor  != null && cursor.getType()  == Material.BUNDLE;
        boolean currentIsBundle = current != null && current.getType() == Material.BUNDLE;
        if (cursorIsBundle || currentIsBundle) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                p.sendMessage("§cBundles are disabled on this server.");
            }
        }
    }

    // Sweep inventory on join in case a player logged out with bundles or
    // received one through a mechanism we didn't intercept.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            boolean removed = removeBundlesFrom(player.getInventory())
                    | removeBundlesFrom(player.getEnderChest());
            if (removed) {
                player.sendMessage("§cBundles are disabled on this server and have been removed from your inventory.");
            }
        });
    }

    private static boolean removeBundlesFrom(Inventory inv) {
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.BUNDLE) {
                inv.setItem(i, null);
                found = true;
            }
        }
        return found;
    }

    // ── String dupe prevention ────────────────────────────────────────────────
    // The string dupe works by using a piston to push TRIPWIRE or TRIPWIRE_HOOK
    // blocks, causing them to drop as items while the block state also persists.
    // Cancelling piston events that affect these blocks prevents the mechanism entirely.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (affectsTripwire(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (affectsTripwire(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    // Secondary guard: if a TRIPWIRE block drops items (e.g. via a block update
    // we didn't intercept), clear the drops so no string items are created.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTripwireDrop(BlockDropItemEvent event) {
        if (event.getBlock().getType() == Material.TRIPWIRE) {
            // Only suppress drops when there is no player directly breaking the block
            // (i.e. the cause is mechanical — piston, block update, etc.)
            if (event.getPlayer() == null || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
                event.getItems().clear();
            }
        }
    }

    private static boolean affectsTripwire(List<Block> blocks) {
        for (Block b : blocks) {
            Material t = b.getType();
            if (t == Material.TRIPWIRE || t == Material.TRIPWIRE_HOOK) return true;
        }
        return false;
    }

    // ── Quit cleanup ──────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.removeData(event.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int countItems(Inventory inv) {
        int total = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null) total += item.getAmount();
        }
        // For player inventories also count armour and offhand
        if (inv instanceof PlayerInventory pi) {
            for (ItemStack item : pi.getArmorContents()) {
                if (item != null) total += item.getAmount();
            }
            ItemStack offhand = pi.getItemInOffHand();
            total += offhand.getAmount();
        }
        return total;
    }
}
