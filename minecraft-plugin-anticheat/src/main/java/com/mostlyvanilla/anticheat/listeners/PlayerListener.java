package com.mostlyvanilla.anticheat.listeners;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.antixray.XrayDetector;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final MostlyVanillaAnticheat plugin;
    private final XrayDetector xrayDetector;

    public PlayerListener(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
        this.xrayDetector = new XrayDetector(plugin, plugin.getAntiXrayEngine());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var player = event.getPlayer();
        plugin.getAntiXrayEngine().revealBlock(player, event.getBlock().getLocation());
        xrayDetector.onBlockBreak(player, event.getBlock().getType());
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getSlotType() != PlayerArmorChangeEvent.SlotType.CHEST) return;
        boolean wasElytra = event.getOldItem() != null && event.getOldItem().getType() == Material.ELYTRA;
        boolean isElytra  = event.getNewItem() != null && event.getNewItem().getType() == Material.ELYTRA;
        if (wasElytra || isElytra) {
            plugin.getData(event.getPlayer().getUniqueId()).lastElytraChangeMs = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAntiXrayEngine().clearPlayer(event.getPlayer());
        plugin.removeData(event.getPlayer().getUniqueId());
    }
}
