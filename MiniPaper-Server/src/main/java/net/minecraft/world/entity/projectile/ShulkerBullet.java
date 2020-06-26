package net.minecraft.world.entity.projectile;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerBullet extends Projectile {

    private Entity finalTarget;
    @Nullable
    private Direction currentMoveDirection;
    private int flightSteps;
    private double targetDeltaX;
    private double targetDeltaY;
    private double targetDeltaZ;
    @Nullable
    private UUID targetId;

    public ShulkerBullet(EntityType<? extends ShulkerBullet> entitytypes, Level world) {
        super(entitytypes, world);
        this.noPhysics = true;
    }

    public ShulkerBullet(Level world, LivingEntity entityliving, Entity entity, Direction.Axis enumdirection_enumaxis) {
        this(EntityType.SHULKER_BULLET, world);
        this.setOwner(entityliving);
        BlockPos blockposition = entityliving.blockPosition();
        double d0 = (double) blockposition.getX() + 0.5D;
        double d1 = (double) blockposition.getY() + 0.5D;
        double d2 = (double) blockposition.getZ() + 0.5D;

        this.moveTo(d0, d1, d2, this.yRot, this.xRot);
        this.finalTarget = entity;
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(enumdirection_enumaxis);
        projectileSource = (org.bukkit.entity.LivingEntity) entityliving.getBukkitEntity(); // CraftBukkit
    }

    // CraftBukkit start
    public Entity getTarget() {
        return this.finalTarget;
    }

    public void setTarget(Entity e) {
        this.finalTarget = e;
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(Direction.Axis.X);
    }
    // CraftBukkit end

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (this.finalTarget != null) {
            nbttagcompound.putUUID("Target", this.finalTarget.getUUID());
        }

        if (this.currentMoveDirection != null) {
            nbttagcompound.putInt("Dir", this.currentMoveDirection.get3DDataValue());
        }

        nbttagcompound.putInt("Steps", this.flightSteps);
        nbttagcompound.putDouble("TXD", this.targetDeltaX);
        nbttagcompound.putDouble("TYD", this.targetDeltaY);
        nbttagcompound.putDouble("TZD", this.targetDeltaZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.flightSteps = nbttagcompound.getInt("Steps");
        this.targetDeltaX = nbttagcompound.getDouble("TXD");
        this.targetDeltaY = nbttagcompound.getDouble("TYD");
        this.targetDeltaZ = nbttagcompound.getDouble("TZD");
        if (nbttagcompound.contains("Dir", 99)) {
            this.currentMoveDirection = Direction.from3DDataValue(nbttagcompound.getInt("Dir"));
        }

        if (nbttagcompound.hasUUID("Target")) {
            this.targetId = nbttagcompound.getUUID("Target");
        }

    }

    @Override
    protected void defineSynchedData() {}

    private void setMoveDirection(@Nullable Direction enumdirection) {
        this.currentMoveDirection = enumdirection;
    }

    private void selectNextMoveDirection(@Nullable Direction.Axis enumdirection_enumaxis) {
        double d0 = 0.5D;
        BlockPos blockposition;

        if (this.finalTarget == null) {
            blockposition = this.blockPosition().below();
        } else {
            d0 = (double) this.finalTarget.getBbHeight() * 0.5D;
            blockposition = new BlockPos(this.finalTarget.getX(), this.finalTarget.getY() + d0, this.finalTarget.getZ());
        }

        double d1 = (double) blockposition.getX() + 0.5D;
        double d2 = (double) blockposition.getY() + d0;
        double d3 = (double) blockposition.getZ() + 0.5D;
        Direction enumdirection = null;

        if (!blockposition.closerThan((Position) this.position(), 2.0D)) {
            BlockPos blockposition1 = this.blockPosition();
            List<Direction> list = Lists.newArrayList();

            if (enumdirection_enumaxis != Direction.Axis.X) {
                if (blockposition1.getX() < blockposition.getX() && this.level.isEmptyBlock(blockposition1.east())) {
                    list.add(Direction.EAST);
                } else if (blockposition1.getX() > blockposition.getX() && this.level.isEmptyBlock(blockposition1.west())) {
                    list.add(Direction.WEST);
                }
            }

            if (enumdirection_enumaxis != Direction.Axis.Y) {
                if (blockposition1.getY() < blockposition.getY() && this.level.isEmptyBlock(blockposition1.above())) {
                    list.add(Direction.UP);
                } else if (blockposition1.getY() > blockposition.getY() && this.level.isEmptyBlock(blockposition1.below())) {
                    list.add(Direction.DOWN);
                }
            }

            if (enumdirection_enumaxis != Direction.Axis.Z) {
                if (blockposition1.getZ() < blockposition.getZ() && this.level.isEmptyBlock(blockposition1.south())) {
                    list.add(Direction.SOUTH);
                } else if (blockposition1.getZ() > blockposition.getZ() && this.level.isEmptyBlock(blockposition1.north())) {
                    list.add(Direction.NORTH);
                }
            }

            enumdirection = Direction.getRandom(this.random);
            if (list.isEmpty()) {
                for (int i = 5; !this.level.isEmptyBlock(blockposition1.relative(enumdirection)) && i > 0; --i) {
                    enumdirection = Direction.getRandom(this.random);
                }
            } else {
                enumdirection = (Direction) list.get(this.random.nextInt(list.size()));
            }

            d1 = this.getX() + (double) enumdirection.getStepX();
            d2 = this.getY() + (double) enumdirection.getStepY();
            d3 = this.getZ() + (double) enumdirection.getStepZ();
        }

        this.setMoveDirection(enumdirection);
        double d4 = d1 - this.getX();
        double d5 = d2 - this.getY();
        double d6 = d3 - this.getZ();
        double d7 = (double) Mth.sqrt(d4 * d4 + d5 * d5 + d6 * d6);

        if (d7 == 0.0D) {
            this.targetDeltaX = 0.0D;
            this.targetDeltaY = 0.0D;
            this.targetDeltaZ = 0.0D;
        } else {
            this.targetDeltaX = d4 / d7 * 0.15D;
            this.targetDeltaY = d5 / d7 * 0.15D;
            this.targetDeltaZ = d6 / d7 * 0.15D;
        }

        this.hasImpulse = true;
        this.flightSteps = 10 + this.random.nextInt(5) * 10;
    }

    @Override
    public void checkDespawn() {
        if (this.level.getDifficulty() == Difficulty.PEACEFUL) {
            this.remove();
        }

    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d;

        if (!this.level.isClientSide) {
            if (this.finalTarget == null && this.targetId != null) {
                this.finalTarget = ((ServerLevel) this.level).getEntity(this.targetId);
                if (this.finalTarget == null) {
                    this.targetId = null;
                }
            }

            if (this.finalTarget != null && this.finalTarget.isAlive() && (!(this.finalTarget instanceof Player) || !((Player) this.finalTarget).isSpectator())) {
                this.targetDeltaX = Mth.clamp(this.targetDeltaX * 1.025D, -1.0D, 1.0D);
                this.targetDeltaY = Mth.clamp(this.targetDeltaY * 1.025D, -1.0D, 1.0D);
                this.targetDeltaZ = Mth.clamp(this.targetDeltaZ * 1.025D, -1.0D, 1.0D);
                vec3d = this.getDeltaMovement();
                this.setDeltaMovement(vec3d.add((this.targetDeltaX - vec3d.x) * 0.2D, (this.targetDeltaY - vec3d.y) * 0.2D, (this.targetDeltaZ - vec3d.z) * 0.2D));
            } else if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            HitResult movingobjectposition = ProjectileUtil.getHitResult(this, this::canHitEntity, ClipContext.Block.COLLIDER);

            if (movingobjectposition.getType() != HitResult.Type.MISS) {
                this.onHit(movingobjectposition);
            }
        }

        vec3d = this.getDeltaMovement();
        this.setPos(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level.isClientSide) {
            this.level.addParticle(ParticleTypes.END_ROD, this.getX() - vec3d.x, this.getY() - vec3d.y + 0.15D, this.getZ() - vec3d.z, 0.0D, 0.0D, 0.0D);
        } else if (this.finalTarget != null && !this.finalTarget.removed) {
            if (this.flightSteps > 0) {
                --this.flightSteps;
                if (this.flightSteps == 0) {
                    this.selectNextMoveDirection(this.currentMoveDirection == null ? null : this.currentMoveDirection.getAxis());
                }
            }

            if (this.currentMoveDirection != null) {
                BlockPos blockposition = this.blockPosition();
                Direction.Axis enumdirection_enumaxis = this.currentMoveDirection.getAxis();

                if (this.level.loadedAndEntityCanStandOn(blockposition.relative(this.currentMoveDirection), (Entity) this)) {
                    this.selectNextMoveDirection(enumdirection_enumaxis);
                } else {
                    BlockPos blockposition1 = this.finalTarget.blockPosition();

                    if (enumdirection_enumaxis == Direction.Axis.X && blockposition.getX() == blockposition1.getX() || enumdirection_enumaxis == Direction.Axis.Z && blockposition.getZ() == blockposition1.getZ() || enumdirection_enumaxis == Direction.Axis.Y && blockposition.getY() == blockposition1.getY()) {
                        this.selectNextMoveDirection(enumdirection_enumaxis);
                    }
                }
            }
        }

    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public float getBrightness() {
        return 1.0F;
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        Entity entity = movingobjectpositionentity.getEntity();
        Entity entity1 = this.getOwner();
        LivingEntity entityliving = entity1 instanceof LivingEntity ? (LivingEntity) entity1 : null;
        boolean flag = entity.hurt(DamageSource.indirectMobAttack((Entity) this, entityliving).setProjectile(), 4.0F);

        if (flag) {
            this.doEnchantDamageEffects(entityliving, entity);
            if (entity instanceof LivingEntity) {
                ((LivingEntity) entity).addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult movingobjectpositionblock) {
        super.onHitBlock(movingobjectpositionblock);
        ((ServerLevel) this.level).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
        this.playSound(SoundEvents.SHULKER_BULLET_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        this.remove();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (!this.level.isClientSide) {
            this.playSound(SoundEvents.SHULKER_BULLET_HURT, 1.0F, 1.0F);
            ((ServerLevel) this.level).sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 15, 0.2D, 0.2D, 0.2D, 0.0D);
            this.remove();
        }

        return true;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
