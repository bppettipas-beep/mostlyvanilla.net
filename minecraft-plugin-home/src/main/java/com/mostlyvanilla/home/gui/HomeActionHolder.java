package com.mostlyvanilla.home.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class HomeActionHolder implements InventoryHolder {

    private Inventory inventory;
    private final String homeName;
    private final int returnPage;

    public HomeActionHolder(String homeName, int returnPage) {
        this.homeName   = homeName;
        this.returnPage = returnPage;
    }

    public String getHomeName()  { return homeName; }
    public int    getReturnPage() { return returnPage; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
