package com.mostlyvanilla.tpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RequestManager {

    // Keyed by target UUID — waiting for target to accept/deny via GUI
    private final Map<UUID, TpaRequest> pendingRequest = new HashMap<>();

    private final JavaPlugin plugin;
    private final long expireMs;
    private final int countdownSeconds;
    private final boolean cancelOnMove;
    private final double maxMoveDistance;
    private final SettingsBridge settingsBridge;

    public RequestManager(JavaPlugin plugin, long expireSeconds, int countdownSeconds,
                          boolean cancelOnMove, double maxMoveDistance) {
        this.plugin           = plugin;
        this.expireMs         = expireSeconds * 1000L;
        this.countdownSeconds = countdownSeconds;
        this.cancelOnMove     = cancelOnMove;
        this.maxMoveDistance  = maxMoveDistance;
        this.settingsBridge   = new SettingsBridge(plugin.getServer());
    }

    // ── Initiate ─────────────────────────────────────────────────────────────

    public void initiateRequest(Player requester, Player target, TpaRequest.Type type) {
        if (pendingRequest.containsKey(target.getUniqueId())) {
            requester.sendMessage(Component.text(target.getName() + " already has a pending teleport request.", NamedTextColor.RED));
            return;
        }

        // Auto-accept: bypass GUI and teleport immediately
        if (settingsBridge.isAutoAccept(target.getUniqueId())) {
            Player mover = type == TpaRequest.Type.TPA ? requester : target;
            Player dest  = type == TpaRequest.Type.TPA ? target   : requester;
            requester.sendMessage(Component.text(target.getName() + " has auto-accept on. Teleporting...", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Auto-accepted teleport request from " + requester.getName() + ".", NamedTextColor.GREEN));
            if (countdownSeconds <= 0) {
                mover.teleport(dest.getLocation());
            } else {
                String waitMsg = cancelOnMove ? "Don't move! Teleporting in " : "Teleporting in ";
                mover.sendMessage(Component.text(waitMsg, NamedTextColor.YELLOW)
                    .append(Component.text(countdownSeconds + "s", NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.YELLOW)));
                new CountdownTask(mover, dest, countdownSeconds, cancelOnMove, maxMoveDistance)
                    .runTaskTimer(plugin, 0L, 5L);
            }
            return;
        }

        TpaRequest req = new TpaRequest(requester.getUniqueId(), target.getUniqueId(), type, expireMs);
        pendingRequest.put(target.getUniqueId(), req);

        String sentMsg = type == TpaRequest.Type.TPA
            ? "Teleport request sent to " + target.getName() + "."
            : "Requested " + target.getName() + " to teleport to you.";
        requester.sendMessage(Component.text(sentMsg, NamedTextColor.GREEN));
        requester.sendMessage(Component.text("Use /tpacancel to cancel.", NamedTextColor.GRAY));

        target.openInventory(new TpaGui(req, requester.getName()).getInventory());
    }

    // ── Accept (target clicks Accept or types /tpaccept) ─────────────────────

    public void accept(Player target) {
        TpaRequest req = pendingRequest.remove(target.getUniqueId());
        if (req == null || req.isExpired()) {
            target.sendMessage(Component.text("No pending teleport request.", NamedTextColor.RED));
            return;
        }

        Player requester = Bukkit.getPlayer(req.getRequester());
        if (requester == null) {
            target.sendMessage(Component.text("The requester is no longer online.", NamedTextColor.RED));
            return;
        }

        Player mover = req.getType() == TpaRequest.Type.TPA ? requester : target;
        Player dest  = req.getType() == TpaRequest.Type.TPA ? target   : requester;

        target.sendMessage(Component.text("Request accepted.", NamedTextColor.GREEN));
        requester.sendMessage(Component.text(target.getName(), NamedTextColor.YELLOW)
            .append(Component.text(" accepted your request.", NamedTextColor.GREEN)));

        if (countdownSeconds <= 0) {
            mover.teleport(dest.getLocation());
            mover.sendMessage(Component.text("Teleported to ", NamedTextColor.GREEN)
                .append(Component.text(dest.getName(), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
            dest.sendMessage(Component.text(mover.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" teleported to you.", NamedTextColor.GREEN)));
        } else {
            String waitMsg = cancelOnMove ? "Don't move! Teleporting in " : "Teleporting in ";
            mover.sendMessage(Component.text(waitMsg, NamedTextColor.YELLOW)
                .append(Component.text(countdownSeconds + "s", NamedTextColor.WHITE))
                .append(Component.text("...", NamedTextColor.YELLOW)));
            new CountdownTask(mover, dest, countdownSeconds, cancelOnMove, maxMoveDistance)
                .runTaskTimer(plugin, 0L, 5L);
        }
    }

    // ── Deny (target clicks Deny or types /tpdeny) ───────────────────────────

    public void deny(Player target) {
        TpaRequest req = pendingRequest.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text("No pending teleport request.", NamedTextColor.RED));
            return;
        }

        target.sendMessage(Component.text("Request denied.", NamedTextColor.RED));
        Player requester = Bukkit.getPlayer(req.getRequester());
        if (requester != null) {
            requester.sendMessage(Component.text(target.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" denied your teleport request.", NamedTextColor.RED)));
        }
    }

    // ── Deny (silent — used when target closes the GUI without clicking) ──────

    public void denyQuiet(Player target) {
        TpaRequest req = pendingRequest.remove(target.getUniqueId());
        if (req == null) return;
        Player requester = Bukkit.getPlayer(req.getRequester());
        if (requester != null) {
            requester.sendMessage(Component.text(target.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" declined your teleport request.", NamedTextColor.RED)));
        }
    }

    // ── Cancel (requester types /tpacancel) ──────────────────────────────────

    public void cancel(Player requester) {
        UUID targetUuid = null;
        for (Map.Entry<UUID, TpaRequest> entry : pendingRequest.entrySet()) {
            if (entry.getValue().getRequester().equals(requester.getUniqueId())) {
                targetUuid = entry.getKey();
                break;
            }
        }

        if (targetUuid == null) {
            requester.sendMessage(Component.text("No active teleport request to cancel.", NamedTextColor.RED));
            return;
        }

        pendingRequest.remove(targetUuid);
        requester.sendMessage(Component.text("Teleport request cancelled.", NamedTextColor.YELLOW));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(Component.text(requester.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" cancelled their teleport request.", NamedTextColor.YELLOW)));
            // Close after removing from pendingRequest so the close event's denyQuiet sees null and exits silently.
            target.closeInventory();
        }
    }

    // ── Cleanup expired ──────────────────────────────────────────────────────

    public void cleanupExpired() {
        // Collect first to avoid ConcurrentModificationException when closeInventory fires close events.
        List<Map.Entry<UUID, TpaRequest>> expired = new ArrayList<>();
        for (Map.Entry<UUID, TpaRequest> e : pendingRequest.entrySet()) {
            if (e.getValue().isExpired()) expired.add(e);
        }

        for (Map.Entry<UUID, TpaRequest> e : expired) {
            pendingRequest.remove(e.getKey());

            Player target = Bukkit.getPlayer(e.getKey());
            if (target != null) {
                // Remove from map before closing so denyQuiet sees null and is silent.
                target.closeInventory();
                target.sendMessage(Component.text("A teleport request expired.", NamedTextColor.RED));
            }

            Player requester = Bukkit.getPlayer(e.getValue().getRequester());
            if (requester != null) {
                requester.sendMessage(Component.text(target != null ? target.getName() : "The player", NamedTextColor.YELLOW)
                    .append(Component.text(" did not respond to your teleport request.", NamedTextColor.RED)));
            }
        }
    }
}
