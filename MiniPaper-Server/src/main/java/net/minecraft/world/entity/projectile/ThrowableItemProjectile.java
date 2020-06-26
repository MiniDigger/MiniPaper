package net.minecraft.world.entity.projectile;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ThrowableItemProjectile extends ThrowableProjectile {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(ThrowableItemProjectile.class, EntityDataSerializers.ITEM_STACK);

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entitytypes, double d0, double d1, double d2, Level world) {
        super(entitytypes, d0, d1, d2, world);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entitytypes, LivingEntity entityliving, Level world) {
        super(entitytypes, entityliving, world);
    }

    public void setItem(ItemStack itemstack) {
        if (itemstack.getItem() != this.getDefaultItemOH() || itemstack.hasTag()) {
            this.getEntityData().set(ThrowableItemProjectile.DATA_ITEM_STACK, Util.make(itemstack.copy(), (itemstack1) -> { // CraftBukkit - decompile error
                if (!itemstack1.isEmpty()) itemstack1.setCount(1); // CraftBukkit
            }));
        }

    }

    protected abstract Item getDefaultItemOH();

    // CraftBukkit start
    public Item getDefaultItemPublic() {
        return getDefaultItemOH();
    }
    // CraftBukkit end

    public ItemStack getItemRaw() {
        return (ItemStack) this.getEntityData().get(ThrowableItemProjectile.DATA_ITEM_STACK);
    }

    public ItemStack getItem() {
        ItemStack itemstack = this.getItemRaw();

        return itemstack.isEmpty() ? new ItemStack(this.getDefaultItemOH()) : itemstack;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(ThrowableItemProjectile.DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        ItemStack itemstack = this.getItemRaw();

        if (!itemstack.isEmpty()) {
            nbttagcompound.put("Item", itemstack.save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        ItemStack itemstack = ItemStack.of(nbttagcompound.getCompound("Item"));

        this.setItem(itemstack);
    }
}
