package com.mostlyvanilla.economy.commands;

import com.mostlyvanilla.economy.EconomyManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.Collection;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economy;

    public BalanceCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can check their balance.");
            return true;
        }
        if (!sender.hasPermission("economy.balance")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;
        Collection<String> currencies = economy.getCurrencies();

        if (currencies.isEmpty()) {
            sender.sendMessage("§cNo currencies have been created yet.");
            return true;
        }

        if (args.length == 0) {
            String main = economy.getMainCurrency();
            if (main != null) {
                double bal = economy.getBalance(player.getUniqueId(), main);
                sender.sendMessage("§aYour §e" + main + " §abalance: §e" + EcoCommand.fmt(bal));
            } else {
                sender.sendMessage("§6§lYour Balances:");
                for (String currency : currencies) {
                    double bal = economy.getBalance(player.getUniqueId(), currency);
                    sender.sendMessage("  §e" + currency + "§7: §a" + EcoCommand.fmt(bal));
                }
            }
        } else {
            String currency = args[0];
            if (!economy.currencyExists(currency)) {
                sender.sendMessage("§cCurrency §e" + currency + " §cdoes not exist.");
                return true;
            }
            double bal = economy.getBalance(player.getUniqueId(), currency);
            sender.sendMessage("§aYour §e" + currency.toLowerCase() + " §abalance: §e" + EcoCommand.fmt(bal));
        }
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
