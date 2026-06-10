package com.mostlyvanilla.settings.gui;

import com.mostlyvanilla.settings.MostlyVanillaSettings;
import com.mostlyvanilla.settings.Setting;
import com.mostlyvanilla.settings.SettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.potion.PotionEffectType;

public class SettingsGuiListener implements Listener {

    private final MostlyVanillaSettings plugin;
    private final SettingsManager settingsManager;

    public SettingsGuiListener(MostlyVanillaSettings plugin) {
        this.plugin = plugin;
        this.settingsManager = plugin.getSettingsManager();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(gui.getOwnerUuid())) return;

        int slot = event.getRawSlot();

        // Find which setting was clicked
        Setting clicked = null;
        for (Setting s : Setting.values()) {
            if (s.slot == slot) { clicked = s; break; }
        }
        if (clicked == null) return;

        boolean nowEnabled = settingsManager.toggle(player.getUniqueId(), clicked);

        // Apply immediate side effects
        applyEffect(player, clicked, nowEnabled);

        // Refresh that slot in the open inventory
        gui.refresh(clicked);

        String state = nowEnabled ? "§aenabled" : "§cdisabled";
        player.sendMessage(Component.text(clicked.displayName + " " + (nowEnabled ? "enabled" : "disabled") + ".",
                nowEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void applyEffect(Player player, Setting setting, boolean enabled) {
        switch (setting) {
            case NIGHT_VISION -> {
                if (enabled) {
                    com.mostlyvanilla.settings.listeners.PlayerListener.applyNightVision(player);
                } else {
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
            }
            default -> {} // Other settings are event-driven, no immediate effect needed
        }
    }
}
