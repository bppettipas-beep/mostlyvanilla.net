package com.mostlyvanilla.combatlog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    private static final long COMBAT_MS = 15_000;

    private final Map<UUID, Long> expiry = new HashMap<>();

    public void tag(Player a, Player b) {
        long end = System.currentTimeMillis() + COMBAT_MS;
        expiry.put(a.getUniqueId(), end);
        expiry.put(b.getUniqueId(), end);
    }

    public boolean isInCombat(UUID uuid) {
        Long end = expiry.get(uuid);
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) { expiry.remove(uuid); return false; }
        return true;
    }

    public void clear(UUID uuid) {
        expiry.remove(uuid);
    }

    /** Called every second — updates action bars and cleans up expired tags. */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = expiry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) { it.remove(); continue; }
            long remaining = e.getValue() - now;
            if (remaining <= 0) {
                it.remove();
                player.sendActionBar(Component.empty());
                player.sendMessage(Component.text("You are no longer in combat.", NamedTextColor.GREEN));
            } else {
                int secs = (int) Math.ceil(remaining / 1000.0);
                NamedTextColor timerColor = secs <= 5 ? NamedTextColor.RED : NamedTextColor.YELLOW;
                player.sendActionBar(
                    Component.text("⚔ In Combat: ", NamedTextColor.RED)
                        .append(Component.text(secs + "s", timerColor).decorate(TextDecoration.BOLD))
                        .append(Component.text(" ⚔", NamedTextColor.RED))
                );
            }
        }
    }
}
