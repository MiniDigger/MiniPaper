package net.minecraft.world.level.block.entity;

// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.block.Lectern;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class BarrelBlockEntity extends RandomizableContainerBlockEntity {

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
       return maxStack;
    }

    @Override
    public void setMaxStackSize(int i) {
        maxStack = i;
    }
    // CraftBukkit end
    private NonNullList<ItemStack> items;
    private int openCount;

    private BarrelBlockEntity(BlockEntityType<?> tileentitytypes) {
        super(tileentitytypes);
        this.items = NonNullList.withSize(27, ItemStack.EMPTY);
    }

    public BarrelBlockEntity() {
        this(BlockEntityType.BARREL);
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
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbttagcompound)) {
            ContainerHelper.loadAllItems(nbttagcompound, this.items);
        }

    }

    @Override
    public int getContainerSize() {
        return 27;
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
    protected Component getDefaultName() {
        return new TranslatableComponent("container.barrel");
    }

    @Override
    protected AbstractContainerMenu createMenu(int i, Inventory playerinventory) {
        return ChestMenu.threeRows(i, playerinventory, this);
    }

    @Override
    public void startOpen(Player entityhuman) {
        if (!entityhuman.isSpectator()) {
            if (this.openCount < 0) {
                this.openCount = 0;
            }

            ++this.openCount;
            BlockState iblockdata = this.getBlock();
            boolean flag = (Boolean) iblockdata.getValue(BarrelBlock.OPEN);

            if (!flag) {
                this.playSound(iblockdata, SoundEvents.BARREL_OPEN);
                this.updateBlockState(iblockdata, true);
            }

            this.scheduleRecheck();
        }

    }

    private void scheduleRecheck() {
        this.level.getBlockTickList().scheduleTick(this.getBlockPos(), this.getBlock().getBlock(), 5);
    }

    public void recheckOpen() {
        int i = this.worldPosition.getX();
        int j = this.worldPosition.getY();
        int k = this.worldPosition.getZ();

        this.openCount = ChestBlockEntity.getOpenCount(this.level, this, i, j, k);
        if (this.openCount > 0) {
            this.scheduleRecheck();
        } else {
            BlockState iblockdata = this.getBlock();

            if (!iblockdata.is(Blocks.BARREL)) {
                this.setRemoved();
                return;
            }

            boolean flag = (Boolean) iblockdata.getValue(BarrelBlock.OPEN);

            if (flag) {
                this.playSound(iblockdata, SoundEvents.BARREL_CLOSE);
                this.updateBlockState(iblockdata, false);
            }
        }

    }

    @Override
    public void stopOpen(Player entityhuman) {
        if (!entityhuman.isSpectator()) {
            --this.openCount;
        }

    }

    private void updateBlockState(BlockState iblockdata, boolean flag) {
        this.level.setTypeAndData(this.getBlockPos(), (BlockState) iblockdata.setValue(BarrelBlock.OPEN, flag), 3);
    }

    private void playSound(BlockState iblockdata, SoundEvent soundeffect) {
        Vec3i baseblockposition = ((Direction) iblockdata.getValue(BarrelBlock.FACING)).getNormal();
        double d0 = (double) this.worldPosition.getX() + 0.5D + (double) baseblockposition.getX() / 2.0D;
        double d1 = (double) this.worldPosition.getY() + 0.5D + (double) baseblockposition.getY() / 2.0D;
        double d2 = (double) this.worldPosition.getZ() + 0.5D + (double) baseblockposition.getZ() / 2.0D;

        this.level.playSound((Player) null, d0, d1, d2, soundeffect, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
    }
}
