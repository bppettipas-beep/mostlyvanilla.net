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
    private final Map<UUID, BukkitTask> pending        = new HashMap<>();
    private final Map<UUID, Location>   startLocations = new HashMap<>();

    public TeleportManager(MostlyVanillaRtp plugin) {
        this.plugin = plugin;
    }

    public void startTeleport(Player player, Location destination) {
        cancel(player.getUniqueId());
        int delay = plugin.getConfig().getInt("teleport-delay", 3);

        if (delay <= 0) {
            player.teleport(destination);
            player.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
            return;
        }

        startLocations.put(player.getUniqueId(), player.getLocation().clone());

        BukkitTask task = new BukkitRunnable() {
            int remaining = delay;

            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); this.cancel(); return; }
                if (remaining > 0) {
                    player.sendActionBar(Component.text("Teleporting in " + remaining + "s…", NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    cleanup();
                    player.teleport(destination);
                    player.sendActionBar(Component.text("Teleported!", NamedTextColor.GREEN));
                    this.cancel();
                }
            }

            private void cleanup() {
                pending.remove(player.getUniqueId());
                startLocations.remove(player.getUniqueId());
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pending.put(player.getUniqueId(), task);
    }

    public void cancel(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
        startLocations.remove(uuid);
    }

    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void checkMove(Player player) {
        if (!plugin.getConfig().getBoolean("cancel-on-move", true)) return;
        if (!isPending(player.getUniqueId())) return;
        Location start   = startLocations.get(player.getUniqueId());
        Location current = player.getLocation();
        if (start == null) return;
        if (current.getBlockX() != start.getBlockX()
                || current.getBlockY() != start.getBlockY()
                || current.getBlockZ() != start.getBlockZ()) {
            cancel(player.getUniqueId());
            player.sendActionBar(Component.text("Teleport cancelled – you moved!", NamedTextColor.RED));
        }
    }
}
