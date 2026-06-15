package com.mostlyvanilla.teams;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTracker implements Listener {

    private static final long COMBAT_MS = 15_000L;
    private final Map<UUID, Long> expiry = new HashMap<>();

    public boolean isInCombat(UUID uuid) {
        Long end = expiry.get(uuid);
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) { expiry.remove(uuid); return false; }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        long end = System.currentTimeMillis() + COMBAT_MS;
        expiry.put(victim.getUniqueId(), end);
        expiry.put(attacker.getUniqueId(), end);
    }
}
