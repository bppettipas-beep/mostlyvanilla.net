package com.mostlyvanilla.gm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MostlyVanillaGm extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, GameMode> previousMode = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();

    @Override
    public void onEnable() {
        for (String cmd : List.of("gm", "gmc", "gms", "gmsp", "gma")) {
            getCommand(cmd).setExecutor(this);
            getCommand(cmd).setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MostlyVanilla GM enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("gm")) {
            sender.sendMessage(Component.text("Gamemode commands: ", NamedTextColor.YELLOW)
                .append(Component.text("/gmc  /gms  /gmsp  /gma", NamedTextColor.WHITE)));
            return true;
        }

        if (!sender.hasPermission("mv.gm")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        GameMode mode = switch (name) {
            case "gmc"  -> GameMode.CREATIVE;
            case "gms"  -> GameMode.SURVIVAL;
            case "gmsp" -> GameMode.SPECTATOR;
            case "gma"  -> GameMode.ADVENTURE;
            default     -> null;
        };

        if (mode == null) return true;

        if (args.length >= 1) {
            if (!sender.hasPermission("mv.gm.other")) {
                sender.sendMessage(Component.text("You don't have permission to change other players' gamemode.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found or not online.", NamedTextColor.RED));
                return true;
            }
            applyGameMode(target, mode, sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Run this from in-game or specify a player.", NamedTextColor.RED));
            return true;
        }

        applyGameMode(player, mode, null);
        return true;
    }

    private void applyGameMode(Player target, GameMode mode, CommandSender caller) {
        GameMode current = target.getGameMode();
        if (current == mode) {
            GameMode prev = previousMode.remove(target.getUniqueId());
            if (prev == null) prev = GameMode.SURVIVAL;
            changeGameMode(target, prev, caller);
        } else {
            previousMode.put(target.getUniqueId(), current);
            changeGameMode(target, mode, caller);
        }
    }

    private void changeGameMode(Player target, GameMode mode, CommandSender caller) {
        boolean wasSpectator = target.getGameMode() == GameMode.SPECTATOR;
        target.setGameMode(mode);
        boolean isSpectator = mode == GameMode.SPECTATOR;

        if (!wasSpectator && isSpectator) vanish(target);
        else if (wasSpectator && !isSpectator) unvanish(target);

        target.sendMessage(Component.text("Gamemode set to ", NamedTextColor.YELLOW)
            .append(Component.text(modeName(mode), NamedTextColor.WHITE))
            .append(Component.text(".", NamedTextColor.YELLOW)));

        if (caller != null) {
            caller.sendMessage(Component.text("Set ", NamedTextColor.YELLOW)
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(" to ", NamedTextColor.YELLOW))
                .append(Component.text(modeName(mode), NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.YELLOW)));
        }
    }

    private void vanish(Player player) {
        vanished.add(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            if (!other.hasPermission("mv.gm.other")) {
                other.hidePlayer(this, player);
            }
        }
        // hidePlayer can't hide a player from their own tab — send the packet directly
        sendTabRemove(player, player.getUniqueId());
    }

    private void unvanish(Player player) {
        vanished.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.showPlayer(this, player);
        }
        // Re-add self to own tab list
        sendTabAdd(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID uid : vanished) {
            Player spectator = Bukkit.getPlayer(uid);
            if (spectator != null && !joining.hasPermission("mv.gm.other")) {
                joining.hidePlayer(this, spectator);
            }
        }
        if (joining.getGameMode() == GameMode.SPECTATOR) {
            getServer().getScheduler().runTaskLater(this, () -> vanish(joining), 1L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        vanished.remove(uid);
        previousMode.remove(uid);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("mv.gm.other")) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .toList();
        }
        return List.of();
    }

    private String modeName(GameMode mode) {
        return switch (mode) {
            case CREATIVE  -> "Creative";
            case SURVIVAL  -> "Survival";
            case SPECTATOR -> "Spectator";
            case ADVENTURE -> "Adventure";
        };
    }

    // ── Packet helpers (tab list) ────────────────────────────────────────────

    // Bukkit's hidePlayer() can't remove a player from their own tab list, so we
    // send ClientboundPlayerInfoRemovePacket directly via reflection.

    private void sendTabRemove(Player viewer, UUID targetId) {
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object conn   = handle.getClass().getField("connection").get(handle);
            Class<?> pktClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Object packet = pktClass.getConstructor(List.class).newInstance(List.of(targetId));
            conn.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet")).invoke(conn, packet);
        } catch (Exception e) {
            getLogger().warning("sendTabRemove failed: " + e.getMessage());
        }
    }

    private void sendTabAdd(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("connection").get(handle);
            Class<?> updateClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Method create = null;
            for (Method m : updateClass.getMethods()) {
                if (m.getName().equals("createPlayerInitializing") && m.getParameterCount() == 1) {
                    create = m;
                    break;
                }
            }
            if (create == null) return;
            Object packet = create.invoke(null, List.of(handle));
            conn.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet")).invoke(conn, packet);
        } catch (Exception e) {
            getLogger().warning("sendTabAdd failed: " + e.getMessage());
        }
    }
}
