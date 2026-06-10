package com.mostlyvanilla.anticheat.checks;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public class FlyCheck {

    private final MostlyVanillaAnticheat plugin;

    public FlyCheck(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public void check(Player player, Location from, Location to) {
        if (!plugin.getConfig().getBoolean("checks.fly.enabled", true)) return;
        if (player.hasPermission("mostlyvanilla.ac.bypass")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isFlying() || player.getAllowFlight()) return;
        if (player.isInsideVehicle()) return;

        PlayerData data = plugin.getData(player.getUniqueId());

        if (player.isGliding()) {
            data.lastGlidingMs = System.currentTimeMillis();
            data.airTicks = 0;
            return;
        }
        if (System.currentTimeMillis() - data.lastGlidingMs < 3000) {
            data.airTicks = 0;
            return;
        }

        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) {
            data.airTicks = 0;
            return;
        }

        boolean onGround     = player.isOnGround();
        boolean inWaterOrLava = player.isInWater() || player.isInLava();
        boolean hasLevitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean hasSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);

        if (onGround || inWaterOrLava || hasLevitation || hasSlowFalling) {
            data.airTicks = 0;
            return;
        }

        // If the player is moving downward, gravity is acting normally — don't accumulate.
        // Fly hacks hold position or move upward; legit falls descend each tick.
        if (to.getY() < from.getY() - 0.08) {
            if (data.airTicks > 0) data.airTicks--;
            return;
        }

        int extra = 0;
        if (player.hasPotionEffect(PotionEffectType.JUMP)) {
            var effect = player.getPotionEffect(PotionEffectType.JUMP);
            if (effect != null) extra = (effect.getAmplifier() + 1) * 6;
        }

        int maxAir = plugin.getConfig().getInt("checks.fly.max-air-ticks", 50) + extra;

        data.airTicks++;
        if (data.airTicks >= maxAir) {
            plugin.getViolationManager().flag(player, "FLY",
                    "airTicks=" + data.airTicks, 2);
            data.airTicks = 0;
        }
    }
}
