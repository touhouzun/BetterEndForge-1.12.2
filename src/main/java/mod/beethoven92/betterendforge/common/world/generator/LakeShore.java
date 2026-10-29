package mod.beethoven92.betterendforge.common.world.generator;

import java.util.ArrayList;
import java.util.List;

import mod.beethoven92.betterendforge.common.init.ModBiomes;
import mod.beethoven92.betterendforge.common.init.ModBlocks;
import mod.beethoven92.betterendforge.common.block.template.UnderwaterPlantBlock;
import mod.beethoven92.betterendforge.common.world.biome.ExtendedBiome;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/** Final insurance against island edges and caves opened after surface building. */
public final class LakeShore
{
    private LakeShore() {}

    public static void seal(World world, BlockPos origin)
    {
        seal(world, origin, 1);
    }

    public static void sealLoadedNeighbours(World world, BlockPos origin)
    {
        // Veins can spill into adjacent chunks that were populated earlier.
        // Never force-load neighbours merely to clean them.
        seal(world, origin, 16);
    }

    private static void seal(World world, BlockPos origin, int margin)
    {
        LakeOreCleaner.clear(world, origin, margin);
        int level = BiomeTerrainNoise.LAKE_LEVEL;
        List<BlockPos> exposed = new ArrayList<>();
        // Include the loaded neighbours' border, so a newly generated void
        // column also seals a previously generated water column across a seam.
        for (int x = origin.getX() - margin; x < origin.getX() + 16 + margin; x++)
        {
            for (int z = origin.getZ() - margin; z < origin.getZ() + 16 + margin; z++)
            {
                BlockPos surface = new BlockPos(x, level, z);
                if (!world.isBlockLoaded(surface, false) || !EndBiomeTerrain.isLake(world.getBiome(surface))
                        || world.getBlockState(surface).getMaterial() != Material.WATER) continue;
                boolean leak = false;
                for (int y = level; y >= level - 15; y--)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (world.getBlockState(pos).getMaterial() != Material.WATER) break;
                    for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL)
                    {
                        BlockPos neighbour = pos.offset(direction);
                        if (!world.isBlockLoaded(neighbour, false)) continue;
                        IBlockState state = world.getBlockState(neighbour);
                        // Aquatic stems occupy water cells in the 1.12 port.
                        // They must not be mistaken for a hole during Post.
                        if (state.getMaterial() != Material.WATER && state.getMaterial().isReplaceable()
                                && !(state.getBlock() instanceof UnderwaterPlantBlock)
                                && state.getBlock() != ModBlocks.END_LOTUS_STEM) leak = true;
                    }
                    if (world.isAirBlock(pos.down()))
                    {
                        // Seal exposed basin floors without making pillars to Y=0.
                        for (int dy = 1; dy <= 3; dy++)
                            if (world.isAirBlock(pos.down(dy))) world.setBlockState(pos.down(dy), Blocks.END_STONE.getDefaultState(), 2);
                    }
                    else if (world.getBlockState(pos.down()).getBlock() instanceof BlockFalling
                            && world.isAirBlock(pos.down(2)))
                    {
                        // Endstone dust is falling sand; caves must not leave
                        // the lake floor cap unsupported after terrain building.
                        for (int dy = 2; dy <= 4; dy++)
                            if (world.isAirBlock(pos.down(dy))) world.setBlockState(pos.down(dy), Blocks.END_STONE.getDefaultState(), 2);
                    }
                }
                if (leak) exposed.add(surface);
            }
        }
        // Decide all exposed columns first; mutations must not influence the
        // decision for the next column (especially across chunk boundaries).
        for (BlockPos surface : exposed)
        {
            IBlockState cap = shoreMaterial(world.getBiome(surface));
            int ridge = (int) Math.round(EndBiomeTerrain.noise(world.getSeed()).shoreRidge(surface.getX(), surface.getZ()));
            int floor = level - 15;
            while (floor < level && world.getBlockState(new BlockPos(surface.getX(), floor, surface.getZ())).getMaterial() != Material.WATER) floor++;
            for (int y = floor - 2; y <= level; y++)
                world.setBlockState(new BlockPos(surface.getX(), y, surface.getZ()), Blocks.END_STONE.getDefaultState(), 2);
            // Rare late cave leaks still need a solid plug, but its crest
            // follows the same world-coordinate noise as the natural coast.
            // Keep decorations above it intact when they are solid blocks.
            int crest = level;
            for (int y = level + 1; y <= ridge; y++)
            {
                BlockPos pos = new BlockPos(surface.getX(), y, surface.getZ());
                if (!world.getBlockState(pos).getMaterial().isReplaceable()) break;
                world.setBlockState(pos, Blocks.END_STONE.getDefaultState(), 2);
                crest = y;
            }
            if (crest > level) world.setBlockState(new BlockPos(surface.getX(), crest, surface.getZ()), cap, 2);
        }
    }

    private static IBlockState shoreMaterial(Biome biome)
    {
        if (EndBiomeTerrain.matches(biome, ModBiomes.MEGALAKE_GROVE)) return ModBlocks.END_MOSS.getDefaultState();
        if (EndBiomeTerrain.matches(biome, ModBiomes.MEGALAKE)) return ModBlocks.ENDSTONE_DUST.getDefaultState();
        if (biome instanceof ExtendedBiome && ((ExtendedBiome) biome).getSurface() != null)
        {
            IBlockState top = ((ExtendedBiome) biome).getSurface().config.getTop();
            if (top.isFullCube()) return top;
        }
        return Blocks.END_STONE.getDefaultState();
    }
}
