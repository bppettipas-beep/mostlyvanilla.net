package com.mostlyvanilla.combatlog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

public class CombatListener implements Listener {

    private static final Set<String> BLOCKED_CMDS = Set.of(
        "afk", "home", "sethome", "spawn", "tpa", "tpaccept", "tpdeny",
        "tpahere", "tpall", "tpcancel", "rtp", "tp", "teleport",
        "warp", "back", "leave", "tpr"
    );

    private final CombatManager manager;

    public CombatListener(CombatManager manager) {
        this.manager = manager;
    }

    // ── Tag players on hit ────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) return;
        manager.tag(attacker, victim);
    }

    // ── Kill on logout ────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;
        manager.clear(player.getUniqueId());
        player.setHealth(0);
        event.quitMessage(Component.text(player.getName() + " logged out during combat and died!", NamedTextColor.RED));
    }

    // ── Clear on natural death ────────────────────────────────────────────────

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.clear(event.getEntity().getUniqueId());
    }

    // ── Block teleport events ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                || cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't teleport while in combat!", NamedTextColor.RED));
        }
    }

    // ── Block TP commands with a clear message ────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;
        String msg = event.getMessage(); // "/command arg1 arg2"
        String cmd = msg.substring(1).split(" ")[0].toLowerCase();
        // strip plugin prefix (e.g. "essentials:home" -> "home")
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (BLOCKED_CMDS.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't teleport while in combat!", NamedTextColor.RED));
        }
    }
}
