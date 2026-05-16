package com.mostlyvanilla.gm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MostlyVanillaGm extends JavaPlugin implements CommandExecutor, TabCompleter {

    @Override
    public void onEnable() {
        for (String cmd : List.of("gm", "gmc", "gms", "gmsp", "gma")) {
            getCommand(cmd).setExecutor(this);
            getCommand(cmd).setTabCompleter(this);
        }
        getLogger().info("MostlyVanilla GM enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("gm")) {
            sender.sendMessage(Component.text("Gamemode commands: ", NamedTextColor.YELLOW)
                .append(Component.text("/gmc  /gms  /gmsp  /gma", NamedTextColor.WHITE)));
            return true;
        }

        if (!sender.hasPermission("mv.gm")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        GameMode mode = switch (name) {
            case "gmc"  -> GameMode.CREATIVE;
            case "gms"  -> GameMode.SURVIVAL;
            case "gmsp" -> GameMode.SPECTATOR;
            case "gma"  -> GameMode.ADVENTURE;
            default     -> null;
        };

        if (mode == null) return true;

        // Targeting another player
        if (args.length >= 1) {
            if (!sender.hasPermission("mv.gm.other")) {
                sender.sendMessage(Component.text("You don't have permission to change other players' gamemode.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found or not online.", NamedTextColor.RED));
                return true;
            }
            target.setGameMode(mode);
            target.sendMessage(Component.text("Your gamemode was set to ", NamedTextColor.YELLOW)
                .append(Component.text(modeName(mode), NamedTextColor.WHITE))
                .append(Component.text(" by ", NamedTextColor.YELLOW))
                .append(Component.text(sender.getName(), NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Set ", NamedTextColor.YELLOW)
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(" to ", NamedTextColor.YELLOW))
                .append(Component.text(modeName(mode), NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.YELLOW)));
            return true;
        }

        // Self
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Run this from in-game or specify a player.", NamedTextColor.RED));
            return true;
        }

        player.setGameMode(mode);
        player.sendMessage(Component.text("Gamemode set to ", NamedTextColor.YELLOW)
            .append(Component.text(modeName(mode), NamedTextColor.WHITE))
            .append(Component.text(".", NamedTextColor.YELLOW)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("mv.gm.other")) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .toList();
        }
        return List.of();
    }

    private String modeName(GameMode mode) {
        return switch (mode) {
            case CREATIVE  -> "Creative";
            case SURVIVAL  -> "Survival";
            case SPECTATOR -> "Spectator";
            case ADVENTURE -> "Adventure";
        };
    }
}
