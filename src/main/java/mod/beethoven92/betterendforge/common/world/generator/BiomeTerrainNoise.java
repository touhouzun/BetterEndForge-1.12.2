package mod.beethoven92.betterendforge.common.world.generator;

import java.util.Random;

/** World-coordinate fields: no chunk-local seeds or radial mountain cones. */
public final class BiomeTerrainNoise
{
    public static final int LAKE_LEVEL = 62;
    private final OpenSimplexNoise warpX, warpZ, lakes, continental, erosion, ridges, detail, strata;

    public BiomeTerrainNoise(long seed)
    {
        Random random = new Random(seed ^ 0x424554544552454EL);
        warpX = new OpenSimplexNoise(random.nextLong());
        warpZ = new OpenSimplexNoise(random.nextLong());
        lakes = new OpenSimplexNoise(random.nextLong());
        continental = new OpenSimplexNoise(random.nextLong());
        erosion = new OpenSimplexNoise(random.nextLong());
        ridges = new OpenSimplexNoise(random.nextLong());
        detail = new OpenSimplexNoise(random.nextLong());
        strata = new OpenSimplexNoise(random.nextLong());
    }

    /** Positive values are land/forest islands, negative values a connected basin. */
    public double lakeLand(int x, int z, boolean grove)
    {
        return lakeLand(x, z, grove ? 1.0 : 0.0);
    }

    public double lakeLand(int x, int z, double grove)
    {
        double wx = x + warpX.eval(x * 0.002, z * 0.002) * 38;
        double wz = z + warpZ.eval(x * 0.002, z * 0.002) * 38;
        double field = fbm(lakes, wx * 0.004, wz * 0.004, 3, 0.42);
        // Main biome is mostly water; the grove keeps broad dry forest patches
        // in the SAME field, so large openings between those patches are lakes.
        return field - BiomeTransitionField.mix(0.36, 0.035, grove);
    }

    public int lakeFloor(int x, int z, boolean grove)
    {
        return (int) Math.round(lakeHeight(x, z, grove ? 1.0 : 0.0));
    }

    public double lakeHeight(int x, int z, double grove)
    {
        double land = lakeLand(x, z, grove);
        // The first few blocks are a shallow shelf, not a three-block step
        // wherever the land mask changes sign.
        return LAKE_LEVEL + 1 + Math.max(0, land) * 18
                - smoothstep(0, 0.06, -land) * 3
                - smoothstep(0, 0.32, -land) * BiomeTransitionField.mix(12, 8, grove);
    }

    /** Octaves + domain warping + feedback-weighted ridges + erosion valleys. */
    public double mountainHeight(int x, int z, boolean painted)
    {
        double wx = x + fbm(warpX, x * 0.0025, z * 0.0025, 3, 0.5) * 65;
        double wz = z + fbm(warpZ, x * 0.0025, z * 0.0025, 3, 0.5) * 65;
        double continents = fbm(continental, wx * 0.0028, wz * 0.0028, 4, 0.5);
        double valleys = fbm(erosion, wx * 0.006, wz * 0.006, 3, 0.5);
        double ridge = ridged(wx * 0.008, wz * 0.008);
        double mountainRegion = smoothstep(-0.3, 0.42, continents);
        double relief = 10 + mountainRegion * (22 + ridge * 68);
        relief *= 1 - smoothstep(0.02, 0.5, valleys) * 0.55;
        double foothills = fbm(detail, wx * 0.026, wz * 0.026, 4, 0.48) * 9;
        return Math.max(2, (relief + foothills) * (painted ? 0.82 : 1.0));
    }

    /** A small 3-D perturbation breaks up upper cliffs without hollowing the foundation. */
    public double rockDensity(int x, int y, int z, double top)
    {
        return top - y + detail.eval(x * 0.055, y * 0.065, z * 0.055) * 3.5;
    }

    public double strataOffset(int x, int z)
    {
        return fbm(strata, x * 0.009, z * 0.009, 3, 0.5) * 5;
    }

    public double shoreWidth(int x, int z)
    {
        double wx = x + warpX.eval(x * 0.008, z * 0.008) * 12;
        double wz = z + warpZ.eval(x * 0.008, z * 0.008) * 12;
        return 21 + fbm(erosion, wx * 0.025, wz * 0.025, 3, 0.5) * 5;
    }

    public double shoreRidge(int x, int z)
    {
        return LAKE_LEVEL + 4 + fbm(strata, x * 0.035, z * 0.035, 3, 0.5) * 2
                + detail.eval(x * 0.12, z * 0.12) * 0.75;
    }

    public double mountainCoastWidth(int x, int z)
    {
        double wx = x + warpX.eval(x * 0.006, z * 0.006) * 18;
        double wz = z + warpZ.eval(x * 0.006, z * 0.006) * 18;
        return 52 + fbm(erosion, wx * 0.018, wz * 0.018, 3, 0.5) * 6;
    }

    public double mountainCliffNoise(int x, int y, int z)
    {
        return erosion.eval(x * 0.035, y * 0.045, z * 0.035) * 6
                + detail.eval(x * 0.10, y * 0.10, z * 0.10) * 1.5;
    }

    private double ridged(double x, double z)
    {
        double sum = 0, amplitude = 1, weight = 1, normalization = 0;
        for (int i = 0; i < 5; i++)
        {
            double signal = 1 - Math.abs(ridges.eval(x, z));
            signal = signal * signal * weight;
            weight = clamp(signal * 1.65, 0, 1);
            sum += signal * amplitude;
            normalization += amplitude;
            amplitude *= 0.5;
            x = x * 2.03 + 19.7;
            z = z * 2.03 - 11.3;
        }
        return sum / normalization;
    }

    private double fbm(OpenSimplexNoise noise, double x, double z, int octaves, double persistence)
    {
        double sum = 0, amplitude = 1, normalization = 0;
        for (int i = 0; i < octaves; i++)
        {
            sum += noise.eval(x, z) * amplitude;
            normalization += amplitude;
            amplitude *= persistence;
            x = x * 2.01 + 23.1;
            z = z * 2.01 - 17.4;
        }
        return sum / normalization;
    }

    public static double smoothstep(double min, double max, double value)
    {
        double t = clamp((value - min) / (max - min), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
