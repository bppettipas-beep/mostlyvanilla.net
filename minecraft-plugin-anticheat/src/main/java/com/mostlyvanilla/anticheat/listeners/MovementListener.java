package com.mostlyvanilla.anticheat.listeners;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.checks.FlyCheck;
import com.mostlyvanilla.anticheat.checks.NoFallCheck;
import com.mostlyvanilla.anticheat.checks.SpeedCheck;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MovementListener implements Listener {

    private final MostlyVanillaAnticheat plugin;
    private final SpeedCheck  speedCheck;
    private final FlyCheck    flyCheck;
    private final NoFallCheck noFallCheck;

    public MovementListener(MostlyVanillaAnticheat plugin) {
        this.plugin      = plugin;
        this.speedCheck  = new SpeedCheck(plugin);
        this.flyCheck    = new FlyCheck(plugin);
        this.noFallCheck = new NoFallCheck(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to   = event.getTo();
        if (to == null) return;

        speedCheck.check(event.getPlayer(), from, to);
        flyCheck.check(event.getPlayer(), from, to);
        noFallCheck.onMove(event.getPlayer(), from, to);
        plugin.getAntiXrayEngine().revealAround(event.getPlayer());
    }
}
