package net.minecraft.world.entity.animal;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.DolphinLookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.DolphinJumpGoal;
import net.minecraft.world.entity.ai.goal.FollowBoatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

public class Dolphin extends WaterAnimal {

    private static final EntityDataAccessor<BlockPos> TREASURE_POS = SynchedEntityData.defineId(Dolphin.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> GOT_FISH = SynchedEntityData.defineId(Dolphin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MOISTNESS_LEVEL = SynchedEntityData.defineId(Dolphin.class, EntityDataSerializers.INT);
    private static final TargetingConditions SWIM_WITH_PLAYER_TARGETING = (new TargetingConditions()).range(10.0D).allowSameTeam().allowInvulnerable().allowUnseeable();
    public static final Predicate<ItemEntity> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive() && entityitem.isInWater();
    };

    public Dolphin(EntityType<? extends Dolphin> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveControl = new Dolphin.DolphinMoveControl(this);
        this.lookControl = new DolphinLookControl(this, 10);
        this.setCanPickUpLoot(true);
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.setAirSupply(this.getMaxAirSupply());
        this.xRot = 0.0F;
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return false;
    }

    @Override
    protected void handleAirSupply(int i) {}

    public void setTreasurePos(BlockPos blockposition) {
        this.entityData.set(Dolphin.TREASURE_POS, blockposition);
    }

    public BlockPos getTreasurePos() {
        return (BlockPos) this.entityData.get(Dolphin.TREASURE_POS);
    }

    public boolean gotFish() {
        return (Boolean) this.entityData.get(Dolphin.GOT_FISH);
    }

    public void setGotFish(boolean flag) {
        this.entityData.set(Dolphin.GOT_FISH, flag);
    }

    public int getMoistnessLevel() {
        return (Integer) this.entityData.get(Dolphin.MOISTNESS_LEVEL);
    }

    public void setMoisntessLevel(int i) {
        this.entityData.set(Dolphin.MOISTNESS_LEVEL, i);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Dolphin.TREASURE_POS, BlockPos.ZERO);
        this.entityData.register(Dolphin.GOT_FISH, false);
        this.entityData.register(Dolphin.MOISTNESS_LEVEL, 2400);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("TreasurePosX", this.getTreasurePos().getX());
        nbttagcompound.putInt("TreasurePosY", this.getTreasurePos().getY());
        nbttagcompound.putInt("TreasurePosZ", this.getTreasurePos().getZ());
        nbttagcompound.putBoolean("GotFish", this.gotFish());
        nbttagcompound.putInt("Moistness", this.getMoistnessLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        int i = nbttagcompound.getInt("TreasurePosX");
        int j = nbttagcompound.getInt("TreasurePosY");
        int k = nbttagcompound.getInt("TreasurePosZ");

        this.setTreasurePos(new BlockPos(i, j, k));
        super.readAdditionalSaveData(nbttagcompound);
        this.setGotFish(nbttagcompound.getBoolean("GotFish"));
        this.setMoisntessLevel(nbttagcompound.getInt("Moistness"));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BreathAirGoal(this));
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new Dolphin.DolphinSwimToTreasureGoal(this));
        this.goalSelector.addGoal(2, new Dolphin.DolphinSwimWithPlayerGoal(this, 4.0D));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 10));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new DolphinJumpGoal(this, 10));
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.2000000476837158D, true));
        this.goalSelector.addGoal(8, new Dolphin.PlayWithItemsGoal());
        this.goalSelector.addGoal(8, new FollowBoatGoal(this));
        this.goalSelector.addGoal(9, new AvoidEntityGoal<>(this, Guardian.class, 8.0F, 1.0D, 1.0D));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{Guardian.class})).setAlertOthers(new Class[0])); // CraftBukkit - decompile error
    }

    public static AttributeSupplier.Builder eN() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.MOVEMENT_SPEED, 1.2000000476837158D).a(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new WaterBoundPathNavigation(this, world);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean flag = entity.hurt(DamageSource.mobAttack(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));

        if (flag) {
            this.doEnchantDamageEffects((LivingEntity) this, entity);
            this.playSound(SoundEvents.DOLPHIN_ATTACK, 1.0F, 1.0F);
        }

        return flag;
    }

    @Override
    public int getMaxAirSupply() {
        return 4800;
    }

    @Override
    protected int increaseAirSupply(int i) {
        return this.getMaxAirSupply();
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.3F;
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return true;
    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

        return !this.getItemBySlot(enumitemslot).isEmpty() ? false : enumitemslot == EquipmentSlot.MAINHAND && super.canTakeItem(itemstack);
    }

    @Override
    protected void pickUpItem(ItemEntity entityitem) {
        if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            ItemStack itemstack = entityitem.getItem();

            if (this.canHoldItem(itemstack)) {
                // CraftBukkit start - call EntityPickupItemEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, false).isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.onItemPickup(entityitem);
                this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
                this.handDropChances[EquipmentSlot.MAINHAND.getIndex()] = 2.0F;
                this.take(entityitem, itemstack.getCount());
                entityitem.remove();
            }
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isNoAi()) {
            this.setAirSupply(this.getMaxAirSupply());
        } else {
            if (this.isInWaterRainOrBubble()) {
                this.setMoisntessLevel(2400);
            } else {
                this.setMoisntessLevel(this.getMoistnessLevel() - 1);
                if (this.getMoistnessLevel() <= 0) {
                    this.hurt(DamageSource.DRY_OUT, 1.0F);
                }

                if (this.onGround) {
                    this.setDeltaMovement(this.getDeltaMovement().add((double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F), 0.5D, (double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F)));
                    this.yRot = this.random.nextFloat() * 360.0F;
                    this.onGround = false;
                    this.hasImpulse = true;
                }
            }

            if (this.level.isClientSide && this.isInWater() && this.getDeltaMovement().lengthSqr() > 0.03D) {
                Vec3 vec3d = this.getViewVector(0.0F);
                float f = Mth.cos(this.yRot * 0.017453292F) * 0.3F;
                float f1 = Mth.sin(this.yRot * 0.017453292F) * 0.3F;
                float f2 = 1.2F - this.random.nextFloat() * 0.7F;

                for (int i = 0; i < 2; ++i) {
                    this.level.addParticle(ParticleTypes.DOLPHIN, this.getX() - vec3d.x * (double) f2 + (double) f, this.getY() - vec3d.y, this.getZ() - vec3d.z * (double) f2 + (double) f1, 0.0D, 0.0D, 0.0D);
                    this.level.addParticle(ParticleTypes.DOLPHIN, this.getX() - vec3d.x * (double) f2 - (double) f, this.getY() - vec3d.y, this.getZ() - vec3d.z * (double) f2 - (double) f1, 0.0D, 0.0D, 0.0D);
                }
            }

        }
    }

    @Override
    protected InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (!itemstack.isEmpty() && itemstack.getItem().is((Tag) ItemTags.FISHES)) {
            if (!this.level.isClientSide) {
                this.playSound(SoundEvents.DOLPHIN_EAT, 1.0F, 1.0F);
            }

            this.setGotFish(true);
            if (!entityhuman.abilities.instabuild) {
                itemstack.shrink(1);
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    public static boolean checkDolphinSpawnRules(EntityType<Dolphin> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return blockposition.getY() > 45 && blockposition.getY() < generatoraccess.getSeaLevel() && (generatoraccess.getBiome(blockposition) != Biomes.OCEAN || generatoraccess.getBiome(blockposition) != Biomes.DEEP_OCEAN) && generatoraccess.getFluidState(blockposition).is((Tag) FluidTags.WATER);
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.DOLPHIN_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isInWater() ? SoundEvents.DOLPHIN_AMBIENT_WATER : SoundEvents.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundSplash() {
        return SoundEvents.DOLPHIN_SPLASH;
    }

    @Override
    protected SoundEvent getSoundSwim() {
        return SoundEvents.DOLPHIN_SWIM;
    }

    protected boolean closeToNextPos() {
        BlockPos blockposition = this.getNavigation().getTargetPos();

        return blockposition != null ? blockposition.closerThan((Position) this.position(), 12.0D) : false;
    }

    @Override
    public void travel(Vec3 vec3d) {
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), vec3d);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(vec3d);
        }

    }

    @Override
    public boolean canBeLeashed(Player entityhuman) {
        return true;
    }

    static class DolphinSwimToTreasureGoal extends Goal {

        private final Dolphin dolphin;
        private boolean stuck;

        DolphinSwimToTreasureGoal(Dolphin entitydolphin) {
            this.dolphin = entitydolphin;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public boolean canUse() {
            return this.dolphin.gotFish() && this.dolphin.getAirSupply() >= 100 && this.dolphin.level.getWorld().canGenerateStructures(); // MC-151364, SPIGOT-5494: hangs if generate-structures=false
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos blockposition = this.dolphin.getTreasurePos();

            return !(new BlockPos((double) blockposition.getX(), this.dolphin.getY(), (double) blockposition.getZ())).closerThan((Position) this.dolphin.position(), 4.0D) && !this.stuck && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public void start() {
            if (this.dolphin.level instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) this.dolphin.level;

                this.stuck = false;
                this.dolphin.getNavigation().stop();
                BlockPos blockposition = this.dolphin.blockPosition();
                StructureFeature<?> structuregenerator = (double) worldserver.random.nextFloat() >= 0.5D ? StructureFeature.OCEAN_RUIN : StructureFeature.SHIPWRECK;
                BlockPos blockposition1 = worldserver.findNearestMapFeature(structuregenerator, blockposition, 50, false);

                if (blockposition1 == null) {
                    StructureFeature<?> structuregenerator1 = structuregenerator.equals(StructureFeature.OCEAN_RUIN) ? StructureFeature.SHIPWRECK : StructureFeature.OCEAN_RUIN;
                    BlockPos blockposition2 = worldserver.findNearestMapFeature(structuregenerator1, blockposition, 50, false);

                    if (blockposition2 == null) {
                        this.stuck = true;
                        return;
                    }

                    this.dolphin.setTreasurePos(blockposition2);
                } else {
                    this.dolphin.setTreasurePos(blockposition1);
                }

                worldserver.broadcastEntityEvent(this.dolphin, (byte) 38);
            }
        }

        @Override
        public void stop() {
            BlockPos blockposition = this.dolphin.getTreasurePos();

            if ((new BlockPos((double) blockposition.getX(), this.dolphin.getY(), (double) blockposition.getZ())).closerThan((Position) this.dolphin.position(), 4.0D) || this.stuck) {
                this.dolphin.setGotFish(false);
            }

        }

        @Override
        public void tick() {
            Level world = this.dolphin.level;

            if (this.dolphin.closeToNextPos() || this.dolphin.getNavigation().isDone()) {
                Vec3 vec3d = Vec3.atCenterOf((Vec3i) this.dolphin.getTreasurePos());
                Vec3 vec3d1 = RandomPos.getPosTowards(this.dolphin, 16, 1, vec3d, 0.39269909262657166D);

                if (vec3d1 == null) {
                    vec3d1 = RandomPos.getPosTowards(this.dolphin, 8, 4, vec3d);
                }

                if (vec3d1 != null) {
                    BlockPos blockposition = new BlockPos(vec3d1);

                    if (!world.getFluidState(blockposition).is((Tag) FluidTags.WATER) || !world.getType(blockposition).isPathfindable((BlockGetter) world, blockposition, PathComputationType.WATER)) {
                        vec3d1 = RandomPos.getPosTowards(this.dolphin, 8, 5, vec3d);
                    }
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.dolphin.getControllerLook().setLookAt(vec3d1.x, vec3d1.y, vec3d1.z, (float) (this.dolphin.getMaxHeadYRot() + 20), (float) this.dolphin.getMaxHeadXRot());
                this.dolphin.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, 1.3D);
                if (world.random.nextInt(80) == 0) {
                    world.broadcastEntityEvent(this.dolphin, (byte) 38);
                }
            }

        }
    }

    static class DolphinSwimWithPlayerGoal extends Goal {

        private final Dolphin dolphin;
        private final double speedModifier;
        private Player player;

        DolphinSwimWithPlayerGoal(Dolphin entitydolphin, double d0) {
            this.dolphin = entitydolphin;
            this.speedModifier = d0;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.player = this.dolphin.level.getNearestPlayer(Dolphin.SWIM_WITH_PLAYER_TARGETING, (LivingEntity) this.dolphin);
            return this.player == null ? false : this.player.isSwimming() && this.dolphin.getTarget() != this.player;
        }

        @Override
        public boolean canContinueToUse() {
            return this.player != null && this.player.isSwimming() && this.dolphin.distanceToSqr((Entity) this.player) < 256.0D;
        }

        @Override
        public void start() {
            this.player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DOLPHIN); // CraftBukkit
        }

        @Override
        public void stop() {
            this.player = null;
            this.dolphin.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.dolphin.getControllerLook().setLookAt(this.player, (float) (this.dolphin.getMaxHeadYRot() + 20), (float) this.dolphin.getMaxHeadXRot());
            if (this.dolphin.distanceToSqr((Entity) this.player) < 6.25D) {
                this.dolphin.getNavigation().stop();
            } else {
                this.dolphin.getNavigation().moveTo((Entity) this.player, this.speedModifier);
            }

            if (this.player.isSwimming() && this.player.level.random.nextInt(6) == 0) {
                this.player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DOLPHIN); // CraftBukkit
            }

        }
    }

    class PlayWithItemsGoal extends Goal {

        private int cooldown;

        private PlayWithItemsGoal() {}

        @Override
        public boolean canUse() {
            if (this.cooldown > Dolphin.this.tickCount) {
                return false;
            } else {
                List<ItemEntity> list = Dolphin.this.level.getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Dolphin.ALLOWED_ITEMS);

                return !list.isEmpty() || !Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            }
        }

        @Override
        public void start() {
            List<ItemEntity> list = Dolphin.this.level.getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Dolphin.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                Dolphin.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
                Dolphin.this.playSound(SoundEvents.DOLPHIN_PLAY, 1.0F, 1.0F);
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemstack = Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                this.drop(itemstack);
                Dolphin.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                this.cooldown = Dolphin.this.tickCount + Dolphin.this.random.nextInt(100);
            }

        }

        @Override
        public void tick() {
            List<ItemEntity> list = Dolphin.this.level.getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Dolphin.ALLOWED_ITEMS);
            ItemStack itemstack = Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                this.drop(itemstack);
                Dolphin.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (!list.isEmpty()) {
                Dolphin.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            }

        }

        private void drop(ItemStack itemstack) {
            if (!itemstack.isEmpty()) {
                double d0 = Dolphin.this.getEyeY() - 0.30000001192092896D;
                ItemEntity entityitem = new ItemEntity(Dolphin.this.level, Dolphin.this.getX(), d0, Dolphin.this.getZ(), itemstack);

                entityitem.setPickUpDelay(40);
                entityitem.setThrower(Dolphin.this.getUUID());
                float f = 0.3F;
                float f1 = Dolphin.this.random.nextFloat() * 6.2831855F;
                float f2 = 0.02F * Dolphin.this.random.nextFloat();

                entityitem.setDeltaMovement((double) (0.3F * -Mth.sin(Dolphin.this.yRot * 0.017453292F) * Mth.cos(Dolphin.this.xRot * 0.017453292F) + Mth.cos(f1) * f2), (double) (0.3F * Mth.sin(Dolphin.this.xRot * 0.017453292F) * 1.5F), (double) (0.3F * Mth.cos(Dolphin.this.yRot * 0.017453292F) * Mth.cos(Dolphin.this.xRot * 0.017453292F) + Mth.sin(f1) * f2));
                Dolphin.this.level.addFreshEntity(entityitem);
            }
        }
    }

    static class DolphinMoveControl extends MoveControl {

        private final Dolphin dolphin;

        public DolphinMoveControl(Dolphin entitydolphin) {
            super(entitydolphin);
            this.dolphin = entitydolphin;
        }

        @Override
        public void tick() {
            if (this.dolphin.isInWater()) {
                this.dolphin.setDeltaMovement(this.dolphin.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
            }

            if (this.operation == MoveControl.Operation.MOVE_TO && !this.dolphin.getNavigation().isDone()) {
                double d0 = this.wantedX - this.dolphin.getX();
                double d1 = this.wantedY - this.dolphin.getY();
                double d2 = this.wantedZ - this.dolphin.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;

                if (d3 < 2.500000277905201E-7D) {
                    this.mob.setZza(0.0F);
                } else {
                    float f = (float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F;

                    this.dolphin.yRot = this.rotlerp(this.dolphin.yRot, f, 10.0F);
                    this.dolphin.yBodyRot = this.dolphin.yRot;
                    this.dolphin.yHeadRot = this.dolphin.yRot;
                    float f1 = (float) (this.speedModifier * this.dolphin.getAttributeValue(Attributes.MOVEMENT_SPEED));

                    if (this.dolphin.isInWater()) {
                        this.dolphin.setSpeed(f1 * 0.02F);
                        float f2 = -((float) (Mth.atan2(d1, (double) Mth.sqrt(d0 * d0 + d2 * d2)) * 57.2957763671875D));

                        f2 = Mth.clamp(Mth.wrapDegrees(f2), -85.0F, 85.0F);
                        this.dolphin.xRot = this.rotlerp(this.dolphin.xRot, f2, 5.0F);
                        float f3 = Mth.cos(this.dolphin.xRot * 0.017453292F);
                        float f4 = Mth.sin(this.dolphin.xRot * 0.017453292F);

                        this.dolphin.zza = f3 * f1;
                        this.dolphin.yya = -f4 * f1;
                    } else {
                        this.dolphin.setSpeed(f1 * 0.1F);
                    }

                }
            } else {
                this.dolphin.setSpeed(0.0F);
                this.dolphin.setXxa(0.0F);
                this.dolphin.setYya(0.0F);
                this.dolphin.setZza(0.0F);
            }
        }
    }
}
