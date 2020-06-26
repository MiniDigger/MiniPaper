package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class OreBlock extends Block {

    public OreBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    protected int xpOnDrop(Random random) {
        return this == Blocks.COAL_ORE ? Mth.nextInt(random, 0, 2) : (this == Blocks.DIAMOND_ORE ? Mth.nextInt(random, 3, 7) : (this == Blocks.EMERALD_ORE ? Mth.nextInt(random, 3, 7) : (this == Blocks.LAPIS_ORE ? Mth.nextInt(random, 2, 5) : (this == Blocks.NETHER_QUARTZ_ORE ? Mth.nextInt(random, 2, 5) : (this == Blocks.NETHER_GOLD_ORE ? Mth.nextInt(random, 0, 1) : 0)))));
    }

    @Override
    public void dropNaturally(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        super.dropNaturally(iblockdata, world, blockposition, itemstack);
        /* CraftBukkit start - Delegated to getExpDrop
        if (EnchantmentManager.getEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            int i = this.a(world.random);

            if (i > 0) {
                this.dropExperience(world, blockposition, i);
            }
        }
        // */

    }

    @Override
    public int getExpDrop(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            int i = this.xpOnDrop(world.random);

            if (i > 0) {
                return i;
            }
        }

        return 0;
        // CraftBukkit end
    }
}
