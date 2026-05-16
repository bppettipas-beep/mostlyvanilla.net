package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final MostlyVanillaRoles plugin;

    public ChatListener(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        String prefix = plugin.getRoleManager().getPrefix(event.getPlayer().getUniqueId());
        if (prefix == null) return;

        Component prefixComponent = prefix.contains("<")
            ? MiniMessage.miniMessage().deserialize(prefix)
            : LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            // Wrap suffix in a component with explicit bold=FALSE so the prefix's
            // bold decoration cannot bleed into the player name or message
            Component suffix = Component.empty()
                .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)
                .append(Component.text(" "))
                .append(sourceDisplayName)
                .append(Component.text(": "))
                .append(message);

            return Component.empty()
                .append(prefixComponent)
                .append(suffix);
        });
    }
}
