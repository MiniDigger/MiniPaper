package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.entity.LivingEntity;
// CraftBukkit end

public abstract class Projectile extends Entity {

    private UUID ownerUUID;
    private int ownerNetworkId;
    private boolean leftOwner;

    Projectile(EntityType<? extends Projectile> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public void setOwner(@Nullable Entity entity) {
        if (entity != null) {
            this.ownerUUID = entity.getUUID();
            this.ownerNetworkId = entity.getId();
        }
        this.projectileSource = entity == null ? null : (LivingEntity) entity.getBukkitEntity(); // CraftBukkit

    }

    @Nullable
    public Entity getOwner() {
        return this.ownerUUID != null && this.level instanceof ServerLevel ? ((ServerLevel) this.level).getEntity(this.ownerUUID) : (this.ownerNetworkId != 0 ? this.level.getEntity(this.ownerNetworkId) : null);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        if (this.ownerUUID != null) {
            nbttagcompound.putUUID("Owner", this.ownerUUID);
        }

        if (this.leftOwner) {
            nbttagcompound.putBoolean("LeftOwner", true);
        }

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        if (nbttagcompound.hasUUID("Owner")) {
            this.ownerUUID = nbttagcompound.getUUID("Owner");
        }

        this.leftOwner = nbttagcompound.getBoolean("LeftOwner");
    }

    @Override
    public void tick() {
        if (!this.leftOwner) {
            this.leftOwner = this.checkLeftOwner();
        }

        super.tick();
    }

    private boolean checkLeftOwner() {
        Entity entity = this.getOwner();

        if (entity != null) {
            Iterator iterator = this.level.getEntities(this, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), (entity1) -> {
                return !entity1.isSpectator() && entity1.isPickable();
            }).iterator();

            while (iterator.hasNext()) {
                Entity entity1 = (Entity) iterator.next();

                if (entity1.getRootVehicle() == entity.getRootVehicle()) {
                    return false;
                }
            }
        }

        return true;
    }

    public void shoot(double d0, double d1, double d2, float f, float f1) {
        Vec3 vec3d = (new Vec3(d0, d1, d2)).normalize().add(this.random.nextGaussian() * 0.007499999832361937D * (double) f1, this.random.nextGaussian() * 0.007499999832361937D * (double) f1, this.random.nextGaussian() * 0.007499999832361937D * (double) f1).scale((double) f);

        this.setDeltaMovement(vec3d);
        float f2 = Mth.sqrt(getHorizontalDistanceSqr(vec3d));

        this.yRot = (float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D);
        this.xRot = (float) (Mth.atan2(vec3d.y, (double) f2) * 57.2957763671875D);
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;
    }

    public void shootFromRotation(Entity entity, float f, float f1, float f2, float f3, float f4) {
        float f5 = -Mth.sin(f1 * 0.017453292F) * Mth.cos(f * 0.017453292F);
        float f6 = -Mth.sin((f + f2) * 0.017453292F);
        float f7 = Mth.cos(f1 * 0.017453292F) * Mth.cos(f * 0.017453292F);

        this.shoot((double) f5, (double) f6, (double) f7, f3, f4);
        Vec3 vec3d = entity.getDeltaMovement();

        this.setDeltaMovement(this.getDeltaMovement().add(vec3d.x, entity.isOnGround() ? 0.0D : vec3d.y, vec3d.z));
    }

    protected void onHit(HitResult movingobjectposition) {
        org.bukkit.craftbukkit.event.CraftEventFactory.callProjectileHitEvent(this, movingobjectposition); // CraftBukkit - Call event
        HitResult.Type movingobjectposition_enummovingobjecttype = movingobjectposition.getType();

        if (movingobjectposition_enummovingobjecttype == HitResult.Type.ENTITY) {
            this.onHitEntity((EntityHitResult) movingobjectposition);
        } else if (movingobjectposition_enummovingobjecttype == HitResult.Type.BLOCK) {
            this.onHitBlock((BlockHitResult) movingobjectposition);
        }

    }

    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {}

    protected void onHitBlock(BlockHitResult movingobjectpositionblock) {
        BlockState iblockdata = this.level.getType(movingobjectpositionblock.getBlockPos());

        iblockdata.onProjectileHit(this.level, iblockdata, movingobjectpositionblock, this);
    }

    protected boolean canHitEntity(Entity entity) {
        if (!entity.isSpectator() && entity.isAlive() && entity.isPickable()) {
            Entity entity1 = this.getOwner();

            return entity1 == null || this.leftOwner || !entity1.isPassengerOfSameVehicle(entity);
        } else {
            return false;
        }
    }

    protected void updateRotation() {
        Vec3 vec3d = this.getDeltaMovement();
        float f = Mth.sqrt(getHorizontalDistanceSqr(vec3d));

        this.xRot = lerpRotation(this.xRotO, (float) (Mth.atan2(vec3d.y, (double) f) * 57.2957763671875D));
        this.yRot = lerpRotation(this.yRotO, (float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D));
    }

    protected static float lerpRotation(float f, float f1) {
        while (f1 - f < -180.0F) {
            f -= 360.0F;
        }

        while (f1 - f >= 180.0F) {
            f += 360.0F;
        }

        return Mth.lerp(0.2F, f, f1);
    }
}
