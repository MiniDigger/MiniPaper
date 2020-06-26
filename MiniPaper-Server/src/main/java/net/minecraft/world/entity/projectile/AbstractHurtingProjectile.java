package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public abstract class AbstractHurtingProjectile extends Projectile {

    public double xPower;
    public double yPower;
    public double zPower;
    public float bukkitYield = 1; // CraftBukkit
    public boolean isIncendiary = true; // CraftBukkit

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entitytypes, double d0, double d1, double d2, double d3, double d4, double d5, Level world) {
        this(entitytypes, world);
        this.moveTo(d0, d1, d2, this.yRot, this.xRot);
        this.reapplyPosition();
        // CraftBukkit start - Added setDirection method
        this.setDirection(d3, d4, d5);
    }

    public void setDirection(double d3, double d4, double d5) {
        // CraftBukkit end
        double d6 = (double) Mth.sqrt(d3 * d3 + d4 * d4 + d5 * d5);

        if (d6 != 0.0D) {
            this.xPower = d3 / d6 * 0.1D;
            this.yPower = d4 / d6 * 0.1D;
            this.zPower = d5 / d6 * 0.1D;
        }

    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entitytypes, LivingEntity entityliving, double d0, double d1, double d2, Level world) {
        this(entitytypes, entityliving.getX(), entityliving.getY(), entityliving.getZ(), d0, d1, d2, world);
        this.setOwner(entityliving);
        this.setRot(entityliving.yRot, entityliving.xRot);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (!this.level.isClientSide && (entity != null && entity.removed || !this.level.hasChunkAt(this.blockPosition()))) {
            this.remove();
        } else {
            super.tick();
            if (this.shouldBurn()) {
                this.setSecondsOnFire(1);
            }

            HitResult movingobjectposition = ProjectileUtil.getHitResult(this, this::canHitEntity, ClipContext.Block.COLLIDER);

            if (movingobjectposition.getType() != HitResult.Type.MISS) {
                this.onHit(movingobjectposition);

                // CraftBukkit start - Fire ProjectileHitEvent
                if (this.removed) {
                    CraftEventFactory.callProjectileHitEvent(this, movingobjectposition);
                }
                // CraftBukkit end
            }

            Vec3 vec3d = this.getDeltaMovement();
            double d0 = this.getX() + vec3d.x;
            double d1 = this.getY() + vec3d.y;
            double d2 = this.getZ() + vec3d.z;

            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            float f = this.getInertia();

            if (this.isInWater()) {
                for (int i = 0; i < 4; ++i) {
                    float f1 = 0.25F;

                    this.level.addParticle(ParticleTypes.BUBBLE, d0 - vec3d.x * 0.25D, d1 - vec3d.y * 0.25D, d2 - vec3d.z * 0.25D, vec3d.x, vec3d.y, vec3d.z);
                }

                f = 0.8F;
            }

            this.setDeltaMovement(vec3d.add(this.xPower, this.yPower, this.zPower).scale((double) f));
            this.level.addParticle(this.getTrailParticle(), d0, d1 + 0.5D, d2, 0.0D, 0.0D, 0.0D);
            this.setPos(d0, d1, d2);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    protected boolean shouldBurn() {
        return true;
    }

    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    protected float getInertia() {
        return 0.95F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.put("power", this.newDoubleList(new double[]{this.xPower, this.yPower, this.zPower}));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("power", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("power", 6);

            if (nbttaglist.size() == 3) {
                this.xPower = nbttaglist.getDouble(0);
                this.yPower = nbttaglist.getDouble(1);
                this.zPower = nbttaglist.getDouble(2);
            }
        }

    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            this.markHurt();
            Entity entity = damagesource.getEntity();

            if (entity != null) {
                // CraftBukkit start
                if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f)) {
                    return false;
                }
                // CraftBukkit end
                Vec3 vec3d = entity.getLookAngle();

                this.setDeltaMovement(vec3d);
                this.xPower = vec3d.x * 0.1D;
                this.yPower = vec3d.y * 0.1D;
                this.zPower = vec3d.z * 0.1D;
                this.setOwner(entity);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public float getBrightness() {
        return 1.0F;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        Entity entity = this.getOwner();
        int i = entity == null ? 0 : entity.getId();

        return new ClientboundAddEntityPacket(this.getId(), this.getUUID(), this.getX(), this.getY(), this.getZ(), this.xRot, this.yRot, this.getType(), i, new Vec3(this.xPower, this.yPower, this.zPower));
    }
}
