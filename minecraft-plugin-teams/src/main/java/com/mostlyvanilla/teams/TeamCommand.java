package com.mostlyvanilla.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager manager;

    public TeamCommand(TeamManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /team.");
            return true;
        }

        if (args.length == 0) {
            TeamData team = manager.getTeamByPlayer(player.getUniqueId());
            if (team == null) { sendHelp(player); return true; }
            TeamGui.open(player, team, manager);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (manager.getTeamByPlayer(player.getUniqueId()) != null) {
                    player.sendMessage(Component.text("You're already in a team. Leave it first.", NamedTextColor.RED)); return true;
                }
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /team create <name>", NamedTextColor.RED)); return true; }
                String name = args[1];
                if (name.length() > 16) { player.sendMessage(Component.text("Team name max 16 characters.", NamedTextColor.RED)); return true; }
                if (manager.getTeamByName(name) != null) { player.sendMessage(Component.text("That name is already taken.", NamedTextColor.RED)); return true; }
                TeamData team = manager.createTeam(name, player);
                player.sendMessage(Component.text("Team \"" + name + "\" created!", NamedTextColor.GREEN));
            }
            case "disband" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                manager.disbandTeam(team);
            }
            case "invite" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /team invite <player>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
                if (manager.getTeamByPlayer(target.getUniqueId()) != null) { player.sendMessage(Component.text("They're already in a team.", NamedTextColor.RED)); return true; }
                if (team.getMembers().size() >= manager.maxTeamSize()) { player.sendMessage(Component.text("Team is full (" + manager.maxTeamSize() + ").", NamedTextColor.RED)); return true; }
                team.getPendingInvites().add(target.getUniqueId());
                player.sendMessage(Component.text("Invited " + target.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You've been invited to join ").color(NamedTextColor.YELLOW)
                    .append(Component.text(team.getName()).color(team.namedColor()))
                    .append(Component.text(" by " + player.getName() + ". Type ", NamedTextColor.YELLOW))
                    .append(Component.text("/team accept", NamedTextColor.GREEN))
                    .append(Component.text(" or ", NamedTextColor.YELLOW))
                    .append(Component.text("/team deny", NamedTextColor.RED)));
            }
            case "accept" -> {
                if (manager.getTeamByPlayer(player.getUniqueId()) != null) { player.sendMessage(Component.text("You're already in a team.", NamedTextColor.RED)); return true; }
                TeamData found = null;
                for (TeamData t : manager.getAllTeams()) if (t.getPendingInvites().remove(player.getUniqueId())) { found = t; break; }
                if (found == null) { player.sendMessage(Component.text("You have no pending invite.", NamedTextColor.RED)); return true; }
                if (found.getMembers().size() >= manager.maxTeamSize()) { player.sendMessage(Component.text("That team is now full.", NamedTextColor.RED)); return true; }
                manager.addMember(found, player);
                player.sendMessage(Component.text("You joined " + found.getName() + "!", NamedTextColor.GREEN));
                for (var uid : found.getMembers()) { Player m = Bukkit.getPlayer(uid); if (m != null && !m.equals(player)) m.sendMessage(Component.text(player.getName() + " joined the team!", NamedTextColor.GREEN)); }
            }
            case "deny" -> {
                boolean denied = false;
                for (TeamData t : manager.getAllTeams()) if (t.getPendingInvites().remove(player.getUniqueId())) { denied = true; break; }
                player.sendMessage(denied ? Component.text("Invite denied.", NamedTextColor.GRAY) : Component.text("No pending invite.", NamedTextColor.RED));
            }
            case "kick" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /team kick <player>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("Player not online.", NamedTextColor.RED)); return true; }
                if (target.equals(player)) { player.sendMessage(Component.text("You can't kick yourself.", NamedTextColor.RED)); return true; }
                if (!team.getMembers().contains(target.getUniqueId())) { player.sendMessage(Component.text("They're not in your team.", NamedTextColor.RED)); return true; }
                manager.removeMember(team, target.getUniqueId());
                player.sendMessage(Component.text("Kicked " + target.getName() + ".", NamedTextColor.YELLOW));
                target.sendMessage(Component.text("You were kicked from " + team.getName() + ".", NamedTextColor.RED));
            }
            case "leave" -> {
                TeamData team = manager.getTeamByPlayer(player.getUniqueId());
                if (team == null) { player.sendMessage(Component.text("You're not in a team.", NamedTextColor.RED)); return true; }
                if (player.getUniqueId().equals(team.getLeader())) { player.sendMessage(Component.text("Leaders must disband the team or transfer leadership first.", NamedTextColor.RED)); return true; }
                manager.removeMember(team, player.getUniqueId());
                player.sendMessage(Component.text("You left " + team.getName() + ".", NamedTextColor.YELLOW));
                for (var uid : team.getMembers()) { Player m = Bukkit.getPlayer(uid); if (m != null) m.sendMessage(Component.text(player.getName() + " left the team.", NamedTextColor.GRAY)); }
            }
            case "home" -> {
                TeamData team = manager.getTeamByPlayer(player.getUniqueId());
                if (team == null) { player.sendMessage(Component.text("You're not in a team.", NamedTextColor.RED)); return true; }
                if (team.getHome() == null) { player.sendMessage(Component.text("No team home set.", NamedTextColor.RED)); return true; }
                player.teleport(team.getHome());
                player.sendMessage(Component.text("Teleported to team home.", NamedTextColor.GREEN));
            }
            case "sethome" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                team.setHome(player.getLocation());
                manager.save();
                player.sendMessage(Component.text("Team home set.", NamedTextColor.GREEN));
            }
            case "chat" -> {
                if (manager.getTeamByPlayer(player.getUniqueId()) == null) { player.sendMessage(Component.text("You're not in a team.", NamedTextColor.RED)); return true; }
                manager.toggleTeamChat(player.getUniqueId());
                player.sendMessage(Component.text("Team chat: " + (manager.hasTeamChat(player.getUniqueId()) ? "ON" : "OFF"),
                    manager.hasTeamChat(player.getUniqueId()) ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            case "ff" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                team.setFriendlyFire(!team.isFriendlyFire());
                manager.syncScoreboard(team);
                manager.save();
                player.sendMessage(Component.text("Friendly fire: " + (team.isFriendlyFire() ? "ON" : "OFF"),
                    team.isFriendlyFire() ? NamedTextColor.RED : NamedTextColor.GREEN));
            }
            case "open" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                team.setOpen(!team.isOpen());
                manager.save();
                player.sendMessage(Component.text("Team is now " + (team.isOpen() ? "open" : "invite-only") + ".",
                    team.isOpen() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "color" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                if (args.length < 2) {
                    player.sendMessage(Component.text("Colors: " + String.join(", ", TeamData.COLORS.keySet()), NamedTextColor.GRAY));
                    return true;
                }
                String col = args[1].toLowerCase();
                if (!TeamData.COLORS.containsKey(col)) { player.sendMessage(Component.text("Unknown color. Options: " + String.join(", ", TeamData.COLORS.keySet()), NamedTextColor.RED)); return true; }
                team.setColor(col);
                manager.syncScoreboard(team);
                manager.save();
                player.sendMessage(Component.text("Team color set to " + col + ".").color(team.namedColor()));
            }
            case "leader" -> {
                TeamData team = requireLeader(player); if (team == null) return true;
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /team leader <player>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("Player not online.", NamedTextColor.RED)); return true; }
                if (!team.getMembers().contains(target.getUniqueId())) { player.sendMessage(Component.text("They're not in your team.", NamedTextColor.RED)); return true; }
                team.setLeader(target.getUniqueId());
                manager.save();
                player.sendMessage(Component.text("Leadership transferred to " + target.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You are now the leader of " + team.getName() + "!", NamedTextColor.GOLD));
            }
            case "info" -> {
                TeamData team = manager.getTeamByPlayer(player.getUniqueId());
                if (team == null) { player.sendMessage(Component.text("You're not in a team.", NamedTextColor.RED)); return true; }
                sendInfo(player, team);
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private TeamData requireLeader(Player player) {
        TeamData team = manager.getTeamByPlayer(player.getUniqueId());
        if (team == null) { player.sendMessage(Component.text("You're not in a team.", NamedTextColor.RED)); return null; }
        if (!team.getLeader().equals(player.getUniqueId())) { player.sendMessage(Component.text("Only the team leader can do that.", NamedTextColor.RED)); return null; }
        return team;
    }

    private void sendInfo(Player player, TeamData team) {
        player.sendMessage(Component.text("── " + team.getName() + " ──").color(team.namedColor()));
        String leader = Bukkit.getOfflinePlayer(team.getLeader()).getName();
        player.sendMessage(Component.text("  Leader: ", NamedTextColor.GRAY).append(Component.text(leader != null ? leader : "?", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Members: ", NamedTextColor.GRAY).append(Component.text(team.getMembers().size() + "/" + manager.maxTeamSize(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Color: ", NamedTextColor.GRAY).append(Component.text(team.getColor()).color(team.namedColor())));
        player.sendMessage(Component.text("  FF: ", NamedTextColor.GRAY).append(Component.text(team.isFriendlyFire() ? "ON" : "OFF", team.isFriendlyFire() ? NamedTextColor.RED : NamedTextColor.GREEN)));
        player.sendMessage(Component.text("  Open: ", NamedTextColor.GRAY).append(Component.text(team.isOpen() ? "Yes" : "No", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Home: ", NamedTextColor.GRAY).append(Component.text(team.getHome() != null ? "Set" : "Not set", NamedTextColor.WHITE)));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("── /team ──", NamedTextColor.GOLD));
        String[][] cmds = {
            {"create <name>",    "Create a new team"},
            {"invite <player>",  "Invite someone"},
            {"accept / deny",    "Accept or deny an invite"},
            {"kick <player>",    "Kick a member (leader)"},
            {"leave",            "Leave your team"},
            {"home",             "Teleport to team home"},
            {"sethome",          "Set team home here (leader)"},
            {"chat",             "Toggle team chat"},
            {"ff",               "Toggle friendly fire (leader)"},
            {"open",             "Toggle open/invite-only (leader)"},
            {"color <color>",    "Set team color (leader)"},
            {"leader <player>",  "Transfer leadership"},
            {"info",             "Show team info"},
            {"disband",          "Disband the team (leader)"},
        };
        for (String[] c : cmds) {
            player.sendMessage(Component.text("  " + c[0] + "  ", NamedTextColor.YELLOW)
                .append(Component.text("— " + c[1], NamedTextColor.GRAY)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("create","invite","accept","deny","kick","leave",
            "home","sethome","chat","ff","open","color","leader","info","disband");
        if (args.length == 2 && args[0].equalsIgnoreCase("color"))
            return new ArrayList<>(TeamData.COLORS.keySet());
        return List.of();
    }
}
