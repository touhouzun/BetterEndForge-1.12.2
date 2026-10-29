package mod.beethoven92.betterendforge.common.world.moderngen.decorator;

import mod.beethoven92.betterendforge.common.capability.EndData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.List;
import java.util.Random;

public class EndDecorator extends BiomeDecorator {
    public List<List<ConfiguredFeature<?, ?>>> list;

    public static boolean decorating = false;

    public EndDecorator(List<List<ConfiguredFeature<?, ?>>> list){
        this.list = list;
    }

    public List<ConfiguredFeature<?, ?>> getFeatures(Decoration stage){
        return list.get(stage.ordinal());
    }

    public void decorate(World worldIn, Random random, Biome biome, BlockPos pos){
        if(decorating){
            EndData.getInstance().storePass(pos);
            return;
        }
        decorating = true;
        try {
            // Caves/other generators can open the island's sides after the primer.
            // Close those shores before any floating vegetation is placed.
            if (worldIn.provider.getDimension() == 1)
                mod.beethoven92.betterendforge.common.world.generator.LakeShore.seal(worldIn, pos);
            int len = Decoration.values().length;
            for(int i = 0; i < len; ++i) {
                for(ConfiguredFeature<?, ?> feature : list.get(i)){
                    try {
                        feature.place(worldIn, random, pos);
                    }catch (Exception e){
                        mod.beethoven92.betterendforge.BetterEnd.LOGGER.warn("Failed to generate {} at {}",
                                feature.feature().getClass().getName(), pos, e);
                    }
                }
            }
        } finally {
            decorating = false;
        }
    }
}
