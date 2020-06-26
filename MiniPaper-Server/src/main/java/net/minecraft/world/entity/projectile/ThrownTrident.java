package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownTrident extends AbstractArrow {

    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    public ItemStack tridentItem;
    private boolean dealtDamage;
    public int clientSideReturnTridentTickCount;

    public ThrownTrident(EntityType<? extends ThrownTrident> entitytypes, Level world) {
        super(entitytypes, world);
        this.tridentItem = new ItemStack(Items.TRIDENT);
    }

    public ThrownTrident(Level world, LivingEntity entityliving, ItemStack itemstack) {
        super(EntityType.TRIDENT, entityliving, world);
        this.tridentItem = new ItemStack(Items.TRIDENT);
        this.tridentItem = itemstack.copy();
        this.entityData.set(ThrownTrident.ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(itemstack));
        this.entityData.set(ThrownTrident.ID_FOIL, itemstack.hasFoil());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(ThrownTrident.ID_LOYALTY, (byte) 0);
        this.entityData.register(ThrownTrident.ID_FOIL, false);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity entity = this.getOwner();

        if ((this.dealtDamage || this.isNoPhysics()) && entity != null) {
            byte b0 = (Byte) this.entityData.get(ThrownTrident.ID_LOYALTY);

            if (b0 > 0 && !this.isAcceptibleReturnOwner()) {
                if (!this.level.isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }

                this.remove();
            } else if (b0 > 0) {
                this.setNoPhysics(true);
                Vec3 vec3d = new Vec3(entity.getX() - this.getX(), entity.getEyeY() - this.getY(), entity.getZ() - this.getZ());

                this.setPosRaw(this.getX(), this.getY() + vec3d.y * 0.015D * (double) b0, this.getZ());
                if (this.level.isClientSide) {
                    this.yOld = this.getY();
                }

                double d0 = 0.05D * (double) b0;

                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(vec3d.normalize().scale(d0)));
                if (this.clientSideReturnTridentTickCount == 0) {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }

                ++this.clientSideReturnTridentTickCount;
            }
        }

        super.tick();
    }

    private boolean isAcceptibleReturnOwner() {
        Entity entity = this.getOwner();

        return entity != null && entity.isAlive() ? !(entity instanceof ServerPlayer) || !entity.isSpectator() : false;
    }

    @Override
    protected ItemStack getPickupItem() {
        return this.tridentItem.copy();
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 vec3d, Vec3 vec3d1) {
        return this.dealtDamage ? null : super.findHitEntity(vec3d, vec3d1);
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        Entity entity = movingobjectpositionentity.getEntity();
        float f = 8.0F;

        if (entity instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) entity;

            f += EnchantmentHelper.getDamageBonus(this.tridentItem, entityliving.getMobType());
        }

        Entity entity1 = this.getOwner();
        DamageSource damagesource = DamageSource.trident((Entity) this, (Entity) (entity1 == null ? this : entity1));

        this.dealtDamage = true;
        SoundEvent soundeffect = SoundEvents.TRIDENT_HIT;

        if (entity.hurt(damagesource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving1 = (LivingEntity) entity;

                if (entity1 instanceof LivingEntity) {
                    EnchantmentHelper.doPostHurtEffects(entityliving1, entity1);
                    EnchantmentHelper.doPostDamageEffects((LivingEntity) entity1, (Entity) entityliving1);
                }

                this.doPostHurtEffects(entityliving1);
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        float f1 = 1.0F;

        if (this.level instanceof ServerLevel && this.level.isThundering() && EnchantmentHelper.hasChanneling(this.tridentItem)) {
            BlockPos blockposition = entity.blockPosition();

            if (this.level.canSeeSky(blockposition)) {
                LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(this.level);

                entitylightning.moveTo(Vec3.atBottomCenterOf((Vec3i) blockposition));
                entitylightning.setCause(entity1 instanceof ServerPlayer ? (ServerPlayer) entity1 : null);
                ((ServerLevel) this.level).strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.TRIDENT); // CraftBukkit
                soundeffect = SoundEvents.TRIDENT_THUNDER;
                f1 = 5.0F;
            }
        }

        this.playSound(soundeffect, f1, 1.0F);
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(Player entityhuman) {
        Entity entity = this.getOwner();

        if (entity == null || entity.getUUID() == entityhuman.getUUID()) {
            super.playerTouch(entityhuman);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("Trident", 10)) {
            this.tridentItem = ItemStack.of(nbttagcompound.getCompound("Trident"));
        }

        this.dealtDamage = nbttagcompound.getBoolean("DealtDamage");
        this.entityData.set(ThrownTrident.ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(this.tridentItem));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.put("Trident", this.tridentItem.save(new CompoundTag()));
        nbttagcompound.putBoolean("DealtDamage", this.dealtDamage);
    }

    @Override
    public void tickDespawn() {
        byte b0 = (Byte) this.entityData.get(ThrownTrident.ID_LOYALTY);

        if (this.pickup != AbstractArrow.Pickup.ALLOWED || b0 <= 0) {
            super.tickDespawn();
        }

    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }
}
