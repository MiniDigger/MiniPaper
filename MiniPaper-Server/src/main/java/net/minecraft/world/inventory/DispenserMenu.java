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

public class DispenserMenu extends AbstractContainerMenu {

    public final Container dispenser;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;
    // CraftBukkit end

    public DispenserMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, new SimpleContainer(9));
    }

    public DispenserMenu(int i, Inventory playerinventory, Container iinventory) {
        super(MenuType.GENERIC_3x3, i);
        // CraftBukkit start - Save player
        this.player = playerinventory;
        // CraftBukkit end

        checkContainerSize(iinventory, 9);
        this.dispenser = iinventory;
        iinventory.startOpen(playerinventory.player);

        int j;
        int k;

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 3; ++k) {
                this.addSlot(new Slot(iinventory, k + j * 3, 62 + k * 18, 17 + j * 18));
            }
        }

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 142));
        }

    }

    @Override
    public boolean stillValid(Player entityhuman) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.dispenser.stillValid(entityhuman);
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i < 9) {
                if (!this.moveItemStackTo(itemstack1, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 9, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(entityhuman, itemstack1);
        }

        return itemstack;
    }

    @Override
    public void removed(Player entityhuman) {
        super.removed(entityhuman);
        this.dispenser.stopOpen(entityhuman);
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventory inventory = new CraftInventory(this.dispenser);
        bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
