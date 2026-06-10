package com.mostlyvanilla.crates;

import org.bukkit.Material;

import java.util.Map;

public record CrateReward(String name, Material material, int amount, int weight, String spawnerType, Map<String, Integer> enchantments) {

    public CrateReward(String name, Material material, int amount, int weight) {
        this(name, material, amount, weight, null, Map.of());
    }

    public CrateReward(String name, Material material, int amount, int weight, String spawnerType) {
        this(name, material, amount, weight, spawnerType, Map.of());
    }
}
