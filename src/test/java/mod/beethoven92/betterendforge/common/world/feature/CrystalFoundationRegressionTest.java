package mod.beethoven92.betterendforge.common.world.feature;

public final class CrystalFoundationRegressionTest
{
    public static void main(String[] args)
    {
        int[][] base = CrystalMountainFeature.sampleFoundation(10, 10, 100, 3, (x, z) -> 100 + (x - 10) / 2);
        require(base != null && base[0][3] == 99 && base[6][3] == 101,
                "Crystal footprint does not retain each column's ground height");
        require(CrystalMountainFeature.sampleFoundation(10, 10, 100, 3,
                (x, z) -> x == 7 && z == 10 ? -1 : 100) == null, "Crystal hangs over void/unloaded terrain");
        require(CrystalMountainFeature.sampleFoundation(10, 10, 100, 3,
                (x, z) -> x == 7 && z == 10 ? 93 : 100) == null, "Crystal bridges an unsupported cliff");
        require(CrystalMountainFeature.sampleFoundation(10, 10, 100, 3,
                (x, z) -> x == 13 && z == 10 ? 105 : 100) == null, "Crystal starts inside a higher cliff");
        require(CrystalMountainFeature.sampleFoundation(10, 10, 100, 3,
                (x, z) -> x == 7 && z == 7 ? -1 : 100) != null, "Excluded octagonal corner affected support");
        System.out.println("Crystal mountain footprint support regression checks passed.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
