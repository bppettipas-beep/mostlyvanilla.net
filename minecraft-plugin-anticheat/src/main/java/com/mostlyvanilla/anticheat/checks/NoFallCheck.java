package com.mostlyvanilla.anticheat.checks;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

public class NoFallCheck {

    private final MostlyVanillaAnticheat plugin;

    // Blocks that negate or significantly reduce fall damage — skip nofall check when landing on these.
    private static final Set<Material> SAFE_LANDING = Set.of(
            Material.SLIME_BLOCK, Material.HONEY_BLOCK,
            Material.COBWEB,
            Material.POWDER_SNOW,
            Material.HAY_BLOCK
    );

    // Blocks the player can be "inside" that stop fall damage accumulation.
    // Reset maxY tracking when the player is on these so ladder/vine descents don't false-flag.
    private static final Set<Material> CLIMBABLE = Set.of(
            Material.LADDER, Material.VINE,
            Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT,
            Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT,
            Material.CAVE_VINES, Material.CAVE_VINES_PLANT,
            Material.SCAFFOLDING
    );

    public NoFallCheck(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public void onMove(Player player, Location from, Location to) {
        if (!plugin.getConfig().getBoolean("checks.nofall.enabled", true)
                && !plugin.getConfig().getBoolean("checks.jump_reset.enabled", true)) return;
        if (player.hasPermission("mostlyvanilla.ac.bypass")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isFlying() || player.getAllowFlight()) return;

        PlayerData data = plugin.getData(player.getUniqueId());
        boolean nowOnGround = player.isOnGround();

        // Reset fall tracking when on a climbable surface to prevent false flags
        // from players descending ladders or vines from height.
        Material inBlock = player.getWorld()
                .getBlockAt(to.getBlockX(), (int) to.getY(), to.getBlockZ()).getType();
        if (CLIMBABLE.contains(inBlock)) {
            data.noFallMaxY = to.getY();
            data.noFallWasOnGround = true;
            return;
        }

        // ── Jump Reset detection ───────────────────────────────────────────
        if (plugin.getConfig().getBoolean("checks.jump_reset.enabled", true)) {
            if (data.noFallWasOnGround && !nowOnGround) {
                long now = System.currentTimeMillis();
                data.jumpTimestamps.addLast(now);
                while (!data.jumpTimestamps.isEmpty()
                        && now - data.jumpTimestamps.peekFirst() > 3000) {
                    data.jumpTimestamps.pollFirst();
                }
            }
        }

        // ── NoFall detection ──────────────────────────────────────────────
        if (plugin.getConfig().getBoolean("checks.nofall.enabled", true)) {
            if (!nowOnGround) {
                if (to.getY() > data.noFallMaxY) data.noFallMaxY = to.getY();
            } else if (!data.noFallWasOnGround) {
                double fallDist = data.noFallMaxY - to.getY();
                double threshold = plugin.getConfig().getDouble("checks.nofall.fall-threshold", 12.0);

                // Use server-tracked fall distance as a gate: if the server didn't register
                // a fall, there's nothing to detect (player was on a climbable, boat, etc.)
                float serverFallDist = player.getFallDistance();

                if (fallDist >= threshold
                        && serverFallDist > 2.0f
                        && !player.isInWater()
                        && !player.isInLava()
                        && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {

                    Material landed = player.getWorld()
                            .getBlockAt(to.getBlockX(), (int) to.getY() - 1, to.getBlockZ())
                            .getType();
                    if (!SAFE_LANDING.contains(landed)) {

                        // Skip if boots have Feather Falling IV — at high levels it absorbs
                        // enough damage that health may not noticeably change for moderate falls.
                        ItemStack boots = player.getInventory().getBoots();
                        int ffLevel = (boots != null)
                                ? boots.getEnchantmentLevel(Enchantment.PROTECTION_FALL) : 0;
                        if (ffLevel >= 4) {
                            data.noFallMaxY = to.getY();
                            data.noFallWasOnGround = nowOnGround;
                            return;
                        }

                        double savedHealth = player.getHealth();
                        double savedFall   = fallDist;

                        // Wait 5 ticks (250ms) to allow damage to register server-side
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (player.getHealth() >= savedHealth) {
                                plugin.getViolationManager().flag(player, "NOFALL",
                                        String.format("fall=%.1f health_unchanged", savedFall), 3);
                            }
                        }, 5L);
                    }
                }

                data.noFallMaxY = to.getY();
            }
        }

        data.noFallWasOnGround = nowOnGround;
    }

    public void onAttack(Player player) {
        if (!plugin.getConfig().getBoolean("checks.jump_reset.enabled", true)) return;
        if (player.hasPermission("mostlyvanilla.ac.bypass")) return;

        PlayerData data = plugin.getData(player.getUniqueId());
        data.lastCombatMs = System.currentTimeMillis();

        int windowMs = plugin.getConfig().getInt("checks.jump_reset.jump-window-ms", 110);
        long now = System.currentTimeMillis();
        boolean jumpedJustBefore = !data.jumpTimestamps.isEmpty()
                && (now - data.jumpTimestamps.peekLast()) <= windowMs
                && (now - data.jumpTimestamps.peekLast()) >= 10;

        if (jumpedJustBefore) {
            data.jumpResetCombo++;
            int threshold = plugin.getConfig().getInt("checks.jump_reset.combo-threshold", 10);
            if (data.jumpResetCombo >= threshold) {
                plugin.getViolationManager().flag(player, "JUMP_RESET",
                        "combo=" + data.jumpResetCombo + " window=" + windowMs + "ms", 2);
                data.jumpResetCombo = 0;
            }
        } else {
            data.jumpResetCombo = 0;
        }
    }
}
