package com.mostlyvanilla.economy;

import com.mostlyvanilla.economy.commands.EcoCommand;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EconomyExpansion extends PlaceholderExpansion {

    private final EconomyManager economy;

    public EconomyExpansion(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public @NotNull String getIdentifier() { return "economy"; }

    @Override
    public @NotNull String getAuthor() { return "MostlyVanilla"; }

    @Override
    public @NotNull String getVersion() { return "1.0.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // %economy_balance% → main currency balance
        if (params.equals("balance")) {
            String main = economy.getMainCurrency();
            if (main == null) return "0";
            return EcoCommand.fmt(economy.getBalance(player.getUniqueId(), main));
        }

        // %economy_currency% → main currency name
        if (params.equals("currency")) {
            String main = economy.getMainCurrency();
            return main != null ? main : "none";
        }

        // %economy_balance_<currency>% → balance for a specific currency
        if (params.startsWith("balance_")) {
            String currency = params.substring(8);
            if (!economy.currencyExists(currency)) return "0";
            return EcoCommand.fmt(economy.getBalance(player.getUniqueId(), currency));
        }

        return null;
    }
}
