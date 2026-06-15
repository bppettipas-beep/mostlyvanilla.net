package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

public class TransferManager {

    private final JavaPlugin plugin;
    private final Map<Inventory, TransferData> panels = new HashMap<>();

    private static final int SLOT_DESC    = 4;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL  = 15;

    public TransferManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTransferPanel(Inventory inv) { return panels.containsKey(inv); }

    // ── Open step 1 ───────────────────────────────────────────────────────────

    public void openStep1(Player staff, OfflinePlayer from, OfflinePlayer to) {
        String fromName = from.getName() != null ? from.getName() : "Unknown";
        String toName   = to.getName()   != null ? to.getName()   : "Unknown";

        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⇄  Transfer Player Data?", NamedTextColor.GOLD, TextDecoration.BOLD));

        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.HOPPER,
            "§e⇄  Transfer §f" + fromName + " §e→ §f" + toName,
            "§7This will transfer ALL data from source to target:",
            "§b• All currency balances",
            "§b• Playtime, XP, kills & deaths (both online required)",
            "§b• All homes",
            "§b• Team membership & leadership",
            "§b• Role",
            "§b• Settings",
            "§b• Transaction history",
            "§b• Auction house listings & orders",
            "",
            "§cSource player data will be cleared after transfer.",
            "§7Click §aYes, proceed §7to continue."));
        inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Yes, proceed"));
        inv.setItem(SLOT_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,  "§c§l✘  Cancel"));

        panels.put(inv, new TransferData(staff.getUniqueId(), from.getUniqueId(), fromName, to.getUniqueId(), toName, 1));
        staff.openInventory(inv);
    }

    // ── Open step 2 ───────────────────────────────────────────────────────────

    private void openStep2(Player staff, TransferData data) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("⚠⚠  ARE YOU SURE?  ⚠⚠", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        fill(inv);
        inv.setItem(SLOT_DESC, btn(Material.BARRIER,
            "§4§lFINAL WARNING",
            "§cThis CANNOT be undone!",
            "",
            "§cTransferring ALL data from",
            "§f" + data.fromName + " §c→ §f" + data.toName,
            "",
            "§cSource data that will be cleared:",
            "§c• All currency balances → §f0",
            "§c• Homes → §fdeleted",
            "§c• Team membership → §fremoved",
            "§c• Role → §fcleared",
            "§c• Settings → §fcleared",
            "",
            "§7Click §aTransfer Everything §7to confirm."));
        inv.setItem(SLOT_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Transfer Everything"));
        inv.setItem(SLOT_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,  "§c§l✘  Cancel"));

        panels.put(inv, new TransferData(data.staffUuid, data.fromUuid, data.fromName, data.toUuid, data.toName, 2));
        staff.openInventory(inv);
    }

    // ── Handle click ──────────────────────────────────────────────────────────

    public void handleClick(Player staff, Inventory inv, int slot) {
        TransferData data = panels.get(inv);
        if (data == null) return;

        if (slot == SLOT_CANCEL) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) staff::closeInventory);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            if (data.step == 1) {
                Bukkit.getScheduler().runTask(plugin, () -> openStep2(staff, data));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    staff.closeInventory();
                    executeTransfer(staff, data);
                });
            }
        }
    }

    public void onInventoryClose(Inventory inv) {
        panels.remove(inv);
    }

    // ── Execute transfer ──────────────────────────────────────────────────────

    private void executeTransfer(Player staff, TransferData data) {
        UUID from     = data.fromUuid;
        UUID to       = data.toUuid;
        String fromName = data.fromName;
        String toName   = data.toName;

        List<String> results = new ArrayList<>();
        transferCurrencies(from, to, results);
        transferStats(from, to, results);
        transferHomes(from, to, results);
        transferRole(from, to, results);
        transferTeam(from, to, results);
        transferSettings(from, to, results);
        transferHistory(from, fromName, to, toName, results);
        transferAH(from, fromName, to, toName, results);

        staff.sendMessage(leg("§e§l⇄ Transfer complete: §f" + fromName + " §e→ §f" + toName));
        for (String line : results) staff.sendMessage(leg(line));
        plugin.getLogger().info("[Transfer] " + staff.getName() + " transferred all data: "
            + fromName + " (" + from + ") -> " + toName + " (" + to + ")");
    }

    // ── Individual transfer methods ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void transferCurrencies(UUID from, UUID to, List<String> out) {
        Plugin eco = Bukkit.getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (eco == null) { out.add("§7• Economy: plugin not found"); return; }
        try {
            Object em = eco.getClass().getMethod("getEconomyManager").invoke(eco);
            Collection<String> currencies = (Collection<String>) em.getClass().getMethod("getCurrencies").invoke(em);
            Method getBalance = em.getClass().getMethod("getBalance", UUID.class, String.class);
            Method setBalance = em.getClass().getMethod("setBalance", UUID.class, String.class, double.class);
            for (String cur : currencies) {
                double bal = (double) getBalance.invoke(em, from, cur);
                setBalance.invoke(em, to, cur, bal);
                setBalance.invoke(em, from, cur, 0.0);
            }
            out.add("§a✔ Economy: balances transferred (" + currencies.size() + " currencies)");
        } catch (Exception e) {
            out.add("§c✘ Economy: " + e.getMessage());
        }
    }

    private void transferStats(UUID from, UUID to, List<String> out) {
        Player pFrom = Bukkit.getPlayer(from);
        Player pTo   = Bukkit.getPlayer(to);
        if (pFrom == null || pTo == null) {
            out.add("§7• Stats/Playtime: skipped — both players must be online");
            return;
        }
        int playtime = pFrom.getStatistic(Statistic.PLAY_ONE_MINUTE);
        pTo.setStatistic(Statistic.PLAY_ONE_MINUTE, pTo.getStatistic(Statistic.PLAY_ONE_MINUTE) + playtime);
        pFrom.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);

        int kills = pFrom.getStatistic(Statistic.PLAYER_KILLS);
        pTo.setStatistic(Statistic.PLAYER_KILLS, pTo.getStatistic(Statistic.PLAYER_KILLS) + kills);
        pFrom.setStatistic(Statistic.PLAYER_KILLS, 0);

        int deaths = pFrom.getStatistic(Statistic.DEATHS);
        pTo.setStatistic(Statistic.DEATHS, pTo.getStatistic(Statistic.DEATHS) + deaths);
        pFrom.setStatistic(Statistic.DEATHS, 0);

        pTo.setLevel(pTo.getLevel() + pFrom.getLevel());
        pFrom.setLevel(0);
        pFrom.setExp(0f);
        pFrom.saveData();
        pTo.saveData();
        out.add("§a✔ Stats: playtime, kills, deaths & XP transferred");
    }

    private void transferHomes(UUID from, UUID to, List<String> out) {
        Plugin homePlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaHome");
        if (homePlugin == null) { out.add("§7• Homes: plugin not found"); return; }
        try {
            File f = new File(homePlugin.getDataFolder(), "homes.yml");
            if (!f.exists()) { out.add("§7• Homes: no data"); return; }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String fromKey = "players." + from + ".homes";
            ConfigurationSection fromSec = cfg.getConfigurationSection(fromKey);
            if (fromSec == null) { out.add("§7• Homes: none to transfer"); return; }

            String toKey = "players." + to + ".homes";
            int count = 0;
            for (String name : fromSec.getKeys(false)) {
                String src = fromKey + "." + name + ".";
                String dst = toKey   + "." + name + ".";
                for (String field : List.of("world", "x", "y", "z", "yaw", "pitch")) {
                    cfg.set(dst + field, cfg.get(src + field));
                }
                count++;
            }
            cfg.set("players." + from, null);
            cfg.save(f);

            Object hm = homePlugin.getClass().getMethod("getHomeManager").invoke(homePlugin);
            hm.getClass().getMethod("load").invoke(hm);
            out.add("§a✔ Homes: " + count + " transferred");
        } catch (Exception e) {
            out.add("§c✘ Homes: " + e.getMessage());
        }
    }

    private void transferRole(UUID from, UUID to, List<String> out) {
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) { out.add("§7• Role: plugin not found"); return; }
        try {
            File f = new File(rolesPlugin.getDataFolder(), "players.yml");
            if (!f.exists()) { out.add("§7• Role: no data"); return; }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String role = cfg.getString("players." + from);
            if (role == null) { out.add("§7• Role: none to transfer"); return; }

            cfg.set("players." + to, role);
            cfg.set("players." + from, null);
            cfg.save(f);

            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            rm.getClass().getMethod("load").invoke(rm);
            out.add("§a✔ Role: '" + role + "' transferred");
        } catch (Exception e) {
            out.add("§c✘ Role: " + e.getMessage());
        }
    }

    private void transferTeam(UUID from, UUID to, List<String> out) {
        Plugin teamsPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaTeams");
        if (teamsPlugin == null) { out.add("§7• Team: plugin not found"); return; }
        try {
            File f = new File(teamsPlugin.getDataFolder(), "teams.yml");
            if (!f.exists()) { out.add("§7• Team: no data"); return; }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection root = cfg.getConfigurationSection("teams");
            if (root == null) { out.add("§7• Team: no teams"); return; }

            String fromStr = from.toString();
            String toStr   = to.toString();
            String foundTeamName = null;
            boolean wasLeader = false;

            for (String id : root.getKeys(false)) {
                String base = "teams." + id + ".";
                List<String> members = new ArrayList<>(cfg.getStringList(base + "members"));
                if (!members.contains(fromStr)) continue;

                members.remove(fromStr);
                if (!members.contains(toStr)) members.add(toStr);
                cfg.set(base + "members", members);

                if (fromStr.equals(cfg.getString(base + "leader"))) {
                    cfg.set(base + "leader", toStr);
                    wasLeader = true;
                }
                foundTeamName = cfg.getString(base + "name", id);
                break;
            }

            if (foundTeamName == null) { out.add("§7• Team: not in any team"); return; }

            cfg.save(f);

            try {
                Object tm = teamsPlugin.getClass().getMethod("getManager").invoke(teamsPlugin);
                tm.getClass().getMethod("load").invoke(tm);
            } catch (Exception ignored) {}

            out.add("§a✔ Team: moved to '" + foundTeamName + "'" + (wasLeader ? " (as leader)" : ""));
        } catch (Exception e) {
            out.add("§c✘ Team: " + e.getMessage());
        }
    }

    private void transferSettings(UUID from, UUID to, List<String> out) {
        Plugin settingsPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaSettings");
        if (settingsPlugin == null) { out.add("§7• Settings: plugin not found"); return; }
        try {
            File f = new File(settingsPlugin.getDataFolder(), "settings.yml");
            if (!f.exists()) { out.add("§7• Settings: no data"); return; }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String fromKey = "players." + from;
            ConfigurationSection fromSec = cfg.getConfigurationSection(fromKey);
            if (fromSec == null) { out.add("§7• Settings: none to transfer"); return; }

            String toKey = "players." + to;
            for (String key : fromSec.getKeys(false)) {
                cfg.set(toKey + "." + key, cfg.get(fromKey + "." + key));
            }
            cfg.set(fromKey, null);
            cfg.save(f);

            Object sm = settingsPlugin.getClass().getMethod("getSettingsManager").invoke(settingsPlugin);
            sm.getClass().getMethod("load").invoke(sm);
            out.add("§a✔ Settings: transferred");
        } catch (Exception e) {
            out.add("§c✘ Settings: " + e.getMessage());
        }
    }

    private void transferHistory(UUID from, String fromName, UUID to, String toName, List<String> out) {
        String[] pluginNames = {"MostlyVanillaShop", "MVSpawners", "MostlyVanillaAuctionHouse"};
        int total = 0;
        for (String pName : pluginNames) {
            Plugin p = Bukkit.getPluginManager().getPlugin(pName);
            if (p == null) continue;
            File f = new File(p.getDataFolder(), "history.yml");
            if (!f.exists()) continue;
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                List<Map<?, ?>> rawList = cfg.getMapList("history");
                boolean changed = false;
                for (Map<?, ?> rawEntry : rawList) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = (Map<String, Object>) rawEntry;
                    if (from.toString().equals(entry.get("uuid"))) {
                        entry.put("uuid", to.toString());
                        entry.put("name", toName);
                        changed = true;
                        total++;
                    }
                }
                if (changed) {
                    cfg.set("history", rawList);
                    cfg.save(f);
                }
            } catch (Exception ignored) {}
        }
        out.add(total > 0
            ? "§a✔ History: " + total + " entries rebranded"
            : "§7• History: no entries found");
    }

    private void transferAH(UUID from, String fromName, UUID to, String toName, List<String> out) {
        Plugin ahPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaAuctionHouse");
        if (ahPlugin == null) { out.add("§7• Auction House: plugin not found"); return; }
        try {
            File dataFolder = ahPlugin.getDataFolder();
            int changed = 0;

            File listingsFile = new File(dataFolder, "listings.yml");
            if (listingsFile.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(listingsFile);
                ConfigurationSection sec = cfg.getConfigurationSection("listings");
                if (sec != null) {
                    for (String id : sec.getKeys(false)) {
                        String base = "listings." + id + ".";
                        if (from.toString().equals(cfg.getString(base + "seller-uuid"))) {
                            cfg.set(base + "seller-uuid", to.toString());
                            cfg.set(base + "seller-name", toName);
                            changed++;
                        }
                    }
                }
                cfg.save(listingsFile);
            }

            File ordersFile = new File(dataFolder, "orders.yml");
            if (ordersFile.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(ordersFile);
                ConfigurationSection orderSec = cfg.getConfigurationSection("orders");
                if (orderSec != null) {
                    for (String id : orderSec.getKeys(false)) {
                        String base = "orders." + id + ".";
                        if (from.toString().equals(cfg.getString(base + "buyer-uuid"))) {
                            cfg.set(base + "buyer-uuid", to.toString());
                            cfg.set(base + "buyer-name", toName);
                            changed++;
                        }
                    }
                }
                // Pending deliveries
                String fromPend = "pending-deliveries." + from;
                String toPend   = "pending-deliveries." + to;
                ConfigurationSection pendSec = cfg.getConfigurationSection(fromPend);
                if (pendSec != null) {
                    int idx = 0;
                    while (cfg.contains(toPend + "." + idx)) idx++;
                    for (String key : pendSec.getKeys(false)) {
                        cfg.set(toPend + "." + (idx++), cfg.get(fromPend + "." + key));
                    }
                    cfg.set(fromPend, null);
                }
                cfg.save(ordersFile);
            }

            out.add(changed > 0
                ? "§a✔ AH: " + changed + " listing(s)/order(s) rebranded (takes effect on restart)"
                : "§7• AH: no listings or orders to transfer");
        } catch (Exception e) {
            out.add("§c✘ Auction House: " + e.getMessage());
        }
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
        meta.lore(Stream.of(lore).map(TransferManager::leg).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
