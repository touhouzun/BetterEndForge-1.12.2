package mod.beethoven92.betterendforge.common.world.feature;

import java.util.Random;
import java.util.function.IntBinaryOperator;

import mod.beethoven92.betterendforge.common.init.ModBiomes;
import mod.beethoven92.betterendforge.common.init.ModBlocks;
import mod.beethoven92.betterendforge.common.init.ModTags;
import mod.beethoven92.betterendforge.common.world.generator.EndBiomeTerrain;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

/** Faceted aurora spires decorate the continuous mountain terrain. */
public class CrystalMountainFeature extends WorldGenerator
{
    @Override
    public boolean generate(World world, Random ignored, BlockPos origin)
    {
        int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
        Random random = new Random(world.getSeed() ^ (chunkX * 341873128712L)
                ^ (chunkZ * 132897987541L) ^ 0x43525953544CL);
        boolean generated = false;
        for (int i = 0, count = 3 + random.nextInt(5); i < count; i++)
        {
            int radius = 1 + random.nextInt(3);
            int x = (chunkX << 4) + radius + random.nextInt(16 - radius * 2);
            int z = (chunkZ << 4) + radius + random.nextInt(16 - radius * 2);
            BlockPos surface = world.getHeight(new BlockPos(x, 0, z)).down();
            if (!EndBiomeTerrain.matches(world.getBiome(surface), ModBiomes.CRYSTAL_MOUNTAINS)
                    || surface.getY() < 76 || !ModTags.GEN_TERRAIN.contains(world.getBlockState(surface).getBlock())) continue;
            int[][] foundation = sampleFoundation(x, z, surface.getY(), radius, (px, pz) -> {
                BlockPos column = new BlockPos(px, 0, pz);
                if (!world.isBlockLoaded(column, false)) return -1;
                BlockPos base = world.getHeight(column).down();
                IBlockState state = world.getBlockState(base);
                return state.isFullCube() && ModTags.GEN_TERRAIN.contains(state.getBlock())
                        && EndBiomeTerrain.matches(world.getBiome(base), ModBiomes.CRYSTAL_MOUNTAINS) ? base.getY() : -1;
            });
            if (foundation == null) continue;
            int height = 5 + radius * 3 + random.nextInt(8) + Math.max(0, surface.getY() - 100) / 8;
            int tipX = random.nextInt(3) - 1, tipZ = random.nextInt(3) - 1;
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    // Octagonal sections with tilted, tapered crystal tips.
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                    int top = Math.min(248, surface.getY() + height + dx * tipX + dz * tipZ
                            - Math.max(Math.abs(dx), Math.abs(dz)) * 2);
                    for (int y = foundation[dx + radius][dz + radius] - 2; y <= top; y++)
                    {
                        BlockPos pos = new BlockPos(x + dx, y, z + dz);
                        IBlockState old = world.getBlockState(pos);
                        if (old.getBlock() == Blocks.AIR || ModTags.GEN_TERRAIN.contains(old.getBlock()))
                            world.setBlockState(pos, ModBlocks.AURORA_CRYSTAL.getDefaultState(), 2);
                    }
                }
            }
            generated = true;
        }
        return generated;
    }

    static int[][] sampleFoundation(int x, int z, int centerY, int radius, IntBinaryOperator terrainHeight)
    {
        int[][] base = new int[radius * 2 + 1][radius * 2 + 1];
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
            {
                if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                int y = terrainHeight.applyAsInt(x + dx, z + dz);
                // Reject a spire hanging off an island or a very steep cliff.
                // Each accepted column starts from its own solid terrain.
                if (y < centerY - 6 || y > centerY + 4) return null;
                base[dx + radius][dz + radius] = y;
            }
        return base;
    }
}
