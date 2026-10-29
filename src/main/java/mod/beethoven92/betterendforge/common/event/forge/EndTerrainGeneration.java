package mod.beethoven92.betterendforge.common.event.forge;

import mod.beethoven92.betterendforge.common.world.generator.LakeShore;
import mod.beethoven92.betterendforge.common.world.generator.SulphurWaterOreCleaner;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Terrain is built in the primer; this hook only closes holes after population.
 */
public final class EndTerrainGeneration
{
	public EndTerrainGeneration() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void populate(PopulateChunkEvent.Post event)
	{
		World world = event.getWorld();
		if (!world.isRemote && world.provider.getDimension() == 1)
		{
			SulphurWaterOreCleaner.clear(world, new BlockPos(event.getChunkX() << 4, 0, event.getChunkZ() << 4));
			LakeShore.seal(world, new BlockPos(event.getChunkX() << 4, 0, event.getChunkZ() << 4));
		}
	}
}
