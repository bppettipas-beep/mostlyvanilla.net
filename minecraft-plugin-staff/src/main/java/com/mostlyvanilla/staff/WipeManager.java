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

public class WipeManager {

    private final JavaPlugin plugin;
    private final Map<Inventory, WipeData> wipePanels = new HashMap<>();

    private static final int SLOT_DESC    = 4;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL  = 15;

    public WipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isWipePanel(Inventory inv) { return wipePanels.containsKey(inv); }

    // ── Open step 1 ───────────────────────────────────────────────────────────

    public void openStep1(Player staff, OfflinePlayer target) {
        String name = target.getName() != null ? target.getName() : "Unknown";
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⚠  Wipe Player Data?", NamedTextColor.GOLD, TextDecoration.BOLD));

        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.TNT, "§e⚠  Wipe §f" + name + "§e?",
            "§7This will permanently delete:",
            "§c• All currency balances",
            "§c• Inventory & armor",
            "§c• Ender chest",
            "§c• Playtime & XP",
            "§c• Kills & deaths",
            "",
            "§7Click §aYes, proceed §7to continue."));
        inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Yes, proceed"));
        inv.setItem(SLOT_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,  "§c§l✘  Cancel"));

        wipePanels.put(inv, new WipeData(staff.getUniqueId(), target.getUniqueId(), name, 1));
        staff.openInventory(inv);
    }

    // ── Open step 2 ───────────────────────────────────────────────────────────

    private void openStep2(Player staff, UUID targetUuid, String targetName) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⚠⚠  ARE YOU SURE?  ⚠⚠", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.BARRIER, "§4§lFINAL WARNING",
            "§cThis CANNOT be undone!",
            "",
            "§cYou are about to permanently wipe",
            "§call data for §f" + targetName + "§c:",
            "§c• All currency balances → §f0",
            "§c• Inventory & armor → §fempty",
            "§c• Ender chest → §fempty",
            "§c• Playtime & XP → §f0",
            "§c• Kills & deaths → §f0",
            "",
            "§7Click §aWipe Everything §7to confirm."));
        inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Wipe Everything"));
        inv.setItem(SLOT_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,  "§c§l✘  Cancel"));

        wipePanels.put(inv, new WipeData(staff.getUniqueId(), targetUuid, targetName, 2));
        staff.openInventory(inv);
    }

    // ── Handle click ──────────────────────────────────────────────────────────

    public void handleClick(Player staff, Inventory inv, int slot) {
        WipeData data = wipePanels.get(inv);
        if (data == null) return;

        if (slot == SLOT_CANCEL) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) staff::closeInventory);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            if (data.step == 1) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    openStep2(staff, data.targetUuid, data.targetName));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    staff.closeInventory();
                    executeWipe(staff, data);
                });
            }
        }
    }

    public void onInventoryClose(Inventory inv) {
        wipePanels.remove(inv);
    }

    // ── Execute wipe ──────────────────────────────────────────────────────────

    private void executeWipe(Player staff, WipeData data) {
        UUID uuid   = data.targetUuid;
        String name = data.targetName;
        Player target = Bukkit.getPlayer(uuid);

        wipeCurrencies(uuid);

        if (target != null) {
            // Online: wipe via API so changes are live immediately
            target.getInventory().clear();
            target.getInventory().setHelmet(null);
            target.getInventory().setChestplate(null);
            target.getInventory().setLeggings(null);
            target.getInventory().setBoots(null);
            target.getInventory().setItemInOffHand(null);
            target.getEnderChest().clear();
            target.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
            target.setStatistic(Statistic.PLAYER_KILLS, 0);
            target.setStatistic(Statistic.DEATHS, 0);
            target.setLevel(0);
            target.setExp(0f);
            target.saveData();
            target.sendMessage(Component.text("Your data has been wiped by staff.", NamedTextColor.RED));
        } else {
            // Offline: delete the data files — they'll start fresh on next login
            World world = Bukkit.getWorlds().get(0);
            File worldFolder = world.getWorldFolder();
            deleteIfExists(new File(worldFolder, "playerdata/" + uuid + ".dat"));
            deleteIfExists(new File(worldFolder, "stats/" + uuid + ".json"));
        }

        staff.sendMessage(
            Component.text("✔ ", NamedTextColor.GREEN)
                .append(Component.text("Wiped all data for ", NamedTextColor.WHITE))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.WHITE)));
        plugin.getLogger().info("[Wipe] " + staff.getName() + " wiped all data for " + name + " (" + uuid + ")");
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
            plugin.getLogger().warning("[Wipe] Failed to wipe currencies: " + e.getMessage());
        }
    }

    private void deleteIfExists(File f) {
        if (f.exists() && !f.delete())
            plugin.getLogger().warning("[Wipe] Could not delete " + f.getName());
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

    private static ItemStack btn(Material mat, String legacy, String... lore) {
        ItemStack item = plain(mat, legacy);
        ItemMeta meta = item.getItemMeta();
        meta.lore(Stream.of(lore).map(WipeManager::leg).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
