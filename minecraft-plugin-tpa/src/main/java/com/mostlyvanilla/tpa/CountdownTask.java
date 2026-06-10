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
    private final double maxDistSq;
    private final long endTimeMs;

    public CountdownTask(Player mover, Player dest, int seconds, boolean cancelOnMove, double maxMoveDistance) {
        this.mover       = mover;
        this.dest        = dest;
        this.cancelOnMove = cancelOnMove;
        this.maxDistSq   = maxMoveDistance > 0 ? maxMoveDistance * maxMoveDistance : -1;
        this.startLoc    = mover.getLocation().clone();
        this.endTimeMs   = System.currentTimeMillis() + (seconds * 1000L);
    }

    @Override
    public void run() {
        if (!mover.isOnline()) { cancel(); return; }

        if (!dest.isOnline()) {
            cancel();
            mover.sendActionBar(Component.empty());
            mover.sendMessage(Component.text("Teleport cancelled: player went offline.", NamedTextColor.RED));
            return;
        }

        if (cancelOnMove || maxDistSq > 0) {
            Location cur = mover.getLocation();
            boolean moved = startLoc.getWorld() == null || cur.getWorld() == null
                    || !startLoc.getWorld().equals(cur.getWorld())
                    || cur.distanceSquared(startLoc) > (cancelOnMove ? 0.0225 : maxDistSq);
            if (moved) {
                cancel();
                mover.sendActionBar(Component.empty());
                mover.sendMessage(Component.text(
                        cancelOnMove ? "Teleport cancelled: you moved." : "Teleport cancelled: you moved too far.",
                        NamedTextColor.RED));
                return;
            }
        }

        long msLeft = endTimeMs - System.currentTimeMillis();
        if (msLeft <= 0) {
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

        int secsLeft = (int) Math.ceil(msLeft / 1000.0);
        mover.sendActionBar(buildBar(secsLeft));
    }

    private Component buildBar(int secs) {
        NamedTextColor color = secs > 2 ? NamedTextColor.GREEN : secs > 1 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text("Teleporting to ", color)
                .append(Component.text(dest.getName(), NamedTextColor.WHITE))
                .append(Component.text(" in ", color))
                .append(Component.text(secs + "s", NamedTextColor.WHITE));
    }
}
