package com.mostlyvanilla.economy.commands;

import com.mostlyvanilla.economy.EconomyManager;
import com.mostlyvanilla.economy.gui.BaltopGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class BaltopCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economy;
    private final BaltopGui gui;

    public BaltopCommand(EconomyManager economy, BaltopGui gui) {
        this.economy = economy;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can open the baltop GUI.");
            return true;
        }
        if (!sender.hasPermission("economy.baltop")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (economy.getCurrencies().isEmpty()) {
            sender.sendMessage("§cNo currencies exist yet.");
            return true;
        }

        String currency;
        if (args.length >= 1) {
            currency = args[0];
            if (!economy.currencyExists(currency)) {
                sender.sendMessage("§cCurrency §e" + currency + " §cdoes not exist.");
                return true;
            }
        } else {
            currency = economy.getMainCurrency();
            if (currency == null) {
                sender.sendMessage("§cNo main currency set. Use §e/eco setmain <currency>"
                        + " §cor specify one: §e/baltop <currency>");
                return true;
            }
        }

        gui.open(player, currency, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String c : economy.getCurrencies()) {
                if (c.startsWith(args[0].toLowerCase())) result.add(c);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
