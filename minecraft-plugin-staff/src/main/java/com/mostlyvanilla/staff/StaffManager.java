package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Stream;
import java.util.UUID;

public class StaffManager {

    private final JavaPlugin   plugin;
    private final MuteManager  muteManager;

    private final Set<UUID> frozenPlayers = new HashSet<>();

    private final Map<Inventory, UUID>        staffPanels   = new HashMap<>();
    private final Map<Inventory, ConfirmData> confirmPanels = new HashMap<>();

    // Staff panel (36 slots, 4 rows)
    // Row 0: border + head
    // Row 1: border + 7 action buttons + border
    // Row 2: border
    // Row 3: border + close button
    private static final int S_HEAD   = 4;
    private static final int S_KICK   = 10;
    private static final int S_BAN    = 11;
    private static final int S_MUTE   = 12;
    private static final int S_FREEZE = 13;
    private static final int S_SPEC   = 14;
    private static final int S_TP     = 15;
    private static final int S_WARN   = 16;
    private static final int S_CLOSE  = 31;

    // Confirm panel (27 slots, 3 rows)
    // Row 0: border + action description
    // Row 1: border + confirm + spacer + cancel
    // Row 2: border
    private static final int C_ACTION  = 4;
    private static final int C_CONFIRM = 11;
    private static final int C_CANCEL  = 15;

    public StaffManager(JavaPlugin plugin, MuteManager muteManager) {
        this.plugin      = plugin;
        this.muteManager = muteManager;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public boolean isMuted(UUID uuid)  { return muteManager.isMuted(uuid); }
    public boolean isFrozen(UUID uuid) { return frozenPlayers.contains(uuid); }

    // ── GUI identification ────────────────────────────────────────────────────

    public boolean isStaffPanel(Inventory inv)   { return staffPanels.containsKey(inv); }
    public boolean isConfirmPanel(Inventory inv) { return confirmPanels.containsKey(inv); }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void openStaffPanel(Player staff, OfflinePlayer target) {
        Inventory inv = buildStaffPanel(staff, target);
        staffPanels.put(inv, target.getUniqueId());
        staff.openInventory(inv);
    }

    private void openConfirmPanel(Player staff, UUID targetUuid, String targetName, StaffAction action) {
        Inventory inv = buildConfirmPanel(targetName, action);
        confirmPanels.put(inv, new ConfirmData(staff.getUniqueId(), targetUuid, targetName, action));
        staff.openInventory(inv);
    }

    // ── Build staff panel ─────────────────────────────────────────────────────

    private Inventory buildStaffPanel(Player staff, OfflinePlayer target) {
        String name = target.getName() != null ? target.getName() : "Unknown";

        Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("Staff Panel  »  ", NamedTextColor.DARK_GRAY)
                .append(Component.text(name, NamedTextColor.YELLOW, TextDecoration.BOLD)));

        ItemStack fill = filler();
        for (int i = 0; i < 36; i++) inv.setItem(i, fill);

        // Player head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(target);
        sm.displayName(Component.text(name, NamedTextColor.YELLOW, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        Player online = target.getPlayer();
        sm.lore(List.of(
            Component.text(online != null ? "● Online" : "● Offline",
                online != null ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        ));
        head.setItemMeta(sm);
        inv.setItem(S_HEAD, head);

        boolean banned  = target.isBanned();
        boolean muted   = muteManager.isMuted(target.getUniqueId());
        boolean frozen  = frozenPlayers.contains(target.getUniqueId());
        boolean canBan  = canBan(staff.getUniqueId());

        inv.setItem(S_KICK,   btn(Material.BARRIER,            "§cKick",      "§7Remove player from the server"));
        inv.setItem(S_BAN,    !canBan
            ? btn(Material.GRAY_STAINED_GLASS, "§7Ban / Unban", "§8No permission")
            : banned
                ? btn(Material.LIME_CONCRETE,  "§aUnban",    "§7Lift the permanent ban")
                : btn(Material.TNT,            "§4Ban",      "§7Permanently ban this player"));
        inv.setItem(S_MUTE,   muted
            ? btn(Material.PAPER,          "§aUnmute",   "§7Allow player to chat again")
            : btn(Material.NAME_TAG,       "§eMute",     "§7Silence player in chat"));
        inv.setItem(S_FREEZE, frozen
            ? btn(Material.BLUE_STAINED_GLASS, "§aUnfreeze", "§7Allow player to move again")
            : btn(Material.PACKED_ICE,     "§bFreeze",   "§7Lock player in place"));
        inv.setItem(S_SPEC,   btn(Material.ENDER_EYE,  "§9Spectate", "§7Switch to spectator", "§7and teleport to player"));
        inv.setItem(S_TP,     btn(Material.ENDER_PEARL, "§dTeleport", "§7Teleport to player"));
        inv.setItem(S_WARN,   btn(Material.BOOK,        "§6Warn",     "§7Send an official warning"));
        inv.setItem(S_CLOSE,  plain(Material.RED_STAINED_GLASS_PANE, "§cClose"));

        return inv;
    }

    // ── Build confirm panel ───────────────────────────────────────────────────

    private Inventory buildConfirmPanel(String name, StaffAction action) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("Confirm Action", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        ItemStack fill = filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        inv.setItem(C_ACTION,  confirmDesc(name, action));
        inv.setItem(C_CONFIRM, plain(Material.LIME_STAINED_GLASS_PANE, "§a§l✔  Confirm"));
        inv.setItem(C_CANCEL,  plain(Material.RED_STAINED_GLASS_PANE,  "§c§l✘  Cancel"));
        return inv;
    }

    private ItemStack confirmDesc(String name, StaffAction action) {
        return switch (action) {
            case KICK     -> btn(Material.BARRIER,            "§cKick §f"        + name, "§7This will remove the player from", "§7the server.");
            case BAN      -> btn(Material.TNT,                "§4Ban §f"         + name, "§7This will permanently ban", "§7this player.");
            case UNBAN    -> btn(Material.LIME_CONCRETE,      "§aUnban §f"       + name, "§7This will lift the ban", "§7from this player.");
            case MUTE     -> btn(Material.NAME_TAG,           "§eMute §f"        + name, "§7This will prevent the player", "§7from chatting.");
            case UNMUTE   -> btn(Material.PAPER,              "§aUnmute §f"      + name, "§7This will allow the player", "§7to chat again.");
            case FREEZE   -> btn(Material.PACKED_ICE,         "§bFreeze §f"      + name, "§7This will lock the player", "§7in place.");
            case UNFREEZE -> btn(Material.BLUE_STAINED_GLASS, "§aUnfreeze §f"    + name, "§7This will allow the player", "§7to move again.");
            case SPECTATE -> btn(Material.ENDER_EYE,          "§9Spectate §f"    + name, "§7You will switch to spectator", "§7mode and teleport to them.");
            case TELEPORT -> btn(Material.ENDER_PEARL,        "§dTeleport to §f" + name, "§7You will teleport to this player.");
            case WARN     -> btn(Material.BOOK,               "§6Warn §f"        + name, "§7Sends an official warning", "§7message to the player.");
        };
    }

    // ── Click handlers ────────────────────────────────────────────────────────

    public void handleStaffClick(Player staff, Inventory inv, int slot) {
        UUID targetUuid = staffPanels.get(inv);
        if (targetUuid == null) return;

        if (slot == S_CLOSE) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) staff::closeInventory);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String name = target.getName() != null ? target.getName() : "Unknown";

        StaffAction action = switch (slot) {
            case S_KICK   -> StaffAction.KICK;
            case S_BAN    -> target.isBanned()           ? StaffAction.UNBAN    : StaffAction.BAN;
            case S_MUTE   -> isMuted(targetUuid)         ? StaffAction.UNMUTE   : StaffAction.MUTE;
            case S_FREEZE -> isFrozen(targetUuid)        ? StaffAction.UNFREEZE : StaffAction.FREEZE;
            case S_SPEC   -> StaffAction.SPECTATE;
            case S_TP     -> StaffAction.TELEPORT;
            case S_WARN   -> StaffAction.WARN;
            default       -> null;
        };

        if (action == null) return;

        StaffAction finalAction = action;
        Bukkit.getScheduler().runTask(plugin, () ->
            openConfirmPanel(staff, targetUuid, name, finalAction));
    }

    public void handleConfirmClick(Player staff, Inventory inv, int slot) {
        ConfirmData data = confirmPanels.get(inv);
        if (data == null) return;

        if (slot == C_CANCEL) {
            Bukkit.getScheduler().runTask(plugin, () ->
                openStaffPanel(staff, Bukkit.getOfflinePlayer(data.targetUuid)));
            return;
        }

        if (slot == C_CONFIRM) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                executeAction(staff, data);
                staff.closeInventory();
            });
        }
    }

    public void onInventoryClose(Inventory inv) {
        staffPanels.remove(inv);
        confirmPanels.remove(inv);
    }

    // ── Execute ───────────────────────────────────────────────────────────────

    private void executeAction(Player staff, ConfirmData data) {
        Player target = Bukkit.getPlayer(data.targetUuid);
        String name   = data.targetName;

        switch (data.action) {
            case KICK -> {
                if (target == null) { err(staff, name + " is not online."); return; }
                target.kick(Component.text("You have been kicked by " + staff.getName() + ".", NamedTextColor.RED));
                ok(staff, "Kicked " + name + ".");
            }
            case BAN -> {
                if (!canBan(staff.getUniqueId())) { err(staff, "You don't have permission to ban players."); return; }
                if (name.equals("Unknown")) { err(staff, "Cannot ban: player name is unknown."); return; }
                Bukkit.getBanList(BanList.Type.NAME)
                    .addBan(name, "Banned by " + staff.getName(), null, staff.getName());
                if (target != null)
                    target.kick(Component.text("You have been banned.", NamedTextColor.DARK_RED));
                ok(staff, "Banned " + name + ".");
            }
            case UNBAN -> {
                if (!canBan(staff.getUniqueId())) { err(staff, "You don't have permission to unban players."); return; }
                Bukkit.getBanList(BanList.Type.NAME).pardon(name);
                ok(staff, "Unbanned " + name + ".");
            }
            case MUTE -> {
                muteManager.mute(data.targetUuid, -1L, "Muted via staff panel.", staff.getName());
                if (target != null)
                    target.sendMessage(Component.text("You have been muted.", NamedTextColor.RED));
                ok(staff, "Muted " + name + ".");
            }
            case UNMUTE -> {
                muteManager.unmute(data.targetUuid);
                if (target != null)
                    target.sendMessage(Component.text("You have been unmuted.", NamedTextColor.GREEN));
                ok(staff, "Unmuted " + name + ".");
            }
            case FREEZE -> {
                if (target == null) { err(staff, name + " is not online."); return; }
                frozenPlayers.add(data.targetUuid);
                target.sendMessage(Component.text("You have been frozen by " + staff.getName() + ".", NamedTextColor.AQUA));
                ok(staff, "Frozen " + name + ".");
            }
            case UNFREEZE -> {
                frozenPlayers.remove(data.targetUuid);
                if (target != null)
                    target.sendMessage(Component.text("You have been unfrozen.", NamedTextColor.GREEN));
                ok(staff, "Unfrozen " + name + ".");
            }
            case SPECTATE -> {
                if (target == null) { err(staff, name + " is not online."); return; }
                staff.setGameMode(GameMode.SPECTATOR);
                staff.teleportAsync(target.getLocation()).thenAccept(success -> {
                    if (success) ok(staff, "Now spectating " + name + ". Use /gms to return to survival.");
                    else err(staff, "Teleport to " + name + " was blocked by another plugin.");
                });
            }
            case TELEPORT -> {
                if (target == null) { err(staff, name + " is not online."); return; }
                staff.teleportAsync(target.getLocation()).thenAccept(success -> {
                    if (success) ok(staff, "Teleported to " + name + ".");
                    else err(staff, "Teleport to " + name + " was blocked by another plugin.");
                });
            }
            case WARN -> {
                if (target == null) { err(staff, name + " is not online."); return; }
                target.sendMessage(
                    Component.text("⚠ ", NamedTextColor.YELLOW)
                        .append(Component.text("Warning", NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" from " + staff.getName() + ": ", NamedTextColor.YELLOW))
                        .append(Component.text("Please follow the server rules.", NamedTextColor.WHITE))
                );
                ok(staff, "Warned " + name + ".");
            }
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack plain(Material mat, String legacyName) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(leg(legacyName));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack btn(Material mat, String legacyName, String... loreLines) {
        ItemStack item = plain(mat, legacyName);
        ItemMeta meta = item.getItemMeta();
        meta.lore(Stream.of(loreLines).map(StaffManager::leg).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection()
            .deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private boolean canBan(UUID uuid) {
        Plugin rolesPlugin = Bukkit.getPluginManager().getPlugin("MostlyVanillaRoles");
        if (rolesPlugin == null) return false;
        try {
            Object rm = rolesPlugin.getClass().getMethod("getRoleManager").invoke(rolesPlugin);
            return (boolean) rm.getClass().getMethod("canUseBan", UUID.class).invoke(rm, uuid);
        } catch (Exception e) { return false; }
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    private void ok(Player p, String msg) {
        p.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
            .append(Component.text(msg, NamedTextColor.WHITE)));
    }

    private void err(Player p, String msg) {
        p.sendMessage(Component.text("✘ ", NamedTextColor.RED)
            .append(Component.text(msg, NamedTextColor.RED)));
    }
}
