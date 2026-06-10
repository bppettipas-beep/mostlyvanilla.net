package com.mostlyvanilla.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class CrateGuiHolder implements InventoryHolder {

    private final CrateType crateType;
    private final boolean preview;
    private Inventory inventory;
    private final Map<Integer, Integer> slotToReward = new HashMap<>();

    public CrateGuiHolder(CrateType crateType, boolean preview) {
        this.crateType = crateType;
        this.preview   = preview;
    }

    public boolean isPreview() { return preview; }

    public CrateType getCrateType()      { return crateType; }

    @Override
    public Inventory getInventory()      { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }

    public void mapSlot(int slot, int rewardIndex) { slotToReward.put(slot, rewardIndex); }

    /** Returns the reward index for this slot, or -1 if it's a filler slot. */
    public int getRewardIndex(int slot)  { return slotToReward.getOrDefault(slot, -1); }
}
