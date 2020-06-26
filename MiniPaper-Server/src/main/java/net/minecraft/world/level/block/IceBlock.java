package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.PushReaction;

public class IceBlock extends HalfTransparentBlock {

    public IceBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public void playerDestroy(Level world, Player entityhuman, BlockPos blockposition, BlockState iblockdata, @Nullable BlockEntity tileentity, ItemStack itemstack) {
        super.playerDestroy(world, entityhuman, blockposition, iblockdata, tileentity, itemstack);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            if (world.dimensionType().ultraWarm()) {
                world.removeBlock(blockposition, false);
                return;
            }

            Material material = world.getType(blockposition.below()).getMaterial();

            if (material.blocksMotion() || material.isLiquid()) {
                world.setTypeUpdate(blockposition, Blocks.WATER.getBlockData());
            }
        }

    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.getBrightness(LightLayer.BLOCK, blockposition) > 11 - iblockdata.getLightBlock((BlockGetter) worldserver, blockposition)) {
            this.melt(iblockdata, worldserver, blockposition);
        }

    }

    protected void melt(BlockState iblockdata, Level world, BlockPos blockposition) {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, blockposition, world.dimensionType().ultraWarm() ? Blocks.AIR.getBlockData() : Blocks.WATER.getBlockData()).isCancelled()) {
            return;
        }
        // CraftBukkit end
        if (world.dimensionType().ultraWarm()) {
            world.removeBlock(blockposition, false);
        } else {
            world.setTypeUpdate(blockposition, Blocks.WATER.getBlockData());
            world.neighborChanged(blockposition, Blocks.WATER, blockposition);
        }
    }

    @Override
    public PushReaction getPushReaction(BlockState iblockdata) {
        return PushReaction.NORMAL;
    }
}
