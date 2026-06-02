package com.mostlyvanilla.spawnstash;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelStashCommand implements CommandExecutor {

    private final StashManager stash;

    public DelStashCommand(StashManager stash) {
        this.stash = stash;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        stash.deleteStash(player);
        return true;
    }
}
