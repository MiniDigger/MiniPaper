package net.minecraft.world.entity.animal;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class Squid extends WaterAnimal {

    public float xBodyRot;
    public float xBodyRotO;
    public float zBodyRot;
    public float zBodyRotO;
    public float tentacleMovement;
    public float oldTentacleMovement;
    public float tentacleAngle;
    public float oldTentacleAngle;
    private float speed;
    private float tentacleSpeed;
    private float rotateSpeed;
    private float tx;
    private float ty;
    private float tz;

    public Squid(EntityType<? extends Squid> entitytypes, Level world) {
        super(entitytypes, world);
        this.random.setSeed((long) this.getId());
        this.tentacleSpeed = 1.0F / (this.random.nextFloat() + 1.0F) * 0.2F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Squid.PathfinderGoalSquid(this));
        this.goalSelector.addGoal(1, new Squid.SquidFleeGoal());
    }

    public static AttributeSupplier.Builder m() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * 0.5F;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.SQUID_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.SQUID_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.SQUID_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.xBodyRotO = this.xBodyRot;
        this.zBodyRotO = this.zBodyRot;
        this.oldTentacleMovement = this.tentacleMovement;
        this.oldTentacleAngle = this.tentacleAngle;
        this.tentacleMovement += this.tentacleSpeed;
        if ((double) this.tentacleMovement > 6.283185307179586D) {
            if (this.level.isClientSide) {
                this.tentacleMovement = 6.2831855F;
            } else {
                this.tentacleMovement = (float) ((double) this.tentacleMovement - 6.283185307179586D);
                if (this.random.nextInt(10) == 0) {
                    this.tentacleSpeed = 1.0F / (this.random.nextFloat() + 1.0F) * 0.2F;
                }

                this.level.broadcastEntityEvent(this, (byte) 19);
            }
        }

        if (this.isInWaterOrBubble()) {
            if (this.tentacleMovement < 3.1415927F) {
                float f = this.tentacleMovement / 3.1415927F;

                this.tentacleAngle = Mth.sin(f * f * 3.1415927F) * 3.1415927F * 0.25F;
                if ((double) f > 0.75D) {
                    this.speed = 1.0F;
                    this.rotateSpeed = 1.0F;
                } else {
                    this.rotateSpeed *= 0.8F;
                }
            } else {
                this.tentacleAngle = 0.0F;
                this.speed *= 0.9F;
                this.rotateSpeed *= 0.99F;
            }

            if (!this.level.isClientSide) {
                this.setDeltaMovement((double) (this.tx * this.speed), (double) (this.ty * this.speed), (double) (this.tz * this.speed));
            }

            Vec3 vec3d = this.getDeltaMovement();
            float f1 = Mth.sqrt(getHorizontalDistanceSqr(vec3d));

            this.yBodyRot += (-((float) Mth.atan2(vec3d.x, vec3d.z)) * 57.295776F - this.yBodyRot) * 0.1F;
            this.yRot = this.yBodyRot;
            this.zBodyRot = (float) ((double) this.zBodyRot + 3.141592653589793D * (double) this.rotateSpeed * 1.5D);
            this.xBodyRot += (-((float) Mth.atan2((double) f1, vec3d.y)) * 57.295776F - this.xBodyRot) * 0.1F;
        } else {
            this.tentacleAngle = Mth.abs(Mth.sin(this.tentacleMovement)) * 3.1415927F * 0.25F;
            if (!this.level.isClientSide) {
                double d0 = this.getDeltaMovement().y;

                if (this.hasEffect(MobEffects.LEVITATION)) {
                    d0 = 0.05D * (double) (this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1);
                } else if (!this.isNoGravity()) {
                    d0 -= 0.08D;
                }

                this.setDeltaMovement(0.0D, d0 * 0.9800000190734863D, 0.0D);
            }

            this.xBodyRot = (float) ((double) this.xBodyRot + (double) (-90.0F - this.xBodyRot) * 0.02D);
        }

    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (super.hurt(damagesource, f) && this.getLastHurtByMob() != null) {
            this.spawnInk();
            return true;
        } else {
            return false;
        }
    }

    private Vec3 rotateVector(Vec3 vec3d) {
        Vec3 vec3d1 = vec3d.xRot(this.xBodyRotO * 0.017453292F);

        vec3d1 = vec3d1.yRot(-this.yBodyRotO * 0.017453292F);
        return vec3d1;
    }

    private void spawnInk() {
        this.playSound(SoundEvents.SQUID_SQUIRT, this.getSoundVolume(), this.getVoicePitch());
        Vec3 vec3d = this.rotateVector(new Vec3(0.0D, -1.0D, 0.0D)).add(this.getX(), this.getY(), this.getZ());

        for (int i = 0; i < 30; ++i) {
            Vec3 vec3d1 = this.rotateVector(new Vec3((double) this.random.nextFloat() * 0.6D - 0.3D, -1.0D, (double) this.random.nextFloat() * 0.6D - 0.3D));
            Vec3 vec3d2 = vec3d1.scale(0.3D + (double) (this.random.nextFloat() * 2.0F));

            ((ServerLevel) this.level).sendParticles(ParticleTypes.SQUID_INK, vec3d.x, vec3d.y + 0.5D, vec3d.z, 0, vec3d2.x, vec3d2.y, vec3d2.z, 0.10000000149011612D);
        }

    }

    @Override
    public void travel(Vec3 vec3d) {
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    public static boolean checkSquidSpawnRules(EntityType<Squid> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return blockposition.getY() > generatoraccess.getLevel().spigotConfig.squidSpawnRangeMin && blockposition.getY() < generatoraccess.getSeaLevel(); // Spigot
    }

    public void setMovementVector(float f, float f1, float f2) {
        this.tx = f;
        this.ty = f1;
        this.tz = f2;
    }

    public boolean hasMovementVector() {
        return this.tx != 0.0F || this.ty != 0.0F || this.tz != 0.0F;
    }

    class SquidFleeGoal extends Goal {

        private int fleeTicks;

        private SquidFleeGoal() {}

        @Override
        public boolean canUse() {
            LivingEntity entityliving = Squid.this.getLastHurtByMob();

            return Squid.this.isInWater() && entityliving != null ? Squid.this.distanceToSqr((Entity) entityliving) < 100.0D : false;
        }

        @Override
        public void start() {
            this.fleeTicks = 0;
        }

        @Override
        public void tick() {
            ++this.fleeTicks;
            LivingEntity entityliving = Squid.this.getLastHurtByMob();

            if (entityliving != null) {
                Vec3 vec3d = new Vec3(Squid.this.getX() - entityliving.getX(), Squid.this.getY() - entityliving.getY(), Squid.this.getZ() - entityliving.getZ());
                BlockState iblockdata = Squid.this.level.getType(new BlockPos(Squid.this.getX() + vec3d.x, Squid.this.getY() + vec3d.y, Squid.this.getZ() + vec3d.z));
                FluidState fluid = Squid.this.level.getFluidState(new BlockPos(Squid.this.getX() + vec3d.x, Squid.this.getY() + vec3d.y, Squid.this.getZ() + vec3d.z));

                if (fluid.is((Tag) FluidTags.WATER) || iblockdata.isAir()) {
                    double d0 = vec3d.length();

                    if (d0 > 0.0D) {
                        vec3d.normalize();
                        float f = 3.0F;

                        if (d0 > 5.0D) {
                            f = (float) ((double) f - (d0 - 5.0D) / 5.0D);
                        }

                        if (f > 0.0F) {
                            vec3d = vec3d.scale((double) f);
                        }
                    }

                    if (iblockdata.isAir()) {
                        vec3d = vec3d.subtract(0.0D, vec3d.y, 0.0D);
                    }

                    Squid.this.setMovementVector((float) vec3d.x / 20.0F, (float) vec3d.y / 20.0F, (float) vec3d.z / 20.0F);
                }

                if (this.fleeTicks % 10 == 5) {
                    Squid.this.level.addParticle(ParticleTypes.BUBBLE, Squid.this.getX(), Squid.this.getY(), Squid.this.getZ(), 0.0D, 0.0D, 0.0D);
                }

            }
        }
    }

    class PathfinderGoalSquid extends Goal {

        private final Squid b;

        public PathfinderGoalSquid(Squid entitysquid) {
            this.b = entitysquid;
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            int i = this.b.getNoActionTime();

            if (i > 100) {
                this.b.setMovementVector(0.0F, 0.0F, 0.0F);
            } else if (this.b.getRandom().nextInt(50) == 0 || !this.b.wasTouchingWater || !this.b.hasMovementVector()) {
                float f = this.b.getRandom().nextFloat() * 6.2831855F;
                float f1 = Mth.cos(f) * 0.2F;
                float f2 = -0.1F + this.b.getRandom().nextFloat() * 0.2F;
                float f3 = Mth.sin(f) * 0.2F;

                this.b.setMovementVector(f1, f2, f3);
            }

        }
    }
}
