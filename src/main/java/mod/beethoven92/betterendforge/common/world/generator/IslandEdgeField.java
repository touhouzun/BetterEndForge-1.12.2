package mod.beethoven92.betterendforge.common.world.generator;

/** Distance to the original island's unsupported fringe, independent of biome borders. */
public final class IslandEdgeField
{
    private static final int STEP = 2;
    private static final double DIAGONAL = Math.sqrt(2);
    private final int startX, startZ;
    private final int size;
    private final double limit;
    private final double[] distances;

    @FunctionalInterface
    public interface Sampler { boolean supported(int x, int z); }

    public IslandEdgeField(int chunkX, int chunkZ, Sampler sampler)
    {
        this(chunkX, chunkZ, 30, sampler);
    }

    public IslandEdgeField(int chunkX, int chunkZ, int range, Sampler sampler)
    {
        limit = range;
        int padding = Math.max(32, ((range + STEP + 15) / 16) * 16);
        size = (16 + padding * 2) / STEP + 1;
        startX = chunkX * 16 - padding;
        startZ = chunkZ * 16 - padding;
        boolean[] supported = new boolean[size * size];
        distances = new double[supported.length];
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++)
            {
                int i = z * size + x;
                supported[i] = sampler.supported(startX + x * STEP, startZ + z * STEP);
                distances[i] = supported[i] ? limit / STEP : 0;
            }
        // Distance to empty/thin original terrain, not to the already raised
        // shore. The halo is larger than the widest noise-warped coastal band.
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++)
            {
                int i = z * size + x;
                if (x > 0) distances[i] = Math.min(distances[i], distances[i - 1] + 1);
                if (z > 0)
                {
                    distances[i] = Math.min(distances[i], distances[i - size] + 1);
                    if (x > 0) distances[i] = Math.min(distances[i], distances[i - size - 1] + DIAGONAL);
                    if (x + 1 < size) distances[i] = Math.min(distances[i], distances[i - size + 1] + DIAGONAL);
                }
            }
        for (int z = size - 1; z >= 0; z--)
            for (int x = size - 1; x >= 0; x--)
            {
                int i = z * size + x;
                if (x + 1 < size) distances[i] = Math.min(distances[i], distances[i + 1] + 1);
                if (z + 1 < size)
                {
                    distances[i] = Math.min(distances[i], distances[i + size] + 1);
                    if (x > 0) distances[i] = Math.min(distances[i], distances[i + size - 1] + DIAGONAL);
                    if (x + 1 < size) distances[i] = Math.min(distances[i], distances[i + size + 1] + DIAGONAL);
                }
            }
        for (int i = 0; i < distances.length; i++)
            distances[i] = supported[i] ? Math.max(0, Math.min(limit, distances[i] * STEP) - STEP * 0.5) : 0;
    }

    public double distance(int x, int z)
    {
        double gx = (x - startX) / (double) STEP, gz = (z - startZ) / (double) STEP;
        int lx = (int) Math.floor(gx), lz = (int) Math.floor(gz);
        int i = lz * size + lx;
        return BiomeTransitionField.mix(BiomeTransitionField.mix(distances[i], distances[i + 1], gx - lx),
                BiomeTransitionField.mix(distances[i + size], distances[i + size + 1], gx - lx), gz - lz);
    }
}
