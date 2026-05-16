package com.mostlyvanilla.economy;

import com.mostlyvanilla.economy.commands.BalanceCommand;
import com.mostlyvanilla.economy.commands.BaltopCommand;
import com.mostlyvanilla.economy.commands.EcoCommand;
import com.mostlyvanilla.economy.commands.PayCommand;
import com.mostlyvanilla.economy.gui.BaltopGui;
import org.bukkit.plugin.java.JavaPlugin;

public class MostlyVanillaEconomy extends JavaPlugin {

    private EconomyManager economyManager;

    public EconomyManager getEconomyManager() { return economyManager; }

    @Override
    public void onEnable() {
        economyManager = new EconomyManager(this);
        economyManager.load();

        BaltopGui baltopGui = new BaltopGui(economyManager, this);

        EcoCommand ecoCommand = new EcoCommand(economyManager);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);

        BalanceCommand balanceCommand = new BalanceCommand(economyManager);
        getCommand("balance").setExecutor(balanceCommand);
        getCommand("balance").setTabCompleter(balanceCommand);

        PayCommand payCommand = new PayCommand(economyManager);
        getCommand("pay").setExecutor(payCommand);
        getCommand("pay").setTabCompleter(payCommand);

        BaltopCommand baltopCommand = new BaltopCommand(economyManager, baltopGui);
        getCommand("baltop").setExecutor(baltopCommand);
        getCommand("baltop").setTabCompleter(baltopCommand);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            registerPlaceholders();
            getLogger().info("PlaceholderAPI found — economy placeholders registered.");
        }

        getLogger().info("MostlyVanillaEconomy enabled!");
    }

    private void registerPlaceholders() {
        new EconomyExpansion(economyManager).register();
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.save();
        }
        getLogger().info("MostlyVanillaEconomy disabled!");
    }
}
