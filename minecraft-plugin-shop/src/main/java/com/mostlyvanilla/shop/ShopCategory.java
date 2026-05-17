package com.mostlyvanilla.shop;

import org.bukkit.Material;

import java.util.List;

public record ShopCategory(
    String key,
    String displayName,
    Material icon,
    int mainSlot,
    List<ShopItem> items
) {}
