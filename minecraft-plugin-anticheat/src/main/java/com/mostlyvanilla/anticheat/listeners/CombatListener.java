package com.mostlyvanilla.anticheat.listeners;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.checks.KillAuraCheck;
import com.mostlyvanilla.anticheat.checks.NoFallCheck;
import com.mostlyvanilla.anticheat.checks.ReachCheck;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatListener implements Listener {

    private final ReachCheck    reachCheck;
    private final KillAuraCheck killAuraCheck;
    private final NoFallCheck   noFallCheck;

    public CombatListener(MostlyVanillaAnticheat plugin) {
        this.reachCheck    = new ReachCheck(plugin);
        this.killAuraCheck = new KillAuraCheck(plugin);
        this.noFallCheck   = new NoFallCheck(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        reachCheck.check(attacker, event.getEntity());
        killAuraCheck.check(attacker, event.getEntity());
        noFallCheck.onAttack(attacker);
    }
}
