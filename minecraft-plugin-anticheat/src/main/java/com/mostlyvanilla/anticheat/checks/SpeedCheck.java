package com.mostlyvanilla.anticheat.checks;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

public class SpeedCheck {

    private static final Set<Material> ICE_TYPES = Set.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);

    private final MostlyVanillaAnticheat plugin;

    public SpeedCheck(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public void check(Player player, Location from, Location to) {
        if (!plugin.getConfig().getBoolean("checks.speed.enabled", true)) return;
        if (player.hasPermission("mostlyvanilla.ac.bypass")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isInsideVehicle()) return;
        if (player.isGliding()) return;
        if (player.isFlying() || player.getAllowFlight()) return;

        PlayerData data = plugin.getData(player.getUniqueId());
        long now = System.currentTimeMillis();

        // Grace periods for legitimate speed bursts
        if (now - data.lastElytraChangeMs  < 1000) return;
        if (now - data.lastKnockbackMs     < 600)  return; // knockback from hits
        if (now - data.lastRiptideMs       < 3000) return; // riptide trident launch

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double maxSpeed = plugin.getConfig().getDouble("checks.speed.max-speed", 0.6);
        int    buffer   = plugin.getConfig().getInt("checks.speed.buffer", 3);

        // Speed potion bonus
        var speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            maxSpeed += 0.085 * (speedEffect.getAmplifier() + 1);
        }

        // Ice surfaces allow significantly faster sliding movement
        Material below = player.getWorld()
                .getBlockAt(to.getBlockX(), (int) to.getY() - 1, to.getBlockZ())
                .getType();
        if (ICE_TYPES.contains(below)) {
            maxSpeed += plugin.getConfig().getDouble("checks.speed.ice-bonus", 0.8);
        }

        if (dist > maxSpeed) {
            data.fastTicks++;
            if (data.fastTicks >= buffer) {
                plugin.getViolationManager().flag(player, "SPEED",
                        String.format("dist=%.3f max=%.3f", dist, maxSpeed), 1);
                data.fastTicks = 0;
            }
        } else {
            if (data.fastTicks > 0) data.fastTicks--;
        }

        data.lastLocation = to.clone();
    }
}
