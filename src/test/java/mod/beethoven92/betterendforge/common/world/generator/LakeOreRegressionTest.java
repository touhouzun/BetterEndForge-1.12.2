package mod.beethoven92.betterendforge.common.world.generator;

import java.util.HashMap;
import java.util.Map;

import mod.beethoven92.betterendforge.common.block.template.UnderwaterPlantBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.oredict.OreDictionary;

/** Real ore dictionary lookups and lake repair against an in-memory world. */
public final class LakeOreRegressionTest
{
    public static void main(String[] args)
    {
        Bootstrap.register();
        IBlockState ore = Blocks.STONE.getStateFromMeta(3);
        IBlockState ordinary = Blocks.STONE.getStateFromMeta(1);
        OreDictionary.registerOre("oreLakeRegression", new ItemStack(Blocks.STONE, 1, 3));
        OreDictionary.registerOre("oreLakeWildcardRegression", new ItemStack(Blocks.WOOL, 1, OreDictionary.WILDCARD_VALUE));
        OreDictionary.registerOre("stoneLakeRegression", new ItemStack(Blocks.STONE, 1, 1));
        LakeOreDictionary ores = new LakeOreDictionary();
        require(ores.isOre(ore), "Dictionary ore without an ore registry name was missed");
        require(!ores.isOre(ordinary), "Non-ore metadata variant was classified as ore");
        require(ores.isOre(Blocks.WOOL.getStateFromMeta(0)) && ores.isOre(Blocks.WOOL.getStateFromMeta(11)),
                "Wildcard dictionary entry did not match every variant");
        require(!ores.isOre(Blocks.AIR.getDefaultState()), "Air was classified as ore");
        // Negative matches must not survive across generation passes.
        require(!ores.isOre(Blocks.DIRT.getDefaultState()), "Unexpected ore before registration");
        OreDictionary.registerOre("oreLakeLateRegression", new ItemStack(Blocks.DIRT));
        require(new LakeOreDictionary().isOre(Blocks.DIRT.getDefaultState()), "Late dictionary registration was lost");

        MemoryWorld world = new MemoryWorld();
        for (int y = 54; y <= 62; y++)
            put(world, 0, y, y <= 55 ? Blocks.END_STONE.getDefaultState() : Blocks.WATER.getDefaultState());
        put(world, 0, 54, ore); // Below the lake: keep it.
        put(world, 0, 55, ore); // Lake floor: restore a solid cap.
        put(world, 0, 58, ore);
        put(world, 0, 60, ordinary);
        put(world, 0, 62, Blocks.WOOL.getStateFromMeta(11));
        IBlockState root = new UnderwaterPlantBlock(Material.WATER).getDefaultState();
        put(world, 0, 56, root);
        put(world, 0, 63, Blocks.WATERLILY.getDefaultState());
        put(world, 0, 64, ore);
        put(world, 0, 90, ore); // A floating ore well above the surface.
        require(LakeOreCleaner.isBasin(world, 0, 0, 55, 62, ores), "Ore on the surface hid an existing lake");
        LakeOreCleaner.clearColumn(world, 0, 0, 55, 62, Blocks.END_STONE.getDefaultState(), ores);
        require(at(world, 0, 54) == ore, "Ore underneath the lake was removed");
        require(at(world, 0, 55).getBlock() == Blocks.END_STONE, "Floor repair opened a hole");
        require(at(world, 0, 58).getBlock() == Blocks.WATER && at(world, 0, 62).getBlock() == Blocks.WATER,
                "Ore in the water was not replaced with water");
        require(at(world, 0, 64).getBlock() == Blocks.AIR && at(world, 0, 90).getBlock() == Blocks.AIR,
                "Ore above the water was not removed");
        require(at(world, 0, 56) == root && at(world, 0, 63).getBlock() == Blocks.WATERLILY,
                "Aquatic vegetation was erased by repair");
        require(at(world, 0, 60) == ordinary, "Non-ore variant in the lake was erased");
        require(!LakeOreCleaner.isBasin(new MemoryWorld(), 0, 0, 55, 62, ores), "Void was treated as a lake");

        // A neighbouring vein can occupy a shallow column completely.
        for (int y = 55; y <= 62; y++) put(world, 1, y, y == 55 ? Blocks.END_STONE.getDefaultState() : ore);
        require(LakeOreCleaner.isBasin(world, 1, 0, 55, 62, ores), "Completely ore-filled water column was missed");
        world.neighbourLoaded = false;
        require(!LakeOreCleaner.isBasin(world, 1, 0, 55, 62, ores), "Repair queried an unloaded neighbour");
        world.neighbourLoaded = true;
        for (int y = 56; y <= 62; y++) put(world, 1, y, Blocks.END_STONE.getDefaultState());
        require(!LakeOreCleaner.isBasin(world, 1, 0, 55, 62, ores), "Solid emergency shore was treated as lake water");
        // A shallow boundary shelf must stop cleanup before buried ore.
        put(world, 2, 55, ore);
        put(world, 2, 58, Blocks.END_STONE.getDefaultState());
        for (int y = 59; y <= 62; y++) put(world, 2, y, Blocks.WATER.getDefaultState());
        int shelf = LakeOreCleaner.findFloor(world, 2, 0, 48, 62, ores);
        require(shelf == 58, "Cleanup ignored the raised boundary shelf");
        LakeOreCleaner.clearColumn(world, 2, 0, shelf, 62, Blocks.END_STONE.getDefaultState(), ores);
        require(at(world, 2, 55) == ore, "Boundary shelf cleanup removed buried ore");
        System.out.println("Lake ore dictionary and repair regression checks passed.");
    }

    private static void put(MemoryWorld world, int x, int y, IBlockState state)
    {
        world.setBlockState(new BlockPos(x, y, 0), state, 2);
    }

    private static IBlockState at(MemoryWorld world, int x, int y)
    {
        return world.getBlockState(new BlockPos(x, y, 0));
    }

    private static final class MemoryWorld extends World
    {
        private final Map<BlockPos, IBlockState> states = new HashMap<>();
        boolean neighbourLoaded = true;
        MemoryWorld() { super(null, null, new WorldProviderEnd(), new Profiler(), false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return neighbourLoaded; }
        @Override public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) { return neighbourLoaded; }
        @Override public IBlockState getBlockState(BlockPos pos)
        {
            return states.getOrDefault(pos, Blocks.AIR.getDefaultState());
        }
        @Override public boolean setBlockState(BlockPos pos, IBlockState state, int flags)
        {
            states.put(pos.toImmutable(), state);
            return true;
        }
        @Override public int getHeight(int x, int z)
        {
            for (int y = 255; y > 0; y--) if (getBlockState(new BlockPos(x, y, z)).getBlock() != Blocks.AIR) return y + 1;
            return 0;
        }
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
