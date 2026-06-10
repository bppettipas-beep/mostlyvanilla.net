package com.mostlyvanilla.anticheat;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    public final UUID uuid;

    // Anti-xray: positions of blocks we sent as fake stone (encoded x<<40|y<<20|z)
    public final Set<Long> fakeBlocks = new HashSet<>();
    public Location lastRevealedAt = null;

    // X-ray detector
    public int totalBlocksMined = 0;
    public int oresMined = 0;

    // Speed check
    public int fastTicks = 0;
    public Location lastLocation = null;
    public long lastElytraChangeMs = 0;
    public long lastKnockbackMs    = 0;
    public long lastRiptideMs      = 0;

    // Fly check
    public int airTicks = 0;
    public long lastGlidingMs = 0;

    // Reach check
    public int reachBuffer = 0;

    // AutoTotem check
    public long lastTotemUseMs = 0;

    // NoFall check
    public double noFallMaxY = 0;
    public boolean noFallWasOnGround = true;

    // Jump Reset check
    public final Deque<Long> jumpTimestamps = new ArrayDeque<>();
    public long lastCombatMs = 0;
    public int jumpResetCombo = 0;

    // Kill-aura: recent attack timestamps per entity UUID
    public final Map<UUID, Long> recentAttacks = new HashMap<>();

    // Violations per check name
    public final Map<String, Integer> violations = new HashMap<>();

    // Alert cooldowns: check name → last alert time ms
    public final Map<String, Long> alertCooldowns = new HashMap<>();

    // Set to true the moment a punishment is queued, preventing double-punishment
    // (e.g. global VL threshold and per-check threshold both trigger in the same flag() call)
    public boolean punishmentScheduled = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public int getViolations(String check) {
        return violations.getOrDefault(check, 0);
    }

    public int addViolation(String check, int amount) {
        int v = violations.getOrDefault(check, 0) + amount;
        violations.put(check, v);
        return v;
    }

    public void resetViolations() {
        violations.clear();
    }

    public int totalViolations() {
        return violations.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static long encodePos(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
