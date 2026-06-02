package com.mostlyvanilla.spawn;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class VoidGenerator extends ChunkGenerator {

    @Override
    @SuppressWarnings("deprecation")
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        return createChunkData(world);
    }
}
