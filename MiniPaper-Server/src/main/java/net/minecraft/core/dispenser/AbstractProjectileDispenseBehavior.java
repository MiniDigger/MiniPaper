package net.minecraft.core.dispenser;

import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public abstract class AbstractProjectileDispenseBehavior extends DefaultDispenseItemBehavior {

    public AbstractProjectileDispenseBehavior() {}

    @Override
    public ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
        Level world = isourceblock.getLevel();
        Position iposition = DispenserBlock.getDispensePosition(isourceblock);
        Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
        Projectile iprojectile = this.getProjectile(world, iposition, itemstack);

        // iprojectile.shoot((double) enumdirection.getAdjacentX(), (double) ((float) enumdirection.getAdjacentY() + 0.1F), (double) enumdirection.getAdjacentZ(), this.getPower(), this.a());
        // CraftBukkit start
        ItemStack itemstack1 = itemstack.split(1);
        org.bukkit.block.Block block = world.getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) enumdirection.getStepX(), (double) ((float) enumdirection.getStepY() + 0.1F), (double) enumdirection.getStepZ()));
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

        iprojectile.shoot(event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), this.getPower(), this.getUncertainty());
        ((Entity) iprojectile).projectileSource = new org.bukkit.craftbukkit.projectiles.CraftBlockProjectileSource((DispenserBlockEntity) isourceblock.getEntity());
        // CraftBukkit end
        world.addFreshEntity(iprojectile);
        // itemstack.subtract(1); // CraftBukkit - Handled during event processing
        return itemstack;
    }

    @Override
    protected void playSound(BlockSource isourceblock) {
        isourceblock.getLevel().levelEvent(1002, isourceblock.getPos(), 0);
    }

    protected abstract Projectile getProjectile(Level world, Position iposition, ItemStack itemstack);

    protected float getUncertainty() {
        return 6.0F;
    }

    protected float getPower() {
        return 1.1F;
    }
}
