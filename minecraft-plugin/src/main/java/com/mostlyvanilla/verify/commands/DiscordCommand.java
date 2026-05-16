package com.mostlyvanilla.verify.commands;

import com.mostlyvanilla.verify.ApiClient;
import com.mostlyvanilla.verify.MostlyVanillaVerify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DiscordCommand implements CommandExecutor {

    private final MostlyVanillaVerify plugin;
    private final String prefix;

    public DiscordCommand(MostlyVanillaVerify plugin) {
        this.plugin = plugin;
        this.prefix = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.prefix", "&a[MostlyVanilla]&r "));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        player.sendMessage(prefix + ChatColor.GREEN + "Contacting the Discord bot...");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getApiClient().isVerified(player.getUniqueId().toString())) {
                    runSync(() -> player.sendMessage(prefix + ChatColor.GREEN + "Your Minecraft account is already linked to Discord!"));
                    return;
                }

                ApiClient.CodeResult result = plugin.getApiClient()
                    .requestCode(player.getUniqueId().toString(), player.getName());

                runSync(() -> {
                    if (result.success()) {
                        sendVerificationMessage(player, result, prefix);
                    } else {
                        player.sendMessage(prefix + ChatColor.RED + "Could not reach the Discord bot. Try again in a moment.");
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

    public static void sendVerificationMessage(Player player, ApiClient.CodeResult result, String prefix) {
        player.sendMessage("");
        player.sendMessage(prefix + ChatColor.GREEN + "══════ Discord Verification ══════");

        if (result.inviteUrl() != null) {
            player.sendMessage(prefix + ChatColor.WHITE + "Join our Discord server:");
            player.sendMessage(
                Component.text(prefix + "  ")
                    .append(Component.text(result.inviteUrl())
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(result.inviteUrl())))
                    .append(Component.text(" (click to open)", NamedTextColor.DARK_GRAY))
            );
            player.sendMessage("");
        }

        player.sendMessage(prefix + ChatColor.WHITE + "Then DM the bot this code:");
        player.sendMessage(
            Component.text(prefix + "  ")
                .append(Component.text(result.code())
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD))
        );
        player.sendMessage("");
        player.sendMessage(prefix + ChatColor.RED + "This invite and code expire in 10 minutes.");
        player.sendMessage(prefix + ChatColor.GREEN + "══════════════════════════════════");
        player.sendMessage("");
    }
}
