package com.mostlyvanilla.home.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class HomeGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final int page;

    public HomeGuiHolder(int page) { this.page = page; }

    public int getPage() { return page; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
