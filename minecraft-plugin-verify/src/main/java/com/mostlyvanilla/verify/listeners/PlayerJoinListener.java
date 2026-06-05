package com.mostlyvanilla.verify.listeners;

import com.mostlyvanilla.verify.MostlyVanillaVerify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerJoinListener implements Listener {

    private final MostlyVanillaVerify plugin;
    private static final long REMINDER_INTERVAL_TICKS = 6000L; // 5 minutes
    private final Map<UUID, BukkitTask> reminderTasks = new ConcurrentHashMap<>();

    public PlayerJoinListener(MostlyVanillaVerify plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                sendWelcomePanel(player);
                scheduleReminders(player);
            }
        }.runTaskLater(plugin, 40L);
    }

    private void sendWelcomePanel(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━ Mostly Vanilla ━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(
            Component.text("/tpa ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("<player>", NamedTextColor.AQUA))
                .append(Component.text("  —  Teleport to a player", NamedTextColor.GRAY))
        );
        player.sendMessage(
            Component.text("/rtp", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("  —  Start your journey", NamedTextColor.GRAY))
        );
        player.sendMessage(
            Component.text("/link", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("  —  Get the Discord verified role", NamedTextColor.GRAY))
        );
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
    }

    private void scheduleReminders(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask old = reminderTasks.remove(uuid);
        if (old != null) old.cancel();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    reminderTasks.remove(uuid);
                    cancel();
                    return;
                }
                if (plugin.getApiClient().isVerified(uuid.toString())) {
                    reminderTasks.remove(uuid);
                    cancel();
                    return;
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) return;
                        player.sendMessage(
                            Component.text("Link your Discord account with ", NamedTextColor.GRAY)
                                .append(Component.text("/link", NamedTextColor.GREEN, TextDecoration.BOLD))
                                .append(Component.text(" to get the verified role.", NamedTextColor.GRAY))
                        );
                    }
                }.runTask(plugin);
            }
        }.runTaskTimerAsynchronously(plugin, REMINDER_INTERVAL_TICKS, REMINDER_INTERVAL_TICKS);
        reminderTasks.put(uuid, task);
    }
}
