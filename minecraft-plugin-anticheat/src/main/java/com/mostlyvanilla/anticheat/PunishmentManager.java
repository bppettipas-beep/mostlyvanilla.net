package com.mostlyvanilla.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

public class PunishmentManager {

    private static final String KICK_MSG =
            "You have been removed from the server.";
    private static final String BAN_MSG =
            "You are suspected of cheating.\nClip this and make a ticket in the Discord server.";
    private static final long BAN_DURATION_MS = 14L * 24 * 60 * 60 * 1000;

    private final MostlyVanillaAnticheat plugin;
    private File strikesFile;
    private YamlConfiguration strikesData;

    public PunishmentManager(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        plugin.getDataFolder().mkdirs();
        strikesFile = new File(plugin.getDataFolder(), "strikes.yml");
        if (!strikesFile.exists()) {
            try { strikesFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("[AC] Could not create strikes.yml"); }
        }
        strikesData = YamlConfiguration.loadConfiguration(strikesFile);
    }

    private void save() {
        try { strikesData.save(strikesFile); }
        catch (IOException e) { plugin.getLogger().warning("[AC] Could not save strikes.yml: " + e.getMessage()); }
    }

    public int getStrikes(UUID uuid) {
        return strikesData.getInt(uuid.toString(), 0);
    }

    private int recordStrike(UUID uuid) {
        int count = getStrikes(uuid) + 1;
        strikesData.set(uuid.toString(), count);
        save();
        return count;
    }

    public void resetStrikes(UUID uuid) {
        strikesData.set(uuid.toString(), null);
        save();
    }

    /**
     * Called whenever the anticheat decides a player should be punished.
     * XRAY: always a silent kick only.
     * All other checks: 1st offense = kick, 2nd+ offense = 14-day ban.
     */
    public void punish(Player player, String check) {
        PlayerData data = plugin.getData(player.getUniqueId());

        // Prevent double-punishment within the same session (e.g. global + per-check both trigger)
        if (data.punishmentScheduled) return;
        data.punishmentScheduled = true;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            int strikes = recordStrike(player.getUniqueId());

            if (strikes >= 2) {
                Date expiry = new Date(System.currentTimeMillis() + BAN_DURATION_MS);
                Bukkit.getBanList(BanList.Type.NAME)
                        .addBan(player.getName(), BAN_MSG, expiry, "MostlyVanillaAnticheat");
                player.kickPlayer(BAN_MSG);
                plugin.getLogger().info("[AC] " + player.getName()
                        + " banned 14d (strike=" + strikes + ", check=" + check + ")");
            } else {
                player.kickPlayer(KICK_MSG);
                plugin.getLogger().info("[AC] " + player.getName()
                        + " kicked (strike=" + strikes + ", check=" + check + ")");
            }
        });
    }
}
