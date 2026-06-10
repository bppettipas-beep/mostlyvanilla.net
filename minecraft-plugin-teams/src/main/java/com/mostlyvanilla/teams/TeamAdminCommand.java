package com.mostlyvanilla.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class TeamAdminCommand implements CommandExecutor, TabCompleter {

    private final TeamManager manager;

    public TeamAdminCommand(TeamManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mostlyvanilla.teams.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                var all = manager.getAllTeams();
                if (all.isEmpty()) { sender.sendMessage(Component.text("No teams exist.", NamedTextColor.GRAY)); return true; }
                sender.sendMessage(Component.text("── All Teams ──", NamedTextColor.GOLD));
                for (TeamData t : all) {
                    String leaderName = Bukkit.getOfflinePlayer(t.getLeader()).getName();
                    sender.sendMessage(Component.text("  ").color(t.namedColor())
                        .append(Component.text(t.getName(), t.namedColor()))
                        .append(Component.text("  leader: " + leaderName + "  members: " + t.getMembers().size(), NamedTextColor.GRAY)));
                }
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /teamadmin info <name>", NamedTextColor.RED)); return true; }
                TeamData t = manager.getTeamByName(args[1]);
                if (t == null) { sender.sendMessage(Component.text("Team not found.", NamedTextColor.RED)); return true; }
                sender.sendMessage(Component.text("── " + t.getName() + " ──").color(t.namedColor()));
                sender.sendMessage(Component.text("  Leader:  " + Bukkit.getOfflinePlayer(t.getLeader()).getName(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  Members: " + t.getMembers().size(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  Color:   " + t.getColor(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  FF:      " + t.isFriendlyFire(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  Open:    " + t.isOpen(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  Home:    " + (t.getHome() != null ? "set" : "none"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  Players:", NamedTextColor.GRAY));
                for (var uid : t.getMembers()) {
                    String name = Bukkit.getOfflinePlayer(uid).getName();
                    boolean isLeader = uid.equals(t.getLeader());
                    sender.sendMessage(Component.text("    " + (isLeader ? "★ " : "") + name,
                        isLeader ? NamedTextColor.GOLD : NamedTextColor.WHITE));
                }
            }
            case "disband" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /teamadmin disband <name>", NamedTextColor.RED)); return true; }
                TeamData t = manager.getTeamByName(args[1]);
                if (t == null) { sender.sendMessage(Component.text("Team not found.", NamedTextColor.RED)); return true; }
                String name = t.getName();
                manager.disbandTeam(t);
                sender.sendMessage(Component.text("Team \"" + name + "\" disbanded.", NamedTextColor.GREEN));
            }
            case "kick" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /teamadmin kick <player>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(Component.text("Player not online.", NamedTextColor.RED)); return true; }
                TeamData t = manager.getTeamByPlayer(target.getUniqueId());
                if (t == null) { sender.sendMessage(Component.text("That player is not in a team.", NamedTextColor.RED)); return true; }
                manager.removeMember(t, target.getUniqueId());
                sender.sendMessage(Component.text("Removed " + target.getName() + " from " + t.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("An admin removed you from your team.", NamedTextColor.RED));
            }
            case "add" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /teamadmin add <player> <team>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(Component.text("Player not online.", NamedTextColor.RED)); return true; }
                if (manager.getTeamByPlayer(target.getUniqueId()) != null) { sender.sendMessage(Component.text("That player is already in a team.", NamedTextColor.RED)); return true; }
                TeamData t = manager.getTeamByName(args[2]);
                if (t == null) { sender.sendMessage(Component.text("Team not found.", NamedTextColor.RED)); return true; }
                if (t.getMembers().size() >= manager.maxTeamSize()) { sender.sendMessage(Component.text("Team is full.", NamedTextColor.RED)); return true; }
                manager.addMember(t, target);
                sender.sendMessage(Component.text("Added " + target.getName() + " to " + t.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("An admin added you to " + t.getName() + ".", NamedTextColor.YELLOW));
            }
            case "reload" -> {
                manager.load();
                sender.sendMessage(Component.text("Teams reloaded.", NamedTextColor.GREEN));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── /teamadmin ──", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  list              ", NamedTextColor.YELLOW).append(Component.text("— List all teams", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  info <team>       ", NamedTextColor.YELLOW).append(Component.text("— Show team details", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  disband <team>    ", NamedTextColor.YELLOW).append(Component.text("— Force disband a team", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  kick <player>     ", NamedTextColor.YELLOW).append(Component.text("— Remove player from their team", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  add <player> <team>", NamedTextColor.YELLOW).append(Component.text("— Add player to a team", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  reload            ", NamedTextColor.YELLOW).append(Component.text("— Reload teams from file", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("list", "info", "disband", "kick", "add", "reload");
        return List.of();
    }
}
