package com.mostlyvanilla.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class WorthCommand implements CommandExecutor {

    private final SellManager sellManager;

    public WorthCommand(SellManager sellManager) {
        this.sellManager = sellManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        sellManager.openWorthGui(player, 0);
        return true;
    }
}
