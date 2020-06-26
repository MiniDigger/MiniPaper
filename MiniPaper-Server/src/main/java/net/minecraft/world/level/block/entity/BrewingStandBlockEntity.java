package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import java.util.List;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class BrewingStandBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, TickableBlockEntity {

    private static final int[] SLOTS_FOR_UP = new int[]{3};
    private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
    private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
    private NonNullList<ItemStack> items;
    public int brewTime;
    private boolean[] lastPotionCount;
    private Item ingredient;
    public int fuel;
    protected final ContainerData dataAccess;
    // CraftBukkit start - add fields and methods
    private int lastTick = MinecraftServer.currentTick;
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = 64;

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    public BrewingStandBlockEntity() {
        super(BlockEntityType.BREWING_STAND);
        this.items = NonNullList.withSize(5, ItemStack.EMPTY);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int i) {
                switch (i) {
                    case 0:
                        return BrewingStandBlockEntity.this.brewTime;
                    case 1:
                        return BrewingStandBlockEntity.this.fuel;
                    default:
                        return 0;
                }
            }

            @Override
            public void set(int i, int j) {
                switch (i) {
                    case 0:
                        BrewingStandBlockEntity.this.brewTime = j;
                        break;
                    case 1:
                        BrewingStandBlockEntity.this.fuel = j;
                }

            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("container.brewing");
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public void tick() {
        ItemStack itemstack = (ItemStack) this.items.get(4);

        if (this.fuel <= 0 && itemstack.getItem() == Items.BLAZE_POWDER) {
            // CraftBukkit start
            BrewingStandFuelEvent event = new BrewingStandFuelEvent(level.getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()), CraftItemStack.asCraftMirror(itemstack), 20);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            this.fuel = event.getFuelPower();
            if (this.fuel > 0 && event.isConsuming()) {
                itemstack.shrink(1);
            }
            // CraftBukkit end
            this.setChanged();
        }

        boolean flag = this.isBrewable();
        boolean flag1 = this.brewTime > 0;
        ItemStack itemstack1 = (ItemStack) this.items.get(3);

        // CraftBukkit start - Use wall time instead of ticks for brewing
        int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
        this.lastTick = MinecraftServer.currentTick;

        if (flag1) {
            this.brewTime -= elapsedTicks;
            boolean flag2 = this.brewTime <= 0; // == -> <=
            // CraftBukkit end

            if (flag2 && flag) {
                this.doBrew();
                this.setChanged();
            } else if (!flag) {
                this.brewTime = 0;
                this.setChanged();
            } else if (this.ingredient != itemstack1.getItem()) {
                this.brewTime = 0;
                this.setChanged();
            }
        } else if (flag && this.fuel > 0) {
            --this.fuel;
            this.brewTime = 400;
            this.ingredient = itemstack1.getItem();
            this.setChanged();
        }

        if (!this.level.isClientSide) {
            boolean[] aboolean = this.getPotionBits();

            if (!Arrays.equals(aboolean, this.lastPotionCount)) {
                this.lastPotionCount = aboolean;
                BlockState iblockdata = this.level.getType(this.getBlockPos());

                if (!(iblockdata.getBlock() instanceof BrewingStandBlock)) {
                    return;
                }

                for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; ++i) {
                    iblockdata = (BlockState) iblockdata.setValue(BrewingStandBlock.HAS_BOTTLE[i], aboolean[i]);
                }

                this.level.setTypeAndData(this.worldPosition, iblockdata, 2);
            }
        }

    }

    public boolean[] getPotionBits() {
        boolean[] aboolean = new boolean[3];

        for (int i = 0; i < 3; ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty()) {
                aboolean[i] = true;
            }
        }

        return aboolean;
    }

    private boolean isBrewable() {
        ItemStack itemstack = (ItemStack) this.items.get(3);

        if (itemstack.isEmpty()) {
            return false;
        } else if (!PotionBrewing.isIngredient(itemstack)) {
            return false;
        } else {
            for (int i = 0; i < 3; ++i) {
                ItemStack itemstack1 = (ItemStack) this.items.get(i);

                if (!itemstack1.isEmpty() && PotionBrewing.hasMix(itemstack1, itemstack)) {
                    return true;
                }
            }

            return false;
        }
    }

    private void doBrew() {
        ItemStack itemstack = (ItemStack) this.items.get(3);
        // CraftBukkit start
        InventoryHolder owner = this.getOwner();
        if (owner != null) {
            BrewEvent event = new BrewEvent(level.getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()), (org.bukkit.inventory.BrewerInventory) owner.getInventory(), this.fuel);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end

        for (int i = 0; i < 3; ++i) {
            this.items.set(i, PotionBrewing.mix(itemstack, (ItemStack) this.items.get(i)));
        }

        itemstack.shrink(1);
        BlockPos blockposition = this.getBlockPos();

        if (itemstack.getItem().hasCraftingRemainingItem()) {
            ItemStack itemstack1 = new ItemStack(itemstack.getItem().getCraftingRemainingItem());

            if (itemstack.isEmpty()) {
                itemstack = itemstack1;
            } else if (!this.level.isClientSide) {
                Containers.dropItemStack(this.level, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), itemstack1);
            }
        }

        this.items.set(3, itemstack);
        this.level.levelEvent(1035, blockposition, 0);
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbttagcompound, this.items);
        this.brewTime = nbttagcompound.getShort("BrewTime");
        this.fuel = nbttagcompound.getByte("Fuel");
    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        nbttagcompound.putShort("BrewTime", (short) this.brewTime);
        ContainerHelper.saveAllItems(nbttagcompound, this.items);
        nbttagcompound.putByte("Fuel", (byte) this.fuel);
        return nbttagcompound;
    }

    @Override
    public ItemStack getItem(int i) {
        return i >= 0 && i < this.items.size() ? (ItemStack) this.items.get(i) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        return ContainerHelper.removeItem(this.items, i, j);
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(this.items, i);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        if (i >= 0 && i < this.items.size()) {
            this.items.set(i, itemstack);
        }

    }

    @Override
    public boolean stillValid(Player entityhuman) {
        return this.level.getBlockEntity(this.worldPosition) != this ? false : entityhuman.distanceToSqr((double) this.worldPosition.getX() + 0.5D, (double) this.worldPosition.getY() + 0.5D, (double) this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        if (i == 3) {
            return PotionBrewing.isIngredient(itemstack);
        } else {
            Item item = itemstack.getItem();

            return i == 4 ? item == Items.BLAZE_POWDER : (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.GLASS_BOTTLE) && this.getItem(i).isEmpty();
        }
    }

    @Override
    public int[] getSlotsForFace(Direction enumdirection) {
        return enumdirection == Direction.UP ? BrewingStandBlockEntity.SLOTS_FOR_UP : (enumdirection == Direction.DOWN ? BrewingStandBlockEntity.SLOTS_FOR_DOWN : BrewingStandBlockEntity.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable Direction enumdirection) {
        return this.canPlaceItem(i, itemstack);
    }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack itemstack, Direction enumdirection) {
        return i == 3 ? itemstack.getItem() == Items.GLASS_BOTTLE : true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    protected AbstractContainerMenu createMenu(int i, Inventory playerinventory) {
        return new BrewingStandMenu(i, playerinventory, this, this.dataAccess);
    }
}
