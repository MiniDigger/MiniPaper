package net.minecraft.world.entity.monster;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Spider extends Monster {

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Spider.class, EntityDataSerializers.BYTE);

    public Spider(EntityType<? extends Spider> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new Spider.PathfinderGoalSpiderMeleeAttack(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(2, new Spider.PathfinderGoalSpiderNearestAttackableTarget<>(this, Player.class));
        this.targetSelector.addGoal(3, new Spider.PathfinderGoalSpiderNearestAttackableTarget<>(this, IronGolem.class));
    }

    @Override
    public double getPassengersRidingOffset() {
        return (double) (this.getBbHeight() * 0.5F);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new WallClimberNavigation(this, world);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Spider.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide) {
            this.setClimbing(this.horizontalCollision);
        }

    }

    public static AttributeSupplier.Builder eL() {
        return Monster.eS().a(Attributes.MAX_HEALTH, 16.0D).a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D);
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    public void makeStuckInBlock(BlockState iblockdata, Vec3 vec3d) {
        if (!iblockdata.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(iblockdata, vec3d);
        }

    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance mobeffect) {
        return mobeffect.getEffect() == MobEffects.POISON ? false : super.canBeAffected(mobeffect);
    }

    public boolean isClimbing() {
        return ((Byte) this.entityData.get(Spider.DATA_FLAGS_ID) & 1) != 0;
    }

    public void setClimbing(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Spider.DATA_FLAGS_ID);

        if (flag) {
            b0 = (byte) (b0 | 1);
        } else {
            b0 &= -2;
        }

        this.entityData.set(Spider.DATA_FLAGS_ID, b0);
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        Object object = super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);

        if (generatoraccess.getRandom().nextInt(100) == 0) {
            Skeleton entityskeleton = (Skeleton) EntityType.SKELETON.create(this.level);

            entityskeleton.moveTo(this.getX(), this.getY(), this.getZ(), this.yRot, 0.0F);
            entityskeleton.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) null, (CompoundTag) null);
            entityskeleton.startRiding(this);
            generatoraccess.addEntity(entityskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.JOCKEY); // CraftBukkit - add SpawnReason
        }

        if (object == null) {
            object = new Spider.GroupDataSpider();
            if (generatoraccess.getDifficulty() == Difficulty.HARD && generatoraccess.getRandom().nextFloat() < 0.1F * difficultydamagescaler.getSpecialMultiplier()) {
                ((Spider.GroupDataSpider) object).a(generatoraccess.getRandom());
            }
        }

        if (object instanceof Spider.GroupDataSpider) {
            MobEffect mobeffectlist = ((Spider.GroupDataSpider) object).a;

            if (mobeffectlist != null) {
                this.addEffect(new MobEffectInstance(mobeffectlist, Integer.MAX_VALUE), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.SPIDER_SPAWN); // CraftBukkit
            }
        }

        return (SpawnGroupData) object;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.65F;
    }

    static class PathfinderGoalSpiderNearestAttackableTarget<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {

        public PathfinderGoalSpiderNearestAttackableTarget(Spider entityspider, Class<T> oclass) {
            super(entityspider, oclass, true);
        }

        @Override
        public boolean canUse() {
            float f = this.mob.getBrightness();

            return f >= 0.5F ? false : super.canUse();
        }
    }

    static class PathfinderGoalSpiderMeleeAttack extends MeleeAttackGoal {

        public PathfinderGoalSpiderMeleeAttack(Spider entityspider) {
            super(entityspider, 1.0D, true);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.mob.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            float f = this.mob.getBrightness();

            if (f >= 0.5F && this.mob.getRandom().nextInt(100) == 0) {
                this.mob.setTarget((LivingEntity) null);
                return false;
            } else {
                return super.canContinueToUse();
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity entityliving) {
            return (double) (4.0F + entityliving.getBbWidth());
        }
    }

    public static class GroupDataSpider implements SpawnGroupData {

        public MobEffect a;

        public GroupDataSpider() {}

        public void a(Random random) {
            int i = random.nextInt(5);

            if (i <= 1) {
                this.a = MobEffects.MOVEMENT_SPEED;
            } else if (i <= 2) {
                this.a = MobEffects.DAMAGE_BOOST;
            } else if (i <= 3) {
                this.a = MobEffects.REGENERATION;
            } else if (i <= 4) {
                this.a = MobEffects.INVISIBILITY;
            }

        }
    }
}
