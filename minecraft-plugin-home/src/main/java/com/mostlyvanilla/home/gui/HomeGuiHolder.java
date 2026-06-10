package com.mostlyvanilla.home.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HomeGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final int page;
    private final Map<Integer, String> slotToHome  = new HashMap<>();
    private final Set<Integer>         emptySlots  = new HashSet<>();
    private final Map<Integer, String> lockedSlots = new HashMap<>();
    private int actionSlot = -1;

    public HomeGuiHolder(int page) { this.page = page; }

    public int  getPage()                     { return page; }
    public void setActionSlot(int slot)       { actionSlot = slot; }
    public int  getActionSlot()               { return actionSlot; }

    public void mapHome(int slot, String name)    { slotToHome.put(slot, name); }
    public void addEmpty(int slot)                { emptySlots.add(slot); }
    public void addLocked(int slot, String role)  { lockedSlots.put(slot, role); }

    public String  getHomeName(int slot)    { return slotToHome.get(slot); }
    public boolean isEmptySlot(int slot)    { return emptySlots.contains(slot); }
    public boolean isLockedSlot(int slot)   { return lockedSlots.containsKey(slot); }
    public String  getLockedRole(int slot)  { return lockedSlots.get(slot); }

    @Override public Inventory getInventory()           { return inventory; }
    public void setInventory(Inventory inv)             { this.inventory = inv; }
}
