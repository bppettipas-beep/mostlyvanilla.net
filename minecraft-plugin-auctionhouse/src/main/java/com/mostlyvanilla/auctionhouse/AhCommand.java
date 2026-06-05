package com.mostlyvanilla.auctionhouse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class AhCommand implements CommandExecutor, TabCompleter {

    private final AuctionManager auctionManager;

    public AhCommand(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            auctionManager.openGui(player, AuctionManager.GuiMode.ALL, AuctionManager.SortMode.NEWEST, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /ah sell <price>", NamedTextColor.RED));
                return true;
            }
            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid price: " + args[1], NamedTextColor.RED));
                return true;
            }
            if (price <= 0) {
                player.sendMessage(Component.text("Price must be greater than zero.", NamedTextColor.RED));
                return true;
            }
            auctionManager.createListing(player, price);
            return true;
        }

        player.sendMessage(Component.text("Usage: /ah [sell <price>]", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("sell".startsWith(partial)) return List.of("sell");
        }
        return List.of();
    }
}
