package mod.beethoven92.betterendforge.common.world.generator;

import mod.beethoven92.betterendforge.common.init.ModBiomes;
import mod.beethoven92.betterendforge.common.init.ModBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Removes ore blocks which ended up exposed in a Sulphur Springs water body.
 *
 * Ore generation runs after biome lakes and caves in 1.12.  The regular ore
 * generators only know about their replaceable target and can therefore put
 * a vein through a spring floor or wall.  Doing this as a final population
 * pass keeps the fix independent of the other mods' ore generators and does
 * not touch buried ore.
 */
public final class SulphurWaterOreCleaner
{
    private SulphurWaterOreCleaner() {}

    public static void clear(World world, BlockPos origin)
    {
        if (world == null || origin == null) return;
        LakeOreDictionary ores = new LakeOreDictionary();
        // Include a one-block loaded margin so ores on a chunk seam are
        // cleaned even when the water cell belongs to the neighbouring chunk.
        int minX = origin.getX() - 1;
        int minZ = origin.getZ() - 1;
        int maxX = origin.getX() + 17;
        int maxZ = origin.getZ() + 17;
        int maxY = Math.min(255, world.getActualHeight() - 1);

        // Most End chunks are unrelated to this biome. Avoid a full vertical
        // scan unless the loaded chunk or its one-block margin actually has a
        // Sulphur Springs column.
        boolean springArea = false;
        for (int x = minX; x < maxX && !springArea; x++)
            for (int z = minZ; z < maxZ; z++)
            {
                BlockPos column = new BlockPos(x, 0, z);
                if (world.isBlockLoaded(column, false)
                        && EndBiomeTerrain.matches(world.getBiome(column), ModBiomes.SULPHUR_SPRINGS))
                {
                    springArea = true;
                    break;
                }
            }
        if (!springArea) return;

        for (int x = minX; x < maxX; x++)
            for (int z = minZ; z < maxZ; z++)
                for (int y = 1; y <= maxY; y++)
                {
                    BlockPos water = new BlockPos(x, y, z);
                    if (!world.isBlockLoaded(water, false)) continue;
                    if (world.getBlockState(water).getMaterial() != Material.WATER
                            || !EndBiomeTerrain.matches(world.getBiome(water), ModBiomes.SULPHUR_SPRINGS))
                        continue;
                    // Start at the water cells rather than walking every ore
                    // in the chunk. Only a block directly touching water is
                    // exposed and should be replaced.
                    for (EnumFacing facing : EnumFacing.values())
                    {
                        BlockPos exposed = water.offset(facing);
                        if (world.isBlockLoaded(exposed, false) && ores.isOre(world.getBlockState(exposed)))
                            world.setBlockState(exposed, replacement(world, exposed, ores), 2);
                    }
                }
    }

    private static IBlockState replacement(World world, BlockPos pos, LakeOreDictionary ores)
    {
        // Preserve the local spring palette where possible. The block below
        // is the usual lake floor and also handles brimstone/flavolite bands.
        for (EnumFacing facing : new EnumFacing[] { EnumFacing.DOWN, EnumFacing.NORTH,
                EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP })
        {
            IBlockState candidate = world.getBlockState(pos.offset(facing));
            if (candidate.getMaterial().isSolid() && candidate.getBlock() != Blocks.AIR
                    && candidate.getMaterial() != Material.WATER && !ores.isOre(candidate))
                return candidate;
        }
        return ModBlocks.SULPHURIC_ROCK.stone.getDefaultState();
    }
}
