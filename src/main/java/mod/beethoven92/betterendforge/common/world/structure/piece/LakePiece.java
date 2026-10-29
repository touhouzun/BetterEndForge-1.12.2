package mod.beethoven92.betterendforge.common.world.structure.piece;

import java.util.Random;

import mod.beethoven92.betterendforge.common.block.template.PlantBlock;
import mod.beethoven92.betterendforge.common.init.ModBiomes;
import mod.beethoven92.betterendforge.common.init.ModTags;
import mod.beethoven92.betterendforge.common.util.BlockHelper;
import mod.beethoven92.betterendforge.common.world.biome.ExtendedBiome;
import mod.beethoven92.betterendforge.common.world.generator.OpenSimplexNoise;
import mod.beethoven92.betterendforge.common.world.generator.LakeOreDictionary;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;

/** A deterministic, closed two-dimensional lake basin. */
public class LakePiece extends StructureComponent
{
	private static final IBlockState ENDSTONE = Blocks.END_STONE.getDefaultState();
	private static final IBlockState AIR = Blocks.AIR.getDefaultState();
	private static final IBlockState WATER = Blocks.WATER.getDefaultState();

	private BlockPos center;
	private float radius;
	private float depth;
	private int waterLevel;
	private int seed;
	private ResourceLocation biomeID;
	private transient OpenSimplexNoise noise;
	private transient LakeOreDictionary ores = new LakeOreDictionary();

	public LakePiece(BlockPos center, float radius, float depth, Random random, Biome biome)
	{
		super(random.nextInt());
		this.center = center;
		this.radius = radius;
		this.depth = depth;
		this.waterLevel = center.getY() - 1;
		this.seed = random.nextInt();
		this.noise = new OpenSimplexNoise(seed);
		this.biomeID = ModBiomes.getBiomeID(biome);
		makeBoundingBox();
	}

	@Override
	protected void readStructureFromNBT(NBTTagCompound nbt, TemplateManager manager)
	{
		this.center = new BlockPos(nbt.getInteger("centerX"), nbt.getInteger("centerY"), nbt.getInteger("centerZ"));
		this.radius = nbt.getFloat("radius");
		this.depth = nbt.getFloat("depth");
		this.waterLevel = nbt.hasKey("waterLevel") ? nbt.getInteger("waterLevel") : center.getY() - 1;
		this.seed = nbt.getInteger("seed");
		this.noise = new OpenSimplexNoise(seed);
		this.biomeID = new ResourceLocation(nbt.getString("biomeid"));
		this.ores = new LakeOreDictionary();
		makeBoundingBox();
	}

	@Override
	protected void writeStructureToNBT(NBTTagCompound tag)
	{
		tag.setInteger("centerX", center.getX());
		tag.setInteger("centerY", center.getY());
		tag.setInteger("centerZ", center.getZ());
		tag.setFloat("radius", radius);
		tag.setFloat("depth", depth);
		tag.setInteger("waterLevel", waterLevel);
		tag.setInteger("seed", seed);
		tag.setString("biomeid", biomeID.toString());
	}

	private void makeBoundingBox()
	{
		int r = MathHelper.ceil(radius) + 2;
		this.boundingBox = new StructureBoundingBox(center.getX() - r, waterLevel - MathHelper.ceil(depth) - 2,
				center.getZ() - r, center.getX() + r, waterLevel + 3, center.getZ() + r);
	}

	@Override
	public boolean addComponentParts(World world, Random random, StructureBoundingBox box)
	{
		ores = new LakeOreDictionary();
		int minX = Math.max(box.minX, boundingBox.minX);
		int maxX = Math.min(box.maxX, boundingBox.maxX);
		int minZ = Math.max(box.minZ, boundingBox.minZ);
		int maxZ = Math.min(box.maxZ, boundingBox.maxZ);
		if (minX > maxX || minZ > maxZ) return false;

		for (int x = minX; x <= maxX; x++)
		{
			for (int z = minZ; z <= maxZ; z++)
			{
				if (!inside(x, z)) continue;
				int surface = getSurfaceHeight(world, x, z);
				if (!validColumn(world, x, z, surface)) continue;
				int bottom = getBottom(x, z);
				if (!canCarveColumn(world, x, z, bottom, surface)) continue;

				for (int y = bottom; y <= surface + 2; y++)
				{
					BlockPos pos = new BlockPos(x, y, z);
					IBlockState state = world.getBlockState(pos);
					if (!canReplace(state)) continue;
					if (y == bottom) BlockHelper.setWithoutUpdate(world, pos, getSurfaceTop(world.getBiome(pos)));
					else if (y <= waterLevel) BlockHelper.setWithoutUpdate(world, pos, WATER);
					else if (y <= surface + 1) BlockHelper.setWithoutUpdate(world, pos, AIR);
				}
				removePlants(world, x, z, surface + 2);
			}
		}

		// Raise a deterministic end-stone lip wherever the lake meets a low edge.
		for (int x = minX; x <= maxX; x++)
		{
			for (int z = minZ; z <= maxZ; z++)
			{
				if (!inside(x, z) || !hasWater(world, x, z)) continue;
				for (int dx = -1; dx <= 1; dx++)
				{
					for (int dz = -1; dz <= 1; dz++)
					{
						if (Math.abs(dx) + Math.abs(dz) != 1 || inside(x + dx, z + dz)) continue;
						sealColumn(world, x + dx, z + dz);
					}
				}
			}
		}

		// A lake can be generated on an island edge where the neighbouring
		// columns are completely empty.  In that case the normal one-block lip
		// has nothing to attach to and water can escape into the void.  Build a
		// closed, deterministic shell around every exposed shore before the
		// decorator gets a chance to place water plants.
		reinforceVoidShore(world, minX, maxX, minZ, maxZ);
		return true;
	}

	private boolean inside(int x, int z)
	{
		double dx = (x - center.getX()) / radius;
		double dz = (z - center.getZ()) / radius;
		double n = noise.eval(x * 0.035D, z * 0.035D) * 0.055D;
		return dx * dx + dz * dz <= (1.0D + n) * (1.0D + n);
	}

	private int getBottom(int x, int z)
	{
		double dx = (x - center.getX()) / radius;
		double dz = (z - center.getZ()) / radius;
		double distance = Math.sqrt(Math.min(1.0D, dx * dx + dz * dz));
		int d = Math.max(1, MathHelper.floor(depth * (1.0D - distance * 0.78D)));
		return waterLevel - d;
	}

	private boolean validColumn(World world, int x, int z, int surface)
	{
		if (surface < waterLevel - 1 || surface > waterLevel + 4) return false;
		// A lake is allowed to cross the parent biome's sub-biome boundary.  The
		// old check against biomeID rejected almost every outer column because a
		// MEGALAKE island commonly contains MEGALAKE_GROVE patches.
		return true;
	}

	private boolean canCarveColumn(World world, int x, int z, int bottom, int surface)
	{
		for (int y = bottom; y <= surface + 1; y++)
		{
			if (!canReplace(world.getBlockState(new BlockPos(x, y, z)))) return false;
		}
		return true;
	}

	private boolean hasWater(World world, int x, int z)
	{
		return inside(x, z) && world.getBlockState(new BlockPos(x, waterLevel, z)).getBlock() == Blocks.WATER;
	}

	private void sealColumn(World world, int x, int z)
	{
		int surface = getSurfaceHeight(world, x, z);
		if (surface >= waterLevel - 1) return;
		IBlockState wall = getEnvironmentBlock(world, x, z);
		// The lip reaches one block above the water surface.  Filling only to
		// waterLevel - 1 still lets a source block flow sideways at the surface.
		for (int y = Math.max(1, surface + 1); y <= waterLevel; y++)
		{
			BlockPos pos = new BlockPos(x, y, z);
			if (canReplace(world.getBlockState(pos))) BlockHelper.setWithoutUpdate(world, pos, wall);
		}
	}

	private void reinforceVoidShore(World world, int minX, int maxX, int minZ, int maxZ)
	{
		int shellRadius = MathHelper.ceil(radius) + 1;
		int shellBottom = Math.max(1, waterLevel - MathHelper.ceil(depth) - 2);
		int shellTop = Math.min(254, waterLevel + 1);
		for (int x = Math.max(minX, center.getX() - shellRadius); x <= Math.min(maxX, center.getX() + shellRadius); x++)
		{
			for (int z = Math.max(minZ, center.getZ() - shellRadius); z <= Math.min(maxZ, center.getZ() + shellRadius); z++)
			{
				if (inside(x, z) || !nearWater(world, x, z)) continue;
				if (!isVoidColumn(world, x, z, shellBottom, waterLevel))
				{
					// Existing low terrain still needs to be raised, but does not
					// need the full void shell.
					sealColumn(world, x, z);
					continue;
				}

				IBlockState wall = getEnvironmentBlock(world, x, z);
				for (int y = shellBottom; y <= shellTop; y++)
				{
					BlockPos blockPos = new BlockPos(x, y, z);
					if (canReplace(world.getBlockState(blockPos)))
					{
						BlockHelper.setWithoutUpdate(world, blockPos, wall);
					}
				}
			}
		}
	}

	private boolean nearWater(World world, int x, int z)
	{
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dz = -1; dz <= 1; dz++)
			{
				if (dx == 0 && dz == 0) continue;
				if (hasWater(world, x + dx, z + dz)) return true;
			}
		}
		return false;
	}

	private boolean isVoidColumn(World world, int x, int z, int minY, int maxY)
	{
		boolean foundSolid = false;
		for (int y = minY; y <= maxY; y++)
		{
			IBlockState state = world.getBlockState(new BlockPos(x, y, z));
			Block block = state.getBlock();
			if (block != Blocks.AIR && block != Blocks.WATER && block != Blocks.FLOWING_WATER
					&& !state.getMaterial().isReplaceable())
			{
				foundSolid = true;
				break;
			}
		}
		return !foundSolid;
	}

	private IBlockState getEnvironmentBlock(World world, int x, int z)
	{
		// Prefer a nearby terrain block so sub-biomes (for example the grove)
		// keep their own shore material even when the edge column is void.
		for (int dx = -2; dx <= 2; dx++)
		{
			for (int dz = -2; dz <= 2; dz++)
			{
				int sx = x + dx;
				int sz = z + dz;
				int sy = getSurfaceHeight(world, sx, sz);
				if (sy <= 1) continue;
				IBlockState state = world.getBlockState(new BlockPos(sx, sy, sz));
				if (state.getBlock() != Blocks.AIR && state.getBlock() != Blocks.WATER
						&& state.getBlock() != Blocks.FLOWING_WATER && ModTags.GEN_TERRAIN.contains(state.getBlock()))
				{
					return state;
				}
			}
		}
		IBlockState biomeTop = getSurfaceTop(world.getBiome(new BlockPos(center.getX(), waterLevel, center.getZ())));
		if (biomeTop.getBlock() != Blocks.AIR && biomeTop.getBlock() != Blocks.WATER
				&& biomeTop.getBlock() != Blocks.FLOWING_WATER)
		{
			return biomeTop;
		}
		return ENDSTONE;
	}

	private int getSurfaceHeight(World world, int x, int z)
	{
		int y = MathHelper.clamp(world.getHeight(x, z), 1, 255);
		if (world.isAirBlock(new BlockPos(x, y, z))) y--;
		while (y > 1 && (world.isAirBlock(new BlockPos(x, y, z)) || world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof PlantBlock)) y--;
		return y;
	}

	private void removePlants(World world, int x, int z, int top)
	{
		for (int y = Math.max(1, waterLevel + 1); y <= top; y++)
		{
			BlockPos pos = new BlockPos(x, y, z);
			if (world.getBlockState(pos).getBlock() instanceof PlantBlock) BlockHelper.setWithoutUpdate(world, pos, AIR);
		}
	}

	private boolean canReplace(IBlockState state)
	{
		Block block = state.getBlock();
		return block == Blocks.AIR || block == Blocks.WATER || block == Blocks.FLOWING_WATER
				|| ModTags.GEN_TERRAIN.contains(block) || block instanceof PlantBlock || ores.isOre(state)
				|| state.getMaterial().isReplaceable();
	}

	private IBlockState getSurfaceTop(Biome biome)
	{
		if (biome instanceof ExtendedBiome)
		{
			ExtendedBiome extended = (ExtendedBiome) biome;
			if (extended.getSurface() != null) return extended.getSurface().config.getTop();
		}
		return biome.topBlock;
	}
}
