package com.mostlyvanilla.spawnstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnStashCommand implements CommandExecutor {

    private final StashManager stash;
    private final RolesBridge  roles;

    public SpawnStashCommand(StashManager stash, RolesBridge roles) {
        this.stash = stash;
        this.roles = roles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!roles.canUseStash(player)) {
            player.sendMessage(Component.text(
                "You don't have permission to use /spawnstash.", NamedTextColor.RED));
            return true;
        }
        stash.createStash(player);
        return true;
    }
}
