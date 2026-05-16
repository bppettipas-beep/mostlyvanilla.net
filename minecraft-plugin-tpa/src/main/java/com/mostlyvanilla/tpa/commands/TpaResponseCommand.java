package com.mostlyvanilla.tpa.commands;

import com.mostlyvanilla.tpa.RequestManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaResponseCommand implements CommandExecutor {

    private final RequestManager rm;

    public TpaResponseCommand(RequestManager rm) {
        this.rm = rm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "tpaconfirm" -> rm.confirm(player);
            case "tpaccept"   -> rm.accept(player);
            case "tpdeny"     -> rm.deny(player);
            case "tpacancel"  -> rm.cancel(player);
        }
        return true;
    }
}
