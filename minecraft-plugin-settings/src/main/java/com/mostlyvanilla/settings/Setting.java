package com.mostlyvanilla.settings;

import org.bukkit.Material;

public enum Setting {

    NIGHT_VISION(
        "Night Vision", Material.GOLDEN_CARROT, 10, false,
        "Grants permanent night vision so you can see in the dark."
    ),
    TPA_AUTO_ACCEPT(
        "TPA Auto-Accept", Material.ENDER_PEARL, 12, false,
        "Automatically accepts all incoming teleport requests."
    ),
    ACCEPT_PAYMENTS(
        "Accept Payments", Material.GOLD_INGOT, 14, true,
        "Allows other players to send you money via /pay."
    ),
    PUBLIC_CHAT(
        "Show Public Chat", Material.PAPER, 16, true,
        "Shows messages from other players in public chat."
    ),
    JOIN_MESSAGES(
        "Join/Leave Messages", Material.OAK_DOOR, 19, true,
        "Shows when players join or leave the server."
    ),
    DEATH_MESSAGES(
        "Death Messages", Material.SKELETON_SKULL, 21, true,
        "Shows messages when players die."
    );

    public final String displayName;
    public final Material icon;
    public final int slot;
    public final boolean defaultValue;
    public final String description;

    Setting(String displayName, Material icon, int slot, boolean defaultValue, String description) {
        this.displayName  = displayName;
        this.icon         = icon;
        this.slot         = slot;
        this.defaultValue = defaultValue;
        this.description  = description;
    }
}
