package mod.beethoven92.betterendforge.mixin.minecraft;

import mod.beethoven92.betterendforge.common.world.generator.LakeShore;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Post-populate terrain events precede Forge's IWorldGenerators. Clean after ALL of them. */
@Mixin(Chunk.class)
public abstract class LakePopulationMixin
{
    @Inject(method = "populate(Lnet/minecraft/world/gen/IChunkGenerator;)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, remap = false,
                    target = "Lnet/minecraftforge/fml/common/registry/GameRegistry;generateWorld(IILnet/minecraft/world/World;Lnet/minecraft/world/gen/IChunkGenerator;Lnet/minecraft/world/chunk/IChunkProvider;)V"),
            require = 1)
    private void betterEndForge$cleanLakeOres(IChunkGenerator generator, CallbackInfo ci)
    {
        Chunk chunk = (Chunk) (Object) this;
        World world = chunk.getWorld();
        if (!world.isRemote && world.provider.getDimension() == 1)
            LakeShore.sealLoadedNeighbours(world, new BlockPos(chunk.x << 4, 0, chunk.z << 4));
    }
}
