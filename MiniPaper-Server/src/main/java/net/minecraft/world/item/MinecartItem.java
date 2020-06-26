package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class MinecartItem extends Item {

    private static final DispenseItemBehavior DISPENSE_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior() {
        private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

        @Override
        public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
            Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
            Level world = isourceblock.getLevel();
            double d0 = isourceblock.x() + (double) enumdirection.getStepX() * 1.125D;
            double d1 = Math.floor(isourceblock.y()) + (double) enumdirection.getStepY();
            double d2 = isourceblock.z() + (double) enumdirection.getStepZ() * 1.125D;
            BlockPos blockposition = isourceblock.getPos().relative(enumdirection);
            BlockState iblockdata = world.getType(blockposition);
            RailShape blockpropertytrackposition = iblockdata.getBlock() instanceof BaseRailBlock ? (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;
            double d3;

            if (iblockdata.is((Tag) BlockTags.RAILS)) {
                if (blockpropertytrackposition.isAscending()) {
                    d3 = 0.6D;
                } else {
                    d3 = 0.1D;
                }
            } else {
                if (!iblockdata.isAir() || !world.getType(blockposition.below()).is((Tag) BlockTags.RAILS)) {
                    return this.defaultDispenseItemBehavior.dispense(isourceblock, itemstack);
                }

                BlockState iblockdata1 = world.getType(blockposition.below());
                RailShape blockpropertytrackposition1 = iblockdata1.getBlock() instanceof BaseRailBlock ? (RailShape) iblockdata1.getValue(((BaseRailBlock) iblockdata1.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;

                if (enumdirection != Direction.DOWN && blockpropertytrackposition1.isAscending()) {
                    d3 = -0.4D;
                } else {
                    d3 = -0.9D;
                }
            }

            // CraftBukkit start
            // EntityMinecartAbstract entityminecartabstract = EntityMinecartAbstract.a(world, d0, d1 + d3, d2, ((ItemMinecart) itemstack.getItem()).b);
            ItemStack itemstack1 = itemstack.split(1);
            org.bukkit.block.Block block2 = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

            BlockDispenseEvent event = new BlockDispenseEvent(block2, craftItem.clone(), new org.bukkit.util.Vector(d0, d1 + d3, d2));
            if (!DispenserBlock.eventFired) {
                world.getServerOH().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                itemstack.grow(1);
                return itemstack;
            }

            if (!event.getItem().equals(craftItem)) {
                itemstack.grow(1);
                // Chain to handler for new item
                ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                    idispensebehavior.dispense(isourceblock, eventStack);
                    return itemstack;
                }
            }

            itemstack1 = CraftItemStack.asNMSCopy(event.getItem());
            AbstractMinecart entityminecartabstract = AbstractMinecart.createMinecart(world, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), ((MinecartItem) itemstack1.getItem()).type);

            if (itemstack.hasCustomHoverName()) {
                entityminecartabstract.setCustomName(itemstack.getHoverName());
            }

            if (!world.addFreshEntity(entityminecartabstract)) itemstack.grow(1);
            // itemstack.subtract(1); // CraftBukkit - handled during event processing
            // CraftBukkit end
            return itemstack;
        }

        @Override
        protected void playSound(BlockSource isourceblock) {
            isourceblock.getLevel().levelEvent(1000, isourceblock.getPos(), 0);
        }
    };
    private final AbstractMinecart.Type type;

    public MinecartItem(AbstractMinecart.Type entityminecartabstract_enumminecarttype, Item.Info item_info) {
        super(item_info);
        this.type = entityminecartabstract_enumminecarttype;
        DispenserBlock.registerBehavior((ItemLike) this, MinecartItem.DISPENSE_ITEM_BEHAVIOR);
    }

    @Override
    public InteractionResult useOn(UseOnContext itemactioncontext) {
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockState iblockdata = world.getType(blockposition);

        if (!iblockdata.is((Tag) BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        } else {
            ItemStack itemstack = itemactioncontext.getItemInHand();

            if (!world.isClientSide) {
                RailShape blockpropertytrackposition = iblockdata.getBlock() instanceof BaseRailBlock ? (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;
                double d0 = 0.0D;

                if (blockpropertytrackposition.isAscending()) {
                    d0 = 0.5D;
                }

                AbstractMinecart entityminecartabstract = AbstractMinecart.createMinecart(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.0625D + d0, (double) blockposition.getZ() + 0.5D, this.type);

                if (itemstack.hasCustomHoverName()) {
                    entityminecartabstract.setCustomName(itemstack.getHoverName());
                }

                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(itemactioncontext, entityminecartabstract).isCancelled()) {
                    return InteractionResult.FAIL;
                }
                // CraftBukkit end
                if (!world.addFreshEntity(entityminecartabstract)) return InteractionResult.PASS; // CraftBukkit
            }

            itemstack.shrink(1);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }
}
