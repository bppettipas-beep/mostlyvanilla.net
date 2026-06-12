package com.mostlyvanilla.roles;

import com.mostlyvanilla.roles.commands.DelOreCommand;
import com.mostlyvanilla.roles.commands.DelStashCommand;
import com.mostlyvanilla.roles.commands.DutyCommand;
import com.mostlyvanilla.roles.commands.DutyRequireCommand;
import com.mostlyvanilla.roles.commands.LinkCommand;
import com.mostlyvanilla.roles.commands.UnlinkCommand;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MostlyVanillaRoles extends JavaPlugin {

    private static MostlyVanillaRoles instance;
    private RoleManager roleManager;
    private TabManager  tabManager;
    private GlowManager glowManager;
    private ApiClient   apiClient;

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        saveDefaultConfig();

        // Set up Discord bot API client if configured
        String botUrl   = getConfig().getString("bot-api-url", "").trim();
        String apiSecret = getConfig().getString("api-secret",   "").trim();
        if (!botUrl.isEmpty() && !apiSecret.isEmpty()) {
            apiClient = new ApiClient(botUrl, apiSecret);
            apiClient.setLogger(getLogger());
        } else {
            getLogger().info("Bot API not configured — Discord sync disabled. Set bot-api-url and api-secret in config.yml.");
        }

        roleManager = new RoleManager(this);
        if (apiClient != null) roleManager.setApiClient(apiClient);
        roleManager.load();

        tabManager = new TabManager(this);
        tabManager.setupPingObjective();
        tabManager.start();

        glowManager = new GlowManager(this);

        var linkCmd = getCommand("link");
        if (linkCmd != null) linkCmd.setExecutor(new LinkCommand(this));

        var unlinkCmd = getCommand("unlink");
        if (unlinkCmd != null) {
            var exec = new UnlinkCommand(this);
            unlinkCmd.setExecutor(exec);
            unlinkCmd.setTabCompleter(exec);
        }

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

        // Poll for Discord→MC role changes every 5 seconds
        if (apiClient != null) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, this::pollDiscordRoles, 100L, 100L);
        }

        getLogger().info("MostlyVanillaRoles enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MostlyVanillaRoles disabled.");
    }

    private void pollDiscordRoles() {
        String json = apiClient.pollPendingRoles();
        if (json == null || json.isBlank() || json.equals("[]")) return;
        List<PendingRoleChange> changes = parseRoleChanges(json);
        if (changes.isEmpty()) return;
        getServer().getScheduler().runTask(this, () -> {
            for (PendingRoleChange c : changes) {
                try {
                    UUID uuid = UUID.fromString(c.mcUuid());
                    if (c.assign() && c.gameRole() != null) {
                        roleManager.assignRoleFromDiscord(uuid, c.gameRole());
                    } else if (!c.assign() && c.gameRole() != null) {
                        roleManager.removePlayerRoleIfMatches(uuid, c.gameRole());
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        });
    }

    private record PendingRoleChange(String mcUuid, String gameRole, boolean assign) {}

    private List<PendingRoleChange> parseRoleChanges(String json) {
        List<PendingRoleChange> result = new ArrayList<>();
        int pos = 0;
        while (pos < json.length()) {
            int open = json.indexOf('{', pos);
            if (open < 0) break;
            int close = json.indexOf('}', open);
            if (close < 0) break;
            String obj = json.substring(open + 1, close);

            String mcUuid   = extractField(obj, "mc_uuid");
            String gameRole = extractField(obj, "game_role");
            String assignStr = extractField(obj, "assign");
            boolean assign  = "1".equals(assignStr) || "true".equals(assignStr);

            if (mcUuid != null && !mcUuid.isEmpty()) {
                result.add(new PendingRoleChange(mcUuid, gameRole, assign));
            }
            pos = close + 1;
        }
        return result;
    }

    private String extractField(String obj, String key) {
        String marker = "\"" + key + "\":";
        int idx = obj.indexOf(marker);
        if (idx < 0) return null;
        int vs = idx + marker.length();
        while (vs < obj.length() && obj.charAt(vs) == ' ') vs++;
        if (vs >= obj.length()) return null;
        char c = obj.charAt(vs);
        if (c == '"') {
            int end = obj.indexOf('"', vs + 1);
            return end < 0 ? null : obj.substring(vs + 1, end);
        }
        if (obj.startsWith("null", vs)) return null;
        int end = vs;
        while (end < obj.length() && ",}\"".indexOf(obj.charAt(end)) < 0) end++;
        return obj.substring(vs, end).trim();
    }

    public static MostlyVanillaRoles getInstance() { return instance; }
    public RoleManager getRoleManager()            { return roleManager; }
    public TabManager  getTabManager()             { return tabManager; }
    public GlowManager getGlowManager()            { return glowManager; }
    public ApiClient   getApiClient()              { return apiClient; }
}
