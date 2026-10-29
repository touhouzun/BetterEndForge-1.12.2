package mod.beethoven92.betterendforge.common.world.generator;

/** Shared terrain profile for both sides of a biome boundary. */
public final class TerrainTransitionProfile
{
    private TerrainTransitionProfile() {}

    public static double mountainBlend(double distance)
    {
        return BiomeTerrainNoise.smoothstep(-32, 64, distance);
    }

    public static double lakeBlend(double distance)
    {
        return BiomeTerrainNoise.smoothstep(0, 40, distance);
    }

    public static double bankBlend(double distance)
    {
        return BiomeTerrainNoise.smoothstep(-48, 0, distance);
    }

    public static double groveMix(double distance)
    {
        return BiomeTerrainNoise.smoothstep(-24, 24, distance);
    }

    public static double paintedMix(double distance)
    {
        return BiomeTerrainNoise.smoothstep(-24, 24, distance);
    }

    public static double elevation(double ground, double mountain, double lakeHeight, double lakeDistance)
    {
        double raised = ground + mountain;
        // Meet at a dry shore just above the water, lowering high terrain and
        // raising low terrain smoothly on the neighbouring side as well.
        double bank = BiomeTransitionField.mix(raised, BiomeTerrainNoise.LAKE_LEVEL + 1, bankBlend(lakeDistance));
        return BiomeTransitionField.mix(bank, lakeHeight, lakeBlend(lakeDistance));
    }
}
