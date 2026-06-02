package com.mostlyvanilla.spawners;

import org.bukkit.entity.EntityType;

public enum SpawnerType {
    SKELETON   ("Skeleton",     "☠",  EntityType.SKELETON),
    ZOMBIE     ("Zombie",       "🧟", EntityType.ZOMBIE),
    PIG        ("Pig",          "🐷", EntityType.PIG),
    COW        ("Cow",          "🐄", EntityType.COW),
    SPIDER     ("Spider",       "🕷", EntityType.SPIDER),
    ZOMBIE_PIGLIN("Zombie Piglin","👺",EntityType.ZOMBIFIED_PIGLIN),
    BLAZE      ("Blaze",        "🔥", EntityType.BLAZE),
    CREEPER    ("Creeper",      "💣", EntityType.CREEPER),
    IRON_GOLEM ("Iron Golem",   "⚙",  EntityType.IRON_GOLEM);

    private final String displayName;
    private final String icon;
    private final EntityType entityType;

    SpawnerType(String displayName, String icon, EntityType entityType) {
        this.displayName = displayName;
        this.icon = icon;
        this.entityType = entityType;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon()        { return icon; }
    public EntityType getEntityType() { return entityType; }

    public String getTitle(int stack) {
        return icon + " " + displayName + " Spawner" + (stack > 1 ? " [×" + stack + "]" : "");
    }

    public static SpawnerType fromString(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static SpawnerType fromEntityType(EntityType et) {
        if (et == null) return null;
        for (SpawnerType t : values()) {
            if (t.entityType == et) return t;
        }
        return null;
    }
}
