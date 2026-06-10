package com.mostlyvanilla.anticheat.checks;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class KillAuraCheck {

    private final MostlyVanillaAnticheat plugin;

    public KillAuraCheck(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public void check(Player attacker, Entity target) {
        if (!plugin.getConfig().getBoolean("checks.killaura.enabled", true)) return;
        if (attacker.hasPermission("mostlyvanilla.ac.bypass")) return;
        if (attacker.getGameMode() == GameMode.CREATIVE) return;

        PlayerData data = plugin.getData(attacker.getUniqueId());
        long now = System.currentTimeMillis();
        long windowMs = plugin.getConfig().getLong("checks.killaura.window-ms", 200);
        int maxEntities = plugin.getConfig().getInt("checks.killaura.max-entities-in-window", 3);

        data.recentAttacks.put(target.getUniqueId(), now);

        // Purge entries outside the window
        Iterator<Map.Entry<UUID, Long>> it = data.recentAttacks.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > windowMs) it.remove();
        }

        if (data.recentAttacks.size() >= maxEntities) {
            plugin.getViolationManager().flag(attacker, "KILLAURA",
                    "entities=" + data.recentAttacks.size() + " in " + windowMs + "ms", 3);
            data.recentAttacks.clear();
        }
    }
}
