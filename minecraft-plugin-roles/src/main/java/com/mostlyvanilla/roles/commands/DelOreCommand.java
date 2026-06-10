package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.ore.OreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelOreCommand implements CommandExecutor {

    private final MostlyVanillaRoles plugin;
    private final OreManager oreManager;

    public DelOreCommand(MostlyVanillaRoles plugin, OreManager oreManager) {
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
            player.sendMessage(Component.text("You don't have permission to use /delore.", NamedTextColor.RED));
            return true;
        }
        if (!oreManager.hasOres(player.getUniqueId())) {
            player.sendMessage(Component.text("You have no spawned ores to remove.", NamedTextColor.YELLOW));
            return true;
        }
        int removed = oreManager.removeOres(player.getUniqueId());
        player.sendMessage(Component.text("Removed " + removed + " ore block" + (removed == 1 ? "" : "s") + " and restored original blocks.", NamedTextColor.GREEN));
        return true;
    }
}
