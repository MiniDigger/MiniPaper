package net.minecraft.world.level.block.entity;

import java.util.Random;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import java.util.List;

import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class DispenserBlockEntity extends RandomizableContainerBlockEntity {

    private static final Random RANDOM = new Random();
    private NonNullList<ItemStack> items;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    protected DispenserBlockEntity(BlockEntityType<?> tileentitytypes) {
        super(tileentitytypes);
        this.items = NonNullList.withSize(9, ItemStack.EMPTY);
    }

    public DispenserBlockEntity() {
        this(BlockEntityType.DISPENSER);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    public int getRandomSlot() {
        this.unpackLootTable((Player) null);
        int i = -1;
        int j = 1;

        for (int k = 0; k < this.items.size(); ++k) {
            if (!((ItemStack) this.items.get(k)).isEmpty() && DispenserBlockEntity.RANDOM.nextInt(j++) == 0) {
                i = k;
            }
        }

        return i;
    }

    public int addItem(ItemStack itemstack) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                this.setItem(i, itemstack);
                return i;
            }
        }

        return -1;
    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("container.dispenser");
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbttagcompound)) {
            ContainerHelper.loadAllItems(nbttagcompound, this.items);
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        if (!this.trySaveLootTable(nbttagcompound)) {
            ContainerHelper.saveAllItems(nbttagcompound, this.items);
        }

        return nbttagcompound;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.items = nonnulllist;
    }

    @Override
    protected AbstractContainerMenu createMenu(int i, Inventory playerinventory) {
        return new DispenserMenu(i, playerinventory, this);
    }
}
