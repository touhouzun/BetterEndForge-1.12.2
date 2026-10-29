package mod.beethoven92.betterendforge.common.world.generator;

/** Tests the same island envelope and cliff noise used by the mountain primer. */
public final class MountainIslandRegressionTest
{
    public static void main(String[] args)
    {
        require(MountainIslandProfile.blend(0, 52, 40) == 0, "Mountain grows vertically at the void fringe");
        require(MountainIslandProfile.blend(59, 52, 40) == 1, "Interior mountain lost its height");
        require(MountainIslandProfile.blend(30, 52, 4) == 0, "Thin fragment supports a huge mountain");
        require(MountainIslandProfile.blend(30, 52, 8) < MountainIslandProfile.blend(30, 52, 30),
                "Island thickness does not constrain added mountain mass");
        IslandEdgeField.Sampler outline = (x, z) -> x >= Math.sin(z * 0.055) * 5;
        for (int cx = -2; cx <= 2; cx++)
        {
            IslandEdgeField a = new IslandEdgeField(cx, -1, 60, outline);
            IslandEdgeField b = new IslandEdgeField(cx + 1, -1, 60, outline);
            for (int z = -16; z < 0; z++)
                require(Math.abs(a.distance((cx + 1) * 16, z) - b.distance((cx + 1) * 16, z)) < 1e-9,
                        "Mountain coast distance jumps at a chunk seam");
        }
        for (long seed : new long[] { 0, 42, -13579 })
        {
            BiomeTerrainNoise noise = new BiomeTerrainNoise(seed);
            double minWidth = 100, maxWidth = 0, maxStep = 0;
            double minCliff = 100, maxCliff = -100;
            for (int z = -128; z <= 128; z += 2)
            {
                double previous = 35;
                for (int x = 0; x <= 64; x++)
                {
                    double width = noise.mountainCoastWidth(x, z);
                    require(width > 45 && width < 59, "Coast exceeds padded distance field");
                    double blend = MountainIslandProfile.blend(x, width, 40);
                    double elevation = 35 + Math.min(x, 40) * 0.8 + 90 * blend;
                    maxStep = Math.max(maxStep, Math.abs(elevation - previous));
                    previous = elevation;
                    if (x == 64) require(blend == 1, "Island blend flattened interior summits");
                    minWidth = Math.min(minWidth, width);
                    maxWidth = Math.max(maxWidth, width);
                    double cliff = noise.mountainCliffNoise(x, 80 + x / 2, z);
                    minCliff = Math.min(minCliff, cliff);
                    maxCliff = Math.max(maxCliff, cliff);
                    double nearVoid = noise.mountainCliffNoise(x, 80, z) * (1 - blend) * blend;
                    if (x == 0) require(nearVoid == 0, "Residual noise leaves a wall at the void fringe");
                }
            }
            require(maxStep < 4.5, "Mountain coast still has a hard Y=45 cliff: " + maxStep);
            require(maxWidth - minWidth > 1 && maxCliff - minCliff > 2, "Cliff and foothill are uniform planes");
            System.out.printf("seed=%d mountainCoastWidth=%.2f..%.2f maxStep=%.2f cliffNoise=%.2f..%.2f%n",
                    seed, minWidth, maxWidth, maxStep, minCliff, maxCliff);
        }
        System.out.println("Mountain island envelope, thickness, cliff noise and seam regression checks passed.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
