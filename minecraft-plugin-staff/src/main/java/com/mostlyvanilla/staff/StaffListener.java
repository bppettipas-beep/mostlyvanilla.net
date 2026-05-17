package com.mostlyvanilla.staff;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class StaffListener implements Listener {

    private final StaffManager manager;
    private final WipeManager  wipeManager;
    private final MuteManager  muteManager;

    public StaffListener(StaffManager manager, WipeManager wipeManager, MuteManager muteManager) {
        this.manager     = manager;
        this.wipeManager = wipeManager;
        this.muteManager = muteManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Inventory top = e.getView().getTopInventory();

        if (manager.isStaffPanel(top) || manager.isConfirmPanel(top) || wipeManager.isWipePanel(top)) {
            e.setCancelled(true);
            // Only react to clicks inside the GUI, not the player's own inventory below
            if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;

            if (manager.isStaffPanel(top))         manager.handleStaffClick(player, top, e.getSlot());
            else if (manager.isConfirmPanel(top))  manager.handleConfirmClick(player, top, e.getSlot());
            else                                   wipeManager.handleClick(player, top, e.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        manager.onInventoryClose(e.getInventory());
        wipeManager.onInventoryClose(e.getInventory());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (!muteManager.isMuted(uuid)) return;
        e.setCancelled(true);
        MuteManager.MuteEntry mute = muteManager.getMute(uuid);
        Component msg = Component.text("You are muted and cannot chat.", NamedTextColor.RED);
        if (mute != null) {
            msg = msg
                .append(Component.newline())
                .append(Component.text("Reason: ", NamedTextColor.GRAY))
                .append(Component.text(mute.reason(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Expires: ", NamedTextColor.GRAY))
                .append(Component.text(MuteManager.formatRemaining(mute.expiresAt()), NamedTextColor.WHITE));
        }
        e.getPlayer().sendMessage(msg);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        if (!manager.isFrozen(e.getPlayer().getUniqueId())) return;
        Location from = e.getFrom();
        Location to   = e.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // Keep position but allow head rotation
            Location frozen = from.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            e.setTo(frozen);
        }
    }
}
