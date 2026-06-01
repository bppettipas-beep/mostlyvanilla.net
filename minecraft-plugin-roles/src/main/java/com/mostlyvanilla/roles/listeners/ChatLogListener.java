package com.mostlyvanilla.roles.listeners;

import com.google.gson.JsonObject;
import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ChatLogListener implements Listener {

    private final MostlyVanillaRoles plugin;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public ChatLogListener(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        RoleManager rm = plugin.getRoleManager();

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        String role  = rm.getPlayerRole(player.getUniqueId());
        String world = player.getWorld().getName();
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", player.getUniqueId().toString());
        body.addProperty("player_name", player.getName());
        body.addProperty("player_role", role != null ? role : "");
        body.addProperty("message", plainMessage);
        body.addProperty("world", world);
        body.addProperty("x", x);
        body.addProperty("y", y);
        body.addProperty("z", z);

        String apiUrl    = plugin.getConfig().getString("bot-api-url", "http://localhost:3000");
        String apiSecret = plugin.getConfig().getString("api-secret", "");

        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/api/chat-message"))
                .header("Content-Type", "application/json")
                .header("x-api-secret", apiSecret)
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[ChatLog] Failed to send: " + ex.getMessage());
                    return null;
                });
        } catch (Exception e) {
            plugin.getLogger().warning("[ChatLog] Error sending chat log: " + e.getMessage());
        }
    }
}
