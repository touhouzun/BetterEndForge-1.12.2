package mod.beethoven92.betterendforge.common.world.generator;

/** An outer slope, noise-warped dry ridge, and shallow inner slope to the lake. */
public final class IslandShoreProfile
{
    private IslandShoreProfile() {}

    public static double elevation(double original, double target, double distance, double width, double ridge)
    {
        double outer = BiomeTerrainNoise.smoothstep(0, width * 0.60, distance);
        double inner = BiomeTerrainNoise.smoothstep(width * 0.60, width, distance);
        return BiomeTransitionField.mix(original, BiomeTransitionField.mix(ridge, target, inner), outer);
    }

    public static boolean canContainWater(double distance, double width)
    {
        return distance >= width * 0.60;
    }
}
