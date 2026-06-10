package com.mostlyvanilla.anticheat.gui;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.UUID;

public class SusGuiListener implements Listener {

    private final MostlyVanillaAnticheat plugin;

    public SusGuiListener(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SusGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (gui.isPrevSlot(slot)) {
            final int prev = gui.getCurrentPage() - 1;
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.openInventory(new SusGui(plugin, prev).getInventory()));
            return;
        }
        if (gui.isNextSlot(slot)) {
            final int next = gui.getCurrentPage() + 1;
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.openInventory(new SusGui(plugin, next).getInventory()));
            return;
        }

        UUID targetUuid = gui.getTargetAt(slot);
        if (targetUuid == null) return;

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String name = target.getName();
        if (name == null) return;

        player.closeInventory();
        player.performCommand("staff " + name);
    }
}
