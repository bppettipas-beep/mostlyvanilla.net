package com.mostlyvanilla.home.listeners;

import com.mostlyvanilla.home.Home;
import com.mostlyvanilla.home.HomeManager;
import com.mostlyvanilla.home.MostlyVanillaHome;
import com.mostlyvanilla.home.TeleportManager;
import com.mostlyvanilla.home.gui.HomeActionHolder;
import com.mostlyvanilla.home.gui.HomeGui;
import com.mostlyvanilla.home.gui.HomeGuiHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuiListener implements Listener {

    private final MostlyVanillaHome plugin;
    private final HomeManager       homeManager;
    private final TeleportManager   teleportManager;
    private final HomeGui           homeGui;

    private final Map<UUID, String> pendingRenames   = new HashMap<>();
    // Guards against double-click / double-fire on action slots
    private final Set<UUID>         processingAction = new HashSet<>();

    public GuiListener(MostlyVanillaHome plugin, HomeManager homeManager,
                       TeleportManager teleportManager, HomeGui homeGui) {
        this.plugin          = plugin;
        this.homeManager     = homeManager;
        this.teleportManager = teleportManager;
        this.homeGui         = homeGui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof HomeGuiHolder) && !(holder instanceof HomeActionHolder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;
        if (processingAction.contains(player.getUniqueId())) return;

        processingAction.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> processingAction.remove(player.getUniqueId()));

        if (holder instanceof HomeGuiHolder h) {
            handleMainGui(player, event.getSlot(), event.isRightClick(), h.getPage(), h);
        } else {
            handleActionGui(player, event.getSlot(), (HomeActionHolder) holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRenames.remove(event.getPlayer().getUniqueId());
    }

    // ── Main GUI ──────────────────────────────────────────────────────────────

    private void handleMainGui(Player player, int slot, boolean rightClick, int page, HomeGuiHolder holder) {
        if (slot == holder.getActionSlot()) { handleSetHomeButton(player, page); return; }

        if (holder.isEmptySlot(slot)) { handleSetHomeButton(player, page); return; }

        if (holder.isLockedSlot(slot)) {
            String role = holder.getLockedRole(slot);
            if (role != null && !role.isEmpty()) {
                player.sendMessage(Component.text("Buy ", NamedTextColor.RED)
                    .append(Component.text(capitalize(role), NamedTextColor.GOLD))
                    .append(Component.text(" to unlock this home slot.", NamedTextColor.RED)));
            }
            return;
        }

        String homeName = holder.getHomeName(slot);
        if (homeName == null) return;

        Home home = homeManager.getHome(player.getUniqueId(), homeName.toLowerCase());
        if (home == null) return;

        if (rightClick) {
            homeGui.openAction(player, home.getName(), page);
        } else {
            teleportTo(player, home);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void handleSetHomeButton(Player player, int page) {
        int limit = homeManager.getHomeLimit(player);
        int count = homeManager.getHomes(player.getUniqueId()).size();
        if (limit >= 0 && count >= limit) {
            player.sendMessage(Component.text("You have reached your home limit (" + limit + ").", NamedTextColor.RED));
            return;
        }
        String name = generateUniqueName(player);
        homeManager.setHome(player, name);
        player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
            .append(Component.text(name, NamedTextColor.GOLD))
            .append(Component.text(" set!", NamedTextColor.GREEN)));
        player.closeInventory();
    }

    // ── Action sub-GUI ────────────────────────────────────────────────────────

    private void handleActionGui(Player player, int slot, HomeActionHolder holder) {
        String homeName  = holder.getHomeName();
        int    returnPage = holder.getReturnPage();

        switch (slot) {
            case 11 -> { // Teleport
                Home home = homeManager.getHome(player.getUniqueId(), homeName.toLowerCase());
                if (home == null) { player.sendMessage(Component.text("Home not found.", NamedTextColor.RED)); homeGui.open(player, returnPage); return; }
                player.closeInventory();
                teleportTo(player, home);
            }
            case 13 -> { // Rename
                player.closeInventory();
                pendingRenames.put(player.getUniqueId(), homeName);
                player.sendMessage(Component.text("Type the new name for ", NamedTextColor.YELLOW)
                    .append(Component.text(homeName, NamedTextColor.GOLD))
                    .append(Component.text(" in chat. Type ", NamedTextColor.YELLOW))
                    .append(Component.text("cancel", NamedTextColor.RED))
                    .append(Component.text(" to abort.", NamedTextColor.YELLOW)));
            }
            case 15 -> { // Delete
                if (homeManager.deleteHome(player.getUniqueId(), homeName)) {
                    player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
                        .append(Component.text(homeName, NamedTextColor.GOLD))
                        .append(Component.text(" deleted.", NamedTextColor.GREEN)));
                }
                homeGui.open(player, returnPage);
            }
            case 22 -> homeGui.open(player, returnPage); // Back
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void teleportTo(Player player, Home home) {
        Location loc = home.toLocation();
        if (loc == null) {
            player.sendMessage(Component.text("The world for that home doesn't exist.", NamedTextColor.RED));
            return;
        }
        teleportManager.startTeleport(player, loc);
    }

    private String generateUniqueName(Player player) {
        List<Home> existing = homeManager.getHomes(player.getUniqueId());
        Set<String> keys = new HashSet<>();
        for (Home h : existing) keys.add(h.getName().toLowerCase());
        if (!keys.contains("home")) return "home";
        int i = 2;
        while (keys.contains("home" + i)) i++;
        return "home" + i;
    }

    public String getPendingRename(UUID uuid) { return pendingRenames.get(uuid); }
    public void clearPendingRename(UUID uuid) { pendingRenames.remove(uuid); }
}
