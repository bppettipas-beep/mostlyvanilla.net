package com.mostlyvanilla.economy.commands;

import com.mostlyvanilla.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economy;

    public PayCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use /pay.");
            return true;
        }
        if (!sender.hasPermission("economy.pay")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /pay <player> <currency> <amount>");
            return true;
        }

        Player payer = (Player) sender;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[0] + " §cis not online.");
            return true;
        }
        if (target.equals(payer)) {
            sender.sendMessage("§cYou cannot pay yourself.");
            return true;
        }

        String currency = args[1];
        if (!economy.currencyExists(currency)) {
            sender.sendMessage("§cCurrency §e" + currency + " §cdoes not exist.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount: §e" + args[2]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§cAmount must be greater than 0.");
            return true;
        }

        double payerBal = economy.getBalance(payer.getUniqueId(), currency);
        if (payerBal < amount) {
            sender.sendMessage("§cInsufficient funds. You have §e" + EcoCommand.fmt(payerBal)
                    + " " + currency.toLowerCase() + "§c.");
            return true;
        }

        economy.takeBalance(payer.getUniqueId(), currency, amount);
        economy.giveBalance(target.getUniqueId(), currency, amount);

        payer.sendMessage("§aYou paid §e" + target.getName() + " §e" + EcoCommand.fmt(amount)
                + " " + currency.toLowerCase() + "§a.");
        target.sendMessage("§e" + payer.getName() + " §apaid you §e" + EcoCommand.fmt(amount)
                + " " + currency.toLowerCase() + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return null; // online player names from Bukkit
        }
        if (args.length == 2) {
            List<String> result = new ArrayList<>();
            for (String c : economy.getCurrencies()) {
                if (c.startsWith(args[1].toLowerCase())) result.add(c);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
