package mod.beethoven92.betterendforge.common.world.generator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import git.jbredwards.nether_api.api.world.INetherAPIChunkGenerator;
import mod.beethoven92.betterendforge.common.init.ModBiomes;
import mod.beethoven92.betterendforge.common.init.ModBlocks;
import mod.beethoven92.betterendforge.common.world.biome.BetterEndBiome;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.ChunkPrimer;

/** Shapes the whole primer after biome surfaces, before caves, ores and plants. */
public final class EndBiomeTerrain
{
    private static volatile Fields cached;
    private static final Map<BiomeProvider, TransitionCache> TRANSITIONS = new WeakHashMap<>();
    private EndBiomeTerrain() {}

    public static BiomeTerrainNoise noise(long seed)
    {
        Fields fields = cached;
        if (fields == null || fields.seed != seed)
        {
            fields = new Fields(seed);
            cached = fields;
        }
        return fields.noise;
    }

    public static boolean matches(Biome biome, BetterEndBiome expected)
    {
        return biome == expected.getBiome() || biome == expected.getActualBiome()
                || expected.getID().equals(biome.getRegistryName());
    }

    public static boolean isLake(Biome biome)
    {
        return matches(biome, ModBiomes.MEGALAKE) || matches(biome, ModBiomes.MEGALAKE_GROVE);
    }

    private static int terrainKind(Biome biome)
    {
        if (matches(biome, ModBiomes.MEGALAKE_GROVE)) return BiomeTransitionField.LAKE | BiomeTransitionField.GROVE;
        if (matches(biome, ModBiomes.MEGALAKE)) return BiomeTransitionField.LAKE;
        if (matches(biome, ModBiomes.PAINTED_MOUNTAINS)) return BiomeTransitionField.MOUNTAIN | BiomeTransitionField.PAINTED;
        if (matches(biome, ModBiomes.CRYSTAL_MOUNTAINS)) return BiomeTransitionField.MOUNTAIN;
        return 0;
    }

    public static void buildChunkSurface(INetherAPIChunkGenerator generator, ChunkPrimer primer, int chunkX, int chunkZ)
    {
        World world = generator.getWorld();
        BiomeTransitionField edges = transitionField(world, chunkX, chunkZ);
        if (!edges.isActive()) return;
        BiomeTerrainNoise fields = noise(world.getSeed());
        IslandEdgeField island = null;
        for (int dx : new int[] { 0, 8, 15 })
            for (int dz : new int[] { 0, 8, 15 })
                if (island == null && (edges.distance(BiomeTransitionField.LAKE, chunkX * 16 + dx, chunkZ * 16 + dz) > -48
                        || edges.distance(BiomeTransitionField.MOUNTAIN, chunkX * 16 + dx, chunkZ * 16 + dz) > -32))
                    island = new IslandEdgeField(chunkX, chunkZ, 60,
                            (x, z) -> OriginalIslandTerrain.supported(OriginalIslandTerrain.span(generator, x, z)));
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++)
            {
                int x = chunkX * 16 + lx, z = chunkZ * 16 + lz;
                int kind = terrainKind(world.getBiomeProvider().getBiome(new BlockPos(x, 0, z)));
                int span = island == null ? -1 : OriginalIslandTerrain.span(generator, x, z);
                buildColumn(primer, fields, edges, island, span, x, z, kind);
            }
    }

    static synchronized BiomeTransitionField transitionField(World world, int chunkX, int chunkZ)
    {
        BiomeProvider provider = world.getBiomeProvider();
        TransitionCache cache = TRANSITIONS.computeIfAbsent(provider, ignored -> new TransitionCache());
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return cache.computeIfAbsent(key, ignored -> new BiomeTransitionField(chunkX, chunkZ,
                (x, z) -> terrainKind(provider.getBiome(new BlockPos(x, 0, z)))));
    }

    private static void buildColumn(ChunkPrimer primer, BiomeTerrainNoise fields,
            BiomeTransitionField edges, IslandEdgeField island, int originalSpan, int x, int z, int kind)
    {
        int lx = x & 15, lz = z & 15;
        int ground = 255;
        while (ground > 0 && primer.getBlockState(lx, ground, lz).getBlock() == Blocks.AIR) ground--;
        IBlockState originalCap = primer.getBlockState(lx, ground, lz);
        boolean lake = (kind & BiomeTransitionField.LAKE) != 0;
        boolean crystal = (kind & BiomeTransitionField.MOUNTAIN) != 0 && (kind & BiomeTransitionField.PAINTED) == 0;
        double lakeDistance = edges.distance(BiomeTransitionField.LAKE, x, z);
        // Sub-grid biome pockets still retain a dry bank; never extend water
        // into a neighbouring biome merely because of interpolation.
        if (!lake) lakeDistance = Math.min(0, lakeDistance);
        boolean coast = island != null && lakeDistance > -48;
        double mountainBlend = TerrainTransitionProfile.mountainBlend(edges.distance(BiomeTransitionField.MOUNTAIN, x, z));
        boolean islandTransition = island != null && (coast || mountainBlend > 0);
        // Island blending replaces the old Y=45 cutoff. Unsupported original
        // columns remain untouched, so noise cannot build new islands in void.
        if (islandTransition ? !OriginalIslandTerrain.supported(originalSpan) : ground < 45) return;
        double islandDistance = islandTransition ? island.distance(x, z) : 60;
        int thickness = originalSpan < 0 ? 0 : (originalSpan >> 8) - (originalSpan & 255) + 1;
        double mountainIslandBlend = islandTransition ? MountainIslandProfile.blend(islandDistance,
                fields.mountainCoastWidth(x, z), thickness) : 1;
        mountainBlend *= mountainIslandBlend;
        double lakeBlend = TerrainTransitionProfile.lakeBlend(lakeDistance);
        if (mountainBlend == 0 && lakeDistance <= -48) return;
        double paintedMix = TerrainTransitionProfile.paintedMix(edges.distance(BiomeTransitionField.PAINTED, x, z));
        double groveMix = TerrainTransitionProfile.groveMix(edges.distance(BiomeTransitionField.GROVE, x, z));
        double mountain = BiomeTransitionField.mix(fields.mountainHeight(x, z, false),
                fields.mountainHeight(x, z, true), paintedMix) * mountainBlend;
        double elevation = Math.min(235, TerrainTransitionProfile.elevation(ground, mountain,
                fields.lakeHeight(x, z, groveMix), lakeDistance));
        double coastDistance = coast ? islandDistance : 30;
        double coastWidth = fields.shoreWidth(x, z);
        if (coast)
        {
            elevation = IslandShoreProfile.elevation(ground, elevation, coastDistance, coastWidth, fields.shoreRidge(x, z));
            // Keep at least three layers above the original island underside;
            // a thin fringe must not become a floating, flat-bottomed basin.
            elevation = Math.max(elevation, (originalSpan & 255) + 3);
        }
        double densityBlend = mountainBlend * (1 - lakeBlend) * (1 - TerrainTransitionProfile.bankBlend(lakeDistance));
        int top = (int) Math.round(elevation);
        boolean wet = lake && lakeDistance > 0 && top < BiomeTerrainNoise.LAKE_LEVEL
                && (!coast || IslandShoreProfile.canContainWater(coastDistance, coastWidth));
        int max = Math.max(ground, Math.min(239, (int) Math.ceil(elevation + 4 * densityBlend)));
        int foundation = Math.max(1, Math.min(ground, top - 3));
        int highest = foundation;
        double offset = fields.strataOffset(x, z);
        for (int y = foundation; y <= Math.max(max, wet ? BiomeTerrainNoise.LAKE_LEVEL : top); y++)
        {
            boolean rock;
            if (lakeBlend > 0)
                rock = y <= top;
            else
            {
                // Noise relief fades with the foothills too: no residual
                // three-block cliff when the mountain's height reaches zero.
                double perturbation = fields.rockDensity(x, y, z, elevation) - (elevation - y);
                // Coarse/fine 3-D erosion gives the retreating rock face varied
                // ledges; it also reaches zero at the unsupported fringe.
                perturbation += fields.mountainCliffNoise(x, y, z) * (1 - mountainIslandBlend);
                rock = y <= Math.min(ground, top) || elevation - y + perturbation * densityBlend >= 0;
            }
            if (rock)
            {
                highest = y;
                if (y <= ground && y < top - 3) continue; // Keep deep terrain, including modded stone.
                IBlockState state = paintedMix > materialSample(x, y, z) && densityBlend > 0.01
                        ? band(y, offset) : Blocks.END_STONE.getDefaultState();
                primer.setBlockState(lx, y, lz, state);
            }
            else primer.setBlockState(lx, y, lz, wet && y <= BiomeTerrainNoise.LAKE_LEVEL
                    ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        IBlockState cap = originalCap;
        if (lake) cap = wet ? ModBlocks.ENDSTONE_DUST.getDefaultState() : ModBlocks.END_MOSS.getDefaultState();
        else if ((kind & BiomeTransitionField.PAINTED) != 0)
            cap = paintedMix > materialSample(x, highest, z) ? band(highest, offset) : Blocks.END_STONE.getDefaultState();
        else if (crystal)
        {
            double slope = Math.abs(fields.mountainHeight(x + 2, z, false) - fields.mountainHeight(x - 2, z, false))
                    + Math.abs(fields.mountainHeight(x, z + 2, false) - fields.mountainHeight(x, z - 2, false));
            cap = highest < 100 && slope < 6 ? ModBlocks.CRYSTAL_MOSS.getDefaultState() : Blocks.END_STONE.getDefaultState();
        }
        // Neighbouring biomes retain their own surface on the new foothill.
        primer.setBlockState(lx, highest, lz, cap);
    }

    private static double materialSample(int x, int y, int z)
    {
        long hash = x * 73428767L ^ z * 912931L ^ y * 42317861L;
        hash = (hash ^ hash >>> 13) * 1274126177L;
        return (hash & 65535) / 65536.0;
    }

    private static IBlockState band(int y, double offset)
    {
        // Several blocks per sedimentary layer, with slowly warped strata.
        int layer = Math.floorMod((int) Math.floor((y + offset) / 4), 8);
        switch (layer)
        {
            case 0: case 1: case 5: return ModBlocks.ENDSTONE_DUST.getDefaultState();
            case 3: return ModBlocks.FLAVOLITE.stone.getDefaultState();
            case 6: return ModBlocks.VIOLECITE.stone.getDefaultState();
            default: return Blocks.END_STONE.getDefaultState();
        }
    }

    private static final class Fields
    {
        final long seed;
        final BiomeTerrainNoise noise;
        Fields(long seed) { this.seed = seed; this.noise = new BiomeTerrainNoise(seed); }
    }

    private static final class TransitionCache extends LinkedHashMap<Long, BiomeTransitionField>
    {
        TransitionCache() { super(64, 0.75f, true); }
        @Override protected boolean removeEldestEntry(Map.Entry<Long, BiomeTransitionField> eldest)
        {
            return size() > 64;
        }
    }
}
