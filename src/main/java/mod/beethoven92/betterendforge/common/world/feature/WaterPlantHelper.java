package mod.beethoven92.betterendforge.common.world.feature;

import mod.beethoven92.betterendforge.common.init.ModTags;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Water and floor lookup must be done independently for each scattered column. */
final class WaterPlantHelper
{
    private WaterPlantHelper() {}

    static boolean isWater(IBlockState state)
    {
        return state.getMaterial() == Material.WATER;
    }

    static BlockPos surface(World world, int x, int z)
    {
        for (int y = Math.min(255, world.getHeight(x, z)); y > 0; y--)
        {
            BlockPos pos = new BlockPos(x, y, z);
            if (isWater(world.getBlockState(pos))) return pos;
        }
        return null;
    }

    static boolean floor(World world, BlockPos.MutableBlockPos pos)
    {
        BlockPos top = surface(world, pos.getX(), pos.getZ());
        if (top == null) return false;
        int y = top.getY();
        while (y > 1 && isWater(world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())))) y--;
        if (!ModTags.END_GROUND.contains(world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).getBlock())) return false;
        pos.setY(y + 1);
        return isWater(world.getBlockState(pos));
    }
}
