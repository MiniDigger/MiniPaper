package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class HopperMenu extends AbstractContainerMenu {

    private final Container hopper;

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventory inventory = new CraftInventory(this.hopper);
        bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end

    public HopperMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, new SimpleContainer(5));
    }

    public HopperMenu(int i, Inventory playerinventory, Container iinventory) {
        super(MenuType.HOPPER, i);
        this.hopper = iinventory;
        this.player = playerinventory; // CraftBukkit - save player
        checkContainerSize(iinventory, 5);
        iinventory.startOpen(playerinventory.player);
        boolean flag = true;

        int j;

        for (j = 0; j < 5; ++j) {
            this.addSlot(new Slot(iinventory, j, 44 + j * 18, 20));
        }

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, j * 18 + 51));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 109));
        }

    }

    @Override
    public boolean stillValid(Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.hopper.stillValid(entityhuman);
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i < this.hopper.getContainerSize()) {
                if (!this.moveItemStackTo(itemstack1, this.hopper.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, this.hopper.getContainerSize(), false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public void removed(Player entityhuman) {
        super.removed(entityhuman);
        this.hopper.stopOpen(entityhuman);
    }
}
