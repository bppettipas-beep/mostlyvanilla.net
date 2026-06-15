package com.mostlyvanilla.home.commands;

import com.mostlyvanilla.home.VanillaSpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanillaSpawnSetCommand implements CommandExecutor {

    private final VanillaSpawnManager spawnManager;

    public VanillaSpawnSetCommand(VanillaSpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        spawnManager.setSpawn(player);
        player.sendMessage(Component.text("Vanilla spawn set! ", NamedTextColor.GREEN)
            .append(Component.text("A 10×10×10 protected zone has been created here.", NamedTextColor.GRAY)));
        return true;
    }
}
