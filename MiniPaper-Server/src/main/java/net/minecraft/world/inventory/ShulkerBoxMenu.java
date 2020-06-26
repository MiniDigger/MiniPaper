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

public class ShulkerBoxMenu extends AbstractContainerMenu {

    private final Container container;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity;
    private Inventory player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), new CraftInventory(this.container), this);
        return bukkitEntity;
    }
    // CraftBukkit end

    public ShulkerBoxMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, new SimpleContainer(27));
    }

    public ShulkerBoxMenu(int i, Inventory playerinventory, Container iinventory) {
        super(MenuType.SHULKER_BOX, i);
        checkContainerSize(iinventory, 27);
        this.container = iinventory;
        this.player = playerinventory; // CraftBukkit - save player
        iinventory.startOpen(playerinventory.player);
        boolean flag = true;
        boolean flag1 = true;

        int j;
        int k;

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 9; ++k) {
                this.addSlot((Slot) (new ShulkerBoxSlot(iinventory, k + j * 9, 8 + k * 18, 18 + j * 18)));
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
        return this.container.stillValid(entityhuman);
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (i < this.container.getContainerSize()) {
                if (!this.moveItemStackTo(itemstack1, this.container.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, this.container.getContainerSize(), false)) {
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
        this.container.stopOpen(entityhuman);
    }
}
