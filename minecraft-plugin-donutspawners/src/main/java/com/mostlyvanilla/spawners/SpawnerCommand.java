package com.mostlyvanilla.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class SpawnerCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> SUBS = List.of("give", "list", "remove", "removeall", "convertall", "reload");
    private static final List<String> TYPE_NAMES = Arrays.stream(SpawnerType.values())
        .map(t -> t.name().toLowerCase())
        .toList();

    private final DonutSpawners plugin;
    private final SpawnerManager manager;
    private final SpawnerConfig  cfg;

    // Tracks who is mid-confirmation for /ds removeall and /ds convertall
    private final Set<UUID> pendingRemoveAll  = new HashSet<>();
    private final Set<UUID> pendingConvertAll = new HashSet<>();

    public SpawnerCommand(DonutSpawners plugin, SpawnerManager manager, SpawnerConfig cfg) {
        this.plugin  = plugin;
        this.manager = manager;
        this.cfg     = cfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mvspawners.admin")) {
            sender.sendMessage(msg("&cYou don't have permission."));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "give"      -> cmdGive(sender, args);
            case "list"      -> cmdList(sender);
            case "remove"    -> cmdRemove(sender, args);
            case "removeall"  -> cmdRemoveAll(sender);
            case "convertall" -> cmdConvertAll(sender);
            case "reload"     -> cmdReload(sender);
            default          -> { sendHelp(sender); yield true; }
        };
    }

    // ── /ds give <player> <type> [amount] ────────────────────────────────────

    private boolean cmdGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("&cUsage: /ds give <player> <type> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(msg("&cPlayer '" + args[1] + "' is not online."));
            return true;
        }
        SpawnerType type = SpawnerType.fromString(args[2]);
        if (type == null) {
            sender.sendMessage(msg("&cUnknown spawner type '" + args[2] + "'. Types: "
                + String.join(", ", TYPE_NAMES)));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Math.max(1, Math.min(Integer.MAX_VALUE, Integer.parseInt(args[3]))); }
            catch (NumberFormatException e) {
                sender.sendMessage(msg("&cAmount must be a number."));
                return true;
            }
        }
        // Give in stacks of 64
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                target.getInventory().addItem(SpawnerItems.create(type, batch));
            overflow.values().forEach(i ->
                target.getWorld().dropItemNaturally(target.getLocation(), i));
            remaining -= batch;
        }
        sender.sendMessage(msg("&a[Spawners] Gave &e" + amount + "x " + type.getDisplayName()
            + " Spawner&a to &e" + target.getName() + "&a."));
        target.sendMessage(msg("&a[Spawners] You received &e" + amount + "x "
            + type.getDisplayName() + " Spawner&a."));
        return true;
    }

    // ── /ds list ─────────────────────────────────────────────────────────────

    private boolean cmdList(CommandSender sender) {
        Collection<SpawnerData> all = manager.getAllSpawners();
        if (all.isEmpty()) {
            sender.sendMessage(msg("&e[Spawners] No spawners placed."));
            return true;
        }
        sender.sendMessage(Component.text("━━━ Placed Spawners (" + all.size() + ") ━━━", NamedTextColor.GOLD));
        for (SpawnerData data : all) {
            sender.sendMessage(Component.text("  " + data.getType().getDisplayName()
                + " ×" + data.getStack() + " @ " + data.getKey(), NamedTextColor.YELLOW));
        }
        return true;
    }

    // ── /ds remove [key] ─────────────────────────────────────────────────────
    // With no argument: removes the spawner the player is looking at (within 5 blocks).
    // With a key argument (world,x,y,z): removes that specific spawner.

    private boolean cmdRemove(CommandSender sender, String[] args) {
        SpawnerData target = null;

        if (args.length >= 2) {
            // Key provided e.g. world,64,70,200
            target = manager.getAllSpawners().stream()
                .filter(d -> d.getKey().equals(args[1]))
                .findFirst().orElse(null);
            if (target == null) {
                sender.sendMessage(msg("&cNo spawner found at '" + args[1] + "'."));
                return true;
            }
        } else if (sender instanceof Player player) {
            // Look at target block
            org.bukkit.block.Block looked = player.getTargetBlockExact(5);
            if (looked != null && manager.isPluginSpawner(looked.getLocation())) {
                target = manager.getSpawner(looked.getLocation());
            }
            if (target == null) {
                // Try nearest within 3 blocks
                target = manager.getNearestSpawner(player.getLocation(), 3);
            }
            if (target == null) {
                sender.sendMessage(msg("&cNo spawner in range. Look at one or provide its key."));
                return true;
            }
        } else {
            sender.sendMessage(msg("&cUsage: /ds remove <world,x,y,z>"));
            return true;
        }

        SpawnerData removed = target;
        manager.removeSpawner(removed.getLocation());
        var loc = removed.getLocation();
        if (loc != null && loc.getBlock().getType() == org.bukkit.Material.SPAWNER) {
            loc.getBlock().setType(org.bukkit.Material.AIR, false);
        }
        sender.sendMessage(msg("&a[Spawners] Removed &e" + removed.getType().getDisplayName()
            + " Spawner &a(×" + removed.getStack() + ") at " + removed.getKey() + "."));
        return true;
    }

    // ── /ds removeall ────────────────────────────────────────────────────────

    private boolean cmdRemoveAll(CommandSender sender) {
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        if (id == null && pendingRemoveAll.isEmpty()) {
            // Console: just confirm once
            int count = manager.removeAll();
            sender.sendMessage(msg("&c[Spawners] Removed all " + count + " spawners."));
            return true;
        }
        if (id != null && pendingRemoveAll.contains(id)) {
            pendingRemoveAll.remove(id);
            int count = manager.removeAll();
            sender.sendMessage(msg("&c[Spawners] ⚠ Removed ALL " + count + " spawners!"));
        } else {
            if (id != null) pendingRemoveAll.add(id);
            sender.sendMessage(msg("&c[Spawners] ⚠ This will wipe EVERY placed spawner! Type /ds removeall again to confirm."));
        }
        return true;
    }

    // ── /ds convertall ───────────────────────────────────────────────────────

    private boolean cmdConvertAll(CommandSender sender) {
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;

        // Require one confirmation
        if (id != null && !pendingConvertAll.contains(id)) {
            pendingConvertAll.add(id);
            sender.sendMessage(msg("&e[Spawners] This will convert every vanilla spawner in loaded chunks to a plugin spawner. Run &f/ds convertall&e again to confirm."));
            return true;
        }
        if (id != null) pendingConvertAll.remove(id);

        int converted = 0;
        int alreadyPlugin = 0;
        int unknownType = 0;

        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof CreatureSpawner cs)) continue;
                    org.bukkit.Location loc = state.getLocation();

                    if (manager.isPluginSpawner(loc)) { alreadyPlugin++; continue; }

                    SpawnerType type = SpawnerType.fromEntityType(cs.getSpawnedType());
                    if (type == null) { unknownType++; continue; }

                    manager.placeSpawner(loc, type, 1);
                    converted++;
                }
            }
        }

        sender.sendMessage(msg("&a[Spawners] Converted &e" + converted + "&a vanilla spawner(s). "
            + "&7(" + alreadyPlugin + " already plugin spawners, "
            + unknownType + " skipped — unknown mob type)"));
        return true;
    }

    // ── /ds reload ───────────────────────────────────────────────────────────

    private boolean cmdReload(CommandSender sender) {
        cfg.reload();
        sender.sendMessage(msg("&a[Spawners] Config reloaded."));
        return true;
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mvspawners.admin")) return List.of();

        if (args.length == 1) return filter(SUBS, args[0]);

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "give"   -> filter(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).collect(Collectors.toList()), args[1]);
                case "remove" -> filter(manager.getAllSpawners().stream()
                    .map(SpawnerData::getKey).collect(Collectors.toList()), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(TYPE_NAMES, args[2]);
        }

        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("━━━ /ds ━━━", NamedTextColor.GOLD));
        sender.sendMessage(msg("  &e/ds give <player> <type> [amount] &7— Give spawner items"));
        sender.sendMessage(msg("  &e/ds list                          &7— List all placed spawners"));
        sender.sendMessage(msg("  &e/ds remove [key]                  &7— Remove spawner you're looking at or by key"));
        sender.sendMessage(msg("  &e/ds removeall                     &7— Wipe every spawner (confirm twice)"));
        sender.sendMessage(msg("  &e/ds convertall                   &7— Convert all vanilla spawners in loaded chunks (confirm twice)"));
        sender.sendMessage(msg("  &e/ds reload                        &7— Reload config.yml"));
        sender.sendMessage(msg("  &7Types: " + String.join(", ", TYPE_NAMES)));
    }

    private List<String> filter(List<String> opts, String input) {
        String lc = input.toLowerCase();
        return opts.stream().filter(o -> o.toLowerCase().startsWith(lc)).collect(Collectors.toList());
    }

    private Component msg(String s) { return LEGACY.deserialize(s); }
}
