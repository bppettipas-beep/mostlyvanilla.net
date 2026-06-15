package com.mostlyvanilla.crates;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class BitShopBridge {

    private Object bitShopManager = null;
    private boolean resolved = false;

    private Object getBsm() {
        if (resolved) return bitShopManager;
        resolved = true;
        Plugin shop = Bukkit.getPluginManager().getPlugin("MostlyVanillaShop");
        if (shop == null) return null;
        try {
            bitShopManager = shop.getClass().getMethod("getBitShopManager").invoke(shop);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Crates] Could not connect to MostlyVanillaShop BitShopManager: " + e.getMessage());
        }
        return bitShopManager;
    }

    public void addCrateKey(String id, String displayName, String material, double price, List<String> lore) {
        Object bsm = getBsm();
        if (bsm == null) return;
        try {
            bsm.getClass()
               .getMethod("addCrateKey", String.class, String.class, String.class, double.class, List.class)
               .invoke(bsm, id, displayName, material, price, lore);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Crates] Failed to register key in BitShop: " + e.getMessage());
        }
    }

    public void removeCrateKey(String id) {
        Object bsm = getBsm();
        if (bsm == null) return;
        try {
            bsm.getClass()
               .getMethod("removeCrateKey", String.class)
               .invoke(bsm, id);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Crates] Failed to remove key from BitShop: " + e.getMessage());
        }
    }
}
