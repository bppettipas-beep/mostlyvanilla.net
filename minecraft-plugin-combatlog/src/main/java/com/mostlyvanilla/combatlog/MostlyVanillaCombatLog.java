package com.mostlyvanilla.combatlog;

import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaCombatLog extends JavaPlugin {

    @Override
    public void onEnable() {
        CombatManager manager = new CombatManager();
        getServer().getPluginManager().registerEvents(new CombatListener(manager), this);

        // Update action bars every second
        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);

        getLogger().info("MostlyVanilla CombatLog enabled.");
    }
}
