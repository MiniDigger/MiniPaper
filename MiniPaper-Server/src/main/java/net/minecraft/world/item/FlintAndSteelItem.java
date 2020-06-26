package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FlintAndSteelItem extends Item {

    public FlintAndSteelItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Player entityhuman = itemactioncontext.getPlayer();
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockState iblockdata = world.getType(blockposition);

        if (CampfireBlock.canLight(iblockdata)) {
            world.playSound(entityhuman, blockposition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, FlintAndSteelItem.random.nextFloat() * 0.4F + 0.8F);
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(BlockStateProperties.LIT, true), 11);
            if (entityhuman != null) {
                itemactioncontext.getItemInHand().hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(itemactioncontext.getHand());
                });
            }

            return InteractionResult.sidedSuccess(world.isClientSide());
        } else {
            BlockPos blockposition1 = blockposition.relative(itemactioncontext.getClickedFace());

            if (BaseFireBlock.canBePlacedAt((LevelAccessor) world, blockposition1)) {
                // CraftBukkit start - Store the clicked block
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition1, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, entityhuman).isCancelled()) {
                    itemactioncontext.getItemInHand().hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                        entityhuman1.broadcastBreakEvent(itemactioncontext.getHand());
                    });
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                world.playSound(entityhuman, blockposition1, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, FlintAndSteelItem.random.nextFloat() * 0.4F + 0.8F);
                BlockState iblockdata1 = BaseFireBlock.getState((BlockGetter) world, blockposition1);

                world.setTypeAndData(blockposition1, iblockdata1, 11);
                ItemStack itemstack = itemactioncontext.getItemInHand();

                if (entityhuman instanceof ServerPlayer) {
                    CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) entityhuman, blockposition1, itemstack);
                    itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                        entityhuman1.broadcastBreakEvent(itemactioncontext.getHand());
                    });
                }

                return InteractionResult.sidedSuccess(world.isClientSide());
            } else {
                return InteractionResult.FAIL;
            }
        }
    }
}
