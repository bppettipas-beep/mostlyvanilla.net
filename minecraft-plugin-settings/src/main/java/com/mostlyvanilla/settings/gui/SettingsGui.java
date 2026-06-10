package com.mostlyvanilla.settings.gui;

import com.mostlyvanilla.settings.Setting;
import com.mostlyvanilla.settings.SettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class SettingsGui implements InventoryHolder {

    private final Inventory inventory;
    private final UUID ownerUuid;
    private final SettingsManager settingsManager;

    public SettingsGui(UUID ownerUuid, SettingsManager settingsManager) {
        this.ownerUuid = ownerUuid;
        this.settingsManager = settingsManager;
        inventory = Bukkit.createInventory(this, 27,
                Component.text("Your Settings", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true));
        fill();
    }

    private void fill() {
        // Gray glass border for all slots
        ItemStack pane = borderPane();
        for (int i = 0; i < 27; i++) inventory.setItem(i, pane);

        // Setting items
        for (Setting setting : Setting.values()) {
            inventory.setItem(setting.slot, buildItem(setting));
        }
    }

    /** Rebuilds a single slot in place after a toggle. */
    public void refresh(Setting setting) {
        inventory.setItem(setting.slot, buildItem(setting));
    }

    private ItemStack buildItem(Setting setting) {
        boolean enabled = settingsManager.isEnabled(ownerUuid, setting);
        ItemStack item = new ItemStack(setting.icon);
        var meta = item.getItemMeta();

        // Name: green when on, red when off
        Component name = Component.text(setting.displayName,
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
        meta.displayName(name);

        // Lore
        Component status = enabled
                ? Component.text("● Enabled", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("● Disabled", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
        meta.lore(List.of(
                Component.text(setting.description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                status,
                Component.text("Click to toggle", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        // Enchant glint when enabled
        if (enabled) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack borderPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    public UUID getOwnerUuid() { return ownerUuid; }

    @Override
    public Inventory getInventory() { return inventory; }
}
