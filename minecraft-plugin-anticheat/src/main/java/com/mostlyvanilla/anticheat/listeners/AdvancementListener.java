package com.mostlyvanilla.anticheat.listeners;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public class AdvancementListener implements Listener {

    private final MostlyVanillaAnticheat plugin;

    public AdvancementListener(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getConfig().getBoolean("advancements.block-announcements", true)) return;

        // Suppress the chat broadcast — the advancement is still awarded, toast is client-side
        event.message(null);
    }
}
