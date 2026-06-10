package com.mostlyvanilla.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamGui {

    // Slot constants
    private static final int SLOT_INFO    = 4;
    private static final int SLOT_FF      = 18;
    private static final int SLOT_OPEN    = 19;
    private static final int SLOT_HOME    = 20;
    private static final int SLOT_SETHOME = 21;
    private static final int SLOT_CHAT    = 22;
    private static final int SLOT_COLOR   = 23;
    private static final int SLOT_ACTION  = 31; // leave / disband

    /** Opens the team GUI for a player. */
    public static void open(Player viewer, TeamData team, TeamManager manager) {
        TeamGuiHolder holder = new TeamGuiHolder(team);
        Inventory inv = Bukkit.createInventory(holder, 36,
            Component.text("⚑ " + team.getName()).color(team.namedColor())
                .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack glass = glass();
        for (int i = 0; i < 36; i++) {
            // Border: row 0 (0-8) and row 3 (27-35)
            if (i < 9 || i >= 27) inv.setItem(i, glass);
        }

        // Team info (slot 4)
        inv.setItem(SLOT_INFO, teamInfoItem(team));

        // Member heads (row 1: slots 9-17)
        List<UUID> members = team.getMembers();
        boolean isLeader = viewer.getUniqueId().equals(team.getLeader());
        int startCol = 9 + (9 - Math.min(members.size(), 9)) / 2;
        for (int i = 0; i < Math.min(members.size(), 9); i++) {
            UUID uuid = members.get(i);
            inv.setItem(startCol + i, memberHead(uuid, team, isLeader));
        }

        // Settings (row 2: 18-26)
        inv.setItem(SLOT_FF,      ffItem(team, isLeader));
        inv.setItem(SLOT_OPEN,    openItem(team, isLeader));
        inv.setItem(SLOT_HOME,    homeItem(team));
        inv.setItem(SLOT_SETHOME, setHomeItem(isLeader));
        inv.setItem(SLOT_CHAT,    chatItem(manager.hasTeamChat(viewer.getUniqueId())));
        inv.setItem(SLOT_COLOR,   colorItem(team, isLeader));
        for (int i = 24; i <= 26; i++) inv.setItem(i, glass);

        // Action (row 3 middle)
        inv.setItem(SLOT_ACTION, actionItem(team, viewer));

        viewer.openInventory(inv);
    }

    /** Handles a click inside the team GUI. Returns true if the GUI should be refreshed. */
    public static boolean handleClick(Player viewer, TeamData team, TeamManager manager, int slot) {
        boolean isLeader = viewer.getUniqueId().equals(team.getLeader());

        if (slot == SLOT_FF && isLeader) {
            team.setFriendlyFire(!team.isFriendlyFire());
            manager.syncScoreboard(team);
            manager.save();
            viewer.sendMessage(Component.text("Friendly fire: " + (team.isFriendlyFire() ? "ON" : "OFF"),
                team.isFriendlyFire() ? NamedTextColor.RED : NamedTextColor.GREEN));
            return true;
        }
        if (slot == SLOT_OPEN && isLeader) {
            team.setOpen(!team.isOpen());
            manager.save();
            viewer.sendMessage(Component.text("Team is now " + (team.isOpen() ? "open" : "invite-only") + ".",
                team.isOpen() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            return true;
        }
        if (slot == SLOT_HOME) {
            if (team.getHome() == null) {
                viewer.sendMessage(Component.text("No team home set.", NamedTextColor.RED));
                viewer.closeInventory();
            } else {
                viewer.closeInventory();
                viewer.teleport(team.getHome());
                viewer.sendMessage(Component.text("Teleported to team home.", NamedTextColor.GREEN));
            }
            return false;
        }
        if (slot == SLOT_SETHOME && isLeader) {
            team.setHome(viewer.getLocation());
            manager.save();
            viewer.sendMessage(Component.text("Team home set here.", NamedTextColor.GREEN));
            return true;
        }
        if (slot == SLOT_CHAT) {
            manager.toggleTeamChat(viewer.getUniqueId());
            viewer.sendMessage(Component.text("Team chat: " + (manager.hasTeamChat(viewer.getUniqueId()) ? "ON" : "OFF"),
                manager.hasTeamChat(viewer.getUniqueId()) ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            return true;
        }
        if (slot == SLOT_COLOR && isLeader) {
            team.cycleColor();
            manager.syncScoreboard(team);
            manager.save();
            viewer.sendMessage(Component.text("Team color: " + team.getColor()).color(team.namedColor()));
            return true;
        }
        if (slot == SLOT_ACTION) {
            viewer.closeInventory();
            if (isLeader) {
                manager.disbandTeam(team);
            } else {
                String name = viewer.getName();
                manager.removeMember(team, viewer.getUniqueId());
                viewer.sendMessage(Component.text("You left the team.", NamedTextColor.YELLOW));
                // Notify remaining members
                for (UUID uid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) p.sendMessage(
                        Component.text(name + " left the team.", NamedTextColor.GRAY));
                }
            }
            return false;
        }
        // Check if clicked a member head (row 1: slots 9-17)
        if (slot >= 9 && slot <= 17 && isLeader) {
            List<UUID> members = team.getMembers();
            int startCol = 9 + (9 - Math.min(members.size(), 9)) / 2;
            int idx = slot - startCol;
            if (idx >= 0 && idx < members.size()) {
                UUID target = members.get(idx);
                if (!target.equals(team.getLeader())) {
                    String targetName = Bukkit.getOfflinePlayer(target).getName();
                    manager.removeMember(team, target);
                    viewer.sendMessage(Component.text("Kicked " + targetName + " from the team.", NamedTextColor.YELLOW));
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) tp.sendMessage(Component.text("You were kicked from " + team.getName() + ".", NamedTextColor.RED));
                    return true;
                }
            }
        }
        return false;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack glass() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.empty());
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack teamInfoItem(TeamData team) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("⚑ " + team.getName()).color(team.namedColor())
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        String leaderName = Bukkit.getOfflinePlayer(team.getLeader()).getName();
        lore.add(line("Leader: ", leaderName != null ? leaderName : "?"));
        lore.add(line("Members: ", team.getMembers().size() + ""));
        lore.add(line("Color: ", team.getColor()));
        lore.add(line("Friendly Fire: ", team.isFriendlyFire() ? "ON" : "OFF"));
        lore.add(line("Open: ", team.isOpen() ? "Yes" : "No"));
        m.lore(lore);
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack memberHead(UUID uuid, TeamData team, boolean viewerIsLeader) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) skull.getItemMeta();
        m.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        boolean isLeader = uuid.equals(team.getLeader());
        m.displayName(Component.text(name != null ? name : "?")
            .color(isLeader ? NamedTextColor.GOLD : NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(isLeader ? "★ Leader" : "Member").color(isLeader ? NamedTextColor.GOLD : NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        if (viewerIsLeader && !isLeader) {
            lore.add(Component.empty());
            lore.add(Component.text("Click to kick", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        m.lore(lore);
        skull.setItemMeta(m);
        return skull;
    }

    private static ItemStack ffItem(TeamData team, boolean isLeader) {
        Material mat = team.isFriendlyFire() ? Material.IRON_SWORD : Material.SHIELD;
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("Friendly Fire: " + (team.isFriendlyFire() ? "ON" : "OFF"))
            .color(team.isFriendlyFire() ? NamedTextColor.RED : NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(isLeader
            ? Component.text("Click to toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.text("Leader only", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack openItem(TeamData team, boolean isLeader) {
        Material mat = team.isOpen() ? Material.OAK_DOOR : Material.BARRIER;
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text(team.isOpen() ? "Open Team" : "Invite Only")
            .color(team.isOpen() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(isLeader
            ? Component.text("Click to toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.text("Leader only", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack homeItem(TeamData team) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("Team Home").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(team.getHome() == null
            ? Component.text("Not set", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.text("Click to teleport", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack setHomeItem(boolean isLeader) {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("Set Home Here").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(isLeader
            ? Component.text("Click to set home to your position", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.text("Leader only", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack chatItem(boolean active) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("Team Chat: " + (active ? "ON" : "OFF"))
            .color(active ? NamedTextColor.GREEN : NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Click to toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack colorItem(TeamData team, boolean isLeader) {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("Color: " + team.getColor()).color(team.namedColor())
            .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(isLeader
            ? Component.text("Click to cycle colors", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.text("Leader only", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack actionItem(TeamData team, Player viewer) {
        boolean isLeader = viewer.getUniqueId().equals(team.getLeader());
        if (isLeader) {
            ItemStack item = new ItemStack(Material.TNT);
            ItemMeta m = item.getItemMeta();
            m.displayName(Component.text("Disband Team").color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            m.lore(List.of(Component.text("This cannot be undone!", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(m);
            return item;
        } else {
            ItemStack item = new ItemStack(Material.OAK_DOOR);
            ItemMeta m = item.getItemMeta();
            m.displayName(Component.text("Leave Team").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(m);
            return item;
        }
    }

    private static Component line(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY)
            .append(Component.text(value, NamedTextColor.WHITE))
            .decoration(TextDecoration.ITALIC, false);
    }
}
