package com.mostlyvanilla.chatfilter;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class ChatFilterListener implements Listener {

    private final MostlyVanillaChatFilter plugin;
    private final FilterManager filterManager;

    public ChatFilterListener(MostlyVanillaChatFilter plugin, FilterManager filterManager) {
        this.plugin        = plugin;
        this.filterManager = filterManager;
    }

    // LOWEST priority: fires before other chat listeners.
    // If we cancel here, higher-priority listeners (e.g. roles formatting) never see the message.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Ops / staff with bypass skip the filter entirely
        if (player.hasPermission("mv.chatfilter.bypass")) return;

        UUID uuid = player.getUniqueId();

        // Check if player is currently muted by the filter
        if (filterManager.isFilterMuted(uuid)) {
            event.setCancelled(true);
            long expiry    = filterManager.getMuteExpiry(uuid);
            String remaining = FilterManager.formatRemaining(expiry);
            player.sendMessage(FilterManager.colorize(
                filterManager.getStillMutedMessage().replace("{remaining}", remaining)
            ));
            return;
        }

        // Extract plain text so we can check it (colour codes etc. are stripped)
        String plainText = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Run both passes
        WordCategory matched = filterManager.check(plainText);
        if (matched == null) return;

        // Block the message immediately (safe to do async)
        event.setCancelled(true);

        // Apply punishment on the main thread — kick() and ban() require it
        plugin.getServer().getScheduler().runTask(plugin,
            () -> filterManager.applyAction(player, matched));
    }
}
