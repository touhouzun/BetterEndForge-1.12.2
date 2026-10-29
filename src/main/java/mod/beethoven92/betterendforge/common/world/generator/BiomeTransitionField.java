package mod.beethoven92.betterendforge.common.world.generator;

/** World-aligned signed distance fields, sampled without loading any chunks. */
public final class BiomeTransitionField
{
    public static final int LAKE = 1, GROVE = 2, MOUNTAIN = 4, PAINTED = 8;
    private static final int STEP = 4, PADDING = 80, SIZE = (16 + PADDING * 2) / STEP + 1;
    private static final double LIMIT = 72, DIAGONAL = Math.sqrt(2);
    private final int startX, startZ;
    private final double[][] fields = new double[4][];
    private final boolean active;

    @FunctionalInterface
    public interface Sampler { int sample(int x, int z); }

    public BiomeTransitionField(int chunkX, int chunkZ, Sampler sampler)
    {
        startX = chunkX * 16 - PADDING;
        startZ = chunkZ * 16 - PADDING;
        int[] masks = new int[SIZE * SIZE];
        int any = 0;
        for (int z = 0; z < SIZE; z++)
            for (int x = 0; x < SIZE; x++)
            {
                int mask = sampler.sample(startX + x * STEP, startZ + z * STEP);
                masks[z * SIZE + x] = mask;
                any |= mask;
            }
        active = any != 0;
        if (!active) return;
        for (int i = 0; i < fields.length; i++)
        {
            int flag = 1 << i;
            double[] inside = distance(masks, flag, true);
            double[] outside = distance(masks, flag, false);
            fields[i] = new double[masks.length];
            for (int j = 0; j < masks.length; j++)
                fields[i][j] = (masks[j] & flag) != 0
                        ? Math.min(LIMIT, outside[j] * STEP) - STEP * 0.5
                        : -(Math.min(LIMIT, inside[j] * STEP) - STEP * 0.5);
        }
    }

    public boolean isActive() { return active; }

    public double distance(int flag, int worldX, int worldZ)
    {
        if (!active) return -LIMIT;
        double gx = (worldX - startX) / (double) STEP, gz = (worldZ - startZ) / (double) STEP;
        int x = (int) Math.floor(gx), z = (int) Math.floor(gz);
        double tx = gx - x, tz = gz - z;
        double[] values = fields[Integer.numberOfTrailingZeros(flag)];
        int index = z * SIZE + x;
        return mix(mix(values[index], values[index + 1], tx),
                mix(values[index + SIZE], values[index + SIZE + 1], tx), tz);
    }

    private static double[] distance(int[] masks, int flag, boolean targetInside)
    {
        double[] values = new double[masks.length];
        for (int i = 0; i < masks.length; i++)
            values[i] = ((masks[i] & flag) != 0) == targetInside ? 0 : LIMIT / STEP;
        // Eight-connected distance transform catches curved boundaries and
        // small neighbouring patches missed by rays. Padding exceeds every
        // blend radius, so chunk-local array edges cannot affect the result.
        for (int z = 0; z < SIZE; z++)
            for (int x = 0; x < SIZE; x++)
            {
                int i = z * SIZE + x;
                if (x > 0) values[i] = Math.min(values[i], values[i - 1] + 1);
                if (z > 0)
                {
                    values[i] = Math.min(values[i], values[i - SIZE] + 1);
                    if (x > 0) values[i] = Math.min(values[i], values[i - SIZE - 1] + DIAGONAL);
                    if (x + 1 < SIZE) values[i] = Math.min(values[i], values[i - SIZE + 1] + DIAGONAL);
                }
            }
        for (int z = SIZE - 1; z >= 0; z--)
            for (int x = SIZE - 1; x >= 0; x--)
            {
                int i = z * SIZE + x;
                if (x + 1 < SIZE) values[i] = Math.min(values[i], values[i + 1] + 1);
                if (z + 1 < SIZE)
                {
                    values[i] = Math.min(values[i], values[i + SIZE] + 1);
                    if (x > 0) values[i] = Math.min(values[i], values[i + SIZE - 1] + DIAGONAL);
                    if (x + 1 < SIZE) values[i] = Math.min(values[i], values[i + SIZE + 1] + DIAGONAL);
                }
            }
        return values;
    }

    static double mix(double from, double to, double amount) { return from + (to - from) * amount; }
}
