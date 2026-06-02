package com.mostlyvanilla.spawn.listeners;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NpcListener implements Listener {

    private final MostlyVanillaSpawn plugin;

    public NpcListener(MostlyVanillaSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        String command = plugin.getNpcManager().getCommand(event.getNPC().getId());
        if (command == null || command.isBlank()) return;
        event.getClicker().performCommand(command);
    }
}
