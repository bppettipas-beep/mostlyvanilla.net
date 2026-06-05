package com.mostlyvanilla.auctionhouse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class OrdersCommand implements CommandExecutor, TabCompleter {

    private final OrderManager orderManager;

    public OrdersCommand(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            orderManager.openGui(player, OrderManager.GuiMode.ALL, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 3) {
                player.sendMessage(Component.text("Usage: /orders create <amount> <price-each>", NamedTextColor.RED));
                return true;
            }
            int amount;
            double priceEach;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
                return true;
            }
            try {
                priceEach = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid price: " + args[2], NamedTextColor.RED));
                return true;
            }
            if (amount <= 0) {
                player.sendMessage(Component.text("Amount must be greater than zero.", NamedTextColor.RED));
                return true;
            }
            if (priceEach <= 0) {
                player.sendMessage(Component.text("Price must be greater than zero.", NamedTextColor.RED));
                return true;
            }
            orderManager.createOrder(player, amount, priceEach);
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /orders cancel <order-id>", NamedTextColor.RED));
                player.sendMessage(Component.text("Tip: find your order IDs in /orders → My Orders.", NamedTextColor.GRAY));
                return true;
            }
            orderManager.cancelOrder(player, args[1]);
            return true;
        }

        player.sendMessage(Component.text("Usage: /orders [create <amount> <price>]", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("create", "cancel").stream()
                .filter(s -> s.startsWith(partial))
                .toList();
        }
        return List.of();
    }
}
