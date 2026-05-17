package com.mostlyvanilla.rtp.commands;

import com.mostlyvanilla.rtp.gui.RtpGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RtpCommand implements CommandExecutor {

    private final RtpGui rtpGui;

    public RtpCommand(RtpGui rtpGui) { this.rtpGui = rtpGui; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        rtpGui.open(player);
        return true;
    }
}
