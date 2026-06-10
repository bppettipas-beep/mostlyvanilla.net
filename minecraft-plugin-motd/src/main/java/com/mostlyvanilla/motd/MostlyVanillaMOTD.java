package com.mostlyvanilla.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    private static final int MOTD_PIXEL_WIDTH = 305;
    private static final int SPACE_WIDTH = 4;

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
                        Component line1 = parseLine(lines.get(0).toString());
                        Component line2 = parseLine(lines.get(1).toString());
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

    private Component parseLine(String raw) {
        if (!raw.startsWith("<center>")) return mm.deserialize(raw);

        String content = raw.substring(8);
        Component parsed = mm.deserialize(content);

        String plain = PlainTextComponentSerializer.plainText().serialize(parsed);
        boolean bold = content.contains("<bold>") || content.contains("<b>");
        int textWidth = pixelWidth(plain, bold);

        int padding = Math.max(0, (MOTD_PIXEL_WIDTH - textWidth) / 2);
        int spaces = padding / (bold ? SPACE_WIDTH + 1 : SPACE_WIDTH);

        return Component.text(" ".repeat(spaces)).append(parsed);
    }

    private static int pixelWidth(String text, boolean bold) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Emoji and high codepoints are wide
            if (c > 0xFF) {
                width += bold ? 14 : 13;
            } else {
                width += bold ? charWidth(c) + 1 : charWidth(c);
            }
        }
        return width;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case 'f', 'k', ' ' -> 4;
            case 'i', 'l', '!', '.', ',', '|', '\'' -> 3;
            case 't' -> 4;
            case 'r' -> 5;
            case 'm', 'M', 'W' -> 9;
            default -> 6;
        };
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
