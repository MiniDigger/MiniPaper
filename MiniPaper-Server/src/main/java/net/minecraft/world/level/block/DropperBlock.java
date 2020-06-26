package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSourceImpl;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
// CraftBukkit end

public class DropperBlock extends DispenserBlock {

    private static final DispenseItemBehavior DISPENSE_BEHAVIOUR = new DefaultDispenseItemBehavior();

    public DropperBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    protected DispenseItemBehavior getDispenseMethod(ItemStack itemstack) {
        return DropperBlock.DISPENSE_BEHAVIOUR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new DropperBlockEntity();
    }

    @Override
    public void dispenseFrom(Level world, BlockPos blockposition) {
        BlockSourceImpl sourceblock = new BlockSourceImpl(world, blockposition);
        DispenserBlockEntity tileentitydispenser = (DispenserBlockEntity) sourceblock.getEntity();
        int i = tileentitydispenser.getRandomSlot();

        if (i < 0) {
            world.levelEvent(1001, blockposition, 0);
        } else {
            ItemStack itemstack = tileentitydispenser.getItem(i);

            if (!itemstack.isEmpty()) {
                Direction enumdirection = (Direction) world.getType(blockposition).getValue(DropperBlock.FACING);
                Container iinventory = HopperBlockEntity.getContainerAt(world, blockposition.relative(enumdirection));
                ItemStack itemstack1;

                if (iinventory == null) {
                    itemstack1 = DropperBlock.DISPENSE_BEHAVIOUR.dispense(sourceblock, itemstack);
                } else {
                    // CraftBukkit start - Fire event when pushing items into other inventories
                    CraftItemStack oitemstack = CraftItemStack.asCraftMirror(itemstack.copy().split(1));

                    org.bukkit.inventory.Inventory destinationInventory;
                    // Have to special case large chests as they work oddly
                    if (iinventory instanceof CompoundContainer) {
                        destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
                    } else {
                        destinationInventory = iinventory.getOwner().getInventory();
                    }

                    InventoryMoveItemEvent event = new InventoryMoveItemEvent(tileentitydispenser.getOwner().getInventory(), oitemstack.clone(), destinationInventory, true);
                    world.getServerOH().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        return;
                    }
                    itemstack1 = HopperBlockEntity.addItem(tileentitydispenser, iinventory, CraftItemStack.asNMSCopy(event.getItem()), enumdirection.getOpposite());
                    if (event.getItem().equals(oitemstack) && itemstack1.isEmpty()) {
                        // CraftBukkit end
                        itemstack1 = itemstack.copy();
                        itemstack1.shrink(1);
                    } else {
                        itemstack1 = itemstack.copy();
                    }
                }

                tileentitydispenser.setItem(i, itemstack1);
            }
        }
    }
}
