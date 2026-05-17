package com.mostlyvanilla.rtp;

import com.mostlyvanilla.rtp.commands.DimensionDisableCommand;
import com.mostlyvanilla.rtp.commands.RtpCommand;
import com.mostlyvanilla.rtp.gui.RtpGui;
import com.mostlyvanilla.rtp.listeners.GuiListener;
import com.mostlyvanilla.rtp.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaRtp extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        TeleportManager   teleportManager = new TeleportManager(this);
        RtpManager        rtpManager      = new RtpManager(this, teleportManager);
        RtpGui            rtpGui          = new RtpGui(rtpManager);

        DimensionDisableCommand disableCmd = new DimensionDisableCommand(rtpManager);

        getCommand("rtp").setExecutor(new RtpCommand(rtpGui));
        getCommand("dimensiondisable").setExecutor(disableCmd);
        getCommand("dimensiondisable").setTabCompleter(disableCmd);

        getServer().getPluginManager().registerEvents(new GuiListener(rtpManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(teleportManager), this);

        getLogger().info("MostlyVanilla RTP enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanilla RTP disabled.");
    }
}
