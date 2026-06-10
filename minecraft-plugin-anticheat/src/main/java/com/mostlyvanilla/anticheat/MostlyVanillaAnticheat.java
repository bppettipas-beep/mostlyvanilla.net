package com.mostlyvanilla.anticheat;

import com.mostlyvanilla.anticheat.antixray.AntiXrayEngine;
import com.mostlyvanilla.anticheat.checks.AutoTotemCheck;
import com.mostlyvanilla.anticheat.commands.AcCommand;
import com.mostlyvanilla.anticheat.commands.SusCommand;
import com.mostlyvanilla.anticheat.gui.SusGuiListener;
import com.mostlyvanilla.anticheat.listeners.AdvancementListener;
import com.mostlyvanilla.anticheat.listeners.ChunkListener;
import com.mostlyvanilla.anticheat.listeners.CombatListener;
import com.mostlyvanilla.anticheat.listeners.MovementListener;
import com.mostlyvanilla.anticheat.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MostlyVanillaAnticheat extends JavaPlugin {

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private AntiXrayEngine antiXrayEngine;
    private ViolationManager violationManager;
    private PunishmentManager punishmentManager;
    private RolesBridge rolesBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        antiXrayEngine    = new AntiXrayEngine(this);
        rolesBridge       = new RolesBridge(this);
        punishmentManager = new PunishmentManager(this);
        violationManager  = new ViolationManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ChunkListener(this),       this);
        pm.registerEvents(new MovementListener(this),    this);
        pm.registerEvents(new CombatListener(this),      this);
        pm.registerEvents(new PlayerListener(this),      this);
        pm.registerEvents(new AdvancementListener(this), this);
        pm.registerEvents(new SusGuiListener(this),      this);
        pm.registerEvents(new AutoTotemCheck(this),      this);

        var acCmd = new AcCommand(this);
        var cmd = getCommand("ac");
        if (cmd != null) {
            cmd.setExecutor(acCmd);
            cmd.setTabCompleter(acCmd);
        }

        var susCmd = getCommand("sus");
        if (susCmd != null) susCmd.setExecutor(new SusCommand(this));

        getLogger().info("MostlyVanillaAnticheat enabled.");
    }

    @Override
    public void onDisable() {
        playerDataMap.clear();
        getLogger().info("MostlyVanillaAnticheat disabled.");
    }

    public PlayerData getData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerData::new);
    }

    public void removeData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public Map<UUID, PlayerData> getAllData() {
        return playerDataMap;
    }

    public AntiXrayEngine getAntiXrayEngine() { return antiXrayEngine; }
    public ViolationManager getViolationManager() { return violationManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public RolesBridge getRolesBridge() { return rolesBridge; }
}
