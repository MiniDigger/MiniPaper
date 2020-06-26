package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.IntRange;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class Bee extends Animal implements NeutralMob, FlyingAnimal {

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Bee.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(Bee.class, EntityDataSerializers.INT);
    private static final IntRange PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    private int ticksWithoutNectarSinceExitingHive;
    public int stayOutOfHiveCountdown;
    private int numCropsGrownSincePollination;
    private int remainingCooldownBeforeLocatingNewHive = 0;
    private int remainingCooldownBeforeLocatingNewFlower = 0;
    @Nullable
    private BlockPos savedFlowerPos = null;
    @Nullable
    public BlockPos hivePos = null;
    private Bee.BeePollinateGoal beePollinateGoal;
    private Bee.BeeGoToHiveGoal goToHiveGoal;
    private Bee.BeeGoToKnownFlowerGoal goToKnownFlowerGoal;
    private int underWaterTicks;

    public Bee(EntityType<? extends Bee> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new Bee.BeeLookControl(this);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Bee.DATA_FLAGS_ID, (byte) 0);
        this.entityData.register(Bee.DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    public float getWalkTargetValue(BlockPos blockposition, LevelReader iworldreader) {
        return iworldreader.getType(blockposition).isAir() ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Bee.BeeAttackGoal(this, 1.399999976158142D, true));
        this.goalSelector.addGoal(1, new Bee.BeeEnterHiveGoal());
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, Ingredient.of((Tag) ItemTags.FLOWERS), false));
        this.beePollinateGoal = new Bee.BeePollinateGoal();
        this.goalSelector.addGoal(4, this.beePollinateGoal);
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(5, new Bee.BeeLocateHiveGoal());
        this.goToHiveGoal = new Bee.BeeGoToHiveGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goToKnownFlowerGoal = new Bee.BeeGoToKnownFlowerGoal();
        this.goalSelector.addGoal(6, this.goToKnownFlowerGoal);
        this.goalSelector.addGoal(7, new Bee.BeeGrowCropGoal());
        this.goalSelector.addGoal(8, new Bee.BeeWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
        this.targetSelector.addGoal(1, (new Bee.BeeHurtByOtherGoal(this)).setAlertOthers(new Class[0]));
        this.targetSelector.addGoal(2, new Bee.BeeBecomeAngryTargetGoal(this));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (this.hasHive()) {
            nbttagcompound.put("HivePos", NbtUtils.writeBlockPos(this.getHivePos()));
        }

        if (this.hasSavedFlowerPos()) {
            nbttagcompound.put("FlowerPos", NbtUtils.writeBlockPos(this.getSavedFlowerPos()));
        }

        nbttagcompound.putBoolean("HasNectar", this.hasNectar());
        nbttagcompound.putBoolean("HasStung", this.hasStung());
        nbttagcompound.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        nbttagcompound.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        nbttagcompound.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.addPersistentAngerSaveData(nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.hivePos = null;
        if (nbttagcompound.contains("HivePos")) {
            this.hivePos = NbtUtils.readBlockPos(nbttagcompound.getCompound("HivePos"));
        }

        this.savedFlowerPos = null;
        if (nbttagcompound.contains("FlowerPos")) {
            this.savedFlowerPos = NbtUtils.readBlockPos(nbttagcompound.getCompound("FlowerPos"));
        }

        super.readAdditionalSaveData(nbttagcompound);
        this.setHasNectar(nbttagcompound.getBoolean("HasNectar"));
        this.setHasStung(nbttagcompound.getBoolean("HasStung"));
        this.ticksWithoutNectarSinceExitingHive = nbttagcompound.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = nbttagcompound.getInt("CannotEnterHiveTicks");
        this.numCropsGrownSincePollination = nbttagcompound.getInt("CropsGrownSincePollination");
        this.readPersistentAngerSaveData((ServerLevel) this.level, nbttagcompound);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean flag = entity.hurt(DamageSource.sting(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));

        if (flag) {
            this.doEnchantDamageEffects((LivingEntity) this, entity);
            if (entity instanceof LivingEntity) {
                ((LivingEntity) entity).setStingerCount(((LivingEntity) entity).getStingerCount() + 1);
                byte b0 = 0;

                if (this.level.getDifficulty() == Difficulty.NORMAL) {
                    b0 = 10;
                } else if (this.level.getDifficulty() == Difficulty.HARD) {
                    b0 = 18;
                }

                if (b0 > 0) {
                    ((LivingEntity) entity).addEffect(new MobEffectInstance(MobEffects.POISON, b0 * 20, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            this.setHasStung(true);
            this.stopBeingAngry();
            this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; ++i) {
                this.spawnFluidParticle(this.level, this.getX() - 0.30000001192092896D, this.getX() + 0.30000001192092896D, this.getZ() - 0.30000001192092896D, this.getZ() + 0.30000001192092896D, this.getY(0.5D), ParticleTypes.FALLING_NECTAR);
            }
        }

        this.updateRollAmount();
    }

    private void spawnFluidParticle(Level world, double d0, double d1, double d2, double d3, double d4, ParticleOptions particleparam) {
        world.addParticle(particleparam, Mth.lerp(world.random.nextDouble(), d0, d1), d4, Mth.lerp(world.random.nextDouble(), d2, d3), 0.0D, 0.0D, 0.0D);
    }

    private void pathfindRandomlyTowards(BlockPos blockposition) {
        Vec3 vec3d = Vec3.atBottomCenterOf((Vec3i) blockposition);
        byte b0 = 0;
        BlockPos blockposition1 = this.blockPosition();
        int i = (int) vec3d.y - blockposition1.getY();

        if (i > 2) {
            b0 = 4;
        } else if (i < -2) {
            b0 = -4;
        }

        int j = 6;
        int k = 8;
        int l = blockposition1.distManhattan(blockposition);

        if (l < 15) {
            j = l / 2;
            k = l / 2;
        }

        Vec3 vec3d1 = RandomPos.getAirPosTowards(this, j, k, b0, vec3d, 0.3141592741012573D);

        if (vec3d1 != null) {
            this.navigation.setMaxVisitedNodesMultiplier(0.5F);
            this.navigation.moveTo(vec3d1.x, vec3d1.y, vec3d1.z, 1.0D);
        }
    }

    @Nullable
    public BlockPos getSavedFlowerPos() {
        return this.savedFlowerPos;
    }

    public boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    public void setSavedFlowerPos(BlockPos blockposition) {
        this.savedFlowerPos = blockposition;
    }

    private boolean isTiredOfLookingForNectar() {
        return this.ticksWithoutNectarSinceExitingHive > 3600;
    }

    private boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.beePollinateGoal.isPollinating() && !this.hasStung() && this.getTarget() == null) {
            boolean flag = this.isTiredOfLookingForNectar() || this.level.isRaining() || this.level.isNight() || this.hasNectar();

            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int i) {
        this.stayOutOfHiveCountdown = i;
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }

    }

    @Override
    protected void customServerAiStep() {
        boolean flag = this.hasStung();

        if (this.isInWaterOrBubble()) {
            ++this.underWaterTicks;
        } else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 20) {
            this.hurt(DamageSource.DROWN, 1.0F);
        }

        if (flag) {
            ++this.timeSinceSting;
            if (this.timeSinceSting % 5 == 0 && this.random.nextInt(Mth.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0) {
                this.hurt(DamageSource.GENERIC, this.getHealth());
            }
        }

        if (!this.hasNectar()) {
            ++this.ticksWithoutNectarSinceExitingHive;
        }

        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level, false);
        }

    }

    public void resetTicksWithoutNectarSinceExitingHive() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    private boolean isHiveNearFire() {
        if (this.hivePos == null) {
            return false;
        } else {
            BlockEntity tileentity = this.level.getBlockEntity(this.hivePos);

            return tileentity instanceof BeehiveBlockEntity && ((BeehiveBlockEntity) tileentity).isFireNearby();
        }
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return (Integer) this.entityData.get(Bee.DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int i) {
        this.entityData.set(Bee.DATA_REMAINING_ANGER_TIME, i);
    }

    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(Bee.PERSISTENT_ANGER_TIME.randomValue(this.random));
    }

    private boolean doesHiveHaveSpace(BlockPos blockposition) {
        BlockEntity tileentity = this.level.getBlockEntity(blockposition);

        return tileentity instanceof BeehiveBlockEntity ? !((BeehiveBlockEntity) tileentity).isFull() : false;
    }

    public boolean hasHive() {
        return this.hivePos != null;
    }

    @Nullable
    public BlockPos getHivePos() {
        return this.hivePos;
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendBeeInfo(this);
    }

    private int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    private void resetNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination = 0;
    }

    private void incrementNumCropsGrownSincePollination() {
        ++this.numCropsGrownSincePollination;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level.isClientSide) {
            if (this.stayOutOfHiveCountdown > 0) {
                --this.stayOutOfHiveCountdown;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                --this.remainingCooldownBeforeLocatingNewFlower;
            }

            boolean flag = this.isAngry() && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr((Entity) this) < 4.0D;

            this.setRolling(flag);
            if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }

    }

    private boolean isHiveValid() {
        if (!this.hasHive()) {
            return false;
        } else {
            BlockEntity tileentity = this.level.getBlockEntity(this.hivePos);

            return tileentity != null && tileentity.getType() == BlockEntityType.BEEHIVE;
        }
    }

    public boolean hasNectar() {
        return this.getFlag(8);
    }

    public void setHasNectar(boolean flag) {
        if (flag) {
            this.resetTicksWithoutNectarSinceExitingHive();
        }

        this.setFlag(8, flag);
    }

    public boolean hasStung() {
        return this.getFlag(4);
    }

    public void setHasStung(boolean flag) {
        this.setFlag(4, flag);
    }

    private boolean isRolling() {
        return this.getFlag(2);
    }

    private void setRolling(boolean flag) {
        this.setFlag(2, flag);
    }

    private boolean isTooFarAway(BlockPos blockposition) {
        return !this.closerThan(blockposition, 32);
    }

    private void setFlag(int i, boolean flag) {
        if (flag) {
            this.entityData.set(Bee.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Bee.DATA_FLAGS_ID) | i));
        } else {
            this.entityData.set(Bee.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Bee.DATA_FLAGS_ID) & ~i));
        }

    }

    private boolean getFlag(int i) {
        return ((Byte) this.entityData.get(Bee.DATA_FLAGS_ID) & i) != 0;
    }

    public static AttributeSupplier.Builder fa() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.FLYING_SPEED, 0.6000000238418579D).a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).a(Attributes.ATTACK_DAMAGE, 2.0D).a(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation navigationflying = new FlyingPathNavigation(this, world) {
            @Override
            public boolean isStableDestination(BlockPos blockposition) {
                return !this.level.getType(blockposition.below()).isAir();
            }

            @Override
            public void tick() {
                if (!Bee.this.beePollinateGoal.isPollinating()) {
                    super.tick();
                }
            }
        };

        navigationflying.setCanOpenDoors(false);
        navigationflying.setCanFloat(false);
        navigationflying.setCanPassDoors(true);
        return navigationflying;
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.getItem().is((Tag) ItemTags.FLOWERS);
    }

    private boolean isFlowerValid(BlockPos blockposition) {
        return this.level.isLoaded(blockposition) && this.level.getType(blockposition).getBlock().is((Tag) BlockTags.FLOWERS);
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {}

    @Override
    protected SoundEvent getSoundAmbient() {
        return null;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.BEE_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.BEE_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public Bee getBreedOffspring(AgableMob entityageable) {
        return (Bee) EntityType.BEE.create(this.level);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return this.isBaby() ? entitysize.height * 0.5F : entitysize.height * 0.5F;
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        return false;
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {}

    @Override
    protected boolean makeFlySound() {
        return true;
    }

    public void dropOffNectar() {
        this.setHasNectar(false);
        this.resetNumCropsGrownSincePollination();
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            Entity entity = damagesource.getEntity();

            // CraftBukkit start
            boolean result = super.hurt(damagesource, f);

            if (result && !this.level.isClientSide) {
                this.beePollinateGoal.stopPollinating();
            }

            return result;
            // CraftBukkit end
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    protected void jumpInLiquid(Tag<Fluid> tag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.01D, 0.0D));
    }

    private boolean closerThan(BlockPos blockposition, int i) {
        return blockposition.closerThan((Vec3i) this.blockPosition(), (double) i);
    }

    class BeeEnterHiveGoal extends Bee.BaseBeeGoal {

        private BeeEnterHiveGoal() {
            super(); // CraftBukkit - decompile error
        }

        @Override
        public boolean canBeeUse() {
            if (Bee.this.hasHive() && Bee.this.wantsToEnterHive() && Bee.this.hivePos.closerThan((Position) Bee.this.position(), 2.0D)) {
                BlockEntity tileentity = Bee.this.level.getBlockEntity(Bee.this.hivePos);

                if (tileentity instanceof BeehiveBlockEntity) {
                    BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

                    if (!tileentitybeehive.isFull()) {
                        return true;
                    }

                    Bee.this.hivePos = null;
                }
            }

            return false;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            BlockEntity tileentity = Bee.this.level.getBlockEntity(Bee.this.hivePos);

            if (tileentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

                tileentitybeehive.addOccupant(Bee.this, Bee.this.hasNectar());
            }

        }
    }

    class BeeAttackGoal extends MeleeAttackGoal {

        BeeAttackGoal(PathfinderMob entitycreature, double d0, boolean flag) {
            super(entitycreature, d0, flag);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && Bee.this.isAngry() && !Bee.this.hasStung();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && Bee.this.isAngry() && !Bee.this.hasStung();
        }
    }

    class BeeGrowCropGoal extends Bee.BaseBeeGoal {

        private BeeGrowCropGoal() {
            super(); // CraftBukkit - decompile error
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.getCropsGrownSincePollination() >= 10 ? false : (Bee.this.random.nextFloat() < 0.3F ? false : Bee.this.hasNectar() && Bee.this.isHiveValid());
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void tick() {
            if (Bee.this.random.nextInt(30) == 0) {
                for (int i = 1; i <= 2; ++i) {
                    BlockPos blockposition = Bee.this.blockPosition().below(i);
                    BlockState iblockdata = Bee.this.level.getType(blockposition);
                    Block block = iblockdata.getBlock();
                    boolean flag = false;
                    IntegerProperty blockstateinteger = null;

                    if (block.is((Tag) BlockTags.BEE_GROWABLES)) {
                        if (block instanceof CropBlock) {
                            CropBlock blockcrops = (CropBlock) block;

                            if (!blockcrops.isRipe(iblockdata)) {
                                flag = true;
                                blockstateinteger = blockcrops.getAgeProperty();
                            }
                        } else {
                            int j;

                            if (block instanceof StemBlock) {
                                j = (Integer) iblockdata.getValue(StemBlock.AGE);
                                if (j < 7) {
                                    flag = true;
                                    blockstateinteger = StemBlock.AGE;
                                }
                            } else if (block == Blocks.SWEET_BERRY_BUSH) {
                                j = (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE);
                                if (j < 3) {
                                    flag = true;
                                    blockstateinteger = SweetBerryBushBlock.AGE;
                                }
                            }
                        }

                        if (flag && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(Bee.this, blockposition, iblockdata.setValue(blockstateinteger, (Integer) iblockdata.getValue(blockstateinteger) + 1)).isCancelled()) { // Spigot
                            Bee.this.level.levelEvent(2005, blockposition, 0);
                            Bee.this.level.setTypeUpdate(blockposition, (BlockState) iblockdata.setValue(blockstateinteger, (Integer) iblockdata.getValue(blockstateinteger) + 1));
                            Bee.this.incrementNumCropsGrownSincePollination();
                        }
                    }
                }

            }
        }
    }

    class BeeLocateHiveGoal extends Bee.BaseBeeGoal {

        private BeeLocateHiveGoal() {
            super(); // CraftBukkit - decompile error
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.remainingCooldownBeforeLocatingNewHive == 0 && !Bee.this.hasHive() && Bee.this.wantsToEnterHive();
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Bee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.findNearbyHivesWithSpace();

            if (!list.isEmpty()) {
                Iterator iterator = list.iterator();

                BlockPos blockposition;

                do {
                    if (!iterator.hasNext()) {
                        Bee.this.goToHiveGoal.clearBlacklist();
                        Bee.this.hivePos = (BlockPos) list.get(0);
                        return;
                    }

                    blockposition = (BlockPos) iterator.next();
                } while (Bee.this.goToHiveGoal.isTargetBlacklisted(blockposition));

                Bee.this.hivePos = blockposition;
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            BlockPos blockposition = Bee.this.blockPosition();
            PoiManager villageplace = ((ServerLevel) Bee.this.level).getPoiManager();
            Stream<PoiRecord> stream = villageplace.getInRange((villageplacetype) -> {
                return villageplacetype == PoiType.BEEHIVE || villageplacetype == PoiType.BEE_NEST;
            }, blockposition, 20, PoiManager.Occupancy.ANY);

            return (List) stream.map(PoiRecord::getPos).filter((blockposition1) -> {
                return Bee.this.doesHiveHaveSpace(blockposition1);
            }).sorted(Comparator.comparingDouble((blockposition1) -> {
                return blockposition1.distSqr(blockposition);
            })).collect(Collectors.toList());
        }
    }

    class BeePollinateGoal extends Bee.BaseBeeGoal {

        private final Predicate<BlockState> VALID_POLLINATION_BLOCKS = (iblockdata) -> {
            return iblockdata.is((Tag) BlockTags.TALL_FLOWERS) ? (iblockdata.is(Blocks.SUNFLOWER) ? iblockdata.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER : true) : iblockdata.is((Tag) BlockTags.SMALL_FLOWERS);
        };
        private int successfulPollinatingTicks = 0;
        private int lastSoundPlayedTick = 0;
        private boolean pollinating;
        private Vec3 hoverPos;
        private int pollinatingTicks = 0;

        BeePollinateGoal() {
            super(); // CraftBukkit - decompile error
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            if (Bee.this.remainingCooldownBeforeLocatingNewFlower > 0) {
                return false;
            } else if (Bee.this.hasNectar()) {
                return false;
            } else if (Bee.this.level.isRaining()) {
                return false;
            } else if (Bee.this.random.nextFloat() < 0.7F) {
                return false;
            } else {
                Optional<BlockPos> optional = this.findNearbyFlower();

                if (optional.isPresent()) {
                    Bee.this.savedFlowerPos = (BlockPos) optional.get();
                    Bee.this.navigation.moveTo((double) Bee.this.savedFlowerPos.getX() + 0.5D, (double) Bee.this.savedFlowerPos.getY() + 0.5D, (double) Bee.this.savedFlowerPos.getZ() + 0.5D, 1.2000000476837158D);
                    return true;
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean canBeeContinueToUse() {
            if (!this.pollinating) {
                return false;
            } else if (!Bee.this.hasSavedFlowerPos()) {
                return false;
            } else if (Bee.this.level.isRaining()) {
                return false;
            } else if (this.hasPollinatedLongEnough()) {
                return Bee.this.random.nextFloat() < 0.2F;
            } else if (Bee.this.tickCount % 20 == 0 && !Bee.this.isFlowerValid(Bee.this.savedFlowerPos)) {
                Bee.this.savedFlowerPos = null;
                return false;
            } else {
                return true;
            }
        }

        private boolean hasPollinatedLongEnough() {
            return this.successfulPollinatingTicks > 400;
        }

        private boolean isPollinating() {
            return this.pollinating;
        }

        private void stopPollinating() {
            this.pollinating = false;
        }

        @Override
        public void start() {
            this.successfulPollinatingTicks = 0;
            this.pollinatingTicks = 0;
            this.lastSoundPlayedTick = 0;
            this.pollinating = true;
            Bee.this.resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (this.hasPollinatedLongEnough()) {
                Bee.this.setHasNectar(true);
            }

            this.pollinating = false;
            Bee.this.navigation.stop();
            Bee.this.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public void tick() {
            ++this.pollinatingTicks;
            if (this.pollinatingTicks > 600) {
                Bee.this.savedFlowerPos = null;
            } else {
                Vec3 vec3d = Vec3.atBottomCenterOf((Vec3i) Bee.this.savedFlowerPos).add(0.0D, 0.6000000238418579D, 0.0D);

                if (vec3d.distanceTo(Bee.this.position()) > 1.0D) {
                    this.hoverPos = vec3d;
                    this.setWantedPos();
                } else {
                    if (this.hoverPos == null) {
                        this.hoverPos = vec3d;
                    }

                    boolean flag = Bee.this.position().distanceTo(this.hoverPos) <= 0.1D;
                    boolean flag1 = true;

                    if (!flag && this.pollinatingTicks > 600) {
                        Bee.this.savedFlowerPos = null;
                    } else {
                        if (flag) {
                            boolean flag2 = Bee.this.random.nextInt(25) == 0;

                            if (flag2) {
                                this.hoverPos = new Vec3(vec3d.x() + (double) this.getOffset(), vec3d.y(), vec3d.z() + (double) this.getOffset());
                                Bee.this.navigation.stop();
                            } else {
                                flag1 = false;
                            }

                            Bee.this.getControllerLook().setLookAt(vec3d.x(), vec3d.y(), vec3d.z());
                        }

                        if (flag1) {
                            this.setWantedPos();
                        }

                        ++this.successfulPollinatingTicks;
                        if (Bee.this.random.nextFloat() < 0.05F && this.successfulPollinatingTicks > this.lastSoundPlayedTick + 60) {
                            this.lastSoundPlayedTick = this.successfulPollinatingTicks;
                            Bee.this.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
                        }

                    }
                }
            }
        }

        private void setWantedPos() {
            Bee.this.getMoveControl().setWantedPosition(this.hoverPos.x(), this.hoverPos.y(), this.hoverPos.z(), 0.3499999940395355D);
        }

        private float getOffset() {
            return (Bee.this.random.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private Optional<BlockPos> findNearbyFlower() {
            return this.findNearestBlock(this.VALID_POLLINATION_BLOCKS, 5.0D);
        }

        private Optional<BlockPos> findNearestBlock(Predicate<BlockState> predicate, double d0) {
            BlockPos blockposition = Bee.this.blockPosition();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

            for (int i = 0; (double) i <= d0; i = i > 0 ? -i : 1 - i) {
                for (int j = 0; (double) j < d0; ++j) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockposition_mutableblockposition.a((Vec3i) blockposition, k, i - 1, l);
                            if (blockposition.closerThan((Vec3i) blockposition_mutableblockposition, d0) && predicate.test(Bee.this.level.getType(blockposition_mutableblockposition))) {
                                return Optional.of(blockposition_mutableblockposition);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        }
    }

    class BeeLookControl extends LookControl {

        BeeLookControl(Mob entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void tick() {
            if (!Bee.this.isAngry()) {
                super.tick();
            }
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Bee.this.beePollinateGoal.isPollinating();
        }
    }

    public class BeeGoToKnownFlowerGoal extends Bee.BaseBeeGoal {

        private int travellingTicks;

        BeeGoToKnownFlowerGoal() {
            super(); // CraftBukkit - decompile error
            this.travellingTicks = Bee.this.level.random.nextInt(10);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.savedFlowerPos != null && !Bee.this.hasRestriction() && this.wantsToGoToKnownFlower() && Bee.this.isFlowerValid(Bee.this.savedFlowerPos) && !Bee.this.closerThan(Bee.this.savedFlowerPos, 2);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            Bee.this.navigation.stop();
            Bee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (Bee.this.savedFlowerPos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > 600) {
                    Bee.this.savedFlowerPos = null;
                } else if (!Bee.this.navigation.isInProgress()) {
                    if (Bee.this.isTooFarAway(Bee.this.savedFlowerPos)) {
                        Bee.this.savedFlowerPos = null;
                    } else {
                        Bee.this.pathfindRandomlyTowards(Bee.this.savedFlowerPos);
                    }
                }
            }
        }

        private boolean wantsToGoToKnownFlower() {
            return Bee.this.ticksWithoutNectarSinceExitingHive > 2400;
        }
    }

    public class BeeGoToHiveGoal extends Bee.BaseBeeGoal {

        private int travellingTicks;
        private List<BlockPos> blacklistedTargets;
        @Nullable
        private Path lastPath;
        private int ticksStuck;

        BeeGoToHiveGoal() {
            super(); // CraftBukkit - decompile error
            this.travellingTicks = Bee.this.level.random.nextInt(10);
            this.blacklistedTargets = Lists.newArrayList();
            this.lastPath = null;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.hivePos != null && !Bee.this.hasRestriction() && Bee.this.wantsToEnterHive() && !this.hasReachedTarget(Bee.this.hivePos) && Bee.this.level.getType(Bee.this.hivePos).is((Tag) BlockTags.BEEHIVES);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            Bee.this.navigation.stop();
            Bee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (Bee.this.hivePos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > 600) {
                    this.dropAndBlacklistHive();
                } else if (!Bee.this.navigation.isInProgress()) {
                    if (!Bee.this.closerThan(Bee.this.hivePos, 16)) {
                        if (Bee.this.isTooFarAway(Bee.this.hivePos)) {
                            this.dropHive();
                        } else {
                            Bee.this.pathfindRandomlyTowards(Bee.this.hivePos);
                        }
                    } else {
                        boolean flag = this.pathfindDirectlyTowards(Bee.this.hivePos);

                        if (!flag) {
                            this.dropAndBlacklistHive();
                        } else if (this.lastPath != null && Bee.this.navigation.getPath().sameAs(this.lastPath)) {
                            ++this.ticksStuck;
                            if (this.ticksStuck > 60) {
                                this.dropHive();
                                this.ticksStuck = 0;
                            }
                        } else {
                            this.lastPath = Bee.this.navigation.getPath();
                        }

                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(BlockPos blockposition) {
            Bee.this.navigation.setMaxVisitedNodesMultiplier(10.0F);
            Bee.this.navigation.moveTo((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 1.0D);
            return Bee.this.navigation.getPath() != null && Bee.this.navigation.getPath().canReach();
        }

        private boolean isTargetBlacklisted(BlockPos blockposition) {
            return this.blacklistedTargets.contains(blockposition);
        }

        private void blacklistTarget(BlockPos blockposition) {
            this.blacklistedTargets.add(blockposition);

            while (this.blacklistedTargets.size() > 3) {
                this.blacklistedTargets.remove(0);
            }

        }

        private void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private void dropAndBlacklistHive() {
            if (Bee.this.hivePos != null) {
                this.blacklistTarget(Bee.this.hivePos);
            }

            this.dropHive();
        }

        private void dropHive() {
            Bee.this.hivePos = null;
            Bee.this.remainingCooldownBeforeLocatingNewHive = 200;
        }

        private boolean hasReachedTarget(BlockPos blockposition) {
            if (Bee.this.closerThan(blockposition, 2)) {
                return true;
            } else {
                Path pathentity = Bee.this.navigation.getPath();

                return pathentity != null && pathentity.getTarget().equals(blockposition) && pathentity.canReach() && pathentity.isDone();
            }
        }
    }

    class BeeWanderGoal extends Goal {

        BeeWanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Bee.this.navigation.isDone() && Bee.this.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return Bee.this.navigation.isInProgress();
        }

        @Override
        public void start() {
            Vec3 vec3d = this.findPos();

            if (vec3d != null) {
                Bee.this.navigation.moveTo(Bee.this.navigation.createPath(new BlockPos(vec3d), 1), 1.0D);
            }

        }

        @Nullable
        private Vec3 findPos() {
            Vec3 vec3d;

            if (Bee.this.isHiveValid() && !Bee.this.closerThan(Bee.this.hivePos, 22)) {
                Vec3 vec3d1 = Vec3.atCenterOf((Vec3i) Bee.this.hivePos);

                vec3d = vec3d1.subtract(Bee.this.position()).normalize();
            } else {
                vec3d = Bee.this.getViewVector(0.0F);
            }

            boolean flag = true;
            Vec3 vec3d2 = RandomPos.getAboveLandPos(Bee.this, 8, 7, vec3d, 1.5707964F, 2, 1);

            return vec3d2 != null ? vec3d2 : RandomPos.getAirPos((PathfinderMob) Bee.this, 8, 4, -2, vec3d, 1.5707963705062866D);
        }
    }

    abstract class BaseBeeGoal extends Goal {

        private BaseBeeGoal() {}

        public abstract boolean canBeeUse();

        public abstract boolean canBeeContinueToUse();

        @Override
        public boolean canUse() {
            return this.canBeeUse() && !Bee.this.isAngry();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canBeeContinueToUse() && !Bee.this.isAngry();
        }
    }

    static class BeeBecomeAngryTargetGoal extends NearestAttackableTargetGoal<Player> {

        BeeBecomeAngryTargetGoal(Bee entitybee) {
            super(entitybee, Player.class, 10, true, false, entitybee::isAngryAt);
        }

        @Override
        public boolean canUse() {
            return this.beeCanTarget() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            boolean flag = this.beeCanTarget();

            if (flag && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            } else {
                this.targetMob = null;
                return false;
            }
        }

        private boolean beeCanTarget() {
            Bee entitybee = (Bee) this.mob;

            return entitybee.isAngry() && !entitybee.hasStung();
        }
    }

    class BeeHurtByOtherGoal extends HurtByTargetGoal {

        BeeHurtByOtherGoal(Bee entitybee) {
            super(entitybee);
        }

        @Override
        public boolean canContinueToUse() {
            return Bee.this.isAngry() && super.canContinueToUse();
        }

        @Override
        protected void alertOther(Mob entityinsentient, LivingEntity entityliving) {
            if (entityinsentient instanceof Bee && this.mob.canSee(entityliving)) {
                entityinsentient.setGoalTarget(entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit - reason
            }

        }
    }
}
