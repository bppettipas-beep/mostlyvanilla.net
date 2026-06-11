package com.mostlyvanilla.shop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;

public record ShopItem(
    Material material,
    String displayName,
    Map<Enchantment, Integer> enchants,
    List<String> lore,
    int amount,
    double price,
    PotionType potionType,
    boolean potionExtended,
    boolean potionUpgraded
) {}
