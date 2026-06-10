package com.mostlyvanilla.anticheat.listeners;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChunkListener implements Listener {

    private final MostlyVanillaAnticheat plugin;

    public ChunkListener(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("anti-xray.enabled", true)) return;
        plugin.getAntiXrayEngine().obfuscateChunk(event.getPlayer(), event.getChunk());
    }
}
