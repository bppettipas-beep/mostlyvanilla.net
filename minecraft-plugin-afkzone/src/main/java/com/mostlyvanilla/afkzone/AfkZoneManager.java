package com.mostlyvanilla.afkzone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AfkZoneManager {

    private static final Title.Times TITLE_TIMES =
        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(200));

    private final JavaPlugin    plugin;
    private final EconomyBridge economy;
    private final RolesBridge   roles;
    private final Set<UUID> playersInZone = new HashSet<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> pendingTp = new HashMap<>();

    private boolean enabled;
    private String world;
    private double minX, minY, minZ, maxX, maxY, maxZ;

    public AfkZoneManager(JavaPlugin plugin, EconomyBridge economy, RolesBridge roles) {
        this.plugin  = plugin;
        this.economy = economy;
        this.roles   = roles;
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
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
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

    public void handlePlayerJoin(Player player) {
        if (!isInZone(player.getLocation())) return;
        playersInZone.add(player.getUniqueId());
        sendAfkTitle(player);
    }

    public void handlePlayerQuit(Player player) {
        playersInZone.remove(player.getUniqueId());
        cancelTeleport(player.getUniqueId(), false);
    }

    // ── /afk teleport ─────────────────────────────────────────────────────────

    public void startAfkTeleport(Player player) {
        cancelTeleport(player.getUniqueId(), false);
        player.sendMessage(Component.text("Teleporting to AFK Zone in ", NamedTextColor.DARK_PURPLE)
            .append(Component.text("5 seconds", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text(". Don't move!", NamedTextColor.DARK_PURPLE)));

        org.bukkit.scheduler.BukkitTask task = plugin.getServer().getScheduler()
            .runTaskLater(plugin, () -> {
                pendingTp.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                Location dest = getCenterLocation();
                if (dest == null) {
                    player.sendMessage(Component.text("The AFK Zone is not configured.", NamedTextColor.RED));
                    return;
                }
                player.teleport(dest);
            }, 100L); // 5 seconds

        pendingTp.put(player.getUniqueId(), task);
    }

    public void cancelTeleport(java.util.UUID uuid, boolean notify) {
        org.bukkit.scheduler.BukkitTask task = pendingTp.remove(uuid);
        if (task == null) return;
        task.cancel();
        if (notify) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text("Teleportation cancelled — you moved.", NamedTextColor.RED));
        }
    }

    public boolean hasPendingTp(java.util.UUID uuid) { return pendingTp.containsKey(uuid); }

    public Location getCenterLocation() {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(world);
        if (w == null) return null;
        double cx = (minX + maxX) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        int y = w.getHighestBlockYAt((int) cx, (int) cz) + 1;
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    /** Called on a repeating task to pay all zone players. */
    public void rewardPlayersInZone() {
        if (playersInZone.isEmpty()) return;
        String currency = economy.getCurrency();
        for (UUID uid : new HashSet<>(playersInZone)) {
            Player p = plugin.getServer().getPlayer(uid);
            if (p == null || !p.isOnline()) {
                playersInZone.remove(uid);
                continue;
            }
            double amount = amountForPlayer(uid);
            economy.deposit(uid, amount);
            p.sendMessage(Component.text("+" + fmt(amount) + " " + currency + " (AFK Zone)")
                .color(NamedTextColor.GREEN));
        }
    }

    private double amountForPlayer(UUID uid) {
        String role = roles.getPlayerRole(uid);
        if (role != null) {
            double roleAmt = plugin.getConfig().getDouble("role-amounts." + role, -1);
            if (roleAmt >= 0) return roleAmt;
        }
        return plugin.getConfig().getDouble("amount", 10.0);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
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
