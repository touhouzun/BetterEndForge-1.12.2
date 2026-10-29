package mod.beethoven92.betterendforge.mixin.minecraft;

import git.jbredwards.nether_api.api.world.INetherAPIChunkGenerator;
import git.jbredwards.nether_api.mod.common.world.gen.ChunkGeneratorTheEnd;
import mod.beethoven92.betterendforge.common.world.generator.EndBiomeTerrain;
import net.minecraft.world.chunk.ChunkPrimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blend both sides, including adjacent biomes supplied by other mods. */
@Mixin(value = ChunkGeneratorTheEnd.class, remap = false)
public abstract class EndBiomeSurfaceMixin
{
    @Inject(method = "buildSurfaces", at = @At("TAIL"), require = 1)
    private void betterEndForge$blendTerrain(int chunkX, int chunkZ, ChunkPrimer primer, CallbackInfo ci)
    {
        EndBiomeTerrain.buildChunkSurface((INetherAPIChunkGenerator) this, primer, chunkX, chunkZ);
    }
}
