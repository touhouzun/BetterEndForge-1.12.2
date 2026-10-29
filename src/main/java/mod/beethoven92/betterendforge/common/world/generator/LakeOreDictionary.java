package mod.beethoven92.betterendforge.common.world.generator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** A per-generation cache: neither metadata variants nor late registrations are lost. */
public final class LakeOreDictionary
{
    private final Map<IBlockState, Boolean> states = new HashMap<>();

    public boolean isOre(IBlockState state)
    {
        return states.computeIfAbsent(state, LakeOreDictionary::matches);
    }

    private static boolean matches(IBlockState state)
    {
        Block block = state.getBlock();
        Item item = Item.getItemFromBlock(block);
        if (item == null || item == Items.AIR) return false;
        // Query the block variant, not its drops (which may be gems or dust,
        // or default to metadata zero for every variant).
        ItemStack stack = new ItemStack(item, 1, block.getMetaFromState(state));
        // Forge includes both exact metadata and WILDCARD_VALUE entries here.
        for (int id : OreDictionary.getOreIDs(stack))
            if (OreDictionary.getOreName(id).toLowerCase(Locale.ROOT).startsWith("ore")) return true;
        return false;
    }
}
