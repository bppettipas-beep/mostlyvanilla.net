package com.mostlyvanilla.tpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CountdownTask extends BukkitRunnable {

    private final Player mover;
    private final Player dest;
    private final Location startLoc;
    private final boolean cancelOnMove;
    private final double maxDistSq; // -1 = unlimited
    private int secondsLeft;

    public CountdownTask(Player mover, Player dest, int seconds, boolean cancelOnMove, double maxMoveDistance) {
        this.mover        = mover;
        this.dest         = dest;
        this.secondsLeft  = seconds;
        this.cancelOnMove = cancelOnMove;
        this.maxDistSq    = maxMoveDistance > 0 ? maxMoveDistance * maxMoveDistance : -1;
        this.startLoc     = mover.getLocation().clone();
    }

    @Override
    public void run() {
        if (!mover.isOnline()) {
            cancel();
            return;
        }

        if (!dest.isOnline()) {
            cancel();
            mover.sendActionBar(Component.empty());
            mover.sendMessage(Component.text("Teleport cancelled: player went offline.", NamedTextColor.RED));
            return;
        }

        // Movement check
        if (cancelOnMove || maxDistSq > 0) {
            Location cur = mover.getLocation();
            boolean moved = startLoc.getWorld() == null || cur.getWorld() == null
                || !startLoc.getWorld().equals(cur.getWorld())
                || cur.distanceSquared(startLoc) > (cancelOnMove ? 0.0225 : maxDistSq); // 0.15^2 for strict
            if (moved) {
                cancel();
                mover.sendActionBar(Component.empty());
                mover.sendMessage(Component.text(
                    cancelOnMove ? "Teleport cancelled: you moved." : "Teleport cancelled: you moved too far.",
                    NamedTextColor.RED));
                return;
            }
        }

        // Countdown done — teleport
        if (secondsLeft <= 0) {
            cancel();
            mover.sendActionBar(Component.empty());
            mover.teleport(dest.getLocation());
            mover.sendMessage(Component.text("Teleported to ", NamedTextColor.GREEN)
                .append(Component.text(dest.getName(), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
            dest.sendMessage(Component.text(mover.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" teleported to you.", NamedTextColor.GREEN)));
            return;
        }

        mover.sendActionBar(buildBar(secondsLeft));
        secondsLeft--;
    }

    private Component buildBar(int secs) {
        NamedTextColor color = secs > 2 ? NamedTextColor.GREEN : secs > 1 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text("Teleporting to ", color)
            .append(Component.text(dest.getName(), NamedTextColor.WHITE))
            .append(Component.text(" in ", color))
            .append(Component.text(secs + "s", NamedTextColor.WHITE));
    }
}
