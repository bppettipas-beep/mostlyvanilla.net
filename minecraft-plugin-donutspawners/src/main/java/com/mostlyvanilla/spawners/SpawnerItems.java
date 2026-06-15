package com.mostlyvanilla.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SpawnerItems {

    private SpawnerItems() {}

    /** Creates a spawner ItemStack tagged with PDC so the plugin can identify it. */
    public static ItemStack create(SpawnerType type, int amount) {
        ItemStack item = new ItemStack(Material.SPAWNER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(type.getIcon() + " " + type.getDisplayName() + " Spawner")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
            Component.text("Type: " + type.getDisplayName())
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            Component.text("Right-click a placed spawner to stack")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("⚠ Breaking destroys stored items!")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer()
            .set(DonutSpawners.KEY_TYPE, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    /** Returns the SpawnerType if this item is a plugin spawner, null otherwise. */
    public static SpawnerType getType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        // Primary: PDC tag (authoritative)
        String typeStr = meta.getPersistentDataContainer()
            .get(DonutSpawners.KEY_TYPE, PersistentDataType.STRING);
        if (typeStr != null) return SpawnerType.fromString(typeStr);

        // Fallback: infer type from display name for items that pre-date the PDC tag
        // e.g. "☠ Skeleton Spawner" → SKELETON
        if (meta.hasDisplayName()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            for (SpawnerType t : SpawnerType.values()) {
                if (plain.contains(t.getDisplayName() + " Spawner")) return t;
            }
        }
        return null;
    }

    public static boolean isSpawnerItem(ItemStack item) {
        return getType(item) != null;
    }
}
