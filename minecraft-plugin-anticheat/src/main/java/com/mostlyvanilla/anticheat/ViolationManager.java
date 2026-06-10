package com.mostlyvanilla.anticheat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;

public class ViolationManager {

    private static final Set<String> SUPER_SUS_CHECKS =
            Set.of("XRAY", "FLY", "KILLAURA", "REACH", "AUTOTOTEM", "NOFALL", "JUMP_RESET");

    private final MostlyVanillaAnticheat plugin;

    public ViolationManager(MostlyVanillaAnticheat plugin) {
        this.plugin = plugin;
    }

    public void flag(Player player, String check, String detail, int amount) {
        PlayerData data = plugin.getData(player.getUniqueId());
        int total = data.addViolation(check, amount);

        if (plugin.getConfig().getBoolean("alerts.enabled", true)) {
            long cooldownMs = plugin.getConfig().getLong("alerts.cooldown-seconds", 10) * 1000L;
            long now = System.currentTimeMillis();
            long last = data.alertCooldowns.getOrDefault(check, 0L);
            if (now - last >= cooldownMs) {
                data.alertCooldowns.put(check, now);
                broadcastAdminAlert(player, check, detail, total);

                if (SUPER_SUS_CHECKS.contains(check)) {
                    broadcastSusAlert(player, check, detail, total);
                }
            }
        }

        if (plugin.getConfig().getBoolean("punishments.log-to-console", true)) {
            plugin.getLogger().warning("[AC] " + player.getName() + " flagged " + check + " (" + detail + ") vl=" + total);
        }

        punish(player, data, check);
        punishPerCheck(player, check, data.getViolations(check));
    }

    private void broadcastAdminAlert(Player player, String check, String detail, int vl) {
        Component msg = Component.text("[AC] ", NamedTextColor.RED)
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" flagged ", NamedTextColor.GRAY))
                .append(Component.text(check, NamedTextColor.RED))
                .append(Component.text(" | " + detail + " | vl=" + vl, NamedTextColor.DARK_GRAY));

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("mostlyvanilla.ac.admin")) {
                admin.sendMessage(msg);
            }
        }
    }

    private void broadcastSusAlert(Player player, String check, String detail, int vl) {
        RolesBridge bridge = plugin.getRolesBridge();
        Component msg = Component.text("[Sus] ", NamedTextColor.GOLD)
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" is acting suspicious", NamedTextColor.GRAY))
                .append(Component.text(" [" + check + "]", NamedTextColor.RED))
                .append(Component.text(" | vl=" + vl + " — use /sus to inspect", NamedTextColor.DARK_GRAY));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("mostlyvanilla.ac.admin")) continue;
            if (bridge.canNotifySus(online)) {
                online.sendMessage(msg);
            }
        }
    }

    private static final Set<String> FLAG_ONLY = Set.of("XRAY");

    // Global cumulative VL threshold — catches checks that don't have their own per-check kick-vl.
    private void punish(Player player, PlayerData data, String check) {
        if (FLAG_ONLY.contains(check)) return;
        int total = data.totalViolations();
        int kickThreshold = plugin.getConfig().getInt("punishments.kick-threshold", 80);

        if (total >= kickThreshold) {
            plugin.getPunishmentManager().punish(player, check);
        }
    }

    // Per-check threshold — kicks/bans fast on obvious PvP hacks without waiting for global VL to climb.
    private void punishPerCheck(Player player, String check, int checkVl) {
        int kickVl = switch (check) {
            case "REACH"      -> plugin.getConfig().getInt("checks.reach.kick-vl",       15);
            case "KILLAURA"   -> plugin.getConfig().getInt("checks.killaura.kick-vl",    20);
            case "AUTOTOTEM"  -> plugin.getConfig().getInt("checks.autototem.kick-vl",   10);
            case "NOFALL"     -> plugin.getConfig().getInt("checks.nofall.kick-vl",      20);
            case "JUMP_RESET" -> plugin.getConfig().getInt("checks.jump_reset.kick-vl",  30);
            default           -> -1;
        };
        if (kickVl < 0 || checkVl < kickVl) return;

        plugin.getPunishmentManager().punish(player, check);
    }
}
