package com.mostlyvanilla.economy.commands;

import com.mostlyvanilla.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class EcoCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economy;

    public EcoCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("economy.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":   return handleCreate(sender, args);
            case "delete":   return handleDelete(sender, args);
            case "give":     return handleGive(sender, args);
            case "take":     return handleTake(sender, args);
            case "set":      return handleSet(sender, args);
            case "balance":
            case "bal":      return handleBalance(sender, args);
            case "reset":    return handleReset(sender, args);
            case "list":     return handleList(sender);
            case "top":      return handleTop(sender, args);
            case "setmain":  return handleSetMain(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    // /eco create <currency>
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eco create <currency>");
            return true;
        }
        String name = args[1];
        if (!name.matches("[a-zA-Z0-9_]+")) {
            sender.sendMessage("§cCurrency names may only contain letters, numbers, and underscores.");
            return true;
        }
        if (economy.createCurrency(name)) {
            sender.sendMessage("§aCurrency §e" + name.toLowerCase() + " §acreated.");
        } else {
            sender.sendMessage("§cCurrency §e" + name.toLowerCase() + " §calready exists.");
        }
        return true;
    }

    // /eco delete <currency>
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eco delete <currency>");
            return true;
        }
        if (economy.deleteCurrency(args[1])) {
            sender.sendMessage("§aCurrency §e" + args[1].toLowerCase() + " §adeleted. All balances erased.");
        } else {
            sender.sendMessage("§cCurrency §e" + args[1] + " §cdoes not exist.");
        }
        return true;
    }

    // /eco give <player> <currency> <amount>
    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /eco give <player> <currency> <amount>");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §chas never joined this server.");
            return true;
        }
        if (!economy.currencyExists(args[2])) {
            sender.sendMessage("§cCurrency §e" + args[2] + " §cdoes not exist.");
            return true;
        }
        double amount = parseAmount(sender, args[3]);
        if (amount <= 0) return true;

        economy.giveBalance(target.getUniqueId(), args[2], amount);
        sender.sendMessage("§aGave §e" + fmt(amount) + " " + args[2].toLowerCase()
                + " §ato §e" + target.getName() + "§a.");
        notifyIfOnline(target, "§aYou received §e" + fmt(amount) + " " + args[2].toLowerCase()
                + " §afrom an admin.");
        return true;
    }

    // /eco take <player> <currency> <amount>
    private boolean handleTake(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /eco take <player> <currency> <amount>");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §chas never joined this server.");
            return true;
        }
        if (!economy.currencyExists(args[2])) {
            sender.sendMessage("§cCurrency §e" + args[2] + " §cdoes not exist.");
            return true;
        }
        double amount = parseAmount(sender, args[3]);
        if (amount <= 0) return true;

        if (economy.takeBalance(target.getUniqueId(), args[2], amount)) {
            sender.sendMessage("§aRemoved §e" + fmt(amount) + " " + args[2].toLowerCase()
                    + " §afrom §e" + target.getName() + "§a.");
            notifyIfOnline(target, "§cAn admin removed §e" + fmt(amount) + " " + args[2].toLowerCase()
                    + " §cfrom your balance.");
        } else {
            double has = economy.getBalance(target.getUniqueId(), args[2]);
            sender.sendMessage("§c" + target.getName() + " §conly has §e" + fmt(has) + " "
                    + args[2].toLowerCase() + "§c.");
        }
        return true;
    }

    // /eco set <player> <currency> <amount>
    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /eco set <player> <currency> <amount>");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §chas never joined this server.");
            return true;
        }
        if (!economy.currencyExists(args[2])) {
            sender.sendMessage("§cCurrency §e" + args[2] + " §cdoes not exist.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount: §e" + args[3]);
            return true;
        }
        if (amount < 0) {
            sender.sendMessage("§cAmount cannot be negative.");
            return true;
        }
        economy.setBalance(target.getUniqueId(), args[2], amount);
        sender.sendMessage("§aSet §e" + target.getName() + "§a's §e" + args[2].toLowerCase()
                + " §abalance to §e" + fmt(amount) + "§a.");
        return true;
    }

    // /eco balance <player> <currency>
    private boolean handleBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /eco balance <player> <currency>");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §chas never joined this server.");
            return true;
        }
        if (!economy.currencyExists(args[2])) {
            sender.sendMessage("§cCurrency §e" + args[2] + " §cdoes not exist.");
            return true;
        }
        double bal = economy.getBalance(target.getUniqueId(), args[2]);
        sender.sendMessage("§e" + target.getName() + " §ahas §e" + fmt(bal) + " "
                + args[2].toLowerCase() + "§a.");
        return true;
    }

    // /eco reset <player> <currency>
    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /eco reset <player> <currency>");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §chas never joined this server.");
            return true;
        }
        if (!economy.currencyExists(args[2])) {
            sender.sendMessage("§cCurrency §e" + args[2] + " §cdoes not exist.");
            return true;
        }
        economy.setBalance(target.getUniqueId(), args[2], 0.0);
        sender.sendMessage("§aReset §e" + target.getName() + "§a's §e" + args[2].toLowerCase()
                + " §abalance to §e0§a.");
        return true;
    }

    // /eco setmain <currency>
    private boolean handleSetMain(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eco setmain <currency>");
            return true;
        }
        if (economy.setMainCurrency(args[1])) {
            sender.sendMessage("§aMain currency set to §e" + args[1].toLowerCase()
                    + "§a. §7(/balance and /baltop will now default to it.)");
        } else {
            sender.sendMessage("§cCurrency §e" + args[1] + " §cdoes not exist.");
        }
        return true;
    }

    // /eco list
    private boolean handleList(CommandSender sender) {
        Set<String> currencies = economy.getCurrencies();
        if (currencies.isEmpty()) {
            sender.sendMessage("§cNo currencies exist. Create one with §e/eco create <name>§c.");
            return true;
        }
        sender.sendMessage("§6§lCurrencies §7(" + currencies.size() + ")§6§l: §e"
                + String.join("§7, §e", currencies));
        return true;
    }

    // /eco top <currency>
    private boolean handleTop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eco top <currency>");
            return true;
        }
        if (!economy.currencyExists(args[1])) {
            sender.sendMessage("§cCurrency §e" + args[1] + " §cdoes not exist.");
            return true;
        }
        Map<UUID, Double> top = economy.getTopBalances(args[1], 10);
        if (top.isEmpty()) {
            sender.sendMessage("§cNo one has any §e" + args[1].toLowerCase() + " §cyet.");
            return true;
        }
        sender.sendMessage("§6§lTop " + args[1].toLowerCase() + " balances:");
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : top.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String name = op.getName() != null ? op.getName() : entry.getKey().toString().substring(0, 8) + "...";
            sender.sendMessage("§6#" + rank + " §e" + name + " §7— §a" + fmt(entry.getValue())
                    + " " + args[1].toLowerCase());
            rank++;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lEconomy Admin Commands:");
        sender.sendMessage("§e/eco create <currency>                 §7Create a new currency");
        sender.sendMessage("§e/eco delete <currency>                 §7Delete a currency");
        sender.sendMessage("§e/eco give <player> <currency> <amount> §7Give currency to a player");
        sender.sendMessage("§e/eco take <player> <currency> <amount> §7Take currency from a player");
        sender.sendMessage("§e/eco set  <player> <currency> <amount> §7Set a player's balance");
        sender.sendMessage("§e/eco balance <player> <currency>       §7Check a player's balance");
        sender.sendMessage("§e/eco reset <player> <currency>         §7Reset balance to 0");
        sender.sendMessage("§e/eco list                              §7List all currencies");
        sender.sendMessage("§e/eco top <currency>                    §7Top 10 richest players");
        sender.sendMessage("§e/eco setmain <currency>                §7Set the default currency");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("economy.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return filterPrefix(Arrays.asList(
                    "create", "delete", "give", "take", "set", "balance", "reset", "list", "top", "setmain"
            ), args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            switch (sub) {
                case "delete":
                case "top":
                case "setmain":
                    return filterPrefix(new ArrayList<>(economy.getCurrencies()), args[1]);
                case "give":
                case "take":
                case "set":
                case "balance":
                case "reset":
                    return null; // let Bukkit suggest online player names
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "give":
                case "take":
                case "set":
                case "balance":
                case "reset":
                    return filterPrefix(new ArrayList<>(economy.getCurrencies()), args[2]);
            }
        }

        return Collections.emptyList();
    }

    // --- Helpers ---

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private void notifyIfOnline(OfflinePlayer target, String message) {
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(message);
        }
    }

    private double parseAmount(CommandSender sender, String raw) {
        try {
            double v = Double.parseDouble(raw);
            if (v <= 0) {
                sender.sendMessage("§cAmount must be greater than 0.");
                return 0;
            }
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount: §e" + raw);
            return 0;
        }
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }

    public static String fmt(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format("%.2f", amount);
    }
}
