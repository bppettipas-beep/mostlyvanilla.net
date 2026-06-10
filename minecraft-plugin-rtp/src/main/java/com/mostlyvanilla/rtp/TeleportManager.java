package com.mostlyvanilla.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private final MostlyVanillaRtp plugin;

    // Active countdowns
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<UUID, Location>   startLocations = new HashMap<>();

    // Destination is set by RtpManager once a safe location + chunks are ready
    private final Map<UUID, Location>   destinations   = new HashMap<>();

    // Max extra ticks to wait after the countdown finishes for the destination
    private static final int MAX_EXTRA_TICKS = 200; // 10 seconds

    public TeleportManager(MostlyVanillaRtp plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the countdown immediately. The search runs in parallel; when it
     * finishes it calls setDestination(). The task teleports the player as soon
     * as both the countdown is done AND the destination is ready.
     */
    public void startCountdown(Player player) {
        cancel(player.getUniqueId());

        int delay = plugin.getConfig().getInt("teleport-delay", 5);
        UUID uuid = player.getUniqueId();
        startLocations.put(uuid, player.getLocation().clone());

        player.sendMessage(Component.text("Don't move for " + delay + " seconds!", NamedTextColor.YELLOW));

        long endTimeMs = System.currentTimeMillis() + (delay * 1000L);

        BukkitTask task = new BukkitRunnable() {
            int extraTicks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); cancel(); return; }

                long msLeft = endTimeMs - System.currentTimeMillis();
                if (msLeft > 0) {
                    int secsLeft = (int) Math.ceil(msLeft / 1000.0);
                    player.sendActionBar(
                        Component.text("Don't move! Teleporting in ")
                            .color(NamedTextColor.YELLOW)
                            .append(Component.text(secsLeft + "s")
                                .color(NamedTextColor.GOLD)));
                    return;
                }

                // Countdown done — wait for destination
                Location dest = destinations.get(uuid);
                if (dest == null) {
                    extraTicks++;
                    if (extraTicks > MAX_EXTRA_TICKS) {
                        cleanup(); cancel();
                        player.sendMessage(Component.text(
                            "Could not find a safe location. Please try again.", NamedTextColor.RED));
                        player.sendActionBar(Component.text("RTP failed.", NamedTextColor.RED));
                    } else {
                        player.sendActionBar(
                            Component.text("Finalizing location… hang on!", NamedTextColor.YELLOW));
                    }
                    return;
                }

                // Ready — teleport
                cleanup(); cancel();
                player.teleport(dest);
                player.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
                player.sendActionBar(Component.text("Teleported!", NamedTextColor.GREEN));
            }

            private void cleanup() {
                countdownTasks.remove(uuid);
                startLocations.remove(uuid);
                destinations.remove(uuid);
            }
        }.runTaskTimer(plugin, 0L, 5L);

        countdownTasks.put(uuid, task);
    }

    /** Called by RtpManager once a safe location and surrounding chunks are ready. */
    public void setDestination(UUID uuid, Location dest) {
        if (countdownTasks.containsKey(uuid)) destinations.put(uuid, dest);
    }

    public void cancel(UUID uuid) {
        BukkitTask t = countdownTasks.remove(uuid);
        if (t != null) t.cancel();
        startLocations.remove(uuid);
        destinations.remove(uuid);
    }

    public boolean isPending(UUID uuid) {
        return countdownTasks.containsKey(uuid);
    }

    public void checkMove(Player player) {
        if (!plugin.getConfig().getBoolean("cancel-on-move", true)) return;
        UUID uuid = player.getUniqueId();
        if (!isPending(uuid)) return;
        Location start   = startLocations.get(uuid);
        Location current = player.getLocation();
        if (start == null) return;
        if (current.getBlockX() != start.getBlockX()
                || current.getBlockY() != start.getBlockY()
                || current.getBlockZ() != start.getBlockZ()) {
            cancel(uuid);
            player.sendMessage(Component.text("Teleport cancelled — you moved!", NamedTextColor.RED));
            player.sendActionBar(Component.text("Teleport cancelled — you moved!", NamedTextColor.RED));
        }
    }
}
