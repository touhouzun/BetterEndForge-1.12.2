package mod.beethoven92.betterendforge.common.world.generator;

import java.util.HashMap;
import java.util.Map;

import mod.beethoven92.betterendforge.common.init.ModBlocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Removes only ore dictionary blocks in an existing basin; never excavates new land. */
public final class LakeOreCleaner
{
    private LakeOreCleaner() {}

    static void clear(World world, BlockPos origin, int margin)
    {
        LakeOreDictionary ores = new LakeOreDictionary();
        BiomeTerrainNoise noise = EndBiomeTerrain.noise(world.getSeed());
        Map<Long, BiomeTransitionField> transitions = new HashMap<>();
        int level = BiomeTerrainNoise.LAKE_LEVEL;
        for (int x = origin.getX() - margin; x < origin.getX() + 16 + margin; x++)
            for (int z = origin.getZ() - margin; z < origin.getZ() + 16 + margin; z++)
            {
                BlockPos surface = new BlockPos(x, level, z);
                if (!world.isBlockLoaded(surface, false) || !EndBiomeTerrain.isLake(world.getBiome(surface))) continue;
                int chunkX = x >> 4, chunkZ = z >> 4;
                long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
                BiomeTransitionField edges = transitions.computeIfAbsent(key,
                        ignored -> EndBiomeTerrain.transitionField(world, chunkX, chunkZ));
                if (edges.distance(BiomeTransitionField.LAKE, x, z) <= 0) continue;
                double grove = TerrainTransitionProfile.groveMix(edges.distance(BiomeTransitionField.GROVE, x, z));
                int minimumFloor = (int) Math.round(noise.lakeHeight(x, z, grove));
                if (minimumFloor >= level) continue;
                int floor = findFloor(world, x, z, minimumFloor, level, ores);
                // A noise mask alone cannot distinguish a lake from preserved
                // void, a biome boundary bank, or the emergency solid shore.
                IBlockState cap = world.getBlockState(surface.up());
                if (cap.getBlock() == ModBlocks.END_MOSS || cap.getBlock() == ModBlocks.ENDSTONE_DUST) continue;
                if (!isBasin(world, x, z, floor, level, ores)) continue;
                clearColumn(world, x, z, floor, level, ModBlocks.ENDSTONE_DUST.getDefaultState(), ores);
            }
    }

    static int findFloor(World world, int x, int z, int minimumFloor, int level, LakeOreDictionary ores)
    {
        // Boundary shelves sit above the deep noise basin. Stop at their
        // actual floor so cleanup cannot turn buried shore ores into water.
        for (int y = level; y > minimumFloor; y--)
        {
            IBlockState state = world.getBlockState(new BlockPos(x, y, z));
            if ((state.getBlock() == Blocks.END_STONE || state.getBlock() instanceof BlockFalling)
                    && !ores.isOre(state)) return y;
        }
        return minimumFloor;
    }

    static boolean isBasin(World world, int x, int z, int floor, int level, LakeOreDictionary ores)
    {
        boolean water = false;
        boolean allOre = true;
        for (int y = floor + 1; y <= level; y++)
        {
            IBlockState state = world.getBlockState(new BlockPos(x, y, z));
            water |= state.getMaterial() == Material.WATER;
            allOre &= ores.isOre(state);
        }
        if (water) return true;
        // Handle a short water column completely replaced by a vein. Require
        // both a supported basin floor and water beside it; never create lakes
        // from the noise mask alone or reopen a solid end-stone retaining wall.
        if (!allOre || !world.getBlockState(new BlockPos(x, floor, z)).isFullCube()) return false;
        for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.Plane.HORIZONTAL)
        {
            BlockPos neighbour = new BlockPos(x, level, z).offset(facing);
            if (world.isBlockLoaded(neighbour, false) && world.getBlockState(neighbour).getMaterial() == Material.WATER)
                return true;
        }
        return false;
    }

    static void clearColumn(World world, int x, int z, int floor, int level,
            IBlockState floorMaterial, LakeOreDictionary ores)
    {
        int top = Math.min(255, Math.max(level, world.getHeight(x, z)));
        for (int y = floor; y <= top; y++)
        {
            BlockPos pos = new BlockPos(x, y, z);
            if (!ores.isOre(world.getBlockState(pos))) continue;
            IBlockState replacement = y == floor ? floorMaterial
                    : y <= level ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
            world.setBlockState(pos, replacement, 2);
        }
    }
}
