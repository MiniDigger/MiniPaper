package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Ghast extends FlyingMob implements Enemy {

    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(Ghast.class, EntityDataSerializers.BOOLEAN);
    private int explosionPower = 1;

    public Ghast(EntityType<? extends Ghast> entitytypes, Level world) {
        super(entitytypes, world);
        this.xpReward = 5;
        this.moveControl = new Ghast.ControllerGhast(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new Ghast.PathfinderGoalGhastIdleMove(this));
        this.goalSelector.addGoal(7, new Ghast.PathfinderGoalGhastMoveTowardsTarget(this));
        this.goalSelector.addGoal(7, new Ghast.GhastShootFireballGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entityliving) -> {
            return Math.abs(entityliving.getY() - this.getY()) <= 4.0D;
        }));
    }

    public void setCharging(boolean flag) {
        this.entityData.set(Ghast.DATA_IS_CHARGING, flag);
    }

    public int getExplosionPower() {
        return this.explosionPower;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (damagesource.getDirectEntity() instanceof LargeFireball && damagesource.getEntity() instanceof Player) {
            super.hurt(damagesource, 1000.0F);
            return true;
        } else {
            return super.hurt(damagesource, f);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Ghast.DATA_IS_CHARGING, false);
    }

    public static AttributeSupplier.Builder eK() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.FOLLOW_RANGE, 100.0D);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }

    public static boolean checkGhastSpawnRules(EntityType<Ghast> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return generatoraccess.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(20) == 0 && checkMobSpawnRules(entitytypes, generatoraccess, enummobspawn, blockposition, random);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("ExplosionPower", this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("ExplosionPower", 99)) {
            this.explosionPower = nbttagcompound.getInt("ExplosionPower");
        }

    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 2.6F;
    }

    static class GhastShootFireballGoal extends Goal {

        private final Ghast ghast;
        public int chargeTime;

        public GhastShootFireballGoal(Ghast entityghast) {
            this.ghast = entityghast;
        }

        @Override
        public boolean canUse() {
            return this.ghast.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public void tick() {
            LivingEntity entityliving = this.ghast.getTarget();
            double d0 = 64.0D;

            if (entityliving.distanceToSqr((Entity) this.ghast) < 4096.0D && this.ghast.canSee(entityliving)) {
                Level world = this.ghast.level;

                ++this.chargeTime;
                if (this.chargeTime == 10 && !this.ghast.isSilent()) {
                    world.levelEvent((Player) null, 1015, this.ghast.blockPosition(), 0);
                }

                if (this.chargeTime == 20) {
                    double d1 = 4.0D;
                    Vec3 vec3d = this.ghast.getViewVector(1.0F);
                    double d2 = entityliving.getX() - (this.ghast.getX() + vec3d.x * 4.0D);
                    double d3 = entityliving.getY(0.5D) - (0.5D + this.ghast.getY(0.5D));
                    double d4 = entityliving.getZ() - (this.ghast.getZ() + vec3d.z * 4.0D);

                    if (!this.ghast.isSilent()) {
                        world.levelEvent((Player) null, 1016, this.ghast.blockPosition(), 0);
                    }

                    LargeFireball entitylargefireball = new LargeFireball(world, this.ghast, d2, d3, d4);

                    // CraftBukkit - set bukkitYield when setting explosionpower
                    entitylargefireball.bukkitYield = entitylargefireball.explosionPower = this.ghast.getExplosionPower();
                    entitylargefireball.setPos(this.ghast.getX() + vec3d.x * 4.0D, this.ghast.getY(0.5D) + 0.5D, entitylargefireball.getZ() + vec3d.z * 4.0D);
                    world.addFreshEntity(entitylargefireball);
                    this.chargeTime = -40;
                }
            } else if (this.chargeTime > 0) {
                --this.chargeTime;
            }

            this.ghast.setCharging(this.chargeTime > 10);
        }
    }

    static class PathfinderGoalGhastMoveTowardsTarget extends Goal {

        private final Ghast a;

        public PathfinderGoalGhastMoveTowardsTarget(Ghast entityghast) {
            this.a = entityghast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            if (this.a.getTarget() == null) {
                Vec3 vec3d = this.a.getDeltaMovement();

                this.a.yRot = -((float) Mth.atan2(vec3d.x, vec3d.z)) * 57.295776F;
                this.a.yBodyRot = this.a.yRot;
            } else {
                LivingEntity entityliving = this.a.getTarget();
                double d0 = 64.0D;

                if (entityliving.distanceToSqr((Entity) this.a) < 4096.0D) {
                    double d1 = entityliving.getX() - this.a.getX();
                    double d2 = entityliving.getZ() - this.a.getZ();

                    this.a.yRot = -((float) Mth.atan2(d1, d2)) * 57.295776F;
                    this.a.yBodyRot = this.a.yRot;
                }
            }

        }
    }

    static class PathfinderGoalGhastIdleMove extends Goal {

        private final Ghast a;

        public PathfinderGoalGhastIdleMove(Ghast entityghast) {
            this.a = entityghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl controllermove = this.a.getMoveControl();

            if (!controllermove.hasWanted()) {
                return true;
            } else {
                double d0 = controllermove.getWantedX() - this.a.getX();
                double d1 = controllermove.getWantedY() - this.a.getY();
                double d2 = controllermove.getWantedZ() - this.a.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;

                return d3 < 1.0D || d3 > 3600.0D;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Random random = this.a.getRandom();
            double d0 = this.a.getX() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double d1 = this.a.getY() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double d2 = this.a.getZ() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);

            this.a.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
        }
    }

    static class ControllerGhast extends MoveControl {

        private final Ghast i;
        private int j;

        public ControllerGhast(Ghast entityghast) {
            super(entityghast);
            this.i = entityghast;
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                if (this.j-- <= 0) {
                    this.j += this.i.getRandom().nextInt(5) + 2;
                    Vec3 vec3d = new Vec3(this.wantedX - this.i.getX(), this.wantedY - this.i.getY(), this.wantedZ - this.i.getZ());
                    double d0 = vec3d.length();

                    vec3d = vec3d.normalize();
                    if (this.a(vec3d, Mth.ceil(d0))) {
                        this.i.setDeltaMovement(this.i.getDeltaMovement().add(vec3d.scale(0.1D)));
                    } else {
                        this.operation = MoveControl.Operation.WAIT;
                    }
                }

            }
        }

        private boolean a(Vec3 vec3d, int i) {
            AABB axisalignedbb = this.i.getBoundingBox();

            for (int j = 1; j < i; ++j) {
                axisalignedbb = axisalignedbb.move(vec3d);
                if (!this.i.level.noCollision(this.i, axisalignedbb)) {
                    return false;
                }
            }

            return true;
        }
    }
}
