package com.mostlyvanilla.rtp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class RtpGuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
