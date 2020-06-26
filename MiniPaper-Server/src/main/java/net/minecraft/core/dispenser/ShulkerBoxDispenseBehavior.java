package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.DirectionalPlaceContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class ShulkerBoxDispenseBehavior extends OptionalDispenseItemBehavior {

    public ShulkerBoxDispenseBehavior() {}

    @Override
    protected ItemStack execute(BlockSource isourceblock, ItemStack itemstack) {
        this.setSuccess(false);
        Item item = itemstack.getItem();

        if (item instanceof BlockItem) {
            Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
            BlockPos blockposition = isourceblock.getPos().relative(enumdirection);
            Direction enumdirection1 = isourceblock.getLevel().isEmptyBlock(blockposition.below()) ? enumdirection : Direction.UP;

            // CraftBukkit start
            org.bukkit.block.Block bukkitBlock = isourceblock.getLevel().getWorld().getBlockAt(isourceblock.getPos().getX(), isourceblock.getPos().getY(), isourceblock.getPos().getZ());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

            BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
            if (!DispenserBlock.eventFired) {
                isourceblock.getLevel().getServerOH().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                return itemstack;
            }

            if (!event.getItem().equals(craftItem)) {
                // Chain to handler for new item
                ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                    idispensebehavior.dispense(isourceblock, eventStack);
                    return itemstack;
                }
            }
            // CraftBukkit end

            this.setSuccess(((BlockItem) item).place((BlockPlaceContext) (new DirectionalPlaceContext(isourceblock.getLevel(), blockposition, enumdirection, itemstack, enumdirection1))).consumesAction());
        }

        return itemstack;
    }
}
