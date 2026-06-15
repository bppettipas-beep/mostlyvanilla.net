package com.mostlyvanilla.roles;

import com.mostlyvanilla.roles.commands.DelOreCommand;
import com.mostlyvanilla.roles.commands.DelStashCommand;
import com.mostlyvanilla.roles.commands.DutyCommand;
import com.mostlyvanilla.roles.commands.DutyRequireCommand;
import com.mostlyvanilla.roles.commands.HistoryCommand;
import com.mostlyvanilla.roles.commands.RoleCommand;
import com.mostlyvanilla.roles.commands.SpawnOreCommand;
import com.mostlyvanilla.roles.commands.SpawnStashCommand;
import com.mostlyvanilla.roles.ore.OreManager;
import com.mostlyvanilla.roles.listeners.ChatListener;
import com.mostlyvanilla.roles.listeners.ChatLogListener;
import com.mostlyvanilla.roles.listeners.CommandListener;
import com.mostlyvanilla.roles.listeners.PlayerJoinListener;
import com.mostlyvanilla.roles.stash.StashManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaRoles extends JavaPlugin {

    private static MostlyVanillaRoles instance;
    private RoleManager roleManager;
    private TabManager  tabManager;
    private GlowManager glowManager;

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        saveDefaultConfig();

        roleManager = new RoleManager(this);
        roleManager.load();

        tabManager = new TabManager(this);
        tabManager.setupPingObjective();
        tabManager.start();

        glowManager = new GlowManager(this);

        var roleCmd = getCommand("role");
        if (roleCmd != null) {
            var executor = new RoleCommand(this);
            roleCmd.setExecutor(executor);
            roleCmd.setTabCompleter(executor);
        }

        var dutyCmd = getCommand("duty");
        if (dutyCmd != null) dutyCmd.setExecutor(new DutyCommand(this));

        StashManager stashManager = new StashManager(this);
        var spawnStashCmd = getCommand("spawnstash");
        if (spawnStashCmd != null) spawnStashCmd.setExecutor(new SpawnStashCommand(this, stashManager));
        var delStashCmd = getCommand("delstash");
        if (delStashCmd != null) delStashCmd.setExecutor(new DelStashCommand(stashManager));

        OreManager oreManager = new OreManager();
        var spawnOreCmd = getCommand("spawnore");
        if (spawnOreCmd != null) {
            var exec = new SpawnOreCommand(this, oreManager);
            spawnOreCmd.setExecutor(exec);
            spawnOreCmd.setTabCompleter(exec);
        }
        var delOreCmd = getCommand("delore");
        if (delOreCmd != null) delOreCmd.setExecutor(new DelOreCommand(this, oreManager));

        var dutyRequireCmd = getCommand("dutyrequire");
        if (dutyRequireCmd != null) {
            var dr = new DutyRequireCommand(this);
            dutyRequireCmd.setExecutor(dr);
            dutyRequireCmd.setTabCompleter(dr);
        }

        HistoryGui historyGui = new HistoryGui();
        getServer().getPluginManager().registerEvents(historyGui, this);
        var historyCmd = getCommand("history");
        if (historyCmd != null) {
            var hc = new HistoryCommand(this, historyGui);
            historyCmd.setExecutor(hc);
            historyCmd.setTabCompleter(hc);
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatLogListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(tabManager, this);

        // Repair any player knocked out of their role's scoreboard team by other plugins
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers())
                roleManager.syncPlayerTeamIfNeeded(p);
        }, 60L, 60L);

        getLogger().info("MostlyVanillaRoles enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaRoles disabled.");
    }

    public static MostlyVanillaRoles getInstance() { return instance; }
    public RoleManager getRoleManager()            { return roleManager; }
    public TabManager  getTabManager()             { return tabManager; }
    public GlowManager getGlowManager()            { return glowManager; }
}
