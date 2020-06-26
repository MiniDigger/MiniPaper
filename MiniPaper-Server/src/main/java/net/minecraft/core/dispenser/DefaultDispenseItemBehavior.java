package net.minecraft.core.dispenser;

import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {

    public DefaultDispenseItemBehavior() {}

    @Override
    public final ItemStack dispense(BlockSource isourceblock, ItemStack itemstack) {
        ItemStack itemstack1 = this.execute(isourceblock, itemstack);

        this.playSound(isourceblock);
        this.playAnimation(isourceblock, (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING));
        return itemstack1;
    }

    protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
        Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
        Position iposition = DispenserBlock.getDispensePosition(isourceblock);
        ItemStack itemstack1 = itemstack.split(1);

        // CraftBukkit start
        if (!a(isourceblock.getLevel(), itemstack1, 6, enumdirection, isourceblock)) {
            itemstack.grow(1);
        }
        // CraftBukkit end
        return itemstack;
    }

    // CraftBukkit start - void -> boolean return, IPosition -> ISourceBlock last argument
    public static boolean a(Level world, ItemStack itemstack, int i, Direction enumdirection, BlockSource isourceblock) {
        if (itemstack.isEmpty()) return true;
        Position iposition = DispenserBlock.getDispensePosition(isourceblock);
        // CraftBukkit end
        double d0 = iposition.x();
        double d1 = iposition.y();
        double d2 = iposition.z();

        if (enumdirection.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125D;
        } else {
            d1 -= 0.15625D;
        }

        ItemEntity entityitem = new ItemEntity(world, d0, d1, d2, itemstack);
        double d3 = world.random.nextDouble() * 0.1D + 0.2D;

        entityitem.setDeltaMovement(world.random.nextGaussian() * 0.007499999832361937D * (double) i + (double) enumdirection.getStepX() * d3, world.random.nextGaussian() * 0.007499999832361937D * (double) i + 0.20000000298023224D, world.random.nextGaussian() * 0.007499999832361937D * (double) i + (double) enumdirection.getStepZ() * d3);

        // CraftBukkit start
        org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), CraftVector.toBukkit(entityitem.getDeltaMovement()));
        if (!DispenserBlock.eventFired) {
            world.getServerOH().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            return false;
        }

        entityitem.setItem(CraftItemStack.asNMSCopy(event.getItem()));
        entityitem.setDeltaMovement(CraftVector.toNMS(event.getVelocity()));

        if (!event.getItem().getType().equals(craftItem.getType())) {
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior.getClass() != DefaultDispenseItemBehavior.class) {
                idispensebehavior.dispense(isourceblock, eventStack);
            } else {
                world.addFreshEntity(entityitem);
            }
            return false;
        }

        world.addFreshEntity(entityitem);

        return true;
        // CraftBukkit end
    }

    protected void playSound(BlockSource isourceblock) {
        isourceblock.getLevel().levelEvent(1000, isourceblock.getPos(), 0);
    }

    protected void playAnimation(BlockSource isourceblock, Direction enumdirection) {
        isourceblock.getLevel().levelEvent(2000, isourceblock.getPos(), enumdirection.get3DDataValue());
    }
}
