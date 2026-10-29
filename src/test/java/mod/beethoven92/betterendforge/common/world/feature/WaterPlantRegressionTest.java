package mod.beethoven92.betterendforge.common.world.feature;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import mod.beethoven92.betterendforge.common.block.BlockProperties.TripleShape;
import mod.beethoven92.betterendforge.common.block.EndLilyBlock;
import mod.beethoven92.betterendforge.common.init.ModTags;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.chunk.IChunkProvider;

/** Exercises real scatter placement against an in-memory water world. */
public final class WaterPlantRegressionTest
{
    public static void main(String[] args)
    {
        Bootstrap.register();
        ModTags.END_GROUND.add(Blocks.END_STONE);
        MemoryWorld world = new MemoryWorld();
        for (int x = -32; x <= 16; x++)
            for (int z = -32; z <= 16; z++)
            {
                int bottom = 55 + Math.floorMod(x + z, 4);
                world.setBlockState(new BlockPos(x, bottom, z), Blocks.END_STONE.getDefaultState(), 2);
                for (int y = bottom + 1; y <= 62; y++)
                    world.setBlockState(new BlockPos(x, y, z), (x % 2 == 0 ? Blocks.WATER : Blocks.FLOWING_WATER).getDefaultState(), 2);
            }
        // Heightmap above the lake can include canopy; water lookup must work underneath it.
        world.setBlockState(new BlockPos(-16, 90, -16), Blocks.END_STONE.getDefaultState(), 2);
        FloatingProbe floating = new FloatingProbe();
        require(floating.generate(world, new Random(42), new BlockPos(-16, 0, -16)) && floating.placed > 0,
                "Floating vegetation rejected a lake surface");
        require(floating.placedAtWrongHeight == 0, "Floating leaves placed below/above water surface");
        RootProbe roots = new RootProbe();
        require(roots.generate(world, new Random(42), new BlockPos(-16, 0, -16)) && roots.placed > 0,
                "Underwater plants rejected a lake floor");
        require(roots.badRoots == 0, "Aquatic roots missed the varying floor");
        require(!floating.generate(new MemoryWorld(), new Random(42), new BlockPos(0, 0, 0)),
                "Floating leaves accepted dry land");

        EndLilyBlock lily = new EndLilyBlock() {
            // Inject the ground tag boundary without starting ModBlocks' mixin-
            // dependent registry. The actual lily survival logic is unchanged.
            @Override protected boolean isTerrain(IBlockState state) {
                return ModTags.END_GROUND.contains(state.getBlock());
            }
        };
        BlockPos bottom = new BlockPos(0, 59, 0);
        IBlockState base = lily.getDefaultState().withProperty(EndLilyBlock.SHAPE, TripleShape.BOTTOM);
        IBlockState middle = base.withProperty(EndLilyBlock.SHAPE, TripleShape.MIDDLE);
        IBlockState top = base.withProperty(EndLilyBlock.SHAPE, TripleShape.TOP);
        world.setBlockState(bottom.down(), Blocks.END_STONE.getDefaultState(), 2);
        world.setBlockState(bottom, base, 2);
        world.setBlockState(bottom.up(), middle, 2);
        world.setBlockState(bottom.up(2), top, 2);
        require(lily.canBlockStay(world, bottom, base), "Lily root removed by vanilla soil check");
        require(lily.canBlockStay(world, bottom.up(), middle), "Lily middle does not survive");
        require(lily.canBlockStay(world, bottom.up(2), top), "Lily surface flower does not survive");
        world.setBlockState(bottom.down(), Blocks.AIR.getDefaultState(), 2);
        require(!lily.canBlockStay(world, bottom, base), "Lily root survives without its floor");
        System.out.println("Water plant placement and survival regression checks passed.");
    }

    private static final class FloatingProbe extends EndLotusLeafFeature
    {
        int placed, placedAtWrongHeight;
        FloatingProbe() { super(6); }
        @Override public void generateInner(World world, Random random, BlockPos pos)
        {
            placed++;
            if (pos.getY() != 63 || !WaterPlantHelper.isWater(world.getBlockState(pos.down()))) placedAtWrongHeight++;
        }
    }

    private static final class RootProbe extends UnderwaterPlantScatter
    {
        int placed, badRoots;
        RootProbe() { super(6); }
        @Override public void generateInner(World world, Random random, BlockPos pos)
        {
            placed++;
            if (world.getBlockState(pos.down()).getBlock() != Blocks.END_STONE || !WaterPlantHelper.isWater(world.getBlockState(pos))) badRoots++;
        }
    }

    private static final class MemoryWorld extends World
    {
        private final Map<BlockPos, IBlockState> states = new HashMap<>();
        MemoryWorld() { super(null, null, new WorldProviderEnd(), new Profiler(), false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        @Override public IBlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        @Override public boolean setBlockState(BlockPos pos, IBlockState state, int flags) { states.put(pos.toImmutable(), state); return true; }
        @Override public int getHeight(int x, int z)
        {
            for (int y = 255; y > 0; y--) if (!isAirBlock(new BlockPos(x, y, z))) return y + 1;
            return 0;
        }
        @Override public BlockPos getHeight(BlockPos pos) { return new BlockPos(pos.getX(), getHeight(pos.getX(), pos.getZ()), pos.getZ()); }
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
