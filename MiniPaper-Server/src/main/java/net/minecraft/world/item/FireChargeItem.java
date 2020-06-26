package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
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

public class FireChargeItem extends Item {

    public FireChargeItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockState iblockdata = world.getType(blockposition);
        boolean flag = false;

        if (CampfireBlock.canLight(iblockdata)) {
            // CraftBukkit start - fire BlockIgniteEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL, itemactioncontext.getPlayer()).isCancelled()) {
                if (!itemactioncontext.getPlayer().abilities.instabuild) {
                    itemactioncontext.getItemInHand().shrink(1);
                }
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.playSound(world, blockposition);
            world.setTypeUpdate(blockposition, (BlockState) iblockdata.setValue(CampfireBlock.LIT, true));
            flag = true;
        } else {
            blockposition = blockposition.relative(itemactioncontext.getClickedFace());
            if (BaseFireBlock.canBePlacedAt((LevelAccessor) world, blockposition)) {
                // CraftBukkit start - fire BlockIgniteEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL, itemactioncontext.getPlayer()).isCancelled()) {
                    if (!itemactioncontext.getPlayer().abilities.instabuild) {
                        itemactioncontext.getItemInHand().shrink(1);
                    }
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                this.playSound(world, blockposition);
                world.setTypeUpdate(blockposition, BaseFireBlock.getState((BlockGetter) world, blockposition));
                flag = true;
            }
        }

        if (flag) {
            itemactioncontext.getItemInHand().shrink(1);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.FAIL;
        }
    }

    private void playSound(Level world, BlockPos blockposition) {
        world.playSound((Player) null, blockposition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, (FireChargeItem.random.nextFloat() - FireChargeItem.random.nextFloat()) * 0.2F + 1.0F);
    }
}
