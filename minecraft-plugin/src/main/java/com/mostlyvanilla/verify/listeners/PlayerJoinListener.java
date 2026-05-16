package com.mostlyvanilla.verify.listeners;

import com.mostlyvanilla.verify.ApiClient;
import com.mostlyvanilla.verify.MostlyVanillaVerify;
import com.mostlyvanilla.verify.commands.DiscordCommand;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinListener implements Listener {

    private final MostlyVanillaVerify plugin;
    private final String prefix;

    public PlayerJoinListener(MostlyVanillaVerify plugin) {
        this.plugin = plugin;
        this.prefix = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.prefix", "&a[MostlyVanilla]&r "));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getApiClient().isVerified(player.getUniqueId().toString())) return;

                // Not verified — generate a fresh invite + code and show it
                ApiClient.CodeResult result = plugin.getApiClient()
                    .requestCode(player.getUniqueId().toString(), player.getName());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) return;

                        if (result.success()) {
                            player.sendMessage(prefix + ChatColor.YELLOW + "You need to link your Minecraft account to join our Discord!");
                            DiscordCommand.sendVerificationMessage(player, result, prefix);
                        } else {
                            player.sendMessage(prefix + ChatColor.GREEN +
                                "Link your Discord account with " + ChatColor.YELLOW + "/discord");
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
}
