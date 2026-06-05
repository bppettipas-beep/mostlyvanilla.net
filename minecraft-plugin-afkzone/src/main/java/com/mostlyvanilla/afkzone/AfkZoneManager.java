package com.mostlyvanilla.afkzone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AfkZoneManager {

    private static final Title.Times TITLE_TIMES =
        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(200));

    private final JavaPlugin plugin;
    private final EconomyBridge economy;
    private final Set<UUID> playersInZone = new HashSet<>();

    private boolean enabled;
    private String world;
    private double minX, minY, minZ, maxX, maxY, maxZ;

    public AfkZoneManager(JavaPlugin plugin, EconomyBridge economy) {
        this.plugin  = plugin;
        this.economy = economy;
        loadFromConfig();
    }

    public void loadFromConfig() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("zone.enabled", false);
        world   = cfg.getString("zone.world", "world");
        double x1 = cfg.getDouble("zone.x1", 0), x2 = cfg.getDouble("zone.x2", 0);
        double y1 = cfg.getDouble("zone.y1", 0), y2 = cfg.getDouble("zone.y2", 0);
        double z1 = cfg.getDouble("zone.z1", 0), z2 = cfg.getDouble("zone.z2", 0);
        minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
        minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
        minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
    }

    public boolean isInZone(Location loc) {
        if (!enabled || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public void handlePlayerMove(Player player, Location to) {
        boolean wasIn = playersInZone.contains(player.getUniqueId());
        boolean isNow = isInZone(to);
        if (!wasIn && isNow) {
            playersInZone.add(player.getUniqueId());
            sendAfkTitle(player);
        } else if (wasIn && !isNow) {
            playersInZone.remove(player.getUniqueId());
            player.resetTitle();
        }
    }

    public void handlePlayerQuit(Player player) {
        playersInZone.remove(player.getUniqueId());
    }

    /** Called on a repeating task to refresh the title for all zone players. */
    public void sendTitlesToZonePlayers() {
        for (UUID uid : new HashSet<>(playersInZone)) {
            Player p = plugin.getServer().getPlayer(uid);
            if (p == null || !p.isOnline()) {
                playersInZone.remove(uid);
                continue;
            }
            sendAfkTitle(p);
        }
    }

    /** Called on a repeating task to pay all zone players. */
    public void rewardPlayersInZone() {
        if (playersInZone.isEmpty()) return;
        double amount   = plugin.getConfig().getDouble("amount", 10.0);
        String currency = economy.getCurrency();
        for (UUID uid : new HashSet<>(playersInZone)) {
            Player p = plugin.getServer().getPlayer(uid);
            if (p == null || !p.isOnline()) {
                playersInZone.remove(uid);
                continue;
            }
            economy.deposit(uid, amount);
            p.sendMessage(Component.text("+" + amount + " " + currency + " (AFK Zone)")
                .color(NamedTextColor.GREEN));
        }
    }

    private void sendAfkTitle(Player player) {
        player.showTitle(Title.title(
            Component.text("AFK ZONE").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD),
            Component.empty(),
            TITLE_TIMES
        ));
    }

    public void setCorner1(Location loc) {
        world = loc.getWorld().getName();
        plugin.getConfig().set("zone.world", world);
        plugin.getConfig().set("zone.x1", loc.getX());
        plugin.getConfig().set("zone.y1", loc.getY());
        plugin.getConfig().set("zone.z1", loc.getZ());
        plugin.saveConfig();
        loadFromConfig();
    }

    public void setCorner2(Location loc) {
        plugin.getConfig().set("zone.x2", loc.getX());
        plugin.getConfig().set("zone.y2", loc.getY());
        plugin.getConfig().set("zone.z2", loc.getZ());
        plugin.saveConfig();
        loadFromConfig();
    }

    public void setEnabled(boolean val) {
        enabled = val;
        plugin.getConfig().set("zone.enabled", val);
        plugin.saveConfig();
    }

    public boolean isEnabled()  { return enabled; }
    public String  getWorld()   { return world; }
    public double  getMinX()    { return minX; }
    public double  getMaxX()    { return maxX; }
    public double  getMinY()    { return minY; }
    public double  getMaxY()    { return maxY; }
    public double  getMinZ()    { return minZ; }
    public double  getMaxZ()    { return maxZ; }
}
