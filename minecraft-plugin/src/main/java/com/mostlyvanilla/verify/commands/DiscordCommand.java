package com.mostlyvanilla.verify.commands;

import com.mostlyvanilla.verify.ApiClient;
import com.mostlyvanilla.verify.MostlyVanillaVerify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DiscordCommand implements CommandExecutor {

    private final MostlyVanillaVerify plugin;

    public DiscordCommand(MostlyVanillaVerify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getApiClient().isVerified(player.getUniqueId().toString())) {
                    runSync(() -> player.sendMessage(
                        Component.text("Your Minecraft account is already linked to Discord!", NamedTextColor.GREEN)
                    ));
                    return;
                }

                ApiClient.CodeResult result = plugin.getApiClient()
                    .requestCode(player.getUniqueId().toString(), player.getName());

                runSync(() -> {
                    if (result.success()) {
                        sendVerificationMessage(player, result);
                    } else {
                        player.sendMessage(Component.text("Could not reach the Discord bot. Try again in a moment.", NamedTextColor.RED));
                        plugin.getLogger().warning("Code request failed for " + player.getName() + ": " + result.error());
                    }
                });
            }

            private void runSync(Runnable r) {
                new BukkitRunnable() {
                    @Override public void run() { r.run(); }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    public static void sendVerificationMessage(Player player, ApiClient.CodeResult result) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━ Discord Verification ━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));

        if (result.inviteUrl() != null) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("Join our Discord server:", NamedTextColor.WHITE));
            player.sendMessage(
                Component.text(result.inviteUrl())
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(result.inviteUrl()))
                    .append(Component.text("  (click to open)", NamedTextColor.DARK_GRAY))
            );
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Then DM the bot this code:", NamedTextColor.WHITE));
        player.sendMessage(
            Component.text(result.code())
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
        );
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Expires in 10 minutes.", NamedTextColor.RED));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.empty());
    }
}
