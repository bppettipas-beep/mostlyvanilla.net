package com.mostlyvanilla.tpa.commands;

import com.mostlyvanilla.tpa.RequestManager;
import com.mostlyvanilla.tpa.TpaRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final RequestManager rm;

    public TpaCommand(RequestManager rm) {
        this.rm = rm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player requester)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 1) {
            requester.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            requester.sendMessage(Component.text("Player not found or not online.", NamedTextColor.RED));
            return true;
        }

        if (target.equals(requester)) {
            requester.sendMessage(Component.text("You can't teleport to yourself.", NamedTextColor.RED));
            return true;
        }

        TpaRequest.Type type = command.getName().equalsIgnoreCase("tpahere")
            ? TpaRequest.Type.TPAHERE
            : TpaRequest.Type.TPA;

        rm.initiateRequest(requester, target, type);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> !n.equals(sender.getName()) && n.toLowerCase().startsWith(input))
                .toList();
        }
        return List.of();
    }
}
