package mod.beethoven92.betterendforge.common.world.feature;

import java.util.Random;
import mod.beethoven92.betterendforge.common.block.EndLotusSeedBlock;
import mod.beethoven92.betterendforge.common.init.ModBlocks;
import mod.beethoven92.betterendforge.common.init.ModTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EndLotusFeature extends UnderwaterPlantScatter
{
    public EndLotusFeature(int radius) { super(radius); }

    @Override
    protected int getChance() { return 15; }

    @Override
    public boolean canGenerate(World world, Random random, BlockPos center, BlockPos pos, float radius)
    {
        return WaterPlantHelper.isWater(world.getBlockState(pos))
                && ModTags.END_GROUND.contains(world.getBlockState(pos.down()).getBlock());
    }

    @Override
    public void generateInner(World world, Random random, BlockPos pos)
    {
        ((EndLotusSeedBlock) ModBlocks.END_LOTUS_SEED).generate(world, random, pos);
    }
}
