package mod.beethoven92.betterendforge.common.world.generator;

/** The added mountain mass retreats to the original island before reaching void. */
public final class MountainIslandProfile
{
    private MountainIslandProfile() {}

    public static double blend(double distance, double width, int thickness)
    {
        return BiomeTerrainNoise.smoothstep(0, width, distance)
                * BiomeTerrainNoise.smoothstep(4, 20, thickness);
    }
}
