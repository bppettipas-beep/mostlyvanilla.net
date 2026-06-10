package com.mostlyvanilla.macelimit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class MaceLimitCommand implements CommandExecutor, TabCompleter {

    private final MaceLimitManager manager;

    public MaceLimitCommand(MaceLimitManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("macelimit.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /macelimit set <amount>  (0 = no limit)", NamedTextColor.RED));
                    return true;
                }
                int val;
                try { val = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("\"" + args[1] + "\" is not a valid number.", NamedTextColor.RED));
                    return true;
                }
                if (val < 0) {
                    sender.sendMessage(Component.text("Limit must be 0 or higher. Use 0 for no limit.", NamedTextColor.RED));
                    return true;
                }
                manager.setLimit(val);
                sender.sendMessage(Component.text("Mace crafting limit set to ", NamedTextColor.GREEN)
                    .append(Component.text(val <= 0 ? "unlimited" : String.valueOf(val), NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            }
            case "info" -> {
                int limit   = manager.getLimit();
                int crafted = manager.getCrafted();
                sender.sendMessage(Component.text("── Mace Limit ──", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("  Crafted:   ", NamedTextColor.GRAY)
                    .append(Component.text(crafted, NamedTextColor.YELLOW)));
                sender.sendMessage(Component.text("  Limit:     ", NamedTextColor.GRAY)
                    .append(Component.text(limit <= 0 ? "unlimited" : String.valueOf(limit), NamedTextColor.YELLOW)));
                sender.sendMessage(Component.text("  Remaining: ", NamedTextColor.GRAY)
                    .append(Component.text(limit <= 0 ? "∞" : String.valueOf(manager.getRemaining()), NamedTextColor.YELLOW)));
            }
            case "reset" -> {
                manager.reset();
                sender.sendMessage(Component.text("Mace craft count reset to 0.", NamedTextColor.GREEN));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("set", "info", "reset");
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── /macelimit ──", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  set <amount>  ", NamedTextColor.YELLOW)
            .append(Component.text("— Set max maces craftable server-wide (0 = no limit)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  info          ", NamedTextColor.YELLOW)
            .append(Component.text("— Show current count and limit", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  reset         ", NamedTextColor.YELLOW)
            .append(Component.text("— Reset crafted count to 0", NamedTextColor.GRAY)));
    }
}
