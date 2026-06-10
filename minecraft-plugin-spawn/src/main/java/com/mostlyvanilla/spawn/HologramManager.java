package com.mostlyvanilla.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HologramManager {

    private static final String TAG = "mv_hologram";

    private final MostlyVanillaSpawn plugin;
    private final Map<UUID, TextDisplay> live  = new HashMap<>();
    private final List<HologramRecord>   saved = new ArrayList<>();
    private File dataFile;

    // ── Purge GUI ─────────────────────────────────────────────────────────────

    private static final int[] CONFIRM_SLOTS = {11, 15, 20};
    private static final int[] CANCEL_SLOTS  = {15, 11, 24};

    private final Map<Inventory, Integer> purgePanels = new HashMap<>();

    public boolean isPurgePanel(Inventory inv) { return purgePanels.containsKey(inv); }

    public void openPurge(Player player) { openPurgeStep(player, 1); }

    private void openPurgeStep(Player player, int step) {
        Inventory inv = buildPurgeStep(step);
        purgePanels.put(inv, step);
        player.openInventory(inv);
    }

    private Inventory buildPurgeStep(int step) {
        int count = saved.size();
        String title = switch (step) {
            case 1 -> "⚠  Delete All Holograms?";
            case 2 -> "⚠⚠  Are you sure?";
            default -> "☠  POINT OF NO RETURN  ☠";
        };
        NamedTextColor color = step < 3 ? NamedTextColor.GOLD : NamedTextColor.DARK_RED;
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text(title, color, TextDecoration.BOLD));
        fill(inv);

        int confirmSlot = CONFIRM_SLOTS[step - 1];
        int cancelSlot  = CANCEL_SLOTS[step - 1];

        Material descMat = switch (step) {
            case 1 -> Material.BARRIER;
            case 2 -> Material.TNT;
            default -> Material.BEDROCK;
        };
        String plural = count == 1 ? "" : "s";

        String[] lore = switch (step) {
            case 1 -> new String[]{
                "§7This will delete §c" + count + " hologram" + plural + "§7.",
                "§7This cannot be undone.",
                "",
                "§7[1/3] — Click §aYes §7to proceed."
            };
            case 2 -> new String[]{
                "§cYou are about to permanently delete",
                "§4" + count + " hologram" + plural + "§c.",
                "§cThis CANNOT be undone.",
                "",
                "§7[2/3] — Button positions have changed."
            };
            default -> new String[]{
                "§4All " + count + " hologram" + plural + " will be",
                "§4permanently deleted.",
                "",
                "§4There is NO going back.",
                "",
                "§7[3/3] — This is your final chance."
            };
        };

        String descTitle = switch (step) {
            case 1 -> "§6⚠  Delete All Holograms";
            case 2 -> "§4§lFINAL WARNING";
            default -> "§4§l☠  LAST CHANCE";
        };

        inv.setItem(13, btn(descMat, descTitle, lore));

        Material confirmMat = switch (step) {
            case 1 -> Material.LIME_STAINED_GLASS_PANE;
            case 2 -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.MAGENTA_STAINED_GLASS_PANE;
        };
        String confirmLabel = switch (step) {
            case 1 -> "§a§l✔  Yes, proceed  [1/3]";
            case 2 -> "§6§lDelete All  [2/3]";
            default -> "§5§lCONFIRM DELETE ALL  [3/3]";
        };

        inv.setItem(confirmSlot, plain(confirmMat, confirmLabel));
        inv.setItem(cancelSlot,  plain(Material.RED_STAINED_GLASS_PANE, "§c§l✘  Cancel"));
        return inv;
    }

    public void handlePurgeClick(Player player, Inventory inv, int slot) {
        Integer step = purgePanels.get(inv);
        if (step == null) return;

        if (slot == CANCEL_SLOTS[step - 1]) {
            plugin.getServer().getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return;
        }

        if (slot == CONFIRM_SLOTS[step - 1]) {
            if (step < 3) {
                int next = step + 1;
                plugin.getServer().getScheduler().runTask(plugin, () -> openPurgeStep(player, next));
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.closeInventory();
                    int deleted = deleteAll();
                    player.sendMessage(Component.text(
                        "Deleted " + deleted + " hologram" + (deleted == 1 ? "" : "s") + ".",
                        NamedTextColor.GREEN));
                });
            }
        }
    }

    public void onPurgeClose(Inventory inv) { purgePanels.remove(inv); }

    /** Deletes all standalone (non-NPC) holograms. Returns the count deleted. */
    public int deleteAll() {
        // Remove tracked entities
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, TextDisplay> e : live.entrySet()) {
            TextDisplay td = e.getValue();
            if (td == null || td.isDead()) { toRemove.add(e.getKey()); continue; }
            if (td.getScoreboardTags().contains("mv_npc_holo")) continue;
            td.remove();
            toRemove.add(e.getKey());
        }
        toRemove.forEach(live::remove);

        // Also sweep the world for any stray tagged entities not in the live map
        World spawnWorld = plugin.getSpawnManager().getSpawnWorld();
        if (spawnWorld != null) {
            for (Entity e : spawnWorld.getEntities()) {
                if (!(e instanceof TextDisplay td)) continue;
                if (!td.getScoreboardTags().contains(TAG)) continue;
                if (td.getScoreboardTags().contains("mv_npc_holo")) continue;
                td.remove();
            }
        }

        int count = saved.size();
        saved.clear();
        save();
        return count;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public HologramManager(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void load(World spawnWorld) {
        dataFile = new File(plugin.getDataFolder(), "hologram-data.yml");

        // Read YAML first so we know which chunks to pre-load
        List<Map<?, ?>> entries = new ArrayList<>();
        if (dataFile.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);
            entries = new ArrayList<>(c.getMapList("holograms"));
        }

        // Force-load every chunk that should contain a saved hologram.
        // Without this, persistent duplicates in unloaded chunks are invisible
        // to the sweep below and reappear after the sweep runs.
        for (Map<?, ?> entry : entries) {
            int cx = (int) Math.floor(((Number) entry.get("x")).doubleValue()) >> 4;
            int cz = (int) Math.floor(((Number) entry.get("z")).doubleValue()) >> 4;
            spawnWorld.loadChunk(cx, cz, false);
        }

        // Sweep all tagged entities — standalone holograms, NPC holograms, and
        // any accumulated duplicates are all removed in one pass.
        // NpcManager re-creates NPC holograms one tick later.
        for (Entity e : spawnWorld.getEntities()) {
            if (e instanceof TextDisplay td && td.getScoreboardTags().contains(TAG)) td.remove();
        }

        // Spawn exactly one entity per saved record
        for (Map<?, ?> entry : entries) {
            double x    = ((Number) entry.get("x")).doubleValue();
            double y    = ((Number) entry.get("y")).doubleValue();
            double z    = ((Number) entry.get("z")).doubleValue();
            String text = (String) entry.get("text");
            Location loc = new Location(spawnWorld, x, y, z);
            saved.add(new HologramRecord(loc, text));
            spawnEntity(loc, text, true);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Creates a standalone (persistent) hologram. */
    public UUID createHologram(Location loc, String text) {
        saved.add(new HologramRecord(loc.clone(), text));
        save();
        return spawnEntity(loc, text, true);
    }

    /** Creates an NPC-attached hologram — not saved to hologram-data.yml. */
    public UUID createNpcHologram(Location loc, String text) {
        return spawnEntity(loc, text, false);
    }

    public void deleteHologram(UUID uuid) {
        TextDisplay entity = live.remove(uuid);
        if (entity != null && !entity.isDead()) entity.remove();
        save();
    }

    /** Removes the nearest standalone hologram within radius. Returns true if one was found. */
    public boolean deleteNearest(Location loc, double radius) {
        TextDisplay nearest = null;
        double nearestDist = radius * radius;

        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof TextDisplay td)) continue;
            if (!td.getScoreboardTags().contains(TAG)) continue;
            if (td.getScoreboardTags().contains("mv_npc_holo")) continue;
            double d = e.getLocation().distanceSquared(loc);
            if (d < nearestDist) { nearestDist = d; nearest = td; }
        }

        if (nearest == null) return false;

        Location removedLoc = nearest.getLocation();
        live.remove(nearest.getUniqueId());
        nearest.remove();

        saved.removeIf(r ->
            Math.abs(r.loc().getX() - removedLoc.getX()) < 0.5 &&
            Math.abs(r.loc().getY() - removedLoc.getY()) < 0.5 &&
            Math.abs(r.loc().getZ() - removedLoc.getZ()) < 0.5
        );
        save();
        return true;
    }

    public TextDisplay getEntity(UUID uuid) { return live.get(uuid); }

    public void removeNpcHologram(UUID uuid) {
        TextDisplay entity = live.remove(uuid);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        } else {
            Entity byUuid = plugin.getServer().getEntity(uuid);
            if (byUuid instanceof TextDisplay td) td.remove();
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private UUID spawnEntity(Location loc, String text, boolean standalone) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, e -> {
            e.text(parse(text));
            e.setBillboard(Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setDefaultBackground(false);
            e.setPersistent(true);
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

    // ── GUI helpers ───────────────────────────────────────────────────────────

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

    private static ItemStack btn(Material mat, String title, String[] lore) {
        ItemStack item = plain(mat, title);
        ItemMeta meta = item.getItemMeta();
        meta.lore(Arrays.stream(lore).map(HologramManager::leg).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private record HologramRecord(Location loc, String text) {}
}
