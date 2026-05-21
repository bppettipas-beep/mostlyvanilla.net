package com.mostlyvanilla.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MostlyVanillaMOTD extends JavaPlugin implements Listener {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final AtomicInteger pingCount = new AtomicInteger(0);
    private List<Component[]> frames = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadFrames();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MostlyVanilla MOTD enabled with " + frames.size() + " frames.");
    }

    private void loadFrames() {
        frames.clear();
        List<?> rawFrames = getConfig().getList("motd-frames");

        if (rawFrames != null) {
            for (Object obj : rawFrames) {
                if (obj instanceof List<?> lines && lines.size() >= 2) {
                    try {
                        Component line1 = mm.deserialize(lines.get(0).toString());
                        Component line2 = mm.deserialize(lines.get(1).toString());
                        frames.add(new Component[]{line1, line2});
                    } catch (Exception e) {
                        getLogger().warning("Failed to parse MOTD frame: " + e.getMessage());
                    }
                }
            }
        }

        if (frames.isEmpty()) {
            getLogger().severe("No valid MOTD frames found in config! Check config.yml.");
        }
    }

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        if (frames.isEmpty()) return;

        int idx = pingCount.getAndUpdate(i -> (i + 1) % frames.size());
        Component[] frame = frames.get(idx);

        event.motd(Component.text()
            .append(frame[0])
            .append(Component.newline())
            .append(frame[1])
            .build());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("motdreload")) return false;

        if (!sender.hasPermission("motd.reload")) {
            sender.sendMessage(mm.deserialize("<red>No permission."));
            return true;
        }

        reloadConfig();
        loadFrames();
        sender.sendMessage(mm.deserialize(
            "<green>MOTD reloaded! <gray>" + frames.size() + " frames loaded."
        ));
        return true;
    }
}
