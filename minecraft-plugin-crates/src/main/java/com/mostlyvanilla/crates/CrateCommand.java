package com.mostlyvanilla.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin   plugin;
    private final CrateManager manager;

    public CrateCommand(JavaPlugin plugin, CrateManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mostlyvanilla.crates.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "set"    -> handleSet(sender, args);
            case "remove" -> handleRemove(sender);
            case "list"   -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "reward" -> handleReward(sender, args);
            case "wipe"   -> handleWipe(sender, args);
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            default       -> sendHelp(sender);
        }
        return true;
    }

    // ── /crate set <type> ─────────────────────────────────────────────────────

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set crates.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /crate set <type>  Types: "
                + String.join(", ", manager.getTypeIds()), NamedTextColor.RED));
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || !Tag.SHULKER_BOXES.isTagged(target.getType())) {
            sender.sendMessage(Component.text("Look at a shulker box within 5 blocks.", NamedTextColor.RED));
            return;
        }
        String typeId = args[1].toLowerCase();
        if (!manager.getTypeIds().contains(typeId)) {
            sender.sendMessage(Component.text("Unknown type: " + typeId
                + "  Types: " + String.join(", ", manager.getTypeIds()), NamedTextColor.RED));
            return;
        }
        manager.setCrate(target, typeId);
        int rewardCount = manager.getType(typeId).rewards().size();
        sender.sendMessage(Component.text(
            "\"" + typeId + "\" crate set at "
            + target.getX() + "," + target.getY() + "," + target.getZ()
            + " with " + rewardCount + " reward(s).", NamedTextColor.GREEN));
    }

    // ── /crate remove ─────────────────────────────────────────────────────────

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can remove crates.", NamedTextColor.RED));
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            sender.sendMessage(Component.text("Look at a crate within 5 blocks.", NamedTextColor.RED));
            return;
        }
        if (manager.removeCrate(target)) {
            sender.sendMessage(Component.text("Crate removed.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("No crate at that location.", NamedTextColor.RED));
        }
    }

    // ── /crate list ───────────────────────────────────────────────────────────

    private void handleList(CommandSender sender) {
        Map<String, String> all = manager.getAll();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No crates placed.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("── Placed Crates ──", NamedTextColor.GOLD));
        all.forEach((loc, type) ->
            sender.sendMessage(Component.text("  " + loc + " → " + type, NamedTextColor.GRAY)));
    }

    // ── /crate wipe [confirm] ─────────────────────────────────────────────────

    private void handleWipe(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            int count = manager.getAll().size();
            sender.sendMessage(Component.text("⚠ This will unregister all " + count + " placed crate(s).", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Run ", NamedTextColor.GRAY)
                .append(Component.text("/crate wipe confirm", NamedTextColor.YELLOW))
                .append(Component.text(" to proceed.", NamedTextColor.GRAY)));
            return;
        }
        int removed = manager.wipeAll();
        sender.sendMessage(Component.text("Wiped " + removed + " crate location(s).", NamedTextColor.GREEN));
    }

    // ── /crate create <id> <display-name...> ─────────────────────────────────

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can create crates (color picker required).", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /crate create <id> <name>  — a color picker will open", NamedTextColor.RED));
            return;
        }
        String id = args[1].toLowerCase();
        if (!id.matches("[a-z0-9_]+")) {
            sender.sendMessage(Component.text("ID must be lowercase letters, numbers, or underscores only.", NamedTextColor.RED));
            return;
        }
        if (manager.getTypeIds().contains(id)) {
            sender.sendMessage(Component.text("A crate type with id \"" + id + "\" already exists.", NamedTextColor.RED));
            return;
        }
        String rawName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        manager.openColorPicker(player, id, rawName);
    }

    // ── /crate delete <id> [confirm] ──────────────────────────────────────────

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /crate delete <id> [confirm]", NamedTextColor.RED));
            return;
        }
        String id = args[1].toLowerCase();
        if (!manager.getTypeIds().contains(id)) {
            sender.sendMessage(Component.text("Unknown crate type: " + id, NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Component.text("⚠ This will permanently delete the \"" + id + "\" crate type and remove its key from /bitshop.", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Run ", NamedTextColor.GRAY)
                .append(Component.text("/crate delete " + id + " confirm", NamedTextColor.YELLOW))
                .append(Component.text(" to proceed.", NamedTextColor.GRAY)));
            return;
        }
        manager.deleteType(id);
        sender.sendMessage(Component.text("Deleted crate type \"" + id + "\" and removed its key from /bitshop.", NamedTextColor.GREEN));
    }

    // ── /crate reload ─────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        manager.load();
        sender.sendMessage(Component.text("Crates reloaded.", NamedTextColor.GREEN));
    }

    // ── /crate reward <list|add|remove> ... ──────────────────────────────────

    private void handleReward(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendRewardHelp(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list"   -> handleRewardList(sender, args);
            case "add"    -> handleRewardAdd(sender, args);
            case "remove" -> handleRewardRemove(sender, args);
            default       -> sendRewardHelp(sender);
        }
    }

    // /crate reward list <type>
    private void handleRewardList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /crate reward list <type>", NamedTextColor.RED));
            return;
        }
        String typeId = args[2].toLowerCase();
        CrateType type = manager.getType(typeId);
        if (type == null) {
            sender.sendMessage(Component.text("Unknown type: " + typeId, NamedTextColor.RED));
            return;
        }
        sender.sendMessage(LEGACY.deserialize("&6── " + type.displayName() + " &6Rewards ──"));
        List<CrateReward> rewards = type.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            CrateReward r = rewards.get(i);
            sender.sendMessage(Component.text("  #" + (i + 1) + "  ", NamedTextColor.YELLOW)
                .append(LEGACY.deserialize(r.name()))
                .append(Component.text("  (" + r.material().name() + " x" + r.amount() + ")", NamedTextColor.DARK_GRAY)));
        }
    }

    // /crate reward add <type> [name...]
    // Uses the held item's material and stack size. Name defaults to display name or formatted material.
    private void handleRewardAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can add rewards.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /crate reward add <type> [name]  — hold the item first", NamedTextColor.RED));
            return;
        }
        String typeId = args[2].toLowerCase();
        if (!manager.getTypeIds().contains(typeId)) {
            sender.sendMessage(Component.text("Unknown type: " + typeId, NamedTextColor.RED));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Component.text("Hold an item in your main hand first.", NamedTextColor.RED));
            return;
        }

        // Read spawner type before determining name so we can auto-name it
        String spawnerType = CrateManager.readSpawnerType(held);

        // Determine name: args[3..] if provided, else item display name, else formatted material
        String name;
        if (args.length >= 4) {
            name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        } else {
            ItemMeta meta = held.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                name = LEGACY.serialize(meta.displayName());
            } else if (spawnerType != null) {
                name = "&6" + CrateManager.formatMaterialName(
                    org.bukkit.Material.valueOf(spawnerType)) + " Spawner";
            } else {
                name = "&f" + CrateManager.formatMaterialName(held.getType());
            }
        }

        // Capture enchantments from the held item
        Map<String, Integer> enchants = new LinkedHashMap<>();
        Map<Enchantment, Integer> itemEnchs = held.getType() == org.bukkit.Material.ENCHANTED_BOOK
                && held.getItemMeta() instanceof EnchantmentStorageMeta esm
                ? esm.getStoredEnchants()
                : held.getEnchantments();
        for (Map.Entry<Enchantment, Integer> ench : itemEnchs.entrySet()) {
            enchants.put(ench.getKey().getKey().getKey(), ench.getValue());
        }

        CrateReward reward = new CrateReward(name, held.getType(), held.getAmount(), 10, spawnerType, enchants);
        manager.addReward(typeId, reward);
        sender.sendMessage(Component.text("Added reward to " + typeId + ": ", NamedTextColor.GREEN)
            .append(LEGACY.deserialize(name))
            .append(Component.text("  x" + held.getAmount()
                + (enchants.isEmpty() ? "" : "  [" + enchants.size() + " enchant(s)]"), NamedTextColor.GRAY)));
    }

    // /crate reward remove <type> <#>
    private void handleRewardRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /crate reward remove <type> <#>", NamedTextColor.RED));
            return;
        }
        String typeId = args[2].toLowerCase();
        if (!manager.getTypeIds().contains(typeId)) {
            sender.sendMessage(Component.text("Unknown type: " + typeId, NamedTextColor.RED));
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[3]) - 1; // 1-based input
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("\"" + args[3] + "\" is not a number.", NamedTextColor.RED));
            return;
        }
        if (manager.removeReward(typeId, index)) {
            sender.sendMessage(Component.text("Removed reward #" + (index + 1) + " from " + typeId + ".", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Invalid reward number. Use /crate reward list " + typeId, NamedTextColor.RED));
        }
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("set", "remove", "list", "reload", "reward", "wipe", "create", "delete");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "set", "delete" -> new ArrayList<>(manager.getTypeIds());
                case "reward"        -> List.of("list", "add", "remove");
                default              -> List.of();
            };
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("reward") && List.of("list", "add", "remove").contains(args[1].toLowerCase()))
                return new ArrayList<>(manager.getTypeIds());
            if (args[0].equalsIgnoreCase("delete"))
                return List.of("confirm");
        }
        return List.of();
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── /crate ──", NamedTextColor.GOLD));
        sender.sendMessage(help("create <id> <name>", "Create a new crate type + /bitshop key"));
        sender.sendMessage(help("delete <id> [confirm]", "Delete a crate type + its /bitshop key"));
        sender.sendMessage(help("set <type>",        "Look at a shulker to register it"));
        sender.sendMessage(help("remove",            "Look at a crate to unregister it"));
        sender.sendMessage(help("list",              "List all placed crates"));
        sender.sendMessage(help("reload",            "Reload config and rewards"));
        sender.sendMessage(help("wipe [confirm]",    "Unregister all placed crates"));
        sender.sendMessage(help("reward list <type>","Show rewards for a crate type"));
        sender.sendMessage(help("reward add <type>", "Hold an item — adds it as a reward"));
        sender.sendMessage(help("reward remove <type> <#>", "Remove a reward by number"));
        sender.sendMessage(Component.text("Types: " + String.join(", ", manager.getTypeIds()), NamedTextColor.GRAY));
    }

    private void sendRewardHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── /crate reward ──", NamedTextColor.GOLD));
        sender.sendMessage(help("reward list <type>",           "Show numbered reward list"));
        sender.sendMessage(help("reward add <type> [name]",     "Hold item → adds it as reward"));
        sender.sendMessage(help("reward remove <type> <#>",     "Remove reward by list number"));
    }

    private static Component help(String usage, String desc) {
        return Component.text("  " + usage + "  ", NamedTextColor.YELLOW)
            .append(Component.text("— " + desc, NamedTextColor.GRAY));
    }
}
