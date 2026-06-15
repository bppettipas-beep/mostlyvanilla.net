package com.mostlyvanilla.auctionhouse;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;

public class AhListener implements Listener {

    private final MostlyVanillaAuctionHouse plugin;
    private final AuctionManager            auctionManager;
    private final OrderManager              orderManager;

    public AhListener(MostlyVanillaAuctionHouse plugin,
                      AuctionManager auctionManager,
                      OrderManager orderManager) {
        this.plugin         = plugin;
        this.auctionManager = auctionManager;
        this.orderManager   = orderManager;
    }

    // ── Inventory events ───────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();

        if (auctionManager.isAhGui(top)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top)
                auctionManager.handleClick(player, top, event.getSlot());
            return;
        }

        if (orderManager.isPickerGui(top)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top)
                orderManager.handlePickerClick(player, top, event.getSlot());
            return;
        }

        if (orderManager.isCurrencyPickerGui(top)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top)
                orderManager.handleCurrencyClick(player, top, event.getSlot());
            return;
        }

        if (orderManager.isOrdersGui(top)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top)
                orderManager.handleClick(player, top, event.getSlot());
            return;
        }

        if (orderManager.isClaimGui(top)) {
            // Allow free interaction in item slots (0-44); only lock the bottom row
            if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
                // Prevent shift-clicking items from player inventory into the claim GUI
                event.setCancelled(true);
                return;
            }
            if (event.getClickedInventory() == top && event.getSlot() >= 45) {
                event.setCancelled(true);
                orderManager.handleClaimClick(player, top, event.getSlot());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        auctionManager.onClose(inv);
        orderManager.onClose(inv);
        orderManager.onClaimClose(inv);
    }

    // ── Sign right-click — open AH or Orders GUI ───────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;

        // Prefer front face line 0; fall back to legacy getLine
        String line0;
        try {
            line0 = sign.getSide(org.bukkit.block.sign.Side.FRONT).getLine(0).trim();
        } catch (NoSuchMethodError | AbstractMethodError e) {
            line0 = sign.getLine(0).trim();
        }

        Player player = event.getPlayer();

        if (line0.equalsIgnoreCase("[orders]") || line0.equalsIgnoreCase("[order]")) {
            event.setCancelled(true);
            orderManager.openGui(player, OrderManager.GuiMode.ALL, 0);
        } else if (line0.equalsIgnoreCase("[ah]")) {
            event.setCancelled(true);
            auctionManager.openGui(player, AuctionManager.GuiMode.ALL, AuctionManager.SortMode.NEWEST, 0);
        }
    }

    // ── Sign placement — create order from sign ────────────────────────────────
    //
    //  Format:
    //    Line 1: [order]
    //    Line 2: item name   (e.g. IRON_ORE or "Iron Ore")
    //    Line 3: amount      (e.g. 64)
    //    Line 4: price each  (e.g. 1.5)
    //
    //  On success the sign is updated to show a summary.
    //  On failure the sign is removed and the item returned.

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
        String line0 = event.getLine(0);
        if (line0 == null || !line0.trim().equalsIgnoreCase("[order]")) return;

        Player player = event.getPlayer();
        String rawItem  = event.getLine(1) != null ? event.getLine(1).trim() : "";
        String rawAmt   = event.getLine(2) != null ? event.getLine(2).trim() : "";
        String rawPrice = event.getLine(3) != null ? event.getLine(3).trim() : "";

        Material mat = OrderManager.parseMaterial(rawItem);
        if (mat == null) {
            player.sendMessage(Component.text("[Orders] Unknown item: \"" + rawItem + "\"", NamedTextColor.RED));
            player.sendMessage(Component.text("Use a material name e.g. IRON_ORE or Iron Ore", NamedTextColor.GRAY));
            failSign(event);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(rawAmt);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Orders] Invalid amount: \"" + rawAmt + "\"", NamedTextColor.RED));
            failSign(event);
            return;
        }

        double priceEach;
        try {
            priceEach = Double.parseDouble(rawPrice);
            if (priceEach <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Orders] Invalid price: \"" + rawPrice + "\"", NamedTextColor.RED));
            failSign(event);
            return;
        }

        boolean success = orderManager.createOrderByMaterial(player, mat, amount, priceEach);
        if (success) {
            event.setLine(0, "[Order]");
            event.setLine(1, OrderManager.prettify(mat));
            event.setLine(2, amount + " @ " + AuctionManager.fmt(priceEach));
            event.setLine(3, player.getName());
        } else {
            failSign(event);
        }
    }

    /** Cancel the sign edit and break the sign block on the next tick so the player gets it back. */
    private void failSign(SignChangeEvent event) {
        event.setCancelled(true);
        Block block = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getState() instanceof Sign) block.breakNaturally();
        });
    }

    // ── Chat — capture amount+price after item picker selection ───────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!orderManager.hasPendingOrder(player.getUniqueId())) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> orderManager.handleChatInput(player, message));
    }

    // ── Pending deliveries on login ────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        orderManager.deliverPending(event.getPlayer());
    }
}
