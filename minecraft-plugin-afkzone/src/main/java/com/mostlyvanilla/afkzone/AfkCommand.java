package com.mostlyvanilla.afkzone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AfkCommand implements CommandExecutor {

    private final AfkZoneManager manager;

    public AfkCommand(AfkZoneManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /afk.");
            return true;
        }
        if (!manager.isEnabled()) {
            player.sendMessage(Component.text("The AFK Zone is not active right now.", NamedTextColor.RED));
            return true;
        }
        if (manager.hasPendingTp(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have a teleport pending.", NamedTextColor.RED));
            return true;
        }
        manager.startAfkTeleport(player);
        return true;
    }
}
