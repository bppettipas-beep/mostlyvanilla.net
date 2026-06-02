package com.mostlyvanilla.spawn.commands;

import com.mostlyvanilla.spawn.MostlyVanillaSpawn;
import com.mostlyvanilla.spawn.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnDropCommand implements CommandExecutor {

    private final MostlyVanillaSpawn plugin;
    private final int point; // 1 or 2

    public SpawnDropCommand(MostlyVanillaSpawn plugin, int point) {
        this.plugin = plugin;
        this.point  = point;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("mostlyvanilla.spawn.admin")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        SpawnManager sm = plugin.getSpawnManager();

        if (!sm.isInSpawnWorld(player.getLocation())) {
            player.sendMessage(Component.text("You must be in the spawn world to use this.", NamedTextColor.RED));
            return true;
        }

        if (point == 1) {
            sm.setDropPoint1(player.getLocation());
            player.sendMessage(Component.text("Drop zone point 1 set at your location.", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Now run /spawndrop2 at the opposite corner.", NamedTextColor.GRAY));
        } else {
            if (!sm.hasDropPoint1()) {
                player.sendMessage(Component.text("Set point 1 first with /spawndrop1.", NamedTextColor.RED));
                return true;
            }
            sm.setDropPoint2(player.getLocation());
            player.sendMessage(Component.text("Drop zone active: ", NamedTextColor.GREEN)
                .append(Component.text(sm.dropZoneDescription(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Players who fall through that rectangle will be sent back to spawn.", NamedTextColor.GRAY));
        }

        return true;
    }
}
