package com.mostlyvanilla.home.listeners;

import com.mostlyvanilla.home.HomeManager;
import com.mostlyvanilla.home.MostlyVanillaHome;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final MostlyVanillaHome plugin;
    private final HomeManager       homeManager;
    private final GuiListener       guiListener;

    public ChatListener(MostlyVanillaHome plugin, HomeManager homeManager, GuiListener guiListener) {
        this.plugin      = plugin;
        this.homeManager = homeManager;
        this.guiListener = guiListener;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player       = event.getPlayer();
        String pendingRename = guiListener.getPendingRename(player.getUniqueId());
        if (pendingRename == null) return;

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            guiListener.clearPendingRename(player.getUniqueId());

            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("Rename cancelled.", NamedTextColor.GRAY));
                return;
            }
            if (input.isEmpty() || input.length() > 16) {
                player.sendMessage(Component.text("Home name must be 1–16 characters.", NamedTextColor.RED));
                return;
            }
            if (!input.matches("[a-zA-Z0-9_\\-]+")) {
                player.sendMessage(Component.text("Home name can only contain letters, numbers, underscores and hyphens.", NamedTextColor.RED));
                return;
            }
            if (homeManager.renameHome(player.getUniqueId(), pendingRename, input)) {
                player.sendMessage(Component.text("Renamed to ", NamedTextColor.GREEN)
                    .append(Component.text(input, NamedTextColor.GOLD))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            } else {
                player.sendMessage(Component.text("Could not rename – the home may have been deleted.", NamedTextColor.RED));
            }
        });
    }
}
