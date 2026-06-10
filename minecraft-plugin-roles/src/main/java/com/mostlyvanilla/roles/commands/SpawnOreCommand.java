package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.ore.OreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class SpawnOreCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaRoles plugin;
    private final OreManager oreManager;

    public SpawnOreCommand(MostlyVanillaRoles plugin, OreManager oreManager) {
        this.plugin     = plugin;
        this.oreManager = oreManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.isOp() && !plugin.getRoleManager().canUseSpawnore(player.getUniqueId())) {
            player.sendMessage(Component.text("You don't have permission to use /spawnore.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /spawnore <ore>", NamedTextColor.RED));
            player.sendMessage(Component.text("Ores: " + String.join(", ", OreManager.ORES.keySet()), NamedTextColor.GRAY));
            return true;
        }

        String oreName = args[0].toLowerCase();
        OreManager.OreSpec spec = OreManager.ORES.get(oreName);
        if (spec == null) {
            player.sendMessage(Component.text("Unknown ore '" + oreName + "'.", NamedTextColor.RED));
            player.sendMessage(Component.text("Ores: " + String.join(", ", OreManager.ORES.keySet()), NamedTextColor.GRAY));
            return true;
        }

        int placed = oreManager.spawnVein(player.getUniqueId(), player.getLocation(), spec);
        if (placed == 0) {
            player.sendMessage(Component.text("No valid host blocks found nearby.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Spawned " + placed + "x " + prettify(oreName) + " ore. Use /delore to undo.", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        String partial = args[0].toLowerCase();
        return OreManager.ORES.keySet().stream()
            .filter(k -> k.startsWith(partial))
            .collect(Collectors.toList());
    }

    private static String prettify(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
