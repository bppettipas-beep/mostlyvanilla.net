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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

public class GlobalWipeManager {

    private final JavaPlugin plugin;

    // Store per-inventory state: which step and where the confirm/cancel buttons are
    private record PanelState(int step, int confirmSlot, int cancelSlot) {}
    private final Map<Inventory, PanelState> panels = new HashMap<>();

    private static final int SLOT_DESC = 4;

    // Button positions shift each step so you can't mindlessly spam-click through
    // Layout (3×9): row1=0-8, row2=9-17, row3=18-26
    //   Step 1: mid-left confirm, mid-right cancel
    //   Step 2: swapped (mid-right confirm, mid-left cancel)
    //   Step 3: moved to bottom-left confirm, bottom-right cancel
    //   Step 4: bottom row swapped (bottom-right confirm, bottom-left cancel)
    private static final int[] CONFIRM_SLOTS = { 11, 15, 20, 24 };
    private static final int[] CANCEL_SLOTS  = { 15, 11, 24, 20 };

    public GlobalWipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isGlobalWipePanel(Inventory inv) { return panels.containsKey(inv); }

    public void openStep1(Player staff) {
        int confirm = CONFIRM_SLOTS[0], cancel = CANCEL_SLOTS[0];
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⚠  Wipe ALL Player Data?", NamedTextColor.GOLD, TextDecoration.BOLD));
        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.TNT, "§6⚠  Global Player Data Wipe",
            "§7This will permanently delete data for",
            "§7EVERY player who has ever joined:",
            "",
            "§c• All inventories & armor",
            "§c• All ender chests",
            "§c• All XP & levels",
            "§c• All kills, deaths & playtime",
            "§c• All economy balances",
            "§c• All advancements",
            "",
            "§7World terrain is NOT affected.",
            "",
            "§e[1/4] §7Find the green button to proceed."));
        inv.setItem(confirm, plain(Material.LIME_STAINED_GLASS_PANE,  "§a§l✔  Yes, proceed"));
        inv.setItem(cancel,  plain(Material.RED_STAINED_GLASS_PANE,   "§c§l✘  Cancel"));
        panels.put(inv, new PanelState(1, confirm, cancel));
        staff.openInventory(inv);
    }

    private void openStep2(Player staff) {
        int confirm = CONFIRM_SLOTS[1], cancel = CANCEL_SLOTS[1];
        int knownPlayers = countKnownPlayers();
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⚠⚠  ARE YOU SURE?  ⚠⚠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.BARRIER, "§4§lSECOND CONFIRMATION",
            "§cThis CANNOT be undone!",
            "",
            "§cYou are about to wipe data for",
            "§f" + knownPlayers + " §cplayers permanently.",
            "",
            "§c• Inventories → §fempty",
            "§c• XP & stats → §f0",
            "§c• Economy → §f0",
            "§c• Advancements → §fdeleted",
            "",
            "§e[2/4] §7The buttons have moved."));
        inv.setItem(confirm, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Confirm — Wipe All"));
        inv.setItem(cancel,  plain(Material.RED_STAINED_GLASS_PANE,  "§c§l✘  Cancel"));
        panels.put(inv, new PanelState(2, confirm, cancel));
        staff.openInventory(inv);
    }

    private void openStep3(Player staff) {
        int confirm = CONFIRM_SLOTS[2], cancel = CANCEL_SLOTS[2];
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("☠  LAST CHANCE  ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.NETHER_STAR, "§4§lTHIRD CONFIRMATION",
            "§cOnce you pass this screen:",
            "§c• All online players will be kicked",
            "§c• EVERY player's data wiped forever",
            "§c• No backups. No recovery.",
            "",
            "§4There is NO going back.",
            "",
            "§e[3/4] §7Buttons moved again. Look carefully."));
        inv.setItem(confirm, plain(Material.ORANGE_STAINED_GLASS_PANE, "§6§l✔  I understand — continue"));
        inv.setItem(cancel,  plain(Material.RED_STAINED_GLASS_PANE,    "§c§l✘  Cancel"));
        panels.put(inv, new PanelState(3, confirm, cancel));
        staff.openInventory(inv);
    }

    private void openStep4(Player staff) {
        int confirm = CONFIRM_SLOTS[3], cancel = CANCEL_SLOTS[3];
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("☠  POINT OF NO RETURN  ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.BEDROCK, "§4§lFINAL CONFIRMATION",
            "§4This is your last chance to cancel.",
            "",
            "§cClicking confirm will IMMEDIATELY:",
            "§c• Kick every online player",
            "§c• Erase all player data forever",
            "",
            "§4NO RECOVERY EXISTS.",
            "",
            "§e[4/4] §7Find the button. Think before you click."));
        inv.setItem(confirm, plain(Material.MAGENTA_STAINED_GLASS_PANE, "§5§l☠  EXECUTE GLOBAL WIPE"));
        inv.setItem(cancel,  plain(Material.RED_STAINED_GLASS_PANE,     "§c§l✘  Cancel"));
        panels.put(inv, new PanelState(4, confirm, cancel));
        staff.openInventory(inv);
    }

    public void handleClick(Player staff, Inventory inv, int slot) {
        PanelState state = panels.get(inv);
        if (state == null) return;

        if (slot == state.cancelSlot()) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) staff::closeInventory);
            return;
        }

        if (slot == state.confirmSlot()) {
            switch (state.step()) {
                case 1 -> Bukkit.getScheduler().runTask(plugin, () -> openStep2(staff));
                case 2 -> Bukkit.getScheduler().runTask(plugin, () -> openStep3(staff));
                case 3 -> Bukkit.getScheduler().runTask(plugin, () -> openStep4(staff));
                case 4 -> Bukkit.getScheduler().runTask(plugin, () -> {
                    staff.closeInventory();
                    executeGlobalWipe(staff);
                });
            }
        }
    }

    public void onInventoryClose(Inventory inv) {
        panels.remove(inv);
    }

    private void executeGlobalWipe(Player staff) {
        plugin.getLogger().info("[GlobalWipe] Initiated by " + staff.getName());

        Bukkit.broadcast(
            Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED).append(Component.newline())
            .append(Component.text("  GLOBAL DATA WIPE INITIATED", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.newline())
            .append(Component.text("  All player data is being erased.", NamedTextColor.YELLOW)).append(Component.newline())
            .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED)));

        Component kickMsg = Component.text("A global data wipe is in progress. Please reconnect shortly.", NamedTextColor.RED);

        // Kick all online players before wiping so no data gets saved on top
        for (Player p : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!p.equals(staff)) p.kick(kickMsg);
        }

        World mainWorld = Bukkit.getWorlds().get(0);
        File worldFolder = mainWorld.getWorldFolder();

        // Collect all known player UUIDs from playerdata files before deletion
        Set<UUID> allUuids = collectAllUuids(worldFolder);

        // Delete playerdata, stats, advancements files
        deleteFilesIn(new File(worldFolder, "playerdata"), ".dat", ".dat_old");
        deleteFilesIn(new File(worldFolder, "stats"), ".json");
        deleteFilesIn(new File(worldFolder, "advancements"), ".json");

        // Wipe economy for every known UUID
        for (UUID uuid : allUuids) {
            wipeCurrencies(uuid);
        }

        int count = allUuids.size();
        staff.sendMessage(
            Component.text("✔ ", NamedTextColor.GREEN)
                .append(Component.text("Global wipe complete. Erased data for ", NamedTextColor.WHITE))
                .append(Component.text(count + " player" + (count == 1 ? "" : "s"), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.WHITE)));
        plugin.getLogger().info("[GlobalWipe] Complete — wiped " + count + " players.");
    }

    private Set<UUID> collectAllUuids(File worldFolder) {
        Set<UUID> uuids = new HashSet<>();
        File pdFolder = new File(worldFolder, "playerdata");
        if (!pdFolder.exists()) return uuids;
        File[] files = pdFolder.listFiles();
        if (files == null) return uuids;
        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(".dat")) continue;
            try {
                uuids.add(UUID.fromString(name.replace(".dat", "")));
            } catch (IllegalArgumentException ignored) {}
        }
        return uuids;
    }

    private void deleteFilesIn(File folder, String... extensions) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            for (String ext : extensions) {
                if (f.getName().endsWith(ext)) {
                    if (!f.delete()) plugin.getLogger().warning("[GlobalWipe] Could not delete " + f.getName());
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void wipeCurrencies(UUID uuid) {
        Plugin ecoPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (ecoPlugin == null) return;
        try {
            Object em = ecoPlugin.getClass().getMethod("getEconomyManager").invoke(ecoPlugin);
            if (em == null) return;
            Collection<String> currencies = (Collection<String>)
                em.getClass().getMethod("getCurrencies").invoke(em);
            Method setBalance = em.getClass().getMethod("setBalance", UUID.class, String.class, double.class);
            for (String currency : currencies) {
                setBalance.invoke(em, uuid, currency, 0.0);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[GlobalWipe] Failed to wipe currencies for " + uuid + ": " + e.getMessage());
        }
    }

    private int countKnownPlayers() {
        World mainWorld = Bukkit.getWorlds().get(0);
        File pdFolder = new File(mainWorld.getWorldFolder(), "playerdata");
        if (!pdFolder.exists()) return 0;
        File[] files = pdFolder.listFiles(f -> f.getName().endsWith(".dat"));
        return files != null ? files.length : 0;
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
        meta.lore(Stream.of(lore).map(GlobalWipeManager::leg).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
