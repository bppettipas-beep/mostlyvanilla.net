package com.mostlyvanilla.roles;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class TabHook {

    private static Boolean available = null;
    private static Class<?> tabApiClass;
    private static Class<?> tabPlayerClass;
    private static Method getInstance;
    private static Method getPlayer;
    private static Method getNameTagManager;
    private static Method setPrefix;

    private static boolean init() {
        if (available != null) return available;
        try {
            tabApiClass    = Class.forName("me.neznamy.tab.api.TabAPI");
            tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            getInstance    = tabApiClass.getMethod("getInstance");
            getPlayer      = tabApiClass.getMethod("getPlayer", UUID.class);
            getNameTagManager = tabApiClass.getMethod("getNameTagManager");

            Class<?> nameTagClass = Class.forName("me.neznamy.tab.api.nametag.NameTagManager");
            setPrefix = nameTagClass.getMethod("setPrefix", tabPlayerClass, String.class);

            available = true;
        } catch (Exception e) {
            available = false;
        }
        return available;
    }

    public static void setPrefix(Player player, String prefix) {
        if (!init()) return;
        try {
            Object api     = getInstance.invoke(null);
            Object tabPlayer = getPlayer.invoke(api, player.getUniqueId());
            if (tabPlayer == null) return;
            Object ntm = getNameTagManager.invoke(api);
            if (ntm == null) return;
            setPrefix.invoke(ntm, tabPlayer, prefix != null ? prefix : "");
        } catch (Exception ignored) {}
    }

    public static boolean isAvailable() {
        return init();
    }
}
