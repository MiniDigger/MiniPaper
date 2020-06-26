package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class FireworkRocketEntity extends Projectile {

    public static final EntityDataAccessor<ItemStack> DATA_ID_FIREWORKS_ITEM = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<OptionalInt> DATA_ATTACHED_TO_TARGET = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    public static final EntityDataAccessor<Boolean> DATA_SHOT_AT_ANGLE = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.BOOLEAN);
    private int life;
    public int lifetime;
    private LivingEntity attachedToEntity;

    public FireworkRocketEntity(EntityType<? extends FireworkRocketEntity> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public FireworkRocketEntity(Level world, double d0, double d1, double d2, ItemStack itemstack) {
        super(EntityType.FIREWORK_ROCKET, world);
        this.life = 0;
        this.setPos(d0, d1, d2);
        int i = 1;

        if (!itemstack.isEmpty() && itemstack.hasTag()) {
            this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, itemstack.copy());
            i += itemstack.getOrCreateTagElement("Fireworks").getByte("Flight");
        }

        this.setDeltaMovement(this.random.nextGaussian() * 0.001D, 0.05D, this.random.nextGaussian() * 0.001D);
        this.lifetime = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    public FireworkRocketEntity(Level world, @Nullable Entity entity, double d0, double d1, double d2, ItemStack itemstack) {
        this(world, d0, d1, d2, itemstack);
        this.setOwner(entity);
    }

    public FireworkRocketEntity(Level world, ItemStack itemstack, LivingEntity entityliving) {
        this(world, entityliving, entityliving.getX(), entityliving.getY(), entityliving.getZ(), itemstack);
        this.entityData.set(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.of(entityliving.getId()));
        this.attachedToEntity = entityliving;
    }

    public FireworkRocketEntity(Level world, ItemStack itemstack, double d0, double d1, double d2, boolean flag) {
        this(world, d0, d1, d2, itemstack);
        this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, flag);
    }

    public FireworkRocketEntity(Level world, ItemStack itemstack, Entity entity, double d0, double d1, double d2, boolean flag) {
        this(world, itemstack, d0, d1, d2, flag);
        this.setOwner(entity);
    }

    // Spigot Start - copied from tick
    @Override
    public void inactiveTick() {
        this.life += 1;

        if (!this.level.isClientSide && this.life > this.lifetime) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }
        super.inactiveTick();
    }
    // Spigot End

    @Override
    protected void defineSynchedData() {
        this.entityData.register(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, ItemStack.EMPTY);
        this.entityData.register(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.empty());
        this.entityData.register(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, false);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d;

        if (this.isAttachedToEntity()) {
            if (this.attachedToEntity == null) {
                ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).ifPresent((i) -> {
                    Entity entity = this.level.getEntity(i);

                    if (entity instanceof LivingEntity) {
                        this.attachedToEntity = (LivingEntity) entity;
                    }

                });
            }

            if (this.attachedToEntity != null) {
                if (this.attachedToEntity.isFallFlying()) {
                    vec3d = this.attachedToEntity.getLookAngle();
                    double d0 = 1.5D;
                    double d1 = 0.1D;
                    Vec3 vec3d1 = this.attachedToEntity.getDeltaMovement();

                    this.attachedToEntity.setDeltaMovement(vec3d1.add(vec3d.x * 0.1D + (vec3d.x * 1.5D - vec3d1.x) * 0.5D, vec3d.y * 0.1D + (vec3d.y * 1.5D - vec3d1.y) * 0.5D, vec3d.z * 0.1D + (vec3d.z * 1.5D - vec3d1.z) * 0.5D));
                }

                this.setPos(this.attachedToEntity.getX(), this.attachedToEntity.getY(), this.attachedToEntity.getZ());
                this.setDeltaMovement(this.attachedToEntity.getDeltaMovement());
            }
        } else {
            if (!this.isShotAtAngle()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.15D, 1.0D, 1.15D).add(0.0D, 0.04D, 0.0D));
            }

            vec3d = this.getDeltaMovement();
            this.move(MoverType.SELF, vec3d);
            this.setDeltaMovement(vec3d);
        }

        HitResult movingobjectposition = ProjectileUtil.getHitResult(this, this::canHitEntity, ClipContext.Block.COLLIDER);

        if (!this.noPhysics) {
            this.onHit(movingobjectposition);
            this.hasImpulse = true;
        }

        this.updateRotation();
        if (this.life == 0 && !this.isSilent()) {
            this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 1.0F);
        }

        ++this.life;
        if (this.level.isClientSide && this.life % 2 < 2) {
            this.level.addParticle(ParticleTypes.FIREWORK, this.getX(), this.getY() - 0.3D, this.getZ(), this.random.nextGaussian() * 0.05D, -this.getDeltaMovement().y * 0.5D, this.random.nextGaussian() * 0.05D);
        }

        if (!this.level.isClientSide && this.life > this.lifetime) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }

    }

    private void explode() {
        this.level.broadcastEntityEvent(this, (byte) 17);
        this.dealExplosionDamage();
        this.remove();
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        if (!this.level.isClientSide) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult movingobjectpositionblock) {
        BlockPos blockposition = new BlockPos(movingobjectpositionblock.getBlockPos());

        this.level.getType(blockposition).entityInside(this.level, blockposition, (Entity) this);
        if (!this.level.isClientSide() && this.hasExplosion()) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }

        super.onHitBlock(movingobjectpositionblock);
    }

    private boolean hasExplosion() {
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
        CompoundTag nbttagcompound = itemstack.isEmpty() ? null : itemstack.getTagElement("Fireworks");
        ListTag nbttaglist = nbttagcompound != null ? nbttagcompound.getList("Explosions", 10) : null;

        return nbttaglist != null && !nbttaglist.isEmpty();
    }

    private void dealExplosionDamage() {
        float f = 0.0F;
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
        CompoundTag nbttagcompound = itemstack.isEmpty() ? null : itemstack.getTagElement("Fireworks");
        ListTag nbttaglist = nbttagcompound != null ? nbttagcompound.getList("Explosions", 10) : null;

        if (nbttaglist != null && !nbttaglist.isEmpty()) {
            f = 5.0F + (float) (nbttaglist.size() * 2);
        }

        if (f > 0.0F) {
            if (this.attachedToEntity != null) {
                CraftEventFactory.entityDamage = this; // CraftBukkit
                this.attachedToEntity.hurt(DamageSource.fireworks(this, this.getOwner()), 5.0F + (float) (nbttaglist.size() * 2));
                CraftEventFactory.entityDamage = null; // CraftBukkit
            }

            double d0 = 5.0D;
            Vec3 vec3d = this.position();
            List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(5.0D));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                LivingEntity entityliving = (LivingEntity) iterator.next();

                if (entityliving != this.attachedToEntity && this.distanceToSqr(entityliving) <= 25.0D) {
                    boolean flag = false;

                    for (int i = 0; i < 2; ++i) {
                        Vec3 vec3d1 = new Vec3(entityliving.getX(), entityliving.getY(0.5D * (double) i), entityliving.getZ());
                        BlockHitResult movingobjectpositionblock = this.level.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

                        if (movingobjectpositionblock.getType() == HitResult.Type.MISS) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        float f1 = f * (float) Math.sqrt((5.0D - (double) this.distanceTo(entityliving)) / 5.0D);

                        CraftEventFactory.entityDamage = this; // CraftBukkit
                        entityliving.hurt(DamageSource.fireworks(this, this.getOwner()), f1);
                        CraftEventFactory.entityDamage = null; // CraftBukkit
                    }
                }
            }
        }

    }

    private boolean isAttachedToEntity() {
        return ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).isPresent();
    }

    public boolean isShotAtAngle() {
        return (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Life", this.life);
        nbttagcompound.putInt("LifeTime", this.lifetime);
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);

        if (!itemstack.isEmpty()) {
            nbttagcompound.put("FireworksItem", itemstack.save(new CompoundTag()));
        }

        nbttagcompound.putBoolean("ShotAtAngle", (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.life = nbttagcompound.getInt("Life");
        this.lifetime = nbttagcompound.getInt("LifeTime");
        ItemStack itemstack = ItemStack.of(nbttagcompound.getCompound("FireworksItem"));

        if (!itemstack.isEmpty()) {
            this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, itemstack);
        }

        if (nbttagcompound.contains("ShotAtAngle")) {
            this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, nbttagcompound.getBoolean("ShotAtAngle"));
        }

    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
