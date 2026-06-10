package com.mostlyvanilla.tpa;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class SettingsBridge {

    private final Server server;

    public SettingsBridge(Server server) {
        this.server = server;
    }

    public boolean isAutoAccept(UUID uuid) {
        Plugin settings = server.getPluginManager().getPlugin("MostlyVanillaSettings");
        if (settings == null) return false;
        try {
            Object sm = settings.getClass().getMethod("getSettingsManager").invoke(settings);
            return (boolean) sm.getClass().getMethod("isEnabled", UUID.class, String.class)
                    .invoke(sm, uuid, "TPA_AUTO_ACCEPT");
        } catch (Exception e) {
            return false;
        }
    }
}
