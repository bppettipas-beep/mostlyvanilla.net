package com.mostlyvanilla.economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EconomyManager {

    private final MostlyVanillaEconomy plugin;
    private final File dataFolder;

    private final Set<String> currencies = new HashSet<>();
    private final Map<String, Map<UUID, Double>> balances = new HashMap<>();
    private String mainCurrency = null;

    public EconomyManager(MostlyVanillaEconomy plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
    }

    public void load() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File currenciesFile = new File(plugin.getDataFolder(), "currencies.yml");
        if (currenciesFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(currenciesFile);
            List<String> list = config.getStringList("currencies");
            currencies.addAll(list);
            mainCurrency = config.getString("main", null);
        }

        for (String currency : currencies) {
            loadCurrencyData(currency);
        }
    }

    private void loadCurrencyData(String currency) {
        File file = new File(dataFolder, currency + ".yml");
        Map<UUID, Double> data = new HashMap<>();
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                try {
                    data.put(UUID.fromString(key), config.getDouble(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        balances.put(currency, data);
    }

    public void save() {
        saveCurrencyList();
        for (String currency : currencies) {
            saveCurrencyData(currency);
        }
    }

    private void saveCurrencyList() {
        File file = new File(plugin.getDataFolder(), "currencies.yml");
        FileConfiguration config = new YamlConfiguration();
        config.set("currencies", new ArrayList<>(currencies));
        if (mainCurrency != null) config.set("main", mainCurrency);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save currencies.yml: " + e.getMessage());
        }
    }

    private void saveCurrencyData(String currency) {
        File file = new File(dataFolder, currency + ".yml");
        FileConfiguration config = new YamlConfiguration();
        Map<UUID, Double> data = balances.getOrDefault(currency, Collections.emptyMap());
        for (Map.Entry<UUID, Double> entry : data.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + currency + ".yml: " + e.getMessage());
        }
    }

    // --- Currency management ---

    public boolean createCurrency(String name) {
        String key = name.toLowerCase();
        if (currencies.contains(key)) return false;
        currencies.add(key);
        balances.put(key, new HashMap<>());
        saveCurrencyList();
        return true;
    }

    public boolean deleteCurrency(String name) {
        String key = name.toLowerCase();
        if (!currencies.contains(key)) return false;
        currencies.remove(key);
        balances.remove(key);
        if (key.equals(mainCurrency)) mainCurrency = null;
        File file = new File(dataFolder, key + ".yml");
        if (file.exists()) file.delete();
        saveCurrencyList();
        return true;
    }

    public boolean currencyExists(String name) {
        return currencies.contains(name.toLowerCase());
    }

    public Set<String> getCurrencies() {
        return Collections.unmodifiableSet(currencies);
    }

    // --- Main currency ---

    public String getMainCurrency() {
        return mainCurrency;
    }

    public boolean setMainCurrency(String name) {
        String key = name.toLowerCase();
        if (!currencies.contains(key)) return false;
        mainCurrency = key;
        saveCurrencyList();
        return true;
    }

    // --- Balance operations ---

    public double getBalance(UUID uuid, String currency) {
        return balances.getOrDefault(currency.toLowerCase(), Collections.emptyMap())
                .getOrDefault(uuid, 0.0);
    }

    public void setBalance(UUID uuid, String currency, double amount) {
        String key = currency.toLowerCase();
        balances.computeIfAbsent(key, k -> new HashMap<>()).put(uuid, amount);
        saveCurrencyData(key);
    }

    public void giveBalance(UUID uuid, String currency, double amount) {
        setBalance(uuid, currency, getBalance(uuid, currency) + amount);
    }

    public boolean takeBalance(UUID uuid, String currency, double amount) {
        double current = getBalance(uuid, currency);
        if (current < amount) return false;
        setBalance(uuid, currency, current - amount);
        return true;
    }

    public Map<UUID, Double> getTopBalances(String currency, int limit) {
        return balances.getOrDefault(currency.toLowerCase(), Collections.emptyMap())
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
