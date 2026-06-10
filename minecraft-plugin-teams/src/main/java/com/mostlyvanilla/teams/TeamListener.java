package com.mostlyvanilla.teams;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class TeamListener implements Listener {

    private final JavaPlugin plugin;
    private final TeamManager manager;

    public TeamListener(JavaPlugin plugin, TeamManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // Sync scoreboard entry when a player joins
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TeamData team = manager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return;
        manager.syncScoreboard(team);
    }

    // Team chat interception
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasTeamChat(player.getUniqueId())) return;
        TeamData team = manager.getTeamByPlayer(player.getUniqueId());
        if (team == null) { manager.setTeamChat(player.getUniqueId(), false); return; }
        event.setCancelled(true);
        Component msg = Component.text("[Team] ", NamedTextColor.GRAY)
            .append(Component.text(player.getName() + ": ").color(team.namedColor()))
            .append(event.message());
        for (UUID uuid : team.getMembers()) {
            Player m = Bukkit.getPlayer(uuid);
            if (m != null) m.sendMessage(msg);
        }
    }

    // GUI clicks
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeamGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        TeamData team = holder.getTeam();
        boolean refresh = TeamGui.handleClick(player, team, manager, event.getRawSlot());
        if (refresh && player.isOnline() && !player.isDead()) {
            plugin.getServer().getScheduler().runTask(plugin,
                () -> TeamGui.open(player, team, manager));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TeamGuiHolder) event.setCancelled(true);
    }
}
