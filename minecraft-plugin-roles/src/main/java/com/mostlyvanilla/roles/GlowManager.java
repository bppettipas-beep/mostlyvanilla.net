package com.mostlyvanilla.roles;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.*;
import java.util.List;

public class GlowManager {

    private static final byte GLOW_BIT = 0x40; // entity shared-flags bit 6 = glowing outline

    private final MostlyVanillaRoles plugin;
    private boolean available = false;

    // Reflective handles — resolved once at startup, null if unavailable
    private Method  getHandle;          // CraftPlayer.getHandle() → ServerPlayer
    private Method  getId;              // Entity.getId() → int
    private Method  getEntityData;      // Entity.getEntityData() → SynchedEntityData
    private Object  flagsAccessor;      // Entity.DATA_SHARED_FLAGS_ID (EntityDataAccessor<Byte>)
    private Method  synchedGet;         // SynchedEntityData.get(EntityDataAccessor) → T
    private Method  dataValueCreate;    // SynchedEntityData.DataValue.create(accessor, value)
    private Constructor<?> packetCtor;  // ClientboundSetEntityDataPacket(int, List)
    private Field   connectionField;    // ServerPlayer.connection
    private Method  sendMethod;         // ServerCommonPacketListenerImpl.send(Packet)

    public GlowManager(MostlyVanillaRoles plugin) {
        this.plugin = plugin;
        try {
            initReflection();
            available = true;
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 4L, 4L);
        } catch (Exception e) {
            plugin.getLogger().warning("[GlowManager] NMS reflection failed — duty glow unavailable. (" + e + ")");
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private void initReflection() throws Exception {
        // Derive CraftPlayer class path from the running server class name
        // 1.21+: "org.bukkit.craftbukkit.CraftServer"
        // 1.20.4: "org.bukkit.craftbukkit.v1_20_R3.CraftServer"
        String serverClass = Bukkit.getServer().getClass().getName();
        String cbPkg       = serverClass.substring(0, serverClass.lastIndexOf('.'));
        Class<?> craftPlayer = Class.forName(cbPkg + ".entity.CraftPlayer");

        getHandle = craftPlayer.getMethod("getHandle");
        Class<?> serverPlayerClass = getHandle.getReturnType(); // ServerPlayer

        getId          = findMethod(serverPlayerClass, "getId");
        getEntityData  = findMethod(serverPlayerClass, "getEntityData");

        // DATA_SHARED_FLAGS_ID lives in some ancestor of ServerPlayer (Entity)
        flagsAccessor = findStaticField(serverPlayerClass, "DATA_SHARED_FLAGS_ID");

        Class<?> synchedClass = getEntityData.getReturnType(); // SynchedEntityData
        synchedGet = findMethodByParamCount(synchedClass, "get", 1);

        // DataValue is a nested record inside SynchedEntityData
        Class<?> dataValueClass = findNestedClass(synchedClass, "DataValue");
        dataValueCreate = findStaticMethodByName(dataValueClass, "create");

        // ClientboundSetEntityDataPacket(int entityId, List<DataValue<?>>)
        Class<?> pktClass = Class.forName(
            "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
        packetCtor = pktClass.getDeclaredConstructor(int.class, List.class);
        packetCtor.setAccessible(true);

        // ServerPlayer.connection → ServerGamePacketListenerImpl (inherits send())
        connectionField = findField(serverPlayerClass, "connection");
        Class<?> connClass = connectionField.getType();
        sendMethod = findMethodByParamCount(connClass, "send", 1);
        sendMethod.setAccessible(true);
    }

    // ── Periodic re-apply (re-sends every 4 ticks so metadata updates don't clear it) ──

    private void tick() {
        RoleManager rm = plugin.getRoleManager();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!rm.isOnDuty(staff.getUniqueId())) continue;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(staff)) continue;
                sendGlow(staff, target, true);
            }
        }
    }

    /** Call immediately when a staff member goes off duty to clear the client-side glow. */
    public void onDutyEnd(Player staff) {
        if (!available) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(staff)) continue;
            sendGlow(staff, target, false);
        }
    }

    // ── Packet send ───────────────────────────────────────────────────────────

    private void sendGlow(Player viewer, Player target, boolean glow) {
        if (!available) return;
        try {
            Object nmsTarget = getHandle.invoke(target);
            Object nmsViewer = getHandle.invoke(viewer);
            int    entityId  = (int) getId.invoke(nmsTarget);
            Object entityData = getEntityData.invoke(nmsTarget);
            byte   real  = (Byte) synchedGet.invoke(entityData, flagsAccessor);
            byte   flags = glow ? (byte)(real | GLOW_BIT) : (byte)(real & ~GLOW_BIT);
            if (flags == real) return; // no change needed
            Object dataValue = dataValueCreate.invoke(null, flagsAccessor, flags);
            Object packet    = packetCtor.newInstance(entityId, List.of(dataValue));
            Object conn      = connectionField.get(nmsViewer);
            sendMethod.invoke(conn, packet);
        } catch (Exception ignored) {
            // Silently swallow — missing a single glow tick is harmless
        }
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static Method findMethod(Class<?> cls, String name) throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) { m.setAccessible(true); return m; }
            }
        }
        throw new NoSuchMethodException(name + " in " + cls.getName());
    }

    private static Method findMethodByParamCount(Class<?> cls, String name, int params)
            throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == params) {
                    m.setAccessible(true); return m;
                }
            }
        }
        throw new NoSuchMethodException(name + "(params=" + params + ") in " + cls.getName());
    }

    private static Method findStaticMethodByName(Class<?> cls, String name)
            throws NoSuchMethodException {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true); return m;
            }
        }
        throw new NoSuchMethodException("static " + name + " in " + cls.getName());
    }

    private static Object findStaticField(Class<?> cls, String name) throws Exception {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f.get(null); }
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " (static) in " + cls.getName());
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " in " + cls.getName());
    }

    private static Class<?> findNestedClass(Class<?> cls, String simpleName)
            throws ClassNotFoundException {
        for (Class<?> nested : cls.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(simpleName)) return nested;
        }
        throw new ClassNotFoundException(cls.getName() + "$" + simpleName);
    }
}
