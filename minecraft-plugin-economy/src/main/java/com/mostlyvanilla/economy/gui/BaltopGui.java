package com.mostlyvanilla.economy.gui;

import com.mostlyvanilla.economy.EconomyManager;
import com.mostlyvanilla.economy.commands.EcoCommand;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class BaltopGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final String GUI_TITLE_PREFIX = "§6§lRichest Players";

    private final EconomyManager economy;
    private final Plugin plugin;

    // Tracks state for each player currently viewing the GUI
    private final Map<UUID, GuiState> openGuiState = new HashMap<>();
    // Set during open() so InventoryCloseEvent (fired by openInventory) doesn't wipe new state
    private final Set<UUID> switching = new HashSet<>();

    public BaltopGui(EconomyManager economy, Plugin plugin) {
        this.economy = economy;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, String currency, int page) {
        List<OfflinePlayer> sorted = buildSortedList(currency);
        int totalPages = Math.max(1, (int) Math.ceil(sorted.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = GUI_TITLE_PREFIX + " §8— §e" + currency;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildSkull(sorted.get(i), i + 1, currency));
        }

        // Fill unused content slots with black pane
        ItemStack filler = glassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = end - start; i < PAGE_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Bottom navigation row — fill gaps with gray pane
        ItemStack gray = glassPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, gray);
        }

        // Previous button
        if (page > 0) {
            inv.setItem(PREV_SLOT, navItem(Material.ARROW, "§aPrevious Page",
                    Collections.singletonList("§7Click to go to page §e" + page)));
        }

        // Info button
        inv.setItem(INFO_SLOT, navItem(Material.PAPER,
                "§aPage §e" + (page + 1) + " §aof §e" + totalPages,
                Arrays.asList(
                        "§7Currency: §e" + currency,
                        "§7Total players: §e" + sorted.size()
                )));

        // Next button
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, navItem(Material.ARROW, "§aNext Page",
                    Collections.singletonList("§7Click to go to page §e" + (page + 2))));
        }

        // Protect state from being wiped by the InventoryCloseEvent that fires
        // when openInventory() closes the old inventory.
        switching.add(player.getUniqueId());
        openGuiState.put(player.getUniqueId(), new GuiState(currency, page, totalPages));
        player.openInventory(inv);
        // Schedule removal of switch guard for after the close event fires
        Bukkit.getScheduler().runTask(plugin, () -> switching.remove(player.getUniqueId()));
    }

    private List<OfflinePlayer> buildSortedList(String currency) {
        List<OfflinePlayer> list = new ArrayList<>(Arrays.asList(Bukkit.getOfflinePlayers()));
        list.sort((a, b) -> Double.compare(
                economy.getBalance(b.getUniqueId(), currency),
                economy.getBalance(a.getUniqueId(), currency)
        ));
        return list;
    }

    private ItemStack buildSkull(OfflinePlayer op, int rank, String sortCurrency) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(op);

        String name = op.getName() != null ? op.getName() : "Unknown";
        meta.setDisplayName(rankColor(rank) + rankSymbol(rank) + name);

        List<String> lore = new ArrayList<>();
        lore.add("§8§m──────────────────");
        lore.add("§7Rank: " + rankColor(rank) + "#" + rank);
        lore.add("");
        lore.add("§7Balances:");
        for (String c : economy.getCurrencies()) {
            double bal = economy.getBalance(op.getUniqueId(), c);
            boolean isSort = c.equalsIgnoreCase(sortCurrency);
            String label = isSort ? "§e§l" + c : "§8" + c;
            String value = isSort ? "§a" + EcoCommand.fmt(bal) : "§7" + EcoCommand.fmt(bal);
            lore.add("  " + label + " §7» " + value);
        }
        lore.add("§8§m──────────────────");

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private String rankColor(int rank) {
        switch (rank) {
            case 1: return "§6";
            case 2: return "§f";
            case 3: return "§c";
            default: return "§7";
        }
    }

    private String rankSymbol(int rank) {
        switch (rank) {
            case 1: return "§l★ #1 §r§6";
            case 2: return "§l#2 §r§f";
            case 3: return "§l#3 §r§c";
            default: return "#" + rank + " §f";
        }
    }

    private ItemStack glassPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        GuiState state = openGuiState.get(player.getUniqueId());
        if (state == null) return;

        int slot = event.getRawSlot();
        if (slot == PREV_SLOT && state.page > 0) {
            open(player, state.currency, state.page - 1);
        } else if (slot == NEXT_SLOT && state.page < state.totalPages - 1) {
            open(player, state.currency, state.page + 1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        UUID uuid = ((Player) event.getPlayer()).getUniqueId();
        if (!switching.contains(uuid)) {
            openGuiState.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openGuiState.remove(uuid);
        switching.remove(uuid);
    }

    private static class GuiState {
        final String currency;
        final int page;
        final int totalPages;

        GuiState(String currency, int page, int totalPages) {
            this.currency = currency;
            this.page = page;
            this.totalPages = totalPages;
        }
    }
}
