package mod.beethoven92.betterendforge.common.world.structure;

import git.jbredwards.nether_api.api.world.INetherAPIChunkGenerator;
import mod.beethoven92.betterendforge.common.world.biome.BetterEndBiome;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Rotation;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.structure.MapGenStructure;

import java.util.Random;

public abstract class SepMapGenStructure extends MapGenStructure {
    public int spacing;
    public int separation;
    public int salt;

    protected final INetherAPIChunkGenerator endProvider;

    public SepMapGenStructure(INetherAPIChunkGenerator provider, int spacing, int separation, int salt){
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.endProvider = provider;
    }

    protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ)
    {
        // Nether API keeps modded structures in one global list.  The 1.16
        // implementation binds each structure to a biome, so perform that
        // check here before applying the spacing rules.
        if (world != null && !canSpawnInBiome(world.getBiome(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8))))
        {
            return false;
        }

        int i = chunkX;
        int j = chunkZ;

//        if (chunkX < 0)
//        {
//            chunkX -= 19;
//        }
//
//        if (chunkZ < 0)
//        {
//            chunkZ -= 19;
//        }

		// Java integer division truncates toward zero, which bunches starts on
		// the negative side of the End around coordinate zero.  Floor division
		// keeps the spacing grid uniform in all four directions.
		int k = Math.floorDiv(chunkX, spacing);
		int l = Math.floorDiv(chunkZ, spacing);
        Random random = this.world.setRandomSeed(k, l, salt);
        k = k * spacing;
        l = l * spacing;
        k = k + (random.nextInt(spacing-separation) + random.nextInt(spacing-separation)) / 2;
        l = l + (random.nextInt(spacing-separation) + random.nextInt(spacing-separation)) / 2;

        if (i == k && j == l)// && this.endProvider.isIslandChunk(i, j))
        {
            // The Nether API primer is not guaranteed to contain the final
            // Better End terrain at this point (some generators return an
            // empty primer until the chunk is requested).  Rejecting starts
            // from that primer made every lake disappear.  The world height
            // is already available during map generation and is the same
            // value used by the piece itself.
			int height = world.getHeight((i << 4) + 8, (j << 4) + 8);
			if (height < 45)
			{
				height = getYPosForStructure(i, j, this.endProvider);
			}
			return height >= 45;
        }
        else
        {
            return false;
        }
    }

    /**
     * Structures are global in Nether API; subclasses override this to keep
     * them in the biome(s) where the 1.16 version places them.
     */
    protected boolean canSpawnInBiome(Biome biome)
    {
        return true;
    }

    protected static boolean isBiome(Biome biome, BetterEndBiome expected)
    {
        return biome == expected.getBiome()
                || biome == expected.getActualBiome()
                || expected.getID().equals(biome.getRegistryName());
    }

    public static int getYPosForStructure(int chunkX, int chunkY, INetherAPIChunkGenerator generatorIn)
    {
        Random random = new Random((long)(chunkX + chunkY * 10387313));
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        ChunkPrimer chunkprimer = new ChunkPrimer();
        generatorIn.setBlocksInPrimer(chunkX, chunkY, chunkprimer);
        int i = 5;
        int j = 5;

        if (rotation == Rotation.CLOCKWISE_90)
        {
            i = -5;
        }
        else if (rotation == Rotation.CLOCKWISE_180)
        {
            i = -5;
            j = -5;
        }
        else if (rotation == Rotation.COUNTERCLOCKWISE_90)
        {
            j = -5;
        }

        int k = chunkprimer.findGroundBlockIdx(7, 7);
        int l = chunkprimer.findGroundBlockIdx(7, 7 + j);
        int i1 = chunkprimer.findGroundBlockIdx(7 + i, 7);
        int j1 = chunkprimer.findGroundBlockIdx(7 + i, 7 + j);
        int k1 = Math.min(Math.min(k, l), Math.min(i1, j1));
        return k1;
    }

    public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored)
    {
        this.world = worldIn;
        return findNearestStructurePosBySpacing(worldIn, this, pos, spacing, separation, salt, true, 100, findUnexplored);
    }

    public int getSalt() {
        return salt;
    }

    public int getSpacing() {
        return spacing;
    }

    public int getSeparation() {
        return separation;
    }
}
