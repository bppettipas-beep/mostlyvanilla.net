package com.mostlyvanilla.crates;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class KeyBridge {

    private Object keyStore = null;
    private boolean resolved = false;

    private Object getKeyStore() {
        if (resolved) return keyStore;
        resolved = true;
        Plugin shop = Bukkit.getPluginManager().getPlugin("MostlyVanillaShop");
        if (shop == null) return null;
        try {
            keyStore = shop.getClass().getMethod("getKeyStore").invoke(shop);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Crates] Could not connect to MostlyVanillaShop KeyStore: " + e.getMessage());
        }
        return keyStore;
    }

    public int getKeys(UUID uuid, String keyId) {
        Object ks = getKeyStore();
        if (ks == null) return 0;
        try {
            return (int) ks.getClass().getMethod("getKeys", UUID.class, String.class).invoke(ks, uuid, keyId);
        } catch (Exception e) { return 0; }
    }

    public boolean takeKey(UUID uuid, String keyId) {
        Object ks = getKeyStore();
        if (ks == null) return false;
        try {
            return (boolean) ks.getClass().getMethod("takeKey", UUID.class, String.class).invoke(ks, uuid, keyId);
        } catch (Exception e) { return false; }
    }
}
