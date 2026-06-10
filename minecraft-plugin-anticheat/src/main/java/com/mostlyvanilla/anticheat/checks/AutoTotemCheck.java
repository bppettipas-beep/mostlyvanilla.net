package com.mostlyvanilla.anticheat.checks;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;

public class AutoTotemCheck implements Listener {

    private final MostlyVanillaAnticheat plugin;

    public AutoTotemCheck(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getConfig().getBoolean("checks.autototem.enabled", true)) return;
        if (player.hasPermission("mostlyvanilla.ac.bypass")) return;

        long useTime = System.currentTimeMillis();

        // After 4 ticks (~200ms at 20TPS) check if a new totem appeared in the offhand.
        // Minimum human reaction to swap is ~300ms+; auto-totem fires in <50ms.
        // Using 4-tick delay so server scheduling variance doesn't produce false alarms.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand == null || offhand.getType() != Material.TOTEM_OF_UNDYING) return;

            long elapsed = System.currentTimeMillis() - useTime;
            int maxMs = plugin.getConfig().getInt("checks.autototem.max-reaction-ms", 250);

            if (elapsed <= maxMs) {
                plugin.getViolationManager().flag(player, "AUTOTOTEM",
                        "reactionMs=" + elapsed + " max=" + maxMs, 5);
            }
        }, 4L);
    }
}
