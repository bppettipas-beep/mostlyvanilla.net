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

    // lowercase key → original display name (e.g. "diamonds" → "Diamonds")
    private final Map<String, String> currencies = new LinkedHashMap<>();

    // lowercase currency key → (playerUUID → balance)
    private final Map<String, Map<UUID, Double>> balances = new HashMap<>();

    // stored as original display name (e.g. "Diamonds")
    private String mainCurrency = null;

    public EconomyManager(MostlyVanillaEconomy plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
    }

    public void load() {
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File currenciesFile = new File(plugin.getDataFolder(), "currencies.yml");
        if (currenciesFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(currenciesFile);
            for (String name : config.getStringList("currencies")) {
                currencies.put(name.toLowerCase(), name);
            }
            mainCurrency = config.getString("main", null);
        }

        for (String key : currencies.keySet()) {
            loadCurrencyData(key);
        }
    }

    private void loadCurrencyData(String key) {
        File file = new File(dataFolder, key + ".yml");
        Map<UUID, Double> data = new HashMap<>();
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String k : config.getKeys(false)) {
                try { data.put(UUID.fromString(k), config.getDouble(k)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        balances.put(key, data);
    }

    public void save() {
        saveCurrencyList();
        for (String key : currencies.keySet()) {
            saveCurrencyData(key);
        }
    }

    private void saveCurrencyList() {
        File file = new File(plugin.getDataFolder(), "currencies.yml");
        FileConfiguration config = new YamlConfiguration();
        config.set("currencies", new ArrayList<>(currencies.values())); // save display names
        if (mainCurrency != null) config.set("main", mainCurrency);
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save currencies.yml: " + e.getMessage()); }
    }

    private void saveCurrencyData(String key) {
        File file = new File(dataFolder, key + ".yml");
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : balances.getOrDefault(key, Collections.emptyMap()).entrySet()) {
            config.set(e.getKey().toString(), e.getValue());
        }
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save " + key + ".yml: " + e.getMessage()); }
    }

    // --- Currency management ---

    public boolean createCurrency(String name) {
        String key = name.toLowerCase();
        if (currencies.containsKey(key)) return false;
        currencies.put(key, name);
        balances.put(key, new HashMap<>());
        saveCurrencyList();
        return true;
    }

    public boolean deleteCurrency(String name) {
        String key = name.toLowerCase();
        if (!currencies.containsKey(key)) return false;
        currencies.remove(key);
        balances.remove(key);
        if (mainCurrency != null && mainCurrency.equalsIgnoreCase(name)) mainCurrency = null;
        File file = new File(dataFolder, key + ".yml");
        if (file.exists()) file.delete();
        saveCurrencyList();
        return true;
    }

    public boolean currencyExists(String name) {
        return currencies.containsKey(name.toLowerCase());
    }

    /** Returns display names (original casing). */
    public Collection<String> getCurrencies() {
        return Collections.unmodifiableCollection(currencies.values());
    }

    /** Returns the original display name for a currency (e.g. "diamonds" → "Diamonds"). */
    public String getDisplayName(String name) {
        return currencies.getOrDefault(name.toLowerCase(), name);
    }

    // --- Main currency ---

    public String getMainCurrency() { return mainCurrency; }

    public boolean setMainCurrency(String name) {
        String key = name.toLowerCase();
        if (!currencies.containsKey(key)) return false;
        mainCurrency = currencies.get(key); // store original display name
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
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
