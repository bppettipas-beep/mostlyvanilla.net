package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class GlobalWipeCommand implements CommandExecutor, TabCompleter {

    private final GlobalWipeManager manager;

    public GlobalWipeCommand(GlobalWipeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!staff.isOp()) {
            staff.sendMessage(Component.text("Only operators can use this command.", NamedTextColor.RED));
            return true;
        }
        manager.openStep1(staff);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
