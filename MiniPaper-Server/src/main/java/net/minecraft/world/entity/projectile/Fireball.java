package net.minecraft.world.entity.projectile;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public abstract class Fireball extends AbstractHurtingProjectile {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(Fireball.class, EntityDataSerializers.ITEM_STACK);

    public Fireball(EntityType<? extends Fireball> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public Fireball(EntityType<? extends Fireball> entitytypes, double d0, double d1, double d2, double d3, double d4, double d5, Level world) {
        super(entitytypes, d0, d1, d2, d3, d4, d5, world);
    }

    public Fireball(EntityType<? extends Fireball> entitytypes, LivingEntity entityliving, double d0, double d1, double d2, Level world) {
        super(entitytypes, entityliving, d0, d1, d2, world);
    }

    public void setItem(ItemStack itemstack) {
        if (itemstack.getItem() != Items.FIRE_CHARGE || itemstack.hasTag()) {
            this.getEntityData().set(Fireball.DATA_ITEM_STACK, Util.make(itemstack.copy(), (itemstack1) -> { // CraftBukkit - decompile error
                itemstack1.setCount(1);
            }));
        }

    }

    public ItemStack getItemRaw() {
        return (ItemStack) this.getEntityData().get(Fireball.DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(Fireball.DATA_ITEM_STACK, ItemStack.EMPTY);
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

        if (!itemstack.isEmpty()) this.setItem(itemstack); // CraftBukkit - SPIGOT-5474 probably came from bugged earlier versions
    }
}
