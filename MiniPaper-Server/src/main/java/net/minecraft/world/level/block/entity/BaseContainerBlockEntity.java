package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BaseContainerBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable {

    public LockCode lockKey;
    public Component name;

    protected BaseContainerBlockEntity(BlockEntityType<?> tileentitytypes) {
        super(tileentitytypes);
        this.lockKey = LockCode.NO_LOCK;
    }

    @Override
    public void load(BlockState iblockdata, CompoundTag nbttagcompound) {
        super.load(iblockdata, nbttagcompound);
        this.lockKey = LockCode.fromTag(nbttagcompound);
        if (nbttagcompound.contains("CustomName", 8)) {
            this.name = Component.ChatSerializer.a(nbttagcompound.getString("CustomName"));
        }

    }

    @Override
    public CompoundTag save(CompoundTag nbttagcompound) {
        super.save(nbttagcompound);
        this.lockKey.addToTag(nbttagcompound);
        if (this.name != null) {
            nbttagcompound.putString("CustomName", Component.ChatSerializer.a(this.name));
        }

        return nbttagcompound;
    }

    public void setCustomName(Component ichatbasecomponent) {
        this.name = ichatbasecomponent;
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : this.getDefaultName();
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    protected abstract Component getDefaultName();

    public boolean canOpen(Player entityhuman) {
        return canUnlock(entityhuman, this.lockKey, this.getDisplayName());
    }

    public static boolean canUnlock(Player entityhuman, LockCode chestlock, Component ichatbasecomponent) {
        if (!entityhuman.isSpectator() && !chestlock.unlocksWith(entityhuman.getMainHandItem())) {
            entityhuman.displayClientMessage((Component) (new TranslatableComponent("container.isLocked", new Object[]{ichatbasecomponent})), true);
            entityhuman.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return false;
        } else {
            return true;
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerinventory, Player entityhuman) {
        return this.canOpen(entityhuman) ? this.createMenu(i, playerinventory) : null;
    }

    protected abstract AbstractContainerMenu createMenu(int i, Inventory playerinventory);

    // CraftBukkit start
    @Override
    public org.bukkit.Location getLocation() {
        if (level == null) return null;
        return new org.bukkit.Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }
    // CraftBukkit end
}
