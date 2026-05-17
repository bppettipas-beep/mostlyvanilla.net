package com.mostlyvanilla.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Home {

    private String name;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;

    public Home(String name, Location loc) {
        this.name  = name;
        this.world = loc.getWorld().getName();
        this.x     = loc.getX();
        this.y     = loc.getY();
        this.z     = loc.getZ();
        this.yaw   = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    public Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
        this.name  = name;
        this.world = world;
        this.x     = x;
        this.y     = y;
        this.z     = z;
        this.yaw   = yaw;
        this.pitch = pitch;
    }

    public String getName()   { return name; }
    public String getWorld()  { return world; }
    public double getX()      { return x; }
    public double getY()      { return y; }
    public double getZ()      { return z; }
    public float  getYaw()    { return yaw; }
    public float  getPitch()  { return pitch; }

    public void setName(String name) { this.name = name; }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
