package com.mostlyvanilla.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KeysCommand implements CommandExecutor, TabCompleter {

    private final KeyStore keyStore;
    private final BitShopManager bitShopManager;

    public KeysCommand(KeyStore keyStore, BitShopManager bitShopManager) {
        this.keyStore       = keyStore;
        this.bitShopManager = bitShopManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            if (!sender.isOp() && !sender.hasPermission("mostlyvanilla.keys.admin")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(Component.text("Usage: /keys " + args[0] + " <player|all> <type> <amount>", NamedTextColor.RED));
                return true;
            }

            String keyId = args[2].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Amount must be a positive number.", NamedTextColor.RED));
                return true;
            }

            boolean broadcast = args[1].equalsIgnoreCase("all") || args[1].equals("*");

            if (broadcast) {
                Collection<? extends Player> online = Bukkit.getOnlinePlayers();
                if (online.isEmpty()) {
                    sender.sendMessage(Component.text("No players online.", NamedTextColor.RED));
                    return true;
                }
                if (args[0].equalsIgnoreCase("give")) {
                    for (Player p : online) {
                        keyStore.addKeys(p.getUniqueId(), keyId, amount);
                        p.sendMessage(Component.text("You received " + amount + "x ", NamedTextColor.GREEN)
                            .append(Component.text(capitalize(keyId) + " Key", NamedTextColor.YELLOW))
                            .append(Component.text("!", NamedTextColor.GREEN)));
                    }
                    sender.sendMessage(Component.text("Gave " + amount + "x " + capitalize(keyId) + " key(s) to all " + online.size() + " online player(s).", NamedTextColor.GREEN));
                } else {
                    for (Player p : online) {
                        int current = keyStore.getKeys(p.getUniqueId(), keyId);
                        keyStore.setKeys(p.getUniqueId(), keyId, Math.max(0, current - amount));
                    }
                    sender.sendMessage(Component.text("Removed up to " + amount + "x " + capitalize(keyId) + " key(s) from all online players.", NamedTextColor.YELLOW));
                }
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found. Use \"all\" to target everyone online.", NamedTextColor.RED));
                return true;
            }

            if (args[0].equalsIgnoreCase("give")) {
                keyStore.addKeys(target.getUniqueId(), keyId, amount);
                sender.sendMessage(Component.text("Gave " + amount + "x " + capitalize(keyId) + " Key(s) to " + target.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You received " + amount + "x ", NamedTextColor.GREEN)
                    .append(Component.text(capitalize(keyId) + " Key", NamedTextColor.YELLOW))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            } else {
                int current = keyStore.getKeys(target.getUniqueId(), keyId);
                int removed = Math.min(current, amount);
                keyStore.setKeys(target.getUniqueId(), keyId, current - removed);
                sender.sendMessage(Component.text("Removed " + removed + "x " + capitalize(keyId) + " Key(s) from " + target.getName() + ".", NamedTextColor.YELLOW));
            }
            return true;
        }

        // /keys [player] — show key balances
        Player target;
        if (args.length >= 1 && sender.isOp()) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Usage: /keys [player]", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("── " + target.getName() + "'s Keys ──", NamedTextColor.GOLD));
        List<String> keyIds = bitShopManager.getKeyIds();
        if (keyIds.isEmpty()) {
            sender.sendMessage(Component.text("No key types configured.", NamedTextColor.GRAY));
            return true;
        }
        for (String id : keyIds) {
            int count = keyStore.getKeys(target.getUniqueId(), id);
            sender.sendMessage(
                Component.text("  " + capitalize(id) + " Key: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(count),
                        count > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("give", "take");
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            List<String> targets = new ArrayList<>();
            targets.add("all");
            Bukkit.getOnlinePlayers().forEach(p -> targets.add(p.getName()));
            return targets;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            return bitShopManager.getKeyIds();
        }
        return List.of();
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
