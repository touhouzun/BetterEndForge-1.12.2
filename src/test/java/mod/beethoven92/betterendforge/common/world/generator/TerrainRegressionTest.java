package mod.beethoven92.betterendforge.common.world.generator;

import java.util.Locale;

/** Standalone numerical regression checks; no game client is required. */
public final class TerrainRegressionTest
{
    public static void main(String[] args)
    {
        long[] seeds = { 0, 1, 42, -13579, 987654321, Long.MIN_VALUE + 17 };
        for (long seed : seeds)
        {
            BiomeTerrainNoise fields = new BiomeTerrainNoise(seed);
            int water = 0, groveWater = 0, samples = 0;
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            double maxStep = 0;
            for (int x = -2048; x < 2048; x += 16)
            {
                for (int z = -2048; z < 2048; z += 16)
                {
                    if (fields.lakeFloor(x, z, false) < BiomeTerrainNoise.LAKE_LEVEL) water++;
                    if (fields.lakeFloor(x, z, true) < BiomeTerrainNoise.LAKE_LEVEL) groveWater++;
                    int floor = fields.lakeFloor(x, z, false);
                    require(floor >= 48 && (fields.lakeLand(x, z, false) >= 0 || floor <= 63),
                            "Lake floor outside supported depth");
                    double height = fields.mountainHeight(x, z, false);
                    require(Double.isFinite(height), "Non-finite mountain height");
                    min = Math.min(min, height);
                    max = Math.max(max, height);
                    // The adjacent sample is on the other side of a chunk seam.
                    maxStep = Math.max(maxStep, Math.abs(height - fields.mountainHeight(x - 1, z, false)));
                    samples++;
                }
            }
            double coverage = water / (double) samples;
            double groveCoverage = groveWater / (double) samples;
            require(coverage >= 0.85, "Main lake coverage below 85% margin: " + coverage);
            require(groveCoverage > 0.45 && groveCoverage < 0.75, "Grove lacks water/forest balance");
            require(max - min > 25, "Mountains lack relief");
            require(maxStep < 8, "Discontinuous height at a chunk boundary: " + maxStep);
            BiomeTerrainNoise duplicate = new BiomeTerrainNoise(seed);
            require(fields.mountainHeight(-16385, 32767, false) == duplicate.mountainHeight(-16385, 32767, false),
                    "Generation is not deterministic");
            System.out.printf(Locale.ROOT, "seed=%d water=%.2f%% grove=%.2f%% relief=%.2f..%.2f seamStep<=%.2f%n",
                    seed, coverage * 100, groveCoverage * 100, min, max, maxStep);
        }
        require(new BiomeTerrainNoise(1).mountainHeight(1000, 2000, false)
                != new BiomeTerrainNoise(2).mountainHeight(1000, 2000, false), "Seed has no effect");
        System.out.println("Terrain regression checks passed.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
