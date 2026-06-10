package com.mostlyvanilla.antidupe;

import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

public class DupeDetector {

    private static final String BAN_MESSAGE =
            "You have been banned for 7 days for suspected item duplication.\n" +
            "If you believe this is a mistake, make a ticket in the Discord server.";

    private final MostlyVanillaAntiDupe plugin;

    public DupeDetector(MostlyVanillaAntiDupe plugin) {
        this.plugin = plugin;
    }

    public void flagDupe(Player player, String checkName, String detail) {
        plugin.getLogger().warning("[AntiDupe] DUPE DETECTED: " + player.getName()
                + " | check=" + checkName + " | " + detail);

        // Alert online admins
        String alertMsg = "§c[AntiDupe] §e" + player.getName()
                + " §cflagged for §f" + checkName + " §8(§7" + detail + "§8)";
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("mostlyvanilla.antidupe.admin")) {
                admin.sendMessage(alertMsg);
            }
        }

        // Execute on main thread (ban/kick require it, and we may be called from an async context)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            boolean wipe = plugin.getConfig().getBoolean("wipe-on-ban", true);
            int banDays  = plugin.getConfig().getInt("ban-days", 7);

            if (wipe) wipePlayer(player);

            Date expiry = new Date(System.currentTimeMillis() + (long) banDays * 24 * 60 * 60 * 1000);
            Bukkit.getBanList(BanList.Type.NAME)
                    .addBan(player.getName(), BAN_MESSAGE, expiry, "MostlyVanillaAntiDupe");

            player.kickPlayer(BAN_MESSAGE);

            plugin.getLogger().info("[AntiDupe] " + player.getName()
                    + " banned " + banDays + "d" + (wipe ? " + wiped" : "") + ".");
        });
    }

    private void wipePlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0f);
        try { player.setStatistic(Statistic.PLAY_ONE_MINUTE, 0); } catch (Exception ignored) {}
        player.saveData();

        // Delete dat files so the wipe persists after the kick/rejoin
        World world = Bukkit.getWorlds().get(0);
        File worldFolder = world.getWorldFolder();
        UUID uuid = player.getUniqueId();
        deleteIfExists(new File(worldFolder, "playerdata/" + uuid + ".dat"));
        deleteIfExists(new File(worldFolder, "playerdata/" + uuid + ".dat_old"));
        deleteIfExists(new File(worldFolder, "stats/" + uuid + ".json"));
        deleteIfExists(new File(worldFolder, "advancements/" + uuid + ".json"));

        wipeCurrencies(uuid);
    }

    @SuppressWarnings("unchecked")
    private void wipeCurrencies(UUID uuid) {
        Plugin ecoPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaEconomy");
        if (ecoPlugin == null) return;
        try {
            Object em = ecoPlugin.getClass().getMethod("getEconomyManager").invoke(ecoPlugin);
            if (em == null) return;
            Collection<String> currencies = (Collection<String>)
                    em.getClass().getMethod("getCurrencies").invoke(em);
            Method setBalance = em.getClass().getMethod("setBalance", UUID.class, String.class, double.class);
            for (String currency : currencies) setBalance.invoke(em, uuid, currency, 0.0);
        } catch (Exception e) {
            plugin.getLogger().warning("[AntiDupe] Could not wipe currencies: " + e.getMessage());
        }
    }

    private void deleteIfExists(File f) {
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("[AntiDupe] Could not delete " + f.getAbsolutePath());
        }
    }
}
