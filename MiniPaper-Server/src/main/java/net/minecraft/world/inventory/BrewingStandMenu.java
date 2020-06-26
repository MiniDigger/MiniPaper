package net.minecraft.world.inventory;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryBrewer;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class BrewingStandMenu extends AbstractContainerMenu {

    private final Container brewingStand;
    private final ContainerData brewingStandData;
    private final Slot ingredientSlot;

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;
    // CraftBukkit end

    public BrewingStandMenu(int i, Inventory playerinventory) {
        this(i, playerinventory, new SimpleContainer(5), new SimpleContainerData(2));
    }

    public BrewingStandMenu(int i, Inventory playerinventory, Container iinventory, ContainerData icontainerproperties) {
        super(MenuType.BREWING_STAND, i);
        player = playerinventory; // CraftBukkit
        checkContainerSize(iinventory, 5);
        checkContainerDataCount(icontainerproperties, 2);
        this.brewingStand = iinventory;
        this.brewingStandData = icontainerproperties;
        this.addSlot((Slot) (new BrewingStandMenu.SlotPotionBottle(iinventory, 0, 56, 51)));
        this.addSlot((Slot) (new BrewingStandMenu.SlotPotionBottle(iinventory, 1, 79, 58)));
        this.addSlot((Slot) (new BrewingStandMenu.SlotPotionBottle(iinventory, 2, 102, 51)));
        this.ingredientSlot = this.addSlot((Slot) (new BrewingStandMenu.SlotBrewing(iinventory, 3, 79, 17)));
        this.addSlot((Slot) (new BrewingStandMenu.FuelSlot(iinventory, 4, 17, 17)));
        this.addDataSlots(icontainerproperties);

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
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
        return this.brewingStand.stillValid(entityhuman);
    }

    @Override
    public ItemStack quickMoveStack(Player entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if ((i < 0 || i > 2) && i != 3 && i != 4) {
                if (BrewingStandMenu.FuelSlot.mayPlaceItem(itemstack)) {
                    if (this.moveItemStackTo(itemstack1, 4, 5, false) || this.ingredientSlot.mayPlace(itemstack1) && !this.moveItemStackTo(itemstack1, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.ingredientSlot.mayPlace(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (BrewingStandMenu.SlotPotionBottle.b_(itemstack) && itemstack.getCount() == 1) {
                    if (!this.moveItemStackTo(itemstack1, 0, 3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= 5 && i < 32) {
                    if (!this.moveItemStackTo(itemstack1, 32, 41, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= 32 && i < 41) {
                    if (!this.moveItemStackTo(itemstack1, 5, 32, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 5, 41, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemstack1, 5, 41, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
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

    static class FuelSlot extends Slot {

        public FuelSlot(Container iinventory, int i, int j, int k) {
            super(iinventory, i, j, k);
        }

        @Override
        public boolean mayPlace(ItemStack itemstack) {
            return mayPlaceItem(itemstack);
        }

        public static boolean mayPlaceItem(ItemStack itemstack) {
            return itemstack.getItem() == Items.BLAZE_POWDER;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    static class SlotBrewing extends Slot {

        public SlotBrewing(Container iinventory, int i, int j, int k) {
            super(iinventory, i, j, k);
        }

        @Override
        public boolean mayPlace(ItemStack itemstack) {
            return PotionBrewing.isIngredient(itemstack);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    static class SlotPotionBottle extends Slot {

        public SlotPotionBottle(Container iinventory, int i, int j, int k) {
            super(iinventory, i, j, k);
        }

        @Override
        public boolean mayPlace(ItemStack itemstack) {
            return b_(itemstack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public ItemStack onTake(Player entityhuman, ItemStack itemstack) {
            Potion potionregistry = PotionUtils.getPotion(itemstack);

            if (entityhuman instanceof ServerPlayer) {
                CriteriaTriggers.BREWED_POTION.trigger((ServerPlayer) entityhuman, potionregistry);
            }

            super.onTake(entityhuman, itemstack);
            return itemstack;
        }

        public static boolean b_(ItemStack itemstack) {
            Item item = itemstack.getItem();

            return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.GLASS_BOTTLE;
        }
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        CraftInventoryBrewer inventory = new CraftInventoryBrewer(this.brewingStand);
        bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
