package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class BoatDispenseItemBehavior extends DefaultDispenseItemBehavior {

    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final Boat.Type type;

    public BoatDispenseItemBehavior(Boat.Type entityboat_enumboattype) {
        this.type = entityboat_enumboattype;
    }

    @Override
    public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
        Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
        Level world = isourceblock.getLevel();
        double d0 = isourceblock.x() + (double) ((float) enumdirection.getStepX() * 1.125F);
        double d1 = isourceblock.y() + (double) ((float) enumdirection.getStepY() * 1.125F);
        double d2 = isourceblock.z() + (double) ((float) enumdirection.getStepZ() * 1.125F);
        BlockPos blockposition = isourceblock.getPos().relative(enumdirection);
        double d3;

        if (world.getFluidState(blockposition).is((Tag) FluidTags.WATER)) {
            d3 = 1.0D;
        } else {
            if (!world.getType(blockposition).isAir() || !world.getFluidState(blockposition.below()).is((Tag) FluidTags.WATER)) {
                return this.defaultDispenseItemBehavior.dispense(isourceblock, itemstack);
            }

            d3 = 0.0D;
        }

        // EntityBoat entityboat = new EntityBoat(world, d0, d1 + d3, d2);
        // CraftBukkit start
        ItemStack itemstack1 = itemstack.split(1);
        org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(d0, d1 + d3, d2));
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

        Boat entityboat = new Boat(world, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ());
        // CraftBukkit end

        entityboat.setType(this.type);
        entityboat.yRot = enumdirection.toYRot();
        if (!world.addFreshEntity(entityboat)) itemstack.grow(1); // CraftBukkit
        // itemstack.subtract(1); // CraftBukkit - handled during event processing
        return itemstack;
    }

    @Override
    protected void playSound(BlockSource isourceblock) {
        isourceblock.getLevel().levelEvent(1000, isourceblock.getPos(), 0);
    }
}
