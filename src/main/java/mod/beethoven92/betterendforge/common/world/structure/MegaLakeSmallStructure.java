package mod.beethoven92.betterendforge.common.world.structure;

import git.jbredwards.nether_api.api.world.INetherAPIChunkGenerator;
import mod.beethoven92.betterendforge.BetterEnd;
import mod.beethoven92.betterendforge.common.init.ModBiomes;
import mod.beethoven92.betterendforge.common.util.ModMathHelper;
import mod.beethoven92.betterendforge.common.world.moderngen.decorator.Decoration;
import mod.beethoven92.betterendforge.common.world.structure.piece.LakePiece;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.Random;

public class MegaLakeSmallStructure extends SepMapGenStructure
{


	public MegaLakeSmallStructure(INetherAPIChunkGenerator endProviderIn, int spacing, int separation, int salt) {
		super(endProviderIn, spacing, separation, salt);
	}

	public Decoration getDecorationStage()
	{
		return Decoration.RAW_GENERATION;
	}
	
	@Override
	public String getStructureName() 
	{
		return BetterEnd.MOD_ID + ":megalake_small_structure";
	}

	@Override
	protected boolean canSpawnInBiome(Biome biome)
	{
		return isBiome(biome, ModBiomes.MEGALAKE_GROVE);
	}

	protected StructureStart getStructureStart(int chunkX, int chunkZ)
	{
		return new SDFStructureStart(this, this.world, this.rand, chunkX, chunkZ);
	}

	public static class SDFStructureStart extends StructureStart
	{
		MegaLakeSmallStructure structure;
		public SDFStructureStart()
		{
		}

		public SDFStructureStart(MegaLakeSmallStructure structure, World worldIn, Random random, int chunkX, int chunkZ)
		{
			super(chunkX, chunkZ);
			this.structure = structure;
			this.create(worldIn, random, chunkX, chunkZ);
		}

		private void create(World worldIn, Random rand, int chunkX, int chunkZ)
		{
			int x = (chunkX << 4) | ModMathHelper.randRange(4, 12, rand);
			int z = (chunkZ << 4) | ModMathHelper.randRange(4, 12, rand);
			int y = worldIn.getHeight(x, z);
			if (y < 45) y = SepMapGenStructure.getYPosForStructure(chunkX, chunkZ, structure.endProvider);
			float radius = ModMathHelper.randRange(24, 38, rand);
			float depth = ModMathHelper.randRange(4, 7, rand);
			if (isValidLocation(worldIn, x, z, y, radius))
			{
				LakePiece piece = new LakePiece(new BlockPos(x, y, z), radius, depth, rand, worldIn.getBiome(new BlockPos(x,y,z)));
				this.components.add(piece);
			}
			this.updateBoundingBox();
		}

		private boolean isValidLocation(World world, int x, int z, int y, float radius)
		{
			if (y < 45 || y > 100) return false;
			Biome centerBiome = world.getBiome(new BlockPos(x, y, z));
			if (!((MegaLakeSmallStructure) structure).canSpawnInBiome(centerBiome)) return false;
			int waterLevel = y - 1;
			for (int i = 0; i < 12; i++)
			{
				double angle = Math.PI * 2.0D * i / 12.0D;
				int px = x + (int) Math.round(Math.cos(angle) * (radius + 2));
				int pz = z + (int) Math.round(Math.sin(angle) * (radius + 2));
				int height = world.getHeight(px, pz);
				if (height < waterLevel - 2 || height > waterLevel + 7 || Math.abs(height - y) > 8) return false;
			}
			return true;
		}
	}
}
