package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.event.hanging.HangingPlaceEvent;
// CraftBukkit end

public class HangingEntityItem extends Item {

    private final EntityType<? extends HangingEntity> type;

    public HangingEntityItem(EntityType<? extends HangingEntity> entitytypes, Item.Info item_info) {
        super(item_info);
        this.type = entitytypes;
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        BlockPos blockposition = itemactioncontext.getClickedPos();
        Direction enumdirection = itemactioncontext.getClickedFace();
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        net.minecraft.world.entity.player.Player entityhuman = itemactioncontext.getPlayer();
        ItemStack itemstack = itemactioncontext.getItemInHand();

        if (entityhuman != null && !this.mayPlace(entityhuman, enumdirection, itemstack, blockposition1)) {
            return InteractionResult.FAIL;
        } else {
            Level world = itemactioncontext.getLevel();
            Object object;

            if (this.type == EntityType.PAINTING) {
                object = new Painting(world, blockposition1, enumdirection);
            } else {
                if (this.type != EntityType.ITEM_FRAME) {
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }

                object = new ItemFrame(world, blockposition1, enumdirection);
            }

            CompoundTag nbttagcompound = itemstack.getTag();

            if (nbttagcompound != null) {
                EntityType.updateCustomEntityTag(world, entityhuman, (Entity) object, nbttagcompound);
            }

            if (((HangingEntity) object).survives()) {
                if (!world.isClientSide) {
                    // CraftBukkit start - fire HangingPlaceEvent
                    Player who = (itemactioncontext.getPlayer() == null) ? null : (Player) itemactioncontext.getPlayer().getBukkitEntity();
                    org.bukkit.block.Block blockClicked = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(enumdirection);

                    HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) ((HangingEntity) object).getBukkitEntity(), who, blockClicked, blockFace);
                    world.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    ((HangingEntity) object).playPlacementSound();
                    world.addFreshEntity((Entity) object);
                }

                itemstack.shrink(1);
                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.CONSUME;
            }
        }
    }

    protected boolean mayPlace(net.minecraft.world.entity.player.Player entityhuman, Direction enumdirection, ItemStack itemstack, BlockPos blockposition) {
        return !enumdirection.getAxis().isVertical() && entityhuman.mayUseItemAt(blockposition, enumdirection, itemstack);
    }
}
