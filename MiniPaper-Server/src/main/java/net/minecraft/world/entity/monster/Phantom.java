package net.minecraft.world.entity.monster;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class Phantom extends FlyingMob implements Enemy {

    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Phantom.class, EntityDataSerializers.INT);
    private Vec3 moveTargetPoint;
    private BlockPos anchorPoint;
    private Phantom.AttackPhase attackPhase;

    public Phantom(EntityType<? extends Phantom> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveTargetPoint = Vec3.ZERO;
        this.anchorPoint = BlockPos.ZERO;
        this.attackPhase = Phantom.AttackPhase.CIRCLE;
        this.xpReward = 5;
        this.moveControl = new Phantom.PhantomMoveControl(this);
        this.lookControl = new Phantom.PhantomLookControl(this);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Phantom.PhantomBodyRotationControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Phantom.PhantomAttackStrategyGoal());
        this.goalSelector.addGoal(2, new Phantom.PhantomSweepAttackGoal());
        this.goalSelector.addGoal(3, new Phantom.PhantomCircleAroundAnchorGoal());
        this.targetSelector.addGoal(1, new Phantom.PhantomAttackPlayerTargetGoal());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Phantom.ID_SIZE, 0);
    }

    public void setPhantomSize(int i) {
        this.entityData.set(Phantom.ID_SIZE, Mth.clamp(i, 0, 64));
    }

    private void updatePhantomSizeInfo() {
        this.refreshDimensions();
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double) (6 + this.getPhantomSize()));
    }

    public int getPhantomSize() {
        return (Integer) this.entityData.get(Phantom.ID_SIZE);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * 0.35F;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (Phantom.ID_SIZE.equals(datawatcherobject)) {
            this.updatePhantomSizeInfo();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            float f = Mth.cos((float) (this.getId() * 3 + this.tickCount) * 0.13F + 3.1415927F);
            float f1 = Mth.cos((float) (this.getId() * 3 + this.tickCount + 1) * 0.13F + 3.1415927F);

            if (f > 0.0F && f1 <= 0.0F) {
                this.level.playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, this.getSoundSource(), 0.95F + this.random.nextFloat() * 0.05F, 0.95F + this.random.nextFloat() * 0.05F, false);
            }

            int i = this.getPhantomSize();
            float f2 = Mth.cos(this.yRot * 0.017453292F) * (1.3F + 0.21F * (float) i);
            float f3 = Mth.sin(this.yRot * 0.017453292F) * (1.3F + 0.21F * (float) i);
            float f4 = (0.3F + f * 0.45F) * ((float) i * 0.2F + 1.0F);

            this.level.addParticle(ParticleTypes.MYCELIUM, this.getX() + (double) f2, this.getY() + (double) f4, this.getZ() + (double) f3, 0.0D, 0.0D, 0.0D);
            this.level.addParticle(ParticleTypes.MYCELIUM, this.getX() - (double) f2, this.getY() + (double) f4, this.getZ() - (double) f3, 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    public void aiStep() {
        if (this.isAlive() && this.isSunBurnTick()) {
            this.setSecondsOnFire(8);
        }

        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
    }

    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.anchorPoint = this.blockPosition().above(5);
        this.setPhantomSize(0);
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("AX")) {
            this.anchorPoint = new BlockPos(nbttagcompound.getInt("AX"), nbttagcompound.getInt("AY"), nbttagcompound.getInt("AZ"));
        }

        this.setPhantomSize(nbttagcompound.getInt("Size"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("AX", this.anchorPoint.getX());
        nbttagcompound.putInt("AY", this.anchorPoint.getY());
        nbttagcompound.putInt("AZ", this.anchorPoint.getZ());
        nbttagcompound.putInt("Size", this.getPhantomSize());
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    @Override
    public boolean canAttackType(EntityType<?> entitytypes) {
        return true;
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        int i = this.getPhantomSize();
        EntityDimensions entitysize = super.getDimensions(entitypose);
        float f = (entitysize.width + 0.2F * (float) i) / entitysize.width;

        return entitysize.scale(f);
    }

    class PhantomAttackPlayerTargetGoal extends Goal {

        private final TargetingConditions attackTargeting;
        private int nextScanTick;

        private PhantomAttackPlayerTargetGoal() {
            this.attackTargeting = (new TargetingConditions()).range(64.0D);
            this.nextScanTick = 20;
        }

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                --this.nextScanTick;
                return false;
            } else {
                this.nextScanTick = 60;
                List<Player> list = Phantom.this.level.getNearbyPlayers(this.attackTargeting, (LivingEntity) Phantom.this, Phantom.this.getBoundingBox().inflate(16.0D, 64.0D, 16.0D));

                if (!list.isEmpty()) {
                    list.sort(Comparator.comparing(Entity::getY).reversed());
                    Iterator iterator = list.iterator();

                    while (iterator.hasNext()) {
                        Player entityhuman = (Player) iterator.next();

                        if (Phantom.this.canAttack((LivingEntity) entityhuman, TargetingConditions.DEFAULT)) {
                            Phantom.this.setGoalTarget(entityhuman, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit - reason
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = Phantom.this.getTarget();

            return entityliving != null ? Phantom.this.canAttack(entityliving, TargetingConditions.DEFAULT) : false;
        }
    }

    class PhantomAttackStrategyGoal extends Goal {

        private int nextSweepTick;

        private PhantomAttackStrategyGoal() {}

        @Override
        public boolean canUse() {
            LivingEntity entityliving = Phantom.this.getTarget();

            return entityliving != null ? Phantom.this.canAttack(Phantom.this.getTarget(), TargetingConditions.DEFAULT) : false;
        }

        @Override
        public void start() {
            this.nextSweepTick = 10;
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            Phantom.this.anchorPoint = Phantom.this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, Phantom.this.anchorPoint).above(10 + Phantom.this.random.nextInt(20));
        }

        @Override
        public void tick() {
            if (Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE) {
                --this.nextSweepTick;
                if (this.nextSweepTick <= 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = (8 + Phantom.this.random.nextInt(4)) * 20;
                    Phantom.this.playSound(SoundEvents.PHANTOM_SWOOP, 10.0F, 0.95F + Phantom.this.random.nextFloat() * 0.1F);
                }
            }

        }

        private void setAnchorAboveTarget() {
            Phantom.this.anchorPoint = Phantom.this.getTarget().blockPosition().above(20 + Phantom.this.random.nextInt(20));
            if (Phantom.this.anchorPoint.getY() < Phantom.this.level.getSeaLevel()) {
                Phantom.this.anchorPoint = new BlockPos(Phantom.this.anchorPoint.getX(), Phantom.this.level.getSeaLevel() + 1, Phantom.this.anchorPoint.getZ());
            }

        }
    }

    class PhantomSweepAttackGoal extends Phantom.PhantomMoveTargetGoal {

        private PhantomSweepAttackGoal() {
            super();
        }

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() != null && Phantom.this.attackPhase == Phantom.AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = Phantom.this.getTarget();

            if (entityliving == null) {
                return false;
            } else if (!entityliving.isAlive()) {
                return false;
            } else if (entityliving instanceof Player && (((Player) entityliving).isSpectator() || ((Player) entityliving).isCreative())) {
                return false;
            } else if (!this.canUse()) {
                return false;
            } else {
                if (Phantom.this.tickCount % 20 == 0) {
                    List<Cat> list = Phantom.this.level.getEntitiesOfClass(Cat.class, Phantom.this.getBoundingBox().inflate(16.0D), EntitySelector.ENTITY_STILL_ALIVE);

                    if (!list.isEmpty()) {
                        Iterator iterator = list.iterator();

                        while (iterator.hasNext()) {
                            Cat entitycat = (Cat) iterator.next();

                            entitycat.hiss();
                        }

                        return false;
                    }
                }

                return true;
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {
            Phantom.this.setTarget((LivingEntity) null);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity entityliving = Phantom.this.getTarget();

            Phantom.this.moveTargetPoint = new Vec3(entityliving.getX(), entityliving.getY(0.5D), entityliving.getZ());
            if (Phantom.this.getBoundingBox().inflate(0.20000000298023224D).intersects(entityliving.getBoundingBox())) {
                Phantom.this.doHurtTarget(entityliving);
                Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                if (!Phantom.this.isSilent()) {
                    Phantom.this.level.levelEvent(1039, Phantom.this.blockPosition(), 0);
                }
            } else if (Phantom.this.horizontalCollision || Phantom.this.hurtTime > 0) {
                Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
            }

        }
    }

    class PhantomCircleAroundAnchorGoal extends Phantom.PhantomMoveTargetGoal {

        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        private PhantomCircleAroundAnchorGoal() {
            super();
        }

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() == null || Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + Phantom.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            this.clockwise = Phantom.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (Phantom.this.random.nextInt(350) == 0) {
                this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            }

            if (Phantom.this.random.nextInt(250) == 0) {
                ++this.distance;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }

            if (Phantom.this.random.nextInt(450) == 0) {
                this.angle = Phantom.this.random.nextFloat() * 2.0F * 3.1415927F;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y < Phantom.this.getY() && !Phantom.this.level.isEmptyBlock(Phantom.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y > Phantom.this.getY() && !Phantom.this.level.isEmptyBlock(Phantom.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }

        }

        private void selectNext() {
            if (BlockPos.ZERO.equals(Phantom.this.anchorPoint)) {
                Phantom.this.anchorPoint = Phantom.this.blockPosition();
            }

            this.angle += this.clockwise * 15.0F * 0.017453292F;
            Phantom.this.moveTargetPoint = Vec3.atLowerCornerOf((Vec3i) Phantom.this.anchorPoint).add((double) (this.distance * Mth.cos(this.angle)), (double) (-4.0F + this.height), (double) (this.distance * Mth.sin(this.angle)));
        }
    }

    abstract class PhantomMoveTargetGoal extends Goal {

        public PhantomMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return Phantom.this.moveTargetPoint.distanceToSqr(Phantom.this.getX(), Phantom.this.getY(), Phantom.this.getZ()) < 4.0D;
        }
    }

    class PhantomLookControl extends LookControl {

        public PhantomLookControl(Mob entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void tick() {}
    }

    class PhantomBodyRotationControl extends BodyRotationControl {

        public PhantomBodyRotationControl(Mob entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void clientTick() {
            Phantom.this.yHeadRot = Phantom.this.yBodyRot;
            Phantom.this.yBodyRot = Phantom.this.yRot;
        }
    }

    class PhantomMoveControl extends MoveControl {

        private float speed = 0.1F;

        public PhantomMoveControl(Mob entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void tick() {
            if (Phantom.this.horizontalCollision) {
                Phantom.this.yRot += 180.0F;
                this.speed = 0.1F;
            }

            float f = (float) (Phantom.this.moveTargetPoint.x - Phantom.this.getX());
            float f1 = (float) (Phantom.this.moveTargetPoint.y - Phantom.this.getY());
            float f2 = (float) (Phantom.this.moveTargetPoint.z - Phantom.this.getZ());
            double d0 = (double) Mth.sqrt(f * f + f2 * f2);
            double d1 = 1.0D - (double) Mth.abs(f1 * 0.7F) / d0;

            f = (float) ((double) f * d1);
            f2 = (float) ((double) f2 * d1);
            d0 = (double) Mth.sqrt(f * f + f2 * f2);
            double d2 = (double) Mth.sqrt(f * f + f2 * f2 + f1 * f1);
            float f3 = Phantom.this.yRot;
            float f4 = (float) Mth.atan2((double) f2, (double) f);
            float f5 = Mth.wrapDegrees(Phantom.this.yRot + 90.0F);
            float f6 = Mth.wrapDegrees(f4 * 57.295776F);

            Phantom.this.yRot = Mth.approachDegrees(f5, f6, 4.0F) - 90.0F;
            Phantom.this.yBodyRot = Phantom.this.yRot;
            if (Mth.degreesDifferenceAbs(f3, Phantom.this.yRot) < 3.0F) {
                this.speed = Mth.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
            } else {
                this.speed = Mth.approach(this.speed, 0.2F, 0.025F);
            }

            float f7 = (float) (-(Mth.atan2((double) (-f1), d0) * 57.2957763671875D));

            Phantom.this.xRot = f7;
            float f8 = Phantom.this.yRot + 90.0F;
            double d3 = (double) (this.speed * Mth.cos(f8 * 0.017453292F)) * Math.abs((double) f / d2);
            double d4 = (double) (this.speed * Mth.sin(f8 * 0.017453292F)) * Math.abs((double) f2 / d2);
            double d5 = (double) (this.speed * Mth.sin(f7 * 0.017453292F)) * Math.abs((double) f1 / d2);
            Vec3 vec3d = Phantom.this.getDeltaMovement();

            Phantom.this.setDeltaMovement(vec3d.add((new Vec3(d3, d5, d4)).subtract(vec3d).scale(0.2D)));
        }
    }

    static enum AttackPhase {

        CIRCLE, SWOOP;

        private AttackPhase() {}
    }
}
