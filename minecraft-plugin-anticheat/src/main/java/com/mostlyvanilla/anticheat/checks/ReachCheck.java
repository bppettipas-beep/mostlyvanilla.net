package com.mostlyvanilla.anticheat.checks;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

public class ReachCheck {

    private final MostlyVanillaAnticheat plugin;

    public ReachCheck(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public void check(Player attacker, Entity target) {
        if (!plugin.getConfig().getBoolean("checks.reach.enabled", true)) return;
        if (attacker.hasPermission("mostlyvanilla.ac.bypass")) return;
        if (attacker.getGameMode() == GameMode.CREATIVE) return;

        double maxReach = plugin.getConfig().getDouble("checks.reach.max-reach", 4.5);
        int    buffer   = plugin.getConfig().getInt("checks.reach.buffer", 6);

        // Measure from attacker eye to nearest point on target's bounding box.
        // This avoids false positives from feet-to-feet distance when attacking
        // tall or short mobs, and gives an accurate result regardless of height.
        Location eye = attacker.getEyeLocation();
        BoundingBox bb = target.getBoundingBox();
        double cx = Math.max(bb.getMinX(), Math.min(eye.getX(), bb.getMaxX()));
        double cy = Math.max(bb.getMinY(), Math.min(eye.getY(), bb.getMaxY()));
        double cz = Math.max(bb.getMinZ(), Math.min(eye.getZ(), bb.getMaxZ()));
        double dx = eye.getX() - cx, dy = eye.getY() - cy, dz = eye.getZ() - cz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        PlayerData data = plugin.getData(attacker.getUniqueId());

        if (dist > maxReach) {
            data.reachBuffer++;
            if (data.reachBuffer >= buffer) {
                plugin.getViolationManager().flag(attacker, "REACH",
                        String.format("dist=%.2f max=%.2f", dist, maxReach), 2);
                data.reachBuffer = 0;
            }
        } else {
            if (data.reachBuffer > 0) data.reachBuffer--;
        }
    }
}
