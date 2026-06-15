package com.mostlyvanilla.home.commands;

import com.mostlyvanilla.home.TeleportManager;
import com.mostlyvanilla.home.VanillaSpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanillaSpawnCommand implements CommandExecutor {

    private final VanillaSpawnManager spawnManager;
    private final TeleportManager     teleportManager;

    public VanillaSpawnCommand(VanillaSpawnManager spawnManager, TeleportManager teleportManager) {
        this.spawnManager    = spawnManager;
        this.teleportManager = teleportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!spawnManager.isConfigured()) {
            player.sendMessage(Component.text("Vanilla spawn has not been set yet.", NamedTextColor.RED));
            return true;
        }

        Location dest = spawnManager.getTeleportLocation();
        if (dest == null) {
            player.sendMessage(Component.text("The spawn world is not loaded.", NamedTextColor.RED));
            return true;
        }

        teleportManager.startTeleport(player, dest);
        return true;
    }
}
