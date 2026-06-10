package com.mostlyvanilla.tpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class TpaGui implements InventoryHolder {

    public static final int ACCEPT_SLOT = 2;
    public static final int DENY_SLOT   = 6;

    private final Inventory inventory;
    private final TpaRequest request;
    private boolean responded = false;

    public TpaGui(TpaRequest request, String requesterName) {
        this.request = request;

        String title = request.getType() == TpaRequest.Type.TPA
            ? requesterName + " → You"
            : "You → " + requesterName;

        this.inventory = Bukkit.createInventory(this, 9,
            Component.text("TPA: ", NamedTextColor.GOLD)
                .append(Component.text(title, NamedTextColor.YELLOW)));

        ItemStack filler = fillerItem();
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);

        inventory.setItem(ACCEPT_SLOT, buildItem(Material.LIME_STAINED_GLASS_PANE,
            Component.text("✔ Accept", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)));

        String loreText = request.getType() == TpaRequest.Type.TPA
            ? requesterName + " wants to teleport to you."
            : requesterName + " wants you to teleport to them.";
        ItemStack info = buildItem(Material.PAPER,
            Component.text(requesterName, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(List.of(
            Component.text(loreText, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        inventory.setItem(DENY_SLOT, buildItem(Material.RED_STAINED_GLASS_PANE,
            Component.text("✘ Deny", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)));
    }

    /** Marks this GUI as responded. Returns true only the first time. */
    public boolean tryRespond() {
        if (responded) return false;
        responded = true;
        return true;
    }

    public boolean isResponded() { return responded; }

    public TpaRequest getRequest() { return request; }

    @Override
    public Inventory getInventory() { return inventory; }

    private static ItemStack fillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
