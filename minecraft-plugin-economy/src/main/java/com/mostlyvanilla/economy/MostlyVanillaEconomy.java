package com.mostlyvanilla.economy;

import com.mostlyvanilla.economy.commands.BalanceCommand;
import com.mostlyvanilla.economy.commands.EcoCommand;
import com.mostlyvanilla.economy.commands.PayCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaEconomy extends JavaPlugin {

    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        economyManager = new EconomyManager(this);
        economyManager.load();

        EcoCommand ecoCommand = new EcoCommand(economyManager);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);

        BalanceCommand balanceCommand = new BalanceCommand(economyManager);
        getCommand("balance").setExecutor(balanceCommand);
        getCommand("balance").setTabCompleter(balanceCommand);

        PayCommand payCommand = new PayCommand(economyManager);
        getCommand("pay").setExecutor(payCommand);
        getCommand("pay").setTabCompleter(payCommand);

        getLogger().info("MostlyVanillaEconomy enabled!");
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.save();
        }
        getLogger().info("MostlyVanillaEconomy disabled!");
    }
}
