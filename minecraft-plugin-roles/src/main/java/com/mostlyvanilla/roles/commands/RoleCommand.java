package com.mostlyvanilla.roles.commands;

import com.mostlyvanilla.roles.MostlyVanillaRoles;
import com.mostlyvanilla.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RoleCommand implements CommandExecutor, TabCompleter {

    private final MostlyVanillaRoles plugin;

    public RoleCommand(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.roles.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        RoleManager rm = plugin.getRoleManager();

        switch (args[0].toLowerCase()) {

            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /role create <name> <prefix>", NamedTextColor.RED));
                    sender.sendMessage(Component.text("Tip: use & for color codes, e.g. &c[Admin]&r", NamedTextColor.GRAY));
                    return true;
                }
                String name   = args[1].toLowerCase();
                String prefix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                rm.createRole(name, prefix);
                sender.sendMessage(
                    Component.text("Created role ", NamedTextColor.GREEN)
                        .append(Component.text(name, NamedTextColor.WHITE))
                        .append(Component.text(" with prefix ", NamedTextColor.GREEN))
                        .append(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix))
                );
            }

            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /role delete <name>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1].toLowerCase();
                if (rm.deleteRole(name)) {
                    sender.sendMessage(Component.text("Deleted role " + name + ".", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Role '" + name + "' does not exist.", NamedTextColor.RED));
                }
            }

            case "assign" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /role assign <player> <role>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                    return true;
                }
                String roleName = args[2].toLowerCase();
                if (rm.assignRole(target.getUniqueId(), roleName)) {
                    sender.sendMessage(Component.text("Assigned role " + roleName + " to " + target.getName() + ".", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("You have been given the " + roleName + " role.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Role '" + roleName + "' does not exist.", NamedTextColor.RED));
                }
            }

            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /role remove <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                    return true;
                }
                if (rm.removePlayerRole(target.getUniqueId())) {
                    sender.sendMessage(Component.text("Removed role from " + target.getName() + ".", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Your role has been removed.", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text(target.getName() + " doesn't have a role.", NamedTextColor.RED));
                }
            }

            case "list" -> {
                Map<String, String> roles = rm.getRoles();
                if (roles.isEmpty()) {
                    sender.sendMessage(Component.text("No roles exist yet. Use /role create to make one.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("━━━ Roles (" + roles.size() + ") ━━━", NamedTextColor.GREEN));
                for (Map.Entry<String, String> entry : roles.entrySet()) {
                    sender.sendMessage(
                        Component.text("  " + entry.getKey() + " — ", NamedTextColor.GRAY)
                            .append(LegacyComponentSerializer.legacyAmpersand().deserialize(entry.getValue()))
                    );
                }
            }

            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /role info <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                    return true;
                }
                String roleName = rm.getPlayerRole(target.getUniqueId());
                if (roleName == null) {
                    sender.sendMessage(Component.text(target.getName() + " has no role.", NamedTextColor.YELLOW));
                } else {
                    String prefix = rm.getPrefix(target.getUniqueId());
                    sender.sendMessage(
                        Component.text(target.getName() + ": ", NamedTextColor.WHITE)
                            .append(Component.text(roleName + " ", NamedTextColor.GRAY))
                            .append(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix))
                    );
                }
            }

            case "setweight" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /role setweight <role> <weight>", NamedTextColor.RED));
                    sender.sendMessage(Component.text("Lower weight = higher in tab list (1 = top, 99 = bottom)", NamedTextColor.GRAY));
                    return true;
                }
                String roleName = args[1].toLowerCase();
                int weight;
                try {
                    weight = Integer.parseInt(args[2]);
                    if (weight < 1 || weight > 99) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Weight must be a number between 1 and 99.", NamedTextColor.RED));
                    return true;
                }
                if (rm.setWeight(roleName, weight)) {
                    sender.sendMessage(Component.text("Set weight of ", NamedTextColor.GREEN)
                        .append(Component.text(roleName, NamedTextColor.WHITE))
                        .append(Component.text(" to " + weight + ".", NamedTextColor.GREEN)));
                } else {
                    sender.sendMessage(Component.text("Role '" + roleName + "' does not exist.", NamedTextColor.RED));
                }
            }

            case "link" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /role link <game-role> <discord-role-id>", NamedTextColor.RED));
                    return true;
                }
                String gameRole = args[1].toLowerCase();
                String discordRoleId = args[2];
                if (rm.linkRole(gameRole, discordRoleId)) {
                    sender.sendMessage(Component.text("Linked ", NamedTextColor.GREEN)
                        .append(Component.text(gameRole, NamedTextColor.WHITE))
                        .append(Component.text(" → Discord role ", NamedTextColor.GREEN))
                        .append(Component.text(discordRoleId, NamedTextColor.WHITE)));
                } else {
                    sender.sendMessage(Component.text("Role '" + gameRole + "' does not exist.", NamedTextColor.RED));
                }
            }

            case "unlink" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /role unlink <game-role>", NamedTextColor.RED));
                    return true;
                }
                String gameRole = args[1].toLowerCase();
                if (rm.unlinkRole(gameRole)) {
                    sender.sendMessage(Component.text("Unlinked game role " + gameRole + " from Discord.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Role '" + gameRole + "' has no Discord link.", NamedTextColor.RED));
                }
            }

            case "links" -> {
                Map<String, String> links = rm.getRoleLinks();
                if (links.isEmpty()) {
                    sender.sendMessage(Component.text("No role links configured.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("━━━ Role Links ━━━", NamedTextColor.GREEN));
                for (Map.Entry<String, String> e : links.entrySet()) {
                    sender.sendMessage(Component.text("  " + e.getKey() + " → " + e.getValue(), NamedTextColor.GRAY));
                }
            }

            case "join" -> {
                if (args.length < 2) {
                    String current = rm.getJoinRole();
                    sender.sendMessage(Component.text("Join role: ", NamedTextColor.GREEN)
                        .append(current != null
                            ? Component.text(current, NamedTextColor.WHITE)
                            : Component.text("none", NamedTextColor.GRAY)));
                    sender.sendMessage(Component.text("Usage: /role join <role|disable>", NamedTextColor.GRAY));
                    return true;
                }
                if (args[1].equalsIgnoreCase("disable")) {
                    rm.clearJoinRole();
                    sender.sendMessage(Component.text("Join role disabled. New players will not be given a role automatically.", NamedTextColor.YELLOW));
                } else {
                    String roleName = args[1].toLowerCase();
                    if (rm.setJoinRole(roleName)) {
                        sender.sendMessage(Component.text("New players will automatically receive the " + roleName + " role.", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Role '" + roleName + "' does not exist.", NamedTextColor.RED));
                    }
                }
            }

            default -> sendUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mostlyvanilla.roles.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("create", "delete", "assign", "remove", "list", "info", "join", "setweight", "link", "unlink", "links"), args[0]);
        }

        RoleManager rm = plugin.getRoleManager();

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "delete", "join", "setweight", "link", "unlink" -> filter(new ArrayList<>(rm.getRoleNames()), args[1]);
                case "assign", "remove", "info" -> filter(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]
                );
                default -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("assign")) {
            return filter(new ArrayList<>(rm.getRoleNames()), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream()
            .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("━━━ /role ━━━", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  /role create <name> <prefix>  ", NamedTextColor.WHITE).append(Component.text("Create a role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role delete <name>           ", NamedTextColor.WHITE).append(Component.text("Delete a role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role assign <player> <role>  ", NamedTextColor.WHITE).append(Component.text("Give a player a role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role remove <player>         ", NamedTextColor.WHITE).append(Component.text("Remove a player's role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role list                    ", NamedTextColor.WHITE).append(Component.text("List all roles", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role info <player>           ", NamedTextColor.WHITE).append(Component.text("See a player's role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role join <role|disable>     ", NamedTextColor.WHITE).append(Component.text("Set the auto-assigned join role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role link <role> <discord-id>", NamedTextColor.WHITE).append(Component.text("Link a game role to a Discord role", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role unlink <role>           ", NamedTextColor.WHITE).append(Component.text("Remove Discord role link", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /role links                   ", NamedTextColor.WHITE).append(Component.text("List all role links", NamedTextColor.GRAY)));
    }
}
