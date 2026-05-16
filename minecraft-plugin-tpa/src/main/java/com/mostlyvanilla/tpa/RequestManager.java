package com.mostlyvanilla.tpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RequestManager {

    // Keyed by requester UUID — waiting for requester to click Confirm
    private final Map<UUID, TpaRequest> pendingConfirm = new HashMap<>();
    // Keyed by target UUID — waiting for target to accept/deny
    private final Map<UUID, TpaRequest> pendingRequest = new HashMap<>();

    private final JavaPlugin plugin;
    private final long expireMs;
    private final int countdownSeconds;
    private final boolean cancelOnMove;
    private final double maxMoveDistance;

    private static final Component BAR = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);

    public RequestManager(JavaPlugin plugin, long expireSeconds, int countdownSeconds,
                          boolean cancelOnMove, double maxMoveDistance) {
        this.plugin          = plugin;
        this.expireMs        = expireSeconds * 1000L;
        this.countdownSeconds = countdownSeconds;
        this.cancelOnMove    = cancelOnMove;
        this.maxMoveDistance = maxMoveDistance;
    }

    // ── Initiate ─────────────────────────────────────────────────────────────

    public void initiateRequest(Player requester, Player target, TpaRequest.Type type) {
        pendingConfirm.put(requester.getUniqueId(),
            new TpaRequest(requester.getUniqueId(), target.getUniqueId(), type, expireMs));

        String action = type == TpaRequest.Type.TPA
            ? "Teleport to §e" + target.getName() + "§r?"
            : "Request §e" + target.getName() + "§r to teleport to you?";

        requester.sendMessage(Component.empty());
        requester.sendMessage(BAR);
        requester.sendMessage(Component.text("  " + action, NamedTextColor.WHITE));
        requester.sendMessage(Component.empty());
        requester.sendMessage(
            Component.text("  ")
                .append(Component.text(" ✔ Confirm ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/tpaconfirm"))
                    .hoverEvent(HoverEvent.showText(Component.text("Send teleport request", NamedTextColor.GREEN))))
                .append(Component.text("   "))
                .append(Component.text(" ✘ Cancel ", NamedTextColor.RED, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/tpacancel"))
                    .hoverEvent(HoverEvent.showText(Component.text("Cancel", NamedTextColor.RED))))
        );
        requester.sendMessage(BAR);
        requester.sendMessage(Component.empty());
    }

    // ── Confirm (requester clicks Confirm) ───────────────────────────────────

    public void confirm(Player requester) {
        TpaRequest req = pendingConfirm.remove(requester.getUniqueId());
        if (req == null || req.isExpired()) {
            requester.sendMessage(Component.text("No pending confirmation.", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(req.getTarget());
        if (target == null) {
            requester.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
            return;
        }

        pendingRequest.put(target.getUniqueId(), req);

        String desc = req.getType() == TpaRequest.Type.TPA
            ? "§e" + requester.getName() + "§r wants to teleport to you."
            : "§e" + requester.getName() + "§r wants you to teleport to them.";

        target.sendMessage(Component.empty());
        target.sendMessage(BAR);
        target.sendMessage(Component.text("  " + desc, NamedTextColor.WHITE));
        target.sendMessage(Component.empty());
        target.sendMessage(
            Component.text("  ")
                .append(Component.text(" ✔ Accept ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/tpaccept"))
                    .hoverEvent(HoverEvent.showText(Component.text("Accept teleport request", NamedTextColor.GREEN))))
                .append(Component.text("   "))
                .append(Component.text(" ✘ Deny ", NamedTextColor.RED, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/tpdeny"))
                    .hoverEvent(HoverEvent.showText(Component.text("Deny teleport request", NamedTextColor.RED))))
        );
        target.sendMessage(BAR);
        target.sendMessage(Component.empty());

        requester.sendMessage(Component.text("Request sent to ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.YELLOW))
            .append(Component.text(".", NamedTextColor.GREEN)));
    }

    // ── Accept (target clicks Accept) ────────────────────────────────────────

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
            if (cancelOnMove) {
                mover.sendMessage(Component.text("Don't move! Teleporting in ", NamedTextColor.YELLOW)
                    .append(Component.text(countdownSeconds + "s", NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.YELLOW)));
            } else {
                mover.sendMessage(Component.text("Teleporting in ", NamedTextColor.YELLOW)
                    .append(Component.text(countdownSeconds + "s", NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.YELLOW)));
            }
            new CountdownTask(mover, dest, countdownSeconds, cancelOnMove, maxMoveDistance)
                .runTaskTimer(plugin, 0L, 20L);
        }
    }

    // ── Deny (target clicks Deny) ─────────────────────────────────────────────

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

    // ── Cancel (requester cancels) ───────────────────────────────────────────

    public void cancel(Player requester) {
        boolean removed = pendingConfirm.remove(requester.getUniqueId()) != null;

        // Find and remove any pending request this player sent
        UUID targetUuid = null;
        for (Map.Entry<UUID, TpaRequest> entry : pendingRequest.entrySet()) {
            if (entry.getValue().getRequester().equals(requester.getUniqueId())) {
                targetUuid = entry.getKey();
                break;
            }
        }
        if (targetUuid != null) {
            pendingRequest.remove(targetUuid);
            removed = true;
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                target.sendMessage(Component.text(requester.getName(), NamedTextColor.YELLOW)
                    .append(Component.text(" cancelled their teleport request.", NamedTextColor.YELLOW)));
            }
        }

        requester.sendMessage(removed
            ? Component.text("Teleport request cancelled.", NamedTextColor.YELLOW)
            : Component.text("No active teleport request to cancel.", NamedTextColor.RED));
    }

    // ── Cleanup expired ──────────────────────────────────────────────────────

    public void cleanupExpired() {
        pendingConfirm.entrySet().removeIf(e -> {
            if (!e.getValue().isExpired()) return false;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.sendMessage(Component.text("Your teleport confirmation expired.", NamedTextColor.RED));
            return true;
        });

        pendingRequest.entrySet().removeIf(e -> {
            if (!e.getValue().isExpired()) return false;
            Player target = Bukkit.getPlayer(e.getKey());
            if (target != null) target.sendMessage(Component.text("A teleport request expired.", NamedTextColor.RED));
            Player requester = Bukkit.getPlayer(e.getValue().getRequester());
            if (requester != null) requester.sendMessage(
                Component.text(target != null ? target.getName() : "The player", NamedTextColor.YELLOW)
                    .append(Component.text(" did not respond to your teleport request.", NamedTextColor.RED)));
            return true;
        });
    }
}
