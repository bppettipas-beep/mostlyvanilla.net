package com.mostlyvanilla.teams;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamGuiHolder implements InventoryHolder {

    private final TeamData team;
    private Inventory inventory;

    public TeamGuiHolder(TeamData team) { this.team = team; }

    public TeamData getTeam()               { return team; }
    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv)  { this.inventory = inv; }
}
