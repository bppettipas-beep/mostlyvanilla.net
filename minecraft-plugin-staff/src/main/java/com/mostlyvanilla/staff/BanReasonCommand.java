package com.mostlyvanilla.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.stream.Collectors;

public class BanReasonCommand implements CommandExecutor, TabCompleter {

    private final BanReasonManager banReasonManager;

    public BanReasonCommand(BanReasonManager banReasonManager) {
        this.banReasonManager = banReasonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Only ops can manage ban reasons.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { sendUsage(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /banreason add <id> <duration> <wipe|nowipe>", NamedTextColor.RED));
                    sender.sendMessage(Component.text("Duration examples: 1h, 7d, 30d, perm", NamedTextColor.GRAY));
                    return true;
                }
                String id = args[1].toLowerCase();
                long duration = BanReasonManager.parseDuration(args[2]);
                if (duration == 0L) {
                    sender.sendMessage(Component.text("Invalid duration. Use: 30s, 5m, 2h, 7d, 30d, perm", NamedTextColor.RED));
                    return true;
                }
                boolean wipe = args[3].equalsIgnoreCase("wipe");
                banReasonManager.addReason(id, duration, wipe);
                sender.sendMessage(Component.text("✔ Ban reason added: ", NamedTextColor.GREEN)
                    .append(Component.text(id, NamedTextColor.WHITE))
                    .append(Component.text(" — " + BanReasonManager.formatDuration(duration)
                        + (wipe ? " §c(wipes player data)" : ""), NamedTextColor.GRAY)));
            }

            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /banreason remove <id>", NamedTextColor.RED));
                    return true;
                }
                if (banReasonManager.removeReason(args[1])) {
                    sender.sendMessage(Component.text("Removed ban reason: " + args[1] + ".", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("No ban reason named '" + args[1] + "'.", NamedTextColor.RED));
                }
            }

            case "list" -> {
                var reasons = banReasonManager.getReasons();
                if (reasons.isEmpty()) {
                    sender.sendMessage(Component.text("No ban reasons defined yet. Use /banreason add.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("━━━ Ban Reasons ━━━", NamedTextColor.GREEN));
                for (var r : reasons.values()) {
                    sender.sendMessage(
                        Component.text("  " + r.id(), NamedTextColor.WHITE)
                            .append(Component.text(" — " + BanReasonManager.formatDuration(r.durationMs())
                                + (r.wipe() ? " §c(wipe)" : ""), NamedTextColor.GRAY)));
                }
            }

            default -> sendUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) return List.of();
        if (args.length == 1)
            return filter(List.of("add", "remove", "list"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("remove"))
            return filter(banReasonManager.getReasons().keySet().stream().toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("add"))
            return filter(List.of("1h", "7d", "14d", "30d", "perm"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("add"))
            return filter(List.of("wipe", "nowipe"), args[3]);
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream()
            .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("━━━ /banreason ━━━", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  /banreason add <id> <duration> <wipe|nowipe>  ", NamedTextColor.WHITE)
            .append(Component.text("Define a preset ban reason", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /banreason remove <id>                        ", NamedTextColor.WHITE)
            .append(Component.text("Remove a preset", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /banreason list                               ", NamedTextColor.WHITE)
            .append(Component.text("Show all presets", NamedTextColor.GRAY)));
    }
}
