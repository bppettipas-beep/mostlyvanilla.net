package com.mostlyvanilla.roles.listeners;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
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

        Component prefixComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);

        event.renderer((source, sourceDisplayName, message, viewer) ->
            prefixComponent
                .append(Component.text(" "))
                .append(sourceDisplayName)
                .append(Component.text(": "))
                .append(message)
        );
    }
}
