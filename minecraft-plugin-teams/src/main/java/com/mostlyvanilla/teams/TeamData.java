package com.mostlyvanilla.teams;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;

import java.util.*;

public class TeamData {

    // Available team colors (name → NamedTextColor)
    public static final Map<String, NamedTextColor> COLORS = new LinkedHashMap<>();
    static {
        COLORS.put("white",        NamedTextColor.WHITE);
        COLORS.put("gray",         NamedTextColor.GRAY);
        COLORS.put("dark_gray",    NamedTextColor.DARK_GRAY);
        COLORS.put("black",        NamedTextColor.BLACK);
        COLORS.put("red",          NamedTextColor.RED);
        COLORS.put("dark_red",     NamedTextColor.DARK_RED);
        COLORS.put("gold",         NamedTextColor.GOLD);
        COLORS.put("yellow",       NamedTextColor.YELLOW);
        COLORS.put("green",        NamedTextColor.GREEN);
        COLORS.put("dark_green",   NamedTextColor.DARK_GREEN);
        COLORS.put("aqua",         NamedTextColor.AQUA);
        COLORS.put("dark_aqua",    NamedTextColor.DARK_AQUA);
        COLORS.put("blue",         NamedTextColor.BLUE);
        COLORS.put("dark_blue",    NamedTextColor.DARK_BLUE);
        COLORS.put("light_purple", NamedTextColor.LIGHT_PURPLE);
        COLORS.put("dark_purple",  NamedTextColor.DARK_PURPLE);
    }

    private final UUID id;
    private String name;
    private String color;
    private UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private Location home;
    private boolean friendlyFire = false;
    private boolean open = false;
    private final Set<UUID> pendingInvites = new HashSet<>();

    public TeamData(UUID id, String name, String color, UUID leader) {
        this.id     = id;
        this.name   = name;
        this.color  = color;
        this.leader = leader;
        this.members.add(leader);
    }

    // ── Scoreboard ID (max 16 chars, stable across renames) ──────────────────
    public String scoreboardId() {
        String hex = id.toString().replace("-", "");
        return "mvt_" + hex.substring(0, 12);
    }

    public NamedTextColor namedColor() {
        return COLORS.getOrDefault(color, NamedTextColor.WHITE);
    }

    // ── Cycle to next color ───────────────────────────────────────────────────
    public void cycleColor() {
        List<String> keys = new ArrayList<>(COLORS.keySet());
        int idx = keys.indexOf(color);
        color = keys.get((idx + 1) % keys.size());
    }

    // ── Getters / setters ─────────────────────────────────────────────────────
    public UUID     getId()                  { return id; }
    public String   getName()               { return name; }
    public void     setName(String n)        { this.name = n; }
    public String   getColor()              { return color; }
    public void     setColor(String c)       { this.color = c; }
    public UUID     getLeader()             { return leader; }
    public void     setLeader(UUID u)        { this.leader = u; }
    public List<UUID> getMembers()          { return members; }
    public Location getHome()               { return home; }
    public void     setHome(Location l)      { this.home = l; }
    public boolean  isFriendlyFire()        { return friendlyFire; }
    public void     setFriendlyFire(boolean b) { this.friendlyFire = b; }
    public boolean  isOpen()                { return open; }
    public void     setOpen(boolean b)       { this.open = b; }
    public Set<UUID> getPendingInvites()    { return pendingInvites; }
}
