package com.mostlyvanilla.settings.listeners;

import com.mostlyvanilla.settings.Setting;
import com.mostlyvanilla.settings.SettingsManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final SettingsManager settingsManager;

    public PlayerListener(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    // ── Night vision refresh ──────────────────────────────────────────────────
    // Called every 200 ticks from a repeating task in the main class.
    public void tickNightVision(Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            if (settingsManager.isEnabled(player.getUniqueId(), Setting.NIGHT_VISION)) {
                var existing = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
                if (existing == null) {
                    applyNightVision(player);
                }
            }
        }
    }

    public static void applyNightVision(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE, 0, false, false, false));
    }

    // ── Public chat filter ────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID senderUuid = event.getPlayer().getUniqueId();
        event.viewers().removeIf(viewer ->
                viewer instanceof Player p &&
                !p.getUniqueId().equals(senderUuid) &&
                !settingsManager.isEnabled(p.getUniqueId(), Setting.PUBLIC_CHAT));
    }

    // ── Join / quit messages ──────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        // First-time players get join/leave messages turned off by default.
        if (settingsManager.isNewPlayer(event.getPlayer().getUniqueId())) {
            settingsManager.set(event.getPlayer().getUniqueId(), Setting.JOIN_MESSAGES, false);
        }

        Component msg = event.joinMessage();
        if (msg == null) return;
        event.joinMessage(null);
        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            if (settingsManager.isEnabled(p.getUniqueId(), Setting.JOIN_MESSAGES)) {
                p.sendMessage(msg);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Component msg = event.quitMessage();
        if (msg == null) return;
        event.quitMessage(null);
        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            if (!p.equals(event.getPlayer()) &&
                settingsManager.isEnabled(p.getUniqueId(), Setting.JOIN_MESSAGES)) {
                p.sendMessage(msg);
            }
        }
    }

    // ── Death messages ────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Component msg = event.deathMessage();
        if (msg == null) return;
        event.deathMessage(null);
        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            if (settingsManager.isEnabled(p.getUniqueId(), Setting.DEATH_MESSAGES)) {
                p.sendMessage(msg);
            }
        }
    }
}
