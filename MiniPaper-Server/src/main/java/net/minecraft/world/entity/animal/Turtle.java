package net.minecraft.world.entity.animal;

import com.google.common.collect.Sets;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.TurtleNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class Turtle extends Animal {

    private static final EntityDataAccessor<BlockPos> HOME_POS = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> HAS_EGG = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LAYING_EGG = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> TRAVEL_POS = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> GOING_HOME = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRAVELLING = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private int layEggCounter;
    public static final Predicate<LivingEntity> BABY_ON_LAND_SELECTOR = (entityliving) -> {
        return entityliving.isBaby() && !entityliving.isInWater();
    };

    public Turtle(EntityType<? extends Turtle> entitytypes, Level world) {
        super(entitytypes, world);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.moveControl = new Turtle.TurtleMoveControl(this);
        this.maxUpStep = 1.0F;
    }

    public void setHomePos(BlockPos blockposition) {
        this.entityData.set(Turtle.HOME_POS, blockposition);
    }

    private BlockPos getHomePos() {
        return (BlockPos) this.entityData.get(Turtle.HOME_POS);
    }

    private void setTravelPos(BlockPos blockposition) {
        this.entityData.set(Turtle.TRAVEL_POS, blockposition);
    }

    private BlockPos getTravelPos() {
        return (BlockPos) this.entityData.get(Turtle.TRAVEL_POS);
    }

    public boolean hasEgg() {
        return (Boolean) this.entityData.get(Turtle.HAS_EGG);
    }

    private void setHasEgg(boolean flag) {
        this.entityData.set(Turtle.HAS_EGG, flag);
    }

    public boolean isLayingEgg() {
        return (Boolean) this.entityData.get(Turtle.LAYING_EGG);
    }

    private void setLayingEgg(boolean flag) {
        this.layEggCounter = flag ? 1 : 0;
        this.entityData.set(Turtle.LAYING_EGG, flag);
    }

    private boolean isGoingHome() {
        return (Boolean) this.entityData.get(Turtle.GOING_HOME);
    }

    private void setGoingHome(boolean flag) {
        this.entityData.set(Turtle.GOING_HOME, flag);
    }

    private boolean isTravelling() {
        return (Boolean) this.entityData.get(Turtle.TRAVELLING);
    }

    private void setTravelling(boolean flag) {
        this.entityData.set(Turtle.TRAVELLING, flag);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Turtle.HOME_POS, BlockPos.ZERO);
        this.entityData.register(Turtle.HAS_EGG, false);
        this.entityData.register(Turtle.TRAVEL_POS, BlockPos.ZERO);
        this.entityData.register(Turtle.GOING_HOME, false);
        this.entityData.register(Turtle.TRAVELLING, false);
        this.entityData.register(Turtle.LAYING_EGG, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("HomePosX", this.getHomePos().getX());
        nbttagcompound.putInt("HomePosY", this.getHomePos().getY());
        nbttagcompound.putInt("HomePosZ", this.getHomePos().getZ());
        nbttagcompound.putBoolean("HasEgg", this.hasEgg());
        nbttagcompound.putInt("TravelPosX", this.getTravelPos().getX());
        nbttagcompound.putInt("TravelPosY", this.getTravelPos().getY());
        nbttagcompound.putInt("TravelPosZ", this.getTravelPos().getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        int i = nbttagcompound.getInt("HomePosX");
        int j = nbttagcompound.getInt("HomePosY");
        int k = nbttagcompound.getInt("HomePosZ");

        this.setHomePos(new BlockPos(i, j, k));
        super.readAdditionalSaveData(nbttagcompound);
        this.setHasEgg(nbttagcompound.getBoolean("HasEgg"));
        int l = nbttagcompound.getInt("TravelPosX");
        int i1 = nbttagcompound.getInt("TravelPosY");
        int j1 = nbttagcompound.getInt("TravelPosZ");

        this.setTravelPos(new BlockPos(l, i1, j1));
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.setHomePos(this.blockPosition());
        this.setTravelPos(BlockPos.ZERO);
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    public static boolean checkTurtleSpawnRules(EntityType<Turtle> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return blockposition.getY() < generatoraccess.getSeaLevel() + 4 && TurtleEggBlock.onSand((BlockGetter) generatoraccess, blockposition) && generatoraccess.getRawBrightness(blockposition, 0) > 8;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Turtle.TurtlePanicGoal(this, 1.2D));
        this.goalSelector.addGoal(1, new Turtle.TurtleBreedGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new Turtle.TurtleLayEggGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new Turtle.TurtleTemptGoal(this, 1.1D, Blocks.SEAGRASS.asItem()));
        this.goalSelector.addGoal(3, new Turtle.TurtleGoToWaterGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new Turtle.TurtleGoHomeGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new Turtle.TurtleTravelGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new Turtle.TurtleRandomStrollGoal(this, 1.0D, 100));
    }

    public static AttributeSupplier.Builder eN() {
        return Mob.p().a(Attributes.MAX_HEALTH, 30.0D).a(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return !this.isInWater() && this.onGround && !this.isBaby() ? SoundEvents.TURTLE_AMBIENT_LAND : super.getSoundAmbient();
    }

    @Override
    protected void playSwimSound(float f) {
        super.playSwimSound(f * 1.5F);
    }

    @Override
    protected SoundEvent getSoundSwim() {
        return SoundEvents.TURTLE_SWIM;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return this.isBaby() ? SoundEvents.TURTLE_HURT_BABY : SoundEvents.TURTLE_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return this.isBaby() ? SoundEvents.TURTLE_DEATH_BABY : SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        SoundEvent soundeffect = this.isBaby() ? SoundEvents.TURTLE_SHAMBLE_BABY : SoundEvents.TURTLE_SHAMBLE;

        this.playSound(soundeffect, 0.15F, 1.0F);
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.hasEgg();
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.15F;
    }

    @Override
    public float getScale() {
        return this.isBaby() ? 0.3F : 1.0F;
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new Turtle.TurtlePathNavigation(this, world);
    }

    @Nullable
    @Override
    public AgableMob getBreedOffspring(AgableMob entityageable) {
        return (AgableMob) EntityType.TURTLE.create(this.level);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.getItem() == Blocks.SEAGRASS.asItem();
    }

    @Override
    public float getWalkTargetValue(BlockPos blockposition, LevelReader iworldreader) {
        return !this.isGoingHome() && iworldreader.getFluidState(blockposition).is((Tag) FluidTags.WATER) ? 10.0F : (TurtleEggBlock.onSand((BlockGetter) iworldreader, blockposition) ? 10.0F : iworldreader.getBrightness(blockposition) - 0.5F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isLayingEgg() && this.layEggCounter >= 1 && this.layEggCounter % 5 == 0) {
            BlockPos blockposition = this.blockPosition();

            if (TurtleEggBlock.onSand((BlockGetter) this.level, blockposition)) {
                this.level.levelEvent(2001, blockposition, Block.getCombinedId(Blocks.SAND.getBlockData()));
            }
        }

    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.isBaby() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.forceDrops = true; // CraftBukkit
            this.spawnAtLocation((ItemLike) Items.SCUTE, 1);
            this.forceDrops = false; // CraftBukkit
        }

    }

    @Override
    public void travel(Vec3 vec3d) {
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(0.1F, vec3d);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null && (!this.isGoingHome() || !this.getHomePos().closerThan((Position) this.position(), 20.0D))) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(vec3d);
        }

    }

    @Override
    public boolean canBeLeashed(Player entityhuman) {
        return false;
    }

    @Override
    public void thunderHit(LightningBolt entitylightning) {
        org.bukkit.craftbukkit.event.CraftEventFactory.entityDamage = entitylightning; // CraftBukkit
        this.hurt(DamageSource.LIGHTNING_BOLT, Float.MAX_VALUE);
        org.bukkit.craftbukkit.event.CraftEventFactory.entityDamage = null; // CraftBukkit
    }

    static class TurtlePathNavigation extends WaterBoundPathNavigation {

        TurtlePathNavigation(Turtle entityturtle, Level world) {
            super(entityturtle, world);
        }

        @Override
        protected boolean canUpdatePath() {
            return true;
        }

        @Override
        protected PathFinder createPathFinder(int i) {
            this.nodeEvaluator = new TurtleNodeEvaluator();
            return new PathFinder(this.nodeEvaluator, i);
        }

        @Override
        public boolean isStableDestination(BlockPos blockposition) {
            if (this.mob instanceof Turtle) {
                Turtle entityturtle = (Turtle) this.mob;

                if (entityturtle.isTravelling()) {
                    return this.level.getType(blockposition).is(Blocks.WATER);
                }
            }

            return !this.level.getType(blockposition.below()).isAir();
        }
    }

    static class TurtleMoveControl extends MoveControl {

        private final Turtle turtle;

        TurtleMoveControl(Turtle entityturtle) {
            super(entityturtle);
            this.turtle = entityturtle;
        }

        private void updateSpeed() {
            if (this.turtle.isInWater()) {
                this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
                if (!this.turtle.getHomePos().closerThan((Position) this.turtle.position(), 16.0D)) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.08F));
                }

                if (this.turtle.isBaby()) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 3.0F, 0.06F));
                }
            } else if (this.turtle.onGround) {
                this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.06F));
            }

        }

        @Override
        public void tick() {
            this.updateSpeed();
            if (this.operation == MoveControl.Operation.MOVE_TO && !this.turtle.getNavigation().isDone()) {
                double d0 = this.wantedX - this.turtle.getX();
                double d1 = this.wantedY - this.turtle.getY();
                double d2 = this.wantedZ - this.turtle.getZ();
                double d3 = (double) Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

                d1 /= d3;
                float f = (float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F;

                this.turtle.yRot = this.rotlerp(this.turtle.yRot, f, 90.0F);
                this.turtle.yBodyRot = this.turtle.yRot;
                float f1 = (float) (this.speedModifier * this.turtle.getAttributeValue(Attributes.MOVEMENT_SPEED));

                this.turtle.setSpeed(Mth.lerp(0.125F, this.turtle.getSpeed(), f1));
                this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0D, (double) this.turtle.getSpeed() * d1 * 0.1D, 0.0D));
            } else {
                this.turtle.setSpeed(0.0F);
            }
        }
    }

    static class TurtleGoToWaterGoal extends MoveToBlockGoal {

        private final Turtle turtle;

        private TurtleGoToWaterGoal(Turtle entityturtle, double d0) {
            super(entityturtle, entityturtle.isBaby() ? 2.0D : d0, 24);
            this.turtle = entityturtle;
            this.verticalSearchStart = -1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.isInWater() && this.tryTicks <= 1200 && this.isValidTarget(this.turtle.level, this.blockPos);
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() && !this.turtle.isInWater() ? super.canUse() : (!this.turtle.isGoingHome() && !this.turtle.isInWater() && !this.turtle.hasEgg() ? super.canUse() : false);
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 160 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader iworldreader, BlockPos blockposition) {
            return iworldreader.getType(blockposition).is(Blocks.WATER);
        }
    }

    static class TurtleRandomStrollGoal extends RandomStrollGoal {

        private final Turtle turtle;

        private TurtleRandomStrollGoal(Turtle entityturtle, double d0, int i) {
            super(entityturtle, d0, i);
            this.turtle = entityturtle;
        }

        @Override
        public boolean canUse() {
            return !this.mob.isInWater() && !this.turtle.isGoingHome() && !this.turtle.hasEgg() ? super.canUse() : false;
        }
    }

    static class TurtleLayEggGoal extends MoveToBlockGoal {

        private final Turtle turtle;

        TurtleLayEggGoal(Turtle entityturtle, double d0) {
            super(entityturtle, d0, 16);
            this.turtle = entityturtle;
        }

        @Override
        public boolean canUse() {
            return this.turtle.hasEgg() && this.turtle.getHomePos().closerThan((Position) this.turtle.position(), 9.0D) ? super.canUse() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.turtle.hasEgg() && this.turtle.getHomePos().closerThan((Position) this.turtle.position(), 9.0D);
        }

        @Override
        public void tick() {
            super.tick();
            BlockPos blockposition = this.turtle.blockPosition();

            if (!this.turtle.isInWater() && this.isReachedTarget()) {
                if (this.turtle.layEggCounter < 1) {
                    this.turtle.setLayingEgg(true);
                } else if (this.turtle.layEggCounter > 200) {
                    Level world = this.turtle.level;

                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.turtle, this.blockPos.above(), (BlockState) Blocks.TURTLE_EGG.getBlockData().setValue(TurtleEggBlock.EGGS, this.turtle.random.nextInt(4) + 1)).isCancelled()) {
                    world.playSound((Player) null, blockposition, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 0.3F, 0.9F + world.random.nextFloat() * 0.2F);
                    world.setTypeAndData(this.blockPos.above(), (BlockState) Blocks.TURTLE_EGG.getBlockData().setValue(TurtleEggBlock.EGGS, this.turtle.random.nextInt(4) + 1), 3);
                    }
                    // CraftBukkit end
                    this.turtle.setHasEgg(false);
                    this.turtle.setLayingEgg(false);
                    this.turtle.setInLoveTime(600);
                }

                if (this.turtle.isLayingEgg()) {
                    this.turtle.layEggCounter++;
                }
            }

        }

        @Override
        protected boolean isValidTarget(LevelReader iworldreader, BlockPos blockposition) {
            return !iworldreader.isEmptyBlock(blockposition.above()) ? false : TurtleEggBlock.isSand(iworldreader, blockposition);
        }
    }

    static class TurtleBreedGoal extends BreedGoal {

        private final Turtle turtle;

        TurtleBreedGoal(Turtle entityturtle, double d0) {
            super(entityturtle, d0);
            this.turtle = entityturtle;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.turtle.hasEgg();
        }

        @Override
        protected void breed() {
            ServerPlayer entityplayer = this.animal.getLoveCause();

            if (entityplayer == null && this.partner.getLoveCause() != null) {
                entityplayer = this.partner.getLoveCause();
            }

            if (entityplayer != null) {
                entityplayer.awardStat(Stats.ANIMALS_BRED);
                CriteriaTriggers.BRED_ANIMALS.trigger(entityplayer, this.animal, this.partner, (AgableMob) null);
            }

            this.turtle.setHasEgg(true);
            this.animal.resetLove();
            this.partner.resetLove();
            Random random = this.animal.getRandom();

            if (this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), random.nextInt(7) + 1));
            }

        }
    }

    static class TurtleTemptGoal extends Goal {

        private static final TargetingConditions TEMPT_TARGETING = (new TargetingConditions()).range(10.0D).allowSameTeam().allowInvulnerable();
        private final Turtle turtle;
        private final double speedModifier;
        private Player player;
        private int calmDown;
        private final Set<Item> items;

        TurtleTemptGoal(Turtle entityturtle, double d0, Item item) {
            this.turtle = entityturtle;
            this.speedModifier = d0;
            this.items = Sets.newHashSet(new Item[]{item});
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.calmDown > 0) {
                --this.calmDown;
                return false;
            } else {
                this.player = this.turtle.level.getNearestPlayer(this.TEMPT_TARGETING, (LivingEntity) this.turtle); // CraftBukkit - decompile error
                return this.player == null ? false : this.shouldFollowItem(this.player.getMainHandItem()) || this.shouldFollowItem(this.player.getOffhandItem());
            }
        }

        private boolean shouldFollowItem(ItemStack itemstack) {
            return this.items.contains(itemstack.getItem());
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            this.player = null;
            this.turtle.getNavigation().stop();
            this.calmDown = 100;
        }

        @Override
        public void tick() {
            this.turtle.getControllerLook().setLookAt(this.player, (float) (this.turtle.getMaxHeadYRot() + 20), (float) this.turtle.getMaxHeadXRot());
            if (this.turtle.distanceToSqr((Entity) this.player) < 6.25D) {
                this.turtle.getNavigation().stop();
            } else {
                this.turtle.getNavigation().moveTo((Entity) this.player, this.speedModifier);
            }

        }
    }

    static class TurtleGoHomeGoal extends Goal {

        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;
        private int closeToHomeTryTicks;

        TurtleGoHomeGoal(Turtle entityturtle, double d0) {
            this.turtle = entityturtle;
            this.speedModifier = d0;
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() ? false : (this.turtle.hasEgg() ? true : (this.turtle.getRandom().nextInt(700) != 0 ? false : !this.turtle.getHomePos().closerThan((Position) this.turtle.position(), 64.0D)));
        }

        @Override
        public void start() {
            this.turtle.setGoingHome(true);
            this.stuck = false;
            this.closeToHomeTryTicks = 0;
        }

        @Override
        public void stop() {
            this.turtle.setGoingHome(false);
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.getHomePos().closerThan((Position) this.turtle.position(), 7.0D) && !this.stuck && this.closeToHomeTryTicks <= 600;
        }

        @Override
        public void tick() {
            BlockPos blockposition = this.turtle.getHomePos();
            boolean flag = blockposition.closerThan((Position) this.turtle.position(), 16.0D);

            if (flag) {
                ++this.closeToHomeTryTicks;
            }

            if (this.turtle.getNavigation().isDone()) {
                Vec3 vec3d = Vec3.atBottomCenterOf((Vec3i) blockposition);
                Vec3 vec3d1 = RandomPos.getPosTowards(this.turtle, 16, 3, vec3d, 0.3141592741012573D);

                if (vec3d1 == null) {
                    vec3d1 = RandomPos.getPosTowards(this.turtle, 8, 7, vec3d);
                }

                if (vec3d1 != null && !flag && !this.turtle.level.getType(new BlockPos(vec3d1)).is(Blocks.WATER)) {
                    vec3d1 = RandomPos.getPosTowards(this.turtle, 16, 5, vec3d);
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.turtle.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, this.speedModifier);
            }

        }
    }

    static class TurtleTravelGoal extends Goal {

        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;

        TurtleTravelGoal(Turtle entityturtle, double d0) {
            this.turtle = entityturtle;
            this.speedModifier = d0;
        }

        @Override
        public boolean canUse() {
            return !this.turtle.isGoingHome() && !this.turtle.hasEgg() && this.turtle.isInWater();
        }

        @Override
        public void start() {
            boolean flag = true;
            boolean flag1 = true;
            Random random = this.turtle.random;
            int i = random.nextInt(1025) - 512;
            int j = random.nextInt(9) - 4;
            int k = random.nextInt(1025) - 512;

            if ((double) j + this.turtle.getY() > (double) (this.turtle.level.getSeaLevel() - 1)) {
                j = 0;
            }

            BlockPos blockposition = new BlockPos((double) i + this.turtle.getX(), (double) j + this.turtle.getY(), (double) k + this.turtle.getZ());

            this.turtle.setTravelPos(blockposition);
            this.turtle.setTravelling(true);
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.turtle.getNavigation().isDone()) {
                Vec3 vec3d = Vec3.atBottomCenterOf((Vec3i) this.turtle.getTravelPos());
                Vec3 vec3d1 = RandomPos.getPosTowards(this.turtle, 16, 3, vec3d, 0.3141592741012573D);

                if (vec3d1 == null) {
                    vec3d1 = RandomPos.getPosTowards(this.turtle, 8, 7, vec3d);
                }

                if (vec3d1 != null) {
                    int i = Mth.floor(vec3d1.x);
                    int j = Mth.floor(vec3d1.z);
                    boolean flag = true;

                    if (!this.turtle.level.hasChunksAt(i - 34, 0, j - 34, i + 34, 0, j + 34)) {
                        vec3d1 = null;
                    }
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.turtle.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, this.speedModifier);
            }

        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.getNavigation().isDone() && !this.stuck && !this.turtle.isGoingHome() && !this.turtle.isInLove() && !this.turtle.hasEgg();
        }

        @Override
        public void stop() {
            this.turtle.setTravelling(false);
            super.stop();
        }
    }

    static class TurtlePanicGoal extends PanicGoal {

        TurtlePanicGoal(Turtle entityturtle, double d0) {
            super(entityturtle, d0);
        }

        @Override
        public boolean canUse() {
            if (this.mob.getLastHurtByMob() == null && !this.mob.isOnFire()) {
                return false;
            } else {
                BlockPos blockposition = this.lookForWater(this.mob.level, this.mob, 7, 4);

                if (blockposition != null) {
                    this.posX = (double) blockposition.getX();
                    this.posY = (double) blockposition.getY();
                    this.posZ = (double) blockposition.getZ();
                    return true;
                } else {
                    return this.findRandomPosition();
                }
            }
        }
    }
}
