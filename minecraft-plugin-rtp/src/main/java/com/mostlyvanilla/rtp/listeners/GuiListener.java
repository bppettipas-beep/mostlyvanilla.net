package com.mostlyvanilla.rtp.listeners;

import com.mostlyvanilla.rtp.RtpManager;
import com.mostlyvanilla.rtp.gui.RtpGui;
import com.mostlyvanilla.rtp.gui.RtpGuiHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuiListener implements Listener {

    private final RtpManager rtpManager;

    public GuiListener(RtpManager rtpManager) {
        this.rtpManager = rtpManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof RtpGuiHolder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;

        int slot = event.getSlot();

        // Middle row only (slots 9–17)
        if (slot < 9 || slot > 17) return;

        List<World> worlds = getGuiWorlds();
        int n = Math.min(worlds.size(), 9);
        int[] slots = buildSlots(n);

        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                World world = worlds.get(i);
                if (rtpManager.isDisabled(world)) {
                    player.sendMessage(Component.text("That dimension is currently disabled.", NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                rtpManager.startRtp(player, world);
                return;
            }
        }
    }

    /** Must match the filtering logic in RtpGui.open(). */
    private static List<World> getGuiWorlds() {
        Set<World.Environment> seen = new HashSet<>();
        List<World> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equals("mv_spawn")) continue;
            if (seen.add(w.getEnvironment())) worlds.add(w);
        }
        return worlds;
    }

    private static int[] buildSlots(int n) {
        n = Math.min(n, 9);
        int[] slots = new int[n];
        if (n <= 4) {
            int center = 13;
            for (int i = 0; i < n; i++) slots[i] = center - (n - 1) + i * 2;
        } else {
            int start = 9 + (9 - n) / 2;
            for (int i = 0; i < n; i++) slots[i] = start + i;
        }
        return slots;
    }
}
