package mod.beethoven92.betterendforge.common.world.generator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import git.jbredwards.nether_api.api.world.INetherAPIChunkGenerator;
import net.minecraft.block.material.Material;
import net.minecraft.world.chunk.ChunkPrimer;

/** Read-only raw density previews; does not create, load or populate neighbouring chunks. */
final class OriginalIslandTerrain
{
    private static final Map<INetherAPIChunkGenerator, Cache> CACHES = new WeakHashMap<>();

    private OriginalIslandTerrain() {}

    static synchronized int span(INetherAPIChunkGenerator generator, int x, int z)
    {
        Cache cache = CACHES.computeIfAbsent(generator, ignored -> new Cache());
        int chunkX = x >> 4, chunkZ = z >> 4;
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        int[] columns = cache.computeIfAbsent(key, ignored -> {
            ChunkPrimer raw = new ChunkPrimer();
            // This calls only vanilla's density interpolation. It does not
            // run buildSurfaces, generateChunk, caves, structures or events.
            generator.setBlocksInPrimer(chunkX, chunkZ, raw);
            int[] result = new int[256];
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                {
                    int top = 255;
                    while (top >= 0 && raw.getBlockState(lx, top, lz).getMaterial() == Material.AIR) top--;
                    int bottom = top;
                    while (bottom > 0 && raw.getBlockState(lx, bottom - 1, lz).getMaterial() != Material.AIR) bottom--;
                    result[lz * 16 + lx] = top < 0 ? -1 : (top << 8) | bottom;
                }
            return result;
        });
        return columns[(z & 15) * 16 + (x & 15)];
    }

    static boolean supported(int span)
    {
        return span >= 0 && (span >> 8) >= 8 && (span >> 8) - (span & 255) + 1 >= 5;
    }

    private static final class Cache extends LinkedHashMap<Long, int[]>
    {
        Cache() { super(128, 0.75f, true); }
        @Override protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) { return size() > 128; }
    }
}
