package mod.beethoven92.betterendforge.common.world.generator;

/** Checks the actual distance fields and blend profiles used by primer generation. */
public final class BiomeTransitionRegressionTest
{
    public static void main(String[] args)
    {
        BiomeTransitionField.Sampler straight = (x, z) -> x >= 0 ? BiomeTransitionField.MOUNTAIN : 0;
        BiomeTransitionField left = new BiomeTransitionField(-1, 0, straight);
        BiomeTransitionField right = new BiomeTransitionField(0, 0, straight);
        double previous = 70;
        double maxStep = 0;
        for (int x = -40; x <= 68; x++)
        {
            BiomeTransitionField field = new BiomeTransitionField(Math.floorDiv(x, 16), 0, straight);
            double distance = field.distance(BiomeTransitionField.MOUNTAIN, x, 0);
            double elevation = 70 + 90 * TerrainTransitionProfile.mountainBlend(distance);
            require(elevation >= previous, "Mountain foothill is not monotone");
            maxStep = Math.max(maxStep, elevation - previous);
            previous = elevation;
        }
        require(maxStep < 1.5, "Foothill has a sudden biome boundary cliff: " + maxStep);
        require(left.distance(BiomeTransitionField.MOUNTAIN, 0, 0)
                == right.distance(BiomeTransitionField.MOUNTAIN, 0, 0), "Distance field jumps between chunk tiles");
        require(TerrainTransitionProfile.mountainBlend(-40) == 0 && TerrainTransitionProfile.mountainBlend(70) == 1,
                "Mountain interior or distant biome was changed by the transition");

        double last = 50;
        for (int distance = -49; distance <= 45; distance++)
        {
            double elevation = TerrainTransitionProfile.elevation(50, 0, 48, distance);
            require(Math.abs(elevation - last) < 0.85, "Low neighbour/lake lip has a vertical step");
            if (distance <= 0) require(elevation <= 63 && elevation >= 50, "Outside bank is not a supported slope");
            last = elevation;
        }
        require(TerrainTransitionProfile.elevation(70, 0, 48, -49) == 70, "Far non-lake terrain was flattened");
        require(TerrainTransitionProfile.elevation(70, 0, 48, 45) == 48, "Deep lake interior was lost");
        require(TerrainTransitionProfile.elevation(70, 90, 48, 0) == 63, "Mountain/lake boundary lacks a dry shore");
        require(Math.abs(TerrainTransitionProfile.elevation(70, 90, 48, -0.01)
                - TerrainTransitionProfile.elevation(70, 90, 48, 0.01)) < 0.01,
                "Mountain/lake height jumps at the biome boundary");

        // A patch between the old eight search rays still contributes a foothill.
        BiomeTransitionField patch = new BiomeTransitionField(0, 0, (x, z) ->
                x >= 12 && x <= 20 && z >= 4 && z <= 8 ? BiomeTransitionField.MOUNTAIN : 0);
        require(patch.distance(BiomeTransitionField.MOUNTAIN, 0, 0) > -20, "Nearby off-axis biome patch was missed");

        BiomeTransitionField.Sampler curved = (x, z) -> {
            int mask = x > z / 2 + 3 ? BiomeTransitionField.LAKE : 0;
            if (x > 20 + (z * z % 31)) mask |= BiomeTransitionField.MOUNTAIN;
            return mask;
        };
        for (int cx = -3; cx <= 2; cx++)
        {
            BiomeTransitionField a = new BiomeTransitionField(cx, -1, curved);
            BiomeTransitionField b = new BiomeTransitionField(cx + 1, -1, curved);
            int seam = (cx + 1) * 16;
            for (int z = -16; z < 0; z++)
                for (int flag : new int[] { BiomeTransitionField.LAKE, BiomeTransitionField.MOUNTAIN })
                    require(Math.abs(a.distance(flag, seam, z) - b.distance(flag, seam, z)) < 1e-9,
                            "Curved distance field depends on which chunk generated it");
        }
        BiomeTerrainNoise noise = new BiomeTerrainNoise(42);
        for (int x = -512; x <= 512; x += 16)
        {
            double height = noise.lakeHeight(x, 123, 0);
            for (int d = -24; d <= 24; d++)
            {
                double next = noise.lakeHeight(x, 123, TerrainTransitionProfile.groveMix(d));
                require(Math.abs(next - height) < 2, "Lake/grove boundary has an abrupt floor step");
                height = next;
            }
        }
        System.out.println("Biome transition, shoreline slope and chunk seam regression checks passed.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
