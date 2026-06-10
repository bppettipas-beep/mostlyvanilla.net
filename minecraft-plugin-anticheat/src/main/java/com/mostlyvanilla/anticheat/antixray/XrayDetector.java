package com.mostlyvanilla.anticheat.antixray;

import com.mostlyvanilla.anticheat.MostlyVanillaAnticheat;
import com.mostlyvanilla.anticheat.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class XrayDetector {

    private final MostlyVanillaAnticheat plugin;
    private final AntiXrayEngine engine;

    public XrayDetector(MostlyVanillaAnticheat plugin, AntiXrayEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    public void onBlockBreak(Player player, Material material) {
        if (!plugin.getConfig().getBoolean("xray-detector.enabled", true)) return;

        PlayerData data = plugin.getData(player.getUniqueId());
        data.totalBlocksMined++;
        if (engine.isHiddenOre(material)) data.oresMined++;

        int minMined = plugin.getConfig().getInt("xray-detector.min-blocks-mined", 35);
        double threshold = plugin.getConfig().getDouble("xray-detector.ore-ratio-threshold", 0.25);

        if (data.totalBlocksMined < minMined) return;

        double ratio = (double) data.oresMined / data.totalBlocksMined;
        if (ratio >= threshold) {
            plugin.getViolationManager().flag(player, "XRAY",
                    String.format("ratio=%.2f (%d/%d)", ratio, data.oresMined, data.totalBlocksMined), 5);
            // Partial reset: keep half the sample so persistent xrayers are flagged sooner next time
            data.totalBlocksMined = minMined / 2;
            data.oresMined = (int) (data.oresMined * 0.5);
        }
    }
}
