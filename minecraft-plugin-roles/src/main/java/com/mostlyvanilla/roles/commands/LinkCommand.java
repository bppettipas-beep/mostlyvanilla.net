package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.ApiClient;
import com.mostlyvanilla.roles.MostlyVanillaRoles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class LinkCommand implements CommandExecutor {

    private final MostlyVanillaRoles plugin;

    public LinkCommand(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        ApiClient api = plugin.getApiClient();
        if (api == null) {
            player.sendMessage(Component.text("Discord linking is not set up on this server.", NamedTextColor.RED));
            return true;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (api.isVerified(player.getUniqueId().toString())) {
                    sync(() -> player.sendMessage(
                        Component.text("Your Minecraft account is already linked to Discord.", NamedTextColor.GREEN)));
                    return;
                }

                ApiClient.CodeResult result = api.requestCode(player.getUniqueId().toString(), player.getName());

                sync(() -> {
                    if (!result.success()) {
                        player.sendMessage(Component.text("Could not reach the bot right now. Try again in a moment.", NamedTextColor.RED));
                        plugin.getLogger().warning("[Link] Code request failed for " + player.getName() + ": " + result.error());
                        return;
                    }
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("━━━━━━ Discord Verification ━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
                    if (result.inviteUrl() != null) {
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("Join our Discord: ", NamedTextColor.WHITE)
                            .append(Component.text(result.inviteUrl())
                                .color(NamedTextColor.AQUA)
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(result.inviteUrl()))));
                    }
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("DM the code below to ", NamedTextColor.WHITE)
                        .append(Component.text("MostlyVanilla Beacon", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" on Discord:", NamedTextColor.WHITE)));
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("  " + result.code())
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.copyToClipboard(result.code()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy code", NamedTextColor.GRAY))));
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("Code expires in 10 minutes.", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.BOLD));
                });
            }

            private void sync(Runnable r) {
                new BukkitRunnable() {
                    @Override public void run() { r.run(); }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }
}
