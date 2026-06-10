package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.stash.StashManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnStashCommand implements CommandExecutor {

    private final MostlyVanillaRoles plugin;
    private final StashManager stash;

    public SpawnStashCommand(MostlyVanillaRoles plugin, StashManager stash) {
        this.plugin = plugin;
        this.stash = stash;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.isOp() && !plugin.getRoleManager().canUseStash(player.getUniqueId())) {
            player.sendMessage(Component.text(
                "You don't have permission to use /spawnstash.", NamedTextColor.RED));
            return true;
        }
        stash.createStash(player);
        return true;
    }
}
