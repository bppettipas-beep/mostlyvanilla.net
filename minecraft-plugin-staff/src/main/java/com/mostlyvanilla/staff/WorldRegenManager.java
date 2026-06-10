package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class WorldRegenManager {

    private final JavaPlugin plugin;

    private record PanelState(int step, boolean overworldOnly) {}
    private final Map<Inventory, PanelState> panels = new HashMap<>();

    private static final int SLOT_DESC    = 4;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL  = 15;

    // Blocks around world spawn to keep untouched when doing overworld-only regen.
    // One region file = 512 blocks, so 512 protects a full region around spawn.
    private static final int SPAWN_PROTECT_RADIUS = 512;

    public WorldRegenManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRegenPanel(Inventory inv) { return panels.containsKey(inv); }

    // ── Entry points ──────────────────────────────────────────────────────────

    public void openStep1(Player staff) {
        openStep(staff, 1, false);
    }

    public void openStep1Overworld(Player staff) {
        openStep(staff, 1, true);
    }

    // ── Step rendering ────────────────────────────────────────────────────────

    private void openStep(Player staff, int step, boolean overworldOnly) {
        Inventory inv = switch (step) {
            case 1 -> buildStep1(overworldOnly);
            case 2 -> buildStep2(overworldOnly);
            case 3 -> buildStep3(overworldOnly);
            default -> throw new IllegalArgumentException("bad step " + step);
        };
        panels.put(inv, new PanelState(step, overworldOnly));
        staff.openInventory(inv);
    }

    private Inventory buildStep1(boolean overworldOnly) {
        String title = overworldOnly ? "⚠  Regenerate Overworld?" : "⚠  Regenerate All Worlds?";
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD));
        fill(inv);

        if (overworldOnly) {
            inv.setItem(SLOT_DESC, btn(Material.TNT, "§6⚠  Overworld Regeneration",
                "§7This will permanently delete ALL overworld terrain,",
                "§7except within §e" + SPAWN_PROTECT_RADIUS + " blocks of spawn §7(spawn is kept).",
                "",
                "§aThe Nether and The End are NOT touched.",
                "§aPlayer inventories, stats, and balances are kept.",
                "",
                "§7The server will restart after deletion.",
                "",
                "§7Click §aYes, proceed §7to continue."));
        } else {
            inv.setItem(SLOT_DESC, btn(Material.TNT, "§6⚠  Full World Regeneration",
                "§7This will permanently delete all terrain in:",
                "§c• Overworld   §c• Nether   §c• The End",
                "",
                "§aPlayer inventories, stats, and balances",
                "§aare NOT affected.",
                "",
                "§7The server will restart after deletion.",
                "",
                "§7Click §aYes, proceed §7to continue."));
        }

        inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE,  "§a§l✔  Yes, proceed"));
        inv.setItem(SLOT_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,   "§c§l✘  Cancel"));
        return inv;
    }

    private Inventory buildStep2(boolean overworldOnly) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⚠⚠  ARE YOU SURE?  ⚠⚠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        fill(inv);

        if (overworldOnly) {
            inv.setItem(SLOT_DESC, btn(Material.BARRIER, "§4§lFINAL WARNING",
                "§cThis CANNOT be undone!",
                "",
                "§c• Overworld terrain → §fdeleted (spawn area kept)",
                "§a• Nether terrain    → §funchanged",
                "§a• End terrain       → §funchanged",
                "§c• Server            → §frestarted immediately",
                "",
                "§7Click §cDelete Overworld Terrain §7to continue."));
            inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Delete Overworld Terrain"));
        } else {
            inv.setItem(SLOT_DESC, btn(Material.BARRIER, "§4§lFINAL WARNING",
                "§cThis CANNOT be undone!",
                "",
                "§c• Overworld terrain → §fdeleted",
                "§c• Nether terrain    → §fdeleted",
                "§c• End terrain       → §fdeleted",
                "§c• Server            → §frestarted immediately",
                "",
                "§7Click §cDelete All Terrain §7to continue."));
            inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Delete All Terrain"));
        }

        inv.setItem(SLOT_CANCEL, plain(Material.RED_STAINED_GLASS_PANE, "§c§l✘  Cancel"));
        return inv;
    }

    private Inventory buildStep3(boolean overworldOnly) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("☠  POINT OF NO RETURN  ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        fill(inv);

        String scope = overworldOnly ? "overworld terrain (spawn kept)" : "terrain in all three worlds";
        inv.setItem(SLOT_DESC, btn(Material.BEDROCK, "§4§l☠  LAST CHANCE",
            "§cOnce confirmed, immediately:",
            "§c• Every player will be kicked",
            "§c• " + scope + " deleted",
            "§c• Server will restart",
            "",
            "§4There is NO going back.",
            "",
            "§7Click §5CONFIRM §7only if certain."));
        inv.setItem(SLOT_CONFIRM, plain(Material.MAGENTA_STAINED_GLASS_PANE, "§5§lCONFIRM WORLD RESET"));
        inv.setItem(SLOT_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,     "§c§l✘  Cancel"));
        return inv;
    }

    // ── Click handling ────────────────────────────────────────────────────────

    public void handleClick(Player staff, Inventory inv, int slot) {
        PanelState state = panels.get(inv);
        if (state == null) return;

        if (slot == SLOT_CANCEL) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) staff::closeInventory);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            boolean ow = state.overworldOnly();
            switch (state.step()) {
                case 1 -> Bukkit.getScheduler().runTask(plugin, () -> openStep(staff, 2, ow));
                case 2 -> Bukkit.getScheduler().runTask(plugin, () -> openStep(staff, 3, ow));
                case 3 -> Bukkit.getScheduler().runTask(plugin, () -> {
                    staff.closeInventory();
                    executeRegen(staff, ow);
                });
            }
        }
    }

    public void onInventoryClose(Inventory inv) {
        panels.remove(inv);
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    private void executeRegen(Player staff, boolean overworldOnly) {
        String scope = overworldOnly ? "overworld-only (spawn protected)" : "all worlds";
        plugin.getLogger().info("[WorldRegen] Initiated by " + staff.getName() + " — mode=" + scope);

        Bukkit.broadcast(
            Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED).append(Component.newline())
            .append(Component.text("  WORLD RESET INITIATED", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.newline())
            .append(Component.text("  Terrain is being wiped.", NamedTextColor.YELLOW)).append(Component.newline())
            .append(Component.text("  The server will restart momentarily.", NamedTextColor.YELLOW)).append(Component.newline())
            .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED)));

        Component kickMsg = Component.text("Server is restarting for world regeneration. Please reconnect shortly.", NamedTextColor.RED);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) p.kick(kickMsg);

            for (World world : Bukkit.getWorlds()) {
                switch (world.getEnvironment()) {
                    case NORMAL -> {
                        // Delete level.dat so the server generates a fresh random seed on restart
                        deleteFileIfExists(new File(world.getWorldFolder(), "level.dat"));
                        deleteFileIfExists(new File(world.getWorldFolder(), "level.dat_old"));

                        if (overworldOnly) {
                            Location spawn = world.getSpawnLocation();
                            deleteRegionFilesProtectingSpawn(new File(world.getWorldFolder(), "region"),   spawn, SPAWN_PROTECT_RADIUS);
                            deleteRegionFilesProtectingSpawn(new File(world.getWorldFolder(), "poi"),      spawn, SPAWN_PROTECT_RADIUS);
                            deleteRegionFilesProtectingSpawn(new File(world.getWorldFolder(), "entities"), spawn, SPAWN_PROTECT_RADIUS);
                        } else {
                            deleteSubfolder(world.getWorldFolder(), "region");
                            deleteSubfolder(world.getWorldFolder(), "poi");
                            deleteSubfolder(world.getWorldFolder(), "entities");
                        }
                    }
                    case NETHER -> {
                        if (!overworldOnly) {
                            File dim = new File(world.getWorldFolder(), "DIM-1");
                            deleteSubfolder(dim, "region");
                            deleteSubfolder(dim, "poi");
                            deleteSubfolder(dim, "entities");
                        }
                    }
                    case THE_END -> {
                        if (!overworldOnly) {
                            File dim = new File(world.getWorldFolder(), "DIM1");
                            deleteSubfolder(dim, "region");
                            deleteSubfolder(dim, "poi");
                            deleteSubfolder(dim, "entities");
                        }
                    }
                    default -> {} // leave custom worlds alone
                }
            }

            Bukkit.spigot().restart();
        }, 60L);
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    /**
     * Deletes individual .mca region files, skipping any that overlap the spawn protection rectangle.
     * Each region file r.X.Z.mca covers blocks [X*512 .. X*512+511] × [Z*512 .. Z*512+511].
     */
    private void deleteRegionFilesProtectingSpawn(File folder, Location spawn, int radius) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        int spawnX = spawn.getBlockX();
        int spawnZ = spawn.getBlockZ();

        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (!name.endsWith(".mca")) { f.delete(); continue; }

            // Parse r.X.Z.mca
            String[] parts = name.split("\\.");
            if (parts.length != 4) { f.delete(); continue; }

            try {
                int rx = Integer.parseInt(parts[1]);
                int rz = Integer.parseInt(parts[2]);
                int regionMinX = rx * 512, regionMaxX = regionMinX + 511;
                int regionMinZ = rz * 512, regionMaxZ = regionMinZ + 511;

                boolean protectedBySpawn =
                        regionMaxX >= (spawnX - radius) && regionMinX <= (spawnX + radius) &&
                        regionMaxZ >= (spawnZ - radius) && regionMinZ <= (spawnZ + radius);

                if (protectedBySpawn) {
                    plugin.getLogger().info("[WorldRegen] Kept (spawn protection): " + name);
                } else {
                    f.delete();
                    plugin.getLogger().info("[WorldRegen] Deleted: " + name);
                }
            } catch (NumberFormatException ignored) {
                f.delete();
            }
        }
    }

    private void deleteFileIfExists(File f) {
        if (f.exists() && !f.delete())
            plugin.getLogger().warning("[WorldRegen] Could not delete " + f.getName());
        else if (f.exists())
            plugin.getLogger().info("[WorldRegen] Deleted " + f.getName());
    }

    private void deleteSubfolder(File parent, String name) {
        File folder = new File(parent, name);
        if (!folder.exists()) return;
        deleteRecursive(folder);
        plugin.getLogger().info("[WorldRegen] Deleted " + folder.getAbsolutePath());
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    // ── Item helpers ──────────────────────────────────────────────────────────

    private static void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private static ItemStack plain(Material mat, String legacy) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(leg(legacy));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack btn(Material mat, String title, String... lore) {
        ItemStack item = plain(mat, title);
        ItemMeta meta = item.getItemMeta();
        meta.lore(Stream.of(lore).map(WorldRegenManager::leg).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
