package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.inventory.InventoryView;
// CraftBukkit end

public class HorseInventoryMenu extends AbstractContainerMenu {

    private final Container horseContainer;
    private final AbstractHorse horse;

    // CraftBukkit start
    org.bukkit.craftbukkit.inventory.CraftInventoryView bukkitEntity;
    Inventory player;

    @Override
    public InventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        return bukkitEntity = new CraftInventoryView(player.player.getBukkitEntity(), horseContainer.getOwner().getInventory(), this);
    }

    public HorseInventoryMenu(int i, Inventory playerinventory, Container iinventory, final AbstractHorse entityhorseabstract) {
        super((MenuType) null, i);
        player = playerinventory;
        // CraftBukkit end
        this.horseContainer = iinventory;
        this.horse = entityhorseabstract;
        boolean flag = true;

        iinventory.startOpen(playerinventory.player);
        boolean flag1 = true;

        this.addSlot(new Slot(iinventory, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return itemstack.getItem() == Items.SADDLE && !this.hasItem() && entityhorseabstract.isSaddleable();
            }
        });
        this.addSlot(new Slot(iinventory, 1, 8, 36) {
            @Override
            public boolean mayPlace(ItemStack itemstack) {
                return entityhorseabstract.isArmor(itemstack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        int j;
        int k;

        if (entityhorseabstract instanceof AbstractChestedHorse && ((AbstractChestedHorse) entityhorseabstract).hasChest()) {
            for (j = 0; j < 3; ++j) {
                for (k = 0; k < ((AbstractChestedHorse) entityhorseabstract).getInventoryColumns(); ++k) {
                    this.addSlot(new Slot(iinventory, 2 + k + j * ((AbstractChestedHorse) entityhorseabstract).getInventoryColumns(), 80 + k * 18, 18 + j * 18));
                }
            }
        }

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerinventory, k + j * 9 + 9, 8 + k * 18, 102 + j * 18 + -18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerinventory, j, 8 + j * 18, 142));
        }

    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.horseContainer.stillValid(entityhuman) && this.horse.isAlive() && this.horse.distanceTo((Entity) entityhuman) < 8.0F;
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            int j = this.horseContainer.getContainerSize();

            if (i < j) {
                if (!this.moveItemStackTo(itemstack1, j, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(itemstack1) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(itemstack1)) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (j <= 2 || !this.moveItemStackTo(itemstack1, 2, j, false)) {
                int k = j + 27;
                int l = k + 9;

                if (i >= k && i < l) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= j && i < k) {
                    if (!this.moveItemStackTo(itemstack1, k, l, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, k, k, false)) {
                    return ItemStack.EMPTY;
                }

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
        this.horseContainer.stopOpen(entityhuman);
    }
}
