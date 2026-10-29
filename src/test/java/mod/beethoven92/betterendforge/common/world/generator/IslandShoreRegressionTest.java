package mod.beethoven92.betterendforge.common.world.generator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import git.jbredwards.nether_api.api.world.INetherAPIChunkGenerator;
import net.minecraft.block.material.Material;
import net.minecraft.init.Bootstrap;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkGeneratorEnd;

/** Raw vanilla terrain previews and noise-shaped coastal containment, without a game client. */
public final class IslandShoreRegressionTest
{
    public static void main(String[] args)
    {
        Bootstrap.register();
        PreviewGenerator generator = new PreviewGenerator(new UnloadedWorld(), 42);
        for (int cx : new int[] { -1, 0, 3, 12 })
        {
            ChunkPrimer oracle = new ChunkPrimer();
            generator.fillOracle(cx, -1, oracle);
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                {
                    int top = 255;
                    while (top >= 0 && oracle.getBlockState(x, top, z).getMaterial() == Material.AIR) top--;
                    int bottom = top;
                    while (bottom > 0 && oracle.getBlockState(x, bottom - 1, z).getMaterial() != Material.AIR) bottom--;
                    int expected = top < 0 ? -1 : top << 8 | bottom;
                    require(OriginalIslandTerrain.span(generator, cx * 16 + x, -16 + z) == expected,
                            "Cached terrain preview disagrees with vanilla density");
                }
        }
        require(generator.rawCalls == 4, "Raw terrain was regenerated for individual columns");
        require(!OriginalIslandTerrain.supported(-1), "Void is considered support");
        require(!OriginalIslandTerrain.supported(50 << 8 | 48), "Three-block fragment is considered a basin");
        require(OriginalIslandTerrain.supported(44 << 8 | 25), "Old hard Y=45 cutoff survived");

        IslandEdgeField.Sampler curved = (x, z) -> x >= Math.sin(z * 0.09) * 7;
        for (int cx = -2; cx <= 1; cx++)
        {
            IslandEdgeField a = new IslandEdgeField(cx, -1, curved);
            IslandEdgeField b = new IslandEdgeField(cx + 1, -1, curved);
            for (int z = -16; z < 0; z++)
                require(Math.abs(a.distance((cx + 1) * 16, z) - b.distance((cx + 1) * 16, z)) < 1e-9,
                        "Island shore depends on chunk generation order");
        }
        for (long seed : new long[] { 0, 42, -13579 })
        {
            Coast coast = new Coast(seed);
            int earliest = 100, latest = 0;
            double minRidge = 100, maxRidge = 0, maxStep = 0;
            for (int z = -64; z <= 64; z++)
            {
                int firstWater = 100;
                Plan previous = coast.plan(0, z);
                for (int x = 0; x <= 48; x++)
                {
                    Plan column = coast.plan(x, z);
                    maxStep = Math.max(maxStep, Math.abs(column.height - previous.height));
                    previous = column;
                    if (!column.water) continue;
                    firstWater = Math.min(firstWater, x);
                    require(column.floor >= 48, "Coastal noise dug below the closed lake floor");
                    for (int y = column.floor + 1; y <= 62; y++)
                    {
                        require(coast.block(x - 1, y, z) != 0 && coast.block(x + 1, y, z) != 0
                                && coast.block(x, y, z - 1) != 0 && coast.block(x, y, z + 1) != 0,
                                "Water has an open side toward void");
                    }
                    for (int depth = 0; depth <= 3; depth++)
                        require(coast.block(x, column.floor - depth, z) == 1, "Lake floor lacks solid support");
                }
                earliest = Math.min(earliest, firstWater);
                latest = Math.max(latest, firstWater);
                double ridge = coast.noise.shoreRidge(12, z);
                minRidge = Math.min(minRidge, ridge);
                maxRidge = Math.max(maxRidge, ridge);
                require(coast.plan(40, z).floor == 48, "Coastal treatment changed the deep lake interior");
            }
            require(latest - earliest >= 2 && maxRidge - minRidge > 1, "Shoreline is a uniform level retaining wall");
            require(maxStep < 4.5, "Coast has a sudden cut at the former Y=45 threshold: " + maxStep);
            require(coast.block(-1, 62, 0) == 0, "Coast created terrain in the void");
            System.out.printf("seed=%d coastalWaterStarts=%d..%d ridge=%.2f..%.2f maxStep=%.2f%n",
                    seed, earliest, latest, minRidge, maxRidge, maxStep);
        }
        System.out.println("Island shoreline noise, vanilla previews, containment and seam regression checks passed.");
    }

    private static final class Coast
    {
        final BiomeTerrainNoise noise;
        final Map<Long, IslandEdgeField> fields = new HashMap<>();
        final Map<Long, Plan> columns = new HashMap<>();
        Coast(long seed) { noise = new BiomeTerrainNoise(seed); }
        Plan plan(int x, int z)
        {
            long position = ((long) x << 32) ^ (z & 0xffffffffL);
            return columns.computeIfAbsent(position, ignored -> {
                int cx = x >> 4, cz = z >> 4;
                long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                IslandEdgeField field = fields.computeIfAbsent(key,
                        unused -> new IslandEdgeField(cx, cz, (px, pz) -> px >= 0));
                double width = noise.shoreWidth(x, z), distance = field.distance(x, z);
                double original = Math.min(65, 35 + Math.max(0, x) * 0.8);
                double height = IslandShoreProfile.elevation(original, 48, distance, width, noise.shoreRidge(x, z));
                int floor = (int) Math.round(height);
                return new Plan(height, floor, x >= 0 && floor < 62 && IslandShoreProfile.canContainWater(distance, width));
            });
        }
        int block(int x, int y, int z)
        {
            if (x < 0 || y < 8) return 0; // Original void and island underside remain empty.
            Plan column = plan(x, z);
            if (y <= column.floor) return 1;
            return column.water && y <= 62 ? 2 : 0;
        }
    }

    private static final class Plan
    {
        final double height;
        final int floor;
        final boolean water;
        Plan(double height, int floor, boolean water) { this.height = height; this.floor = floor; this.water = water; }
    }

    private static final class PreviewGenerator extends ChunkGeneratorEnd implements INetherAPIChunkGenerator
    {
        final World testWorld;
        final Random random = new Random(0);
        int rawCalls;
        PreviewGenerator(World world, long seed) { super(world, false, seed, BlockPos.ORIGIN); testWorld = world; }
        @Override public World getWorld() { return testWorld; }
        @Override public Random getRand() { return random; }
        @Override public boolean areStructuresEnabled() { return false; }
        @Override public void populateWithVanilla(int x, int z) { throw new AssertionError("Preview populated a chunk"); }
        @Override public Chunk generateChunk(int x, int z) { throw new AssertionError("Preview loaded a chunk"); }
        @Override public void setBlocksInPrimer(int x, int z, ChunkPrimer primer)
        {
            rawCalls++;
            super.setBlocksInChunk(x, z, primer);
        }
        void fillOracle(int x, int z, ChunkPrimer primer) { super.setBlocksInChunk(x, z, primer); }
    }

    private static final class UnloadedWorld extends World
    {
        UnloadedWorld() { super(null, null, new WorldProviderEnd(), new Profiler(), false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return false; }
        @Override public Chunk getChunk(int x, int z) { throw new AssertionError("Terrain preview requested a world chunk"); }
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
