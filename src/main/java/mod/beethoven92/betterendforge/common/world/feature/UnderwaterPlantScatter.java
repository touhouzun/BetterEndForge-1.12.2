package mod.beethoven92.betterendforge.common.world.feature;

import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class UnderwaterPlantScatter extends ScatterFeature
{
    public UnderwaterPlantScatter(int radius) { super(radius); }

    @Override
    protected BlockPos getCenterGround(World world, BlockPos pos)
    {
        BlockPos water = WaterPlantHelper.surface(world, pos.getX(), pos.getZ());
        return water == null ? pos : water;
    }

    @Override
    protected boolean canSpawn(World world, BlockPos pos)
    {
        return WaterPlantHelper.isWater(world.getBlockState(pos));
    }

    @Override
    public boolean canGenerate(World world, Random random, BlockPos center, BlockPos pos, float radius)
    {
        return WaterPlantHelper.isWater(world.getBlockState(pos));
    }

    @Override
    protected boolean getGroundPlant(World world, BlockPos.MutableBlockPos pos)
    {
        return WaterPlantHelper.floor(world, pos);
    }

    @Override
    protected int getYOffset() { return 0; }

    @Override
    protected int getChance() { return 5; }
}
