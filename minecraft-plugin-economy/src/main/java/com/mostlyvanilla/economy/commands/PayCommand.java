package com.mostlyvanilla.economy.commands;

import com.mostlyvanilla.economy.EconomyManager;
import com.mostlyvanilla.economy.SettingsBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economy;
    private final SettingsBridge settingsBridge;

    public PayCommand(EconomyManager economy, SettingsBridge settingsBridge) {
        this.economy = economy;
        this.settingsBridge = settingsBridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage("§cOnly players can use /pay.");
            return true;
        }
        if (!sender.hasPermission("economy.pay")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: §e/pay <player> <amount> §7or §e/pay <player> <currency> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[0] + " §cis not online.");
            return true;
        }
        if (target.equals(payer)) {
            sender.sendMessage("§cYou cannot pay yourself.");
            return true;
        }
        if (!settingsBridge.acceptsPayments(target.getUniqueId())) {
            sender.sendMessage("§e" + target.getName() + " §chas payments disabled.");
            return true;
        }

        // Resolve currency and amount:
        //   /pay <player> <amount>             → use main currency
        //   /pay <player> <currency> <amount>  → use named currency
        String currency;
        String amountStr;

        if (args.length >= 3 && economy.currencyExists(args[1])) {
            // Explicit currency provided
            currency  = args[1];
            amountStr = args[2];
        } else {
            // Default to main currency; args[1] is the amount
            currency = economy.getMainCurrency();
            if (currency == null) {
                sender.sendMessage("§cNo main currency is set. Use §e/pay <player> <currency> <amount>§c.");
                return true;
            }
            amountStr = args[1];
        }

        double amount = EcoCommand.parseAmountStatic(sender, amountStr, false);
        if (amount < 0) return true;

        double payerBal = economy.getBalance(payer.getUniqueId(), currency);
        if (payerBal < amount) {
            sender.sendMessage("§cInsufficient funds. You have §e" + EcoCommand.fmt(payerBal)
                    + " " + economy.getDisplayName(currency) + "§c.");
            return true;
        }

        economy.takeBalance(payer.getUniqueId(), currency, amount);
        economy.giveBalance(target.getUniqueId(), currency, amount);

        String cur = economy.getDisplayName(currency);
        payer.sendMessage("§aYou paid §e" + target.getName() + " §e" + EcoCommand.fmt(amount) + " " + cur + "§a.");
        target.sendMessage("§e" + payer.getName() + " §apaid you §e" + EcoCommand.fmt(amount) + " " + cur + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return null; // Bukkit handles online player names
        if (args.length == 2) {
            // Could be an amount or a currency name — suggest currencies as hints
            List<String> result = new ArrayList<>();
            for (String c : economy.getCurrencies()) {
                if (c.toLowerCase().startsWith(args[1].toLowerCase())) result.add(c);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
