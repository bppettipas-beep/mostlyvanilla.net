package com.mostlyvanilla.economy;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class SettingsBridge {

    private final Server server;

    public SettingsBridge(Server server) {
        this.server = server;
    }

    public boolean acceptsPayments(UUID uuid) {
        Plugin settings = server.getPluginManager().getPlugin("MostlyVanillaSettings");
        if (settings == null) return true; // allow by default if settings plugin is absent
        try {
            Object sm = settings.getClass().getMethod("getSettingsManager").invoke(settings);
            return (boolean) sm.getClass().getMethod("isEnabled", UUID.class, String.class)
                    .invoke(sm, uuid, "ACCEPT_PAYMENTS");
        } catch (Exception e) {
            return true;
        }
    }
}
