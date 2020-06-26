package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.JumpGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

public class Fox extends Animal {

    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Optional<UUID>> DATA_TRUSTED_ID_0 = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final EntityDataAccessor<Optional<UUID>> DATA_TRUSTED_ID_1 = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final Predicate<ItemEntity> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive();
    };
    private static final Predicate<Entity> TRUSTED_TARGET_SELECTOR = (entity) -> {
        if (!(entity instanceof LivingEntity)) {
            return false;
        } else {
            LivingEntity entityliving = (LivingEntity) entity;

            return entityliving.getLastHurtMob() != null && entityliving.getLastHurtMobTimestamp() < entityliving.tickCount + 600;
        }
    };
    private static final Predicate<Entity> STALKABLE_PREY = (entity) -> {
        return entity instanceof Chicken || entity instanceof Rabbit;
    };
    private static final Predicate<Entity> AVOID_PLAYERS = (entity) -> {
        return !entity.isDiscrete() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    };
    private Goal landTargetGoal;
    private Goal turtleEggTargetGoal;
    private Goal fishTargetGoal;
    private float interestedAngle;
    private float interestedAngleO;
    private float crouchAmount;
    private float crouchAmountO;
    private int ticksSinceEaten;

    public Fox(EntityType<? extends Fox> entitytypes, Level world) {
        super(entitytypes, world);
        this.lookControl = new Fox.FoxLookControl();
        this.moveControl = new Fox.FoxMoveControl();
        this.setPathfindingMalus(BlockPathTypes.DANGER_OTHER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, 0.0F);
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Fox.DATA_TRUSTED_ID_0, Optional.empty());
        this.entityData.register(Fox.DATA_TRUSTED_ID_1, Optional.empty());
        this.entityData.register(Fox.DATA_TYPE_ID, 0);
        this.entityData.register(Fox.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.landTargetGoal = new NearestAttackableTargetGoal<>(this, Animal.class, 10, false, false, (entityliving) -> {
            return entityliving instanceof Chicken || entityliving instanceof Rabbit;
        });
        this.turtleEggTargetGoal = new NearestAttackableTargetGoal<>(this, Turtle.class, 10, false, false, Turtle.BABY_ON_LAND_SELECTOR);
        this.fishTargetGoal = new NearestAttackableTargetGoal<>(this, AbstractFish.class, 20, false, false, (entityliving) -> {
            return entityliving instanceof AbstractSchoolingFish;
        });
        this.goalSelector.addGoal(0, new Fox.FoxFloatGoal());
        this.goalSelector.addGoal(1, new Fox.FaceplantGoal());
        this.goalSelector.addGoal(2, new Fox.FoxPanicGoal(2.2D));
        this.goalSelector.addGoal(3, new Fox.FoxBreedGoal(1.0D));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 16.0F, 1.6D, 1.4D, (entityliving) -> {
            return Fox.AVOID_PLAYERS.test(entityliving) && !this.trusts(entityliving.getUUID()) && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Wolf.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
            return !((Wolf) entityliving).isTame() && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, PolarBear.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
            return !this.isDefending();
        }));
        this.goalSelector.addGoal(5, new Fox.StalkPreyGoal());
        this.goalSelector.addGoal(6, new Fox.FoxPounceGoal());
        this.goalSelector.addGoal(6, new Fox.SeekShelterGoal(1.25D));
        this.goalSelector.addGoal(7, new Fox.FoxMeleeAttackGoal(1.2000000476837158D, true));
        this.goalSelector.addGoal(7, new Fox.SleepGoal());
        this.goalSelector.addGoal(8, new Fox.FoxFollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(9, new Fox.FoxStrollThroughVillageGoal(32, 200));
        this.goalSelector.addGoal(10, new Fox.FoxEatBerriesGoal(1.2000000476837158D, 12, 2));
        this.goalSelector.addGoal(10, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(11, new Fox.FoxSearchForItemsGoal());
        this.goalSelector.addGoal(12, new Fox.FoxLookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(13, new Fox.PerchAndSearchGoal());
        this.targetSelector.addGoal(3, new Fox.DefendTrustedTargetGoal(LivingEntity.class, false, false, (entityliving) -> {
            return Fox.TRUSTED_TARGET_SELECTOR.test(entityliving) && !this.trusts(entityliving.getUUID());
        }));
    }

    @Override
    public SoundEvent getEatingSound(ItemStack itemstack) {
        return SoundEvents.FOX_EAT;
    }

    @Override
    public void aiStep() {
        if (!this.level.isClientSide && this.isAlive() && this.isEffectiveAi()) {
            ++this.ticksSinceEaten;
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (this.canEat(itemstack)) {
                if (this.ticksSinceEaten > 600) {
                    ItemStack itemstack1 = itemstack.finishUsingItem(this.level, (LivingEntity) this);

                    if (!itemstack1.isEmpty()) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, itemstack1);
                    }

                    this.ticksSinceEaten = 0;
                } else if (this.ticksSinceEaten > 560 && this.random.nextFloat() < 0.1F) {
                    this.playSound(this.getEatingSound(itemstack), 1.0F, 1.0F);
                    this.level.broadcastEntityEvent(this, (byte) 45);
                }
            }

            LivingEntity entityliving = this.getTarget();

            if (entityliving == null || !entityliving.isAlive()) {
                this.setIsCrouching(false);
                this.setIsInterested(false);
            }
        }

        if (this.isSleeping() || this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }

        super.aiStep();
        if (this.isDefending() && this.random.nextFloat() < 0.05F) {
            this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
        }

    }

    @Override
    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    private boolean canEat(ItemStack itemstack) {
        return itemstack.getItem().isEdible() && this.getTarget() == null && this.onGround && !this.isSleeping();
    }

    @Override
    protected void populateDefaultEquipmentSlots(DifficultyInstance difficultydamagescaler) {
        if (this.random.nextFloat() < 0.2F) {
            float f = this.random.nextFloat();
            ItemStack itemstack;

            if (f < 0.05F) {
                itemstack = new ItemStack(Items.EMERALD);
            } else if (f < 0.2F) {
                itemstack = new ItemStack(Items.EGG);
            } else if (f < 0.4F) {
                itemstack = this.random.nextBoolean() ? new ItemStack(Items.RABBIT_FOOT) : new ItemStack(Items.RABBIT_HIDE);
            } else if (f < 0.6F) {
                itemstack = new ItemStack(Items.WHEAT);
            } else if (f < 0.8F) {
                itemstack = new ItemStack(Items.LEATHER);
            } else {
                itemstack = new ItemStack(Items.FEATHER);
            }

            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
        }

    }

    public static AttributeSupplier.Builder eL() {
        return Mob.p().a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.FOLLOW_RANGE, 32.0D).a(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    public Fox getBreedOffspring(AgableMob entityageable) {
        Fox entityfox = (Fox) EntityType.FOX.create(this.level);

        entityfox.setFoxType(this.random.nextBoolean() ? this.getFoxType() : ((Fox) entityageable).getFoxType());
        return entityfox;
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        Biome biomebase = generatoraccess.getBiome(this.blockPosition());
        Fox.Type entityfox_type = Fox.Type.byBiome(biomebase);
        boolean flag = false;

        if (groupdataentity instanceof Fox.FoxGroupData) {
            entityfox_type = ((Fox.FoxGroupData) groupdataentity).type;
            if (((Fox.FoxGroupData) groupdataentity).getGroupSize() >= 2) {
                flag = true;
            }
        } else {
            groupdataentity = new Fox.FoxGroupData(entityfox_type);
        }

        this.setFoxType(entityfox_type);
        if (flag) {
            this.setAge(-24000);
        }

        if (generatoraccess instanceof ServerLevel) {
            this.setTargetGoals();
        }

        this.populateDefaultEquipmentSlots(difficultydamagescaler);
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    private void setTargetGoals() {
        if (this.getFoxType() == Fox.Type.RED) {
            this.targetSelector.addGoal(4, this.landTargetGoal);
            this.targetSelector.addGoal(4, this.turtleEggTargetGoal);
            this.targetSelector.addGoal(6, this.fishTargetGoal);
        } else {
            this.targetSelector.addGoal(4, this.fishTargetGoal);
            this.targetSelector.addGoal(6, this.landTargetGoal);
            this.targetSelector.addGoal(6, this.turtleEggTargetGoal);
        }

    }

    @Override
    protected void usePlayerItem(Player entityhuman, ItemStack itemstack) {
        if (this.isFood(itemstack)) {
            this.playSound(this.getEatingSound(itemstack), 1.0F, 1.0F);
        }

        super.usePlayerItem(entityhuman, itemstack);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return this.isBaby() ? entitysize.height * 0.85F : 0.4F;
    }

    public Fox.Type getFoxType() {
        return Fox.Type.byId((Integer) this.entityData.get(Fox.DATA_TYPE_ID));
    }

    public void setFoxType(Fox.Type entityfox_type) {
        this.entityData.set(Fox.DATA_TYPE_ID, entityfox_type.getId());
    }

    private List<UUID> getTrustedUUIDs() {
        List<UUID> list = Lists.newArrayList();

        list.add((this.entityData.get(Fox.DATA_TRUSTED_ID_0)).orElse(null)); // CraftBukkit - decompile error
        list.add((this.entityData.get(Fox.DATA_TRUSTED_ID_1)).orElse(null)); // CraftBukkit - decompile error
        return list;
    }

    private void addTrustedUUID(@Nullable UUID uuid) {
        if (((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_0)).isPresent()) {
            this.entityData.set(Fox.DATA_TRUSTED_ID_1, Optional.ofNullable(uuid));
        } else {
            this.entityData.set(Fox.DATA_TRUSTED_ID_0, Optional.ofNullable(uuid));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        List<UUID> list = this.getTrustedUUIDs();
        ListTag nbttaglist = new ListTag();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            UUID uuid = (UUID) iterator.next();

            if (uuid != null) {
                nbttaglist.add(NbtUtils.createUUID(uuid));
            }
        }

        nbttagcompound.put("Trusted", nbttaglist);
        nbttagcompound.putBoolean("Sleeping", this.isSleeping());
        nbttagcompound.putString("Type", this.getFoxType().getName());
        nbttagcompound.putBoolean("Sitting", this.isSitting());
        nbttagcompound.putBoolean("Crouching", this.isCrouching());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        ListTag nbttaglist = nbttagcompound.getList("Trusted", 11);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            this.addTrustedUUID(NbtUtils.loadUUID(nbttaglist.get(i)));
        }

        this.setSleeping(nbttagcompound.getBoolean("Sleeping"));
        this.setFoxType(Fox.Type.byName(nbttagcompound.getString("Type")));
        this.setSitting(nbttagcompound.getBoolean("Sitting"));
        this.setIsCrouching(nbttagcompound.getBoolean("Crouching"));
        if (this.level instanceof ServerLevel) {
            this.setTargetGoals();
        }

    }

    public boolean isSitting() {
        return this.getFlag(1);
    }

    public void setSitting(boolean flag) {
        this.setFlag(1, flag);
    }

    public boolean isFaceplanted() {
        return this.getFlag(64);
    }

    private void setFaceplanted(boolean flag) {
        this.setFlag(64, flag);
    }

    private boolean isDefending() {
        return this.getFlag(128);
    }

    private void setDefending(boolean flag) {
        this.setFlag(128, flag);
    }

    @Override
    public boolean isSleeping() {
        return this.getFlag(32);
    }

    public void setSleeping(boolean flag) {
        this.setFlag(32, flag);
    }

    private void setFlag(int i, boolean flag) {
        if (flag) {
            this.entityData.set(Fox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) | i));
        } else {
            this.entityData.set(Fox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) & ~i));
        }

    }

    private boolean getFlag(int i) {
        return ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) & i) != 0;
    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

        return !this.getItemBySlot(enumitemslot).isEmpty() ? false : enumitemslot == EquipmentSlot.MAINHAND && super.canTakeItem(itemstack);
    }

    @Override
    public boolean canHoldItem(ItemStack itemstack) {
        Item item = itemstack.getItem();
        ItemStack itemstack1 = this.getItemBySlot(EquipmentSlot.MAINHAND);

        return itemstack1.isEmpty() || this.ticksSinceEaten > 0 && item.isEdible() && !itemstack1.getItem().isEdible();
    }

    private void spitOutItem(ItemStack itemstack) {
        if (!itemstack.isEmpty() && !this.level.isClientSide) {
            ItemEntity entityitem = new ItemEntity(this.level, this.getX() + this.getLookAngle().x, this.getY() + 1.0D, this.getZ() + this.getLookAngle().z, itemstack);

            entityitem.setPickUpDelay(40);
            entityitem.setThrower(this.getUUID());
            this.playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
            this.level.addFreshEntity(entityitem);
        }
    }

    private void dropItemStack(ItemStack itemstack) {
        ItemEntity entityitem = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), itemstack);

        this.level.addFreshEntity(entityitem);
    }

    @Override
    protected void pickUpItem(ItemEntity entityitem) {
        ItemStack itemstack = entityitem.getItem();

        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, itemstack.getCount() - 1, !this.canHoldItem(itemstack)).isCancelled()) { // CraftBukkit - call EntityPickupItemEvent
            int i = itemstack.getCount();

            if (i > 1) {
                this.dropItemStack(itemstack.split(i - 1));
            }

            this.spitOutItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
            this.onItemPickup(entityitem);
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack.split(1));
            this.handDropChances[EquipmentSlot.MAINHAND.getIndex()] = 2.0F;
            this.take(entityitem, itemstack.getCount());
            entityitem.remove();
            this.ticksSinceEaten = 0;
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isEffectiveAi()) {
            boolean flag = this.isInWater();

            if (flag || this.getTarget() != null || this.level.isThundering()) {
                this.wakeUp();
            }

            if (flag || this.isSleeping()) {
                this.setSitting(false);
            }

            if (this.isFaceplanted() && this.level.random.nextFloat() < 0.2F) {
                BlockPos blockposition = this.blockPosition();
                BlockState iblockdata = this.level.getType(blockposition);

                this.level.levelEvent(2001, blockposition, Block.getCombinedId(iblockdata));
            }
        }

        this.interestedAngleO = this.interestedAngle;
        if (this.isInterested()) {
            this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
        } else {
            this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
        }

        this.crouchAmountO = this.crouchAmount;
        if (this.isCrouching()) {
            this.crouchAmount += 0.2F;
            if (this.crouchAmount > 3.0F) {
                this.crouchAmount = 3.0F;
            }
        } else {
            this.crouchAmount = 0.0F;
        }

    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.getItem() == Items.SWEET_BERRIES;
    }

    @Override
    protected void onOffspringSpawnedFromEgg(Player entityhuman, Mob entityinsentient) {
        ((Fox) entityinsentient).addTrustedUUID(entityhuman.getUUID());
    }

    public boolean isPouncing() {
        return this.getFlag(16);
    }

    public void setIsPouncing(boolean flag) {
        this.setFlag(16, flag);
    }

    public boolean isFullyCrouched() {
        return this.crouchAmount == 3.0F;
    }

    public void setIsCrouching(boolean flag) {
        this.setFlag(4, flag);
    }

    public boolean isCrouching() {
        return this.getFlag(4);
    }

    public void setIsInterested(boolean flag) {
        this.setFlag(8, flag);
    }

    public boolean isInterested() {
        return this.getFlag(8);
    }

    @Override
    public void setTarget(@Nullable LivingEntity entityliving) {
        if (this.isDefending() && entityliving == null) {
            this.setDefending(false);
        }

        super.setTarget(entityliving);
    }

    @Override
    protected int calculateFallDamage(float f, float f1) {
        return Mth.ceil((f - 5.0F) * f1);
    }

    private void wakeUp() {
        this.setSleeping(false);
    }

    private void clearStates() {
        this.setIsInterested(false);
        this.setIsCrouching(false);
        this.setSitting(false);
        this.setSleeping(false);
        this.setDefending(false);
        this.setFaceplanted(false);
    }

    private boolean canMove() {
        return !this.isSleeping() && !this.isSitting() && !this.isFaceplanted();
    }

    @Override
    public void playAmbientSound() {
        SoundEvent soundeffect = this.getSoundAmbient();

        if (soundeffect == SoundEvents.FOX_SCREECH) {
            this.playSound(soundeffect, 2.0F, this.getVoicePitch());
        } else {
            super.playAmbientSound();
        }

    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        if (this.isSleeping()) {
            return SoundEvents.FOX_SLEEP;
        } else {
            if (!this.level.isDay() && this.random.nextFloat() < 0.1F) {
                List<Player> list = this.level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0D, 16.0D, 16.0D), EntitySelector.NO_SPECTATORS);

                if (list.isEmpty()) {
                    return SoundEvents.FOX_SCREECH;
                }
            }

            return SoundEvents.FOX_AMBIENT;
        }
    }

    @Nullable
    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.FOX_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.FOX_DEATH;
    }

    private boolean trusts(UUID uuid) {
        return this.getTrustedUUIDs().contains(uuid);
    }

    @Override
    protected void dropAllDeathLoot(DamageSource damagesource) {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!itemstack.isEmpty()) {
            this.spawnAtLocation(itemstack);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        super.dropAllDeathLoot(damagesource);
    }

    public static boolean isPathClear(Fox entityfox, LivingEntity entityliving) {
        double d0 = entityliving.getZ() - entityfox.getZ();
        double d1 = entityliving.getX() - entityfox.getX();
        double d2 = d0 / d1;
        boolean flag = true;

        for (int i = 0; i < 6; ++i) {
            double d3 = d2 == 0.0D ? 0.0D : d0 * (double) ((float) i / 6.0F);
            double d4 = d2 == 0.0D ? d1 * (double) ((float) i / 6.0F) : d3 / d2;

            for (int j = 1; j < 4; ++j) {
                if (!entityfox.level.getType(new BlockPos(entityfox.getX() + d4, entityfox.getY() + (double) j, entityfox.getZ() + d3)).getMaterial().isReplaceable()) {
                    return false;
                }
            }
        }

        return true;
    }

    class FoxLookAtPlayerGoal extends LookAtPlayerGoal {

        public FoxLookAtPlayerGoal(Mob entityinsentient, Class oclass, float f) {
            super(entityinsentient, oclass, f);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }
    }

    class FoxFollowParentGoal extends FollowParentGoal {

        private final Fox fox;

        public FoxFollowParentGoal(Fox entityfox, double d0) {
            super(entityfox, d0);
            this.fox = entityfox;
        }

        @Override
        public boolean canUse() {
            return !this.fox.isDefending() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.fox.isDefending() && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.fox.clearStates();
            super.start();
        }
    }

    public class FoxLookControl extends LookControl {

        public FoxLookControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (!Fox.this.isSleeping()) {
                super.tick();
            }

        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Fox.this.isPouncing() && !Fox.this.isCrouching() && !Fox.this.isInterested() & !Fox.this.isFaceplanted();
        }
    }

    public class FoxPounceGoal extends JumpGoal {

        public FoxPounceGoal() {}

        @Override
        public boolean canUse() {
            if (!Fox.this.isFullyCrouched()) {
                return false;
            } else {
                LivingEntity entityliving = Fox.this.getTarget();

                if (entityliving != null && entityliving.isAlive()) {
                    if (entityliving.getMotionDirection() != entityliving.getDirection()) {
                        return false;
                    } else {
                        boolean flag = Fox.isPathClear((Fox) Fox.this, entityliving);

                        if (!flag) {
                            Fox.this.getNavigation().createPath((Entity) entityliving, 0);
                            Fox.this.setIsCrouching(false);
                            Fox.this.setIsInterested(false);
                        }

                        return flag;
                    }
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null && entityliving.isAlive()) {
                double d0 = Fox.this.getDeltaMovement().y;

                return (d0 * d0 >= 0.05000000074505806D || Math.abs(Fox.this.xRot) >= 15.0F || !Fox.this.onGround) && !Fox.this.isFaceplanted();
            } else {
                return false;
            }
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            Fox.this.setJumping(true);
            Fox.this.setIsPouncing(true);
            Fox.this.setIsInterested(false);
            LivingEntity entityliving = Fox.this.getTarget();

            Fox.this.getControllerLook().setLookAt(entityliving, 60.0F, 30.0F);
            Vec3 vec3d = (new Vec3(entityliving.getX() - Fox.this.getX(), entityliving.getY() - Fox.this.getY(), entityliving.getZ() - Fox.this.getZ())).normalize();

            Fox.this.setDeltaMovement(Fox.this.getDeltaMovement().add(vec3d.x * 0.8D, 0.9D, vec3d.z * 0.8D));
            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setIsCrouching(false);
            Fox.this.crouchAmount = 0.0F;
            Fox.this.crouchAmountO = 0.0F;
            Fox.this.setIsInterested(false);
            Fox.this.setIsPouncing(false);
        }

        @Override
        public void tick() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null) {
                Fox.this.getControllerLook().setLookAt(entityliving, 60.0F, 30.0F);
            }

            if (!Fox.this.isFaceplanted()) {
                Vec3 vec3d = Fox.this.getDeltaMovement();

                if (vec3d.y * vec3d.y < 0.029999999329447746D && Fox.this.xRot != 0.0F) {
                    Fox.this.xRot = Mth.rotlerp(Fox.this.xRot, 0.0F, 0.2F);
                } else {
                    double d0 = Math.sqrt(Entity.getHorizontalDistanceSqr(vec3d));
                    double d1 = Math.signum(-vec3d.y) * Math.acos(d0 / vec3d.length()) * 57.2957763671875D;

                    Fox.this.xRot = (float) d1;
                }
            }

            if (entityliving != null && Fox.this.distanceTo((Entity) entityliving) <= 2.0F) {
                Fox.this.doHurtTarget(entityliving);
            } else if (Fox.this.xRot > 0.0F && Fox.this.onGround && (float) Fox.this.getDeltaMovement().y != 0.0F && Fox.this.level.getType(Fox.this.blockPosition()).is(Blocks.SNOW)) {
                Fox.this.xRot = 60.0F;
                Fox.this.setTarget((LivingEntity) null);
                Fox.this.setFaceplanted(true);
            }

        }
    }

    class FoxFloatGoal extends FloatGoal {

        public FoxFloatGoal() {
            super(Fox.this);
        }

        @Override
        public void start() {
            super.start();
            Fox.this.clearStates();
        }

        @Override
        public boolean canUse() {
            return Fox.this.isInWater() && Fox.this.getFluidHeight((Tag) FluidTags.WATER) > 0.25D || Fox.this.isInLava();
        }
    }

    class FoxStrollThroughVillageGoal extends StrollThroughVillageGoal {

        public FoxStrollThroughVillageGoal(int i, int j) {
            super(Fox.this, j);
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.canFoxMove();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.canFoxMove();
        }

        private boolean canFoxMove() {
            return !Fox.this.isSleeping() && !Fox.this.isSitting() && !Fox.this.isDefending() && Fox.this.getTarget() == null;
        }
    }

    class FoxPanicGoal extends PanicGoal {

        public FoxPanicGoal(double d0) {
            super(Fox.this, d0);
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isDefending() && super.canUse();
        }
    }

    class FaceplantGoal extends Goal {

        int countdown;

        public FaceplantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Fox.this.isFaceplanted();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && this.countdown > 0;
        }

        @Override
        public void start() {
            this.countdown = 40;
        }

        @Override
        public void stop() {
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void tick() {
            --this.countdown;
        }
    }

    public static class FoxGroupData extends AgableMob.AgableMobGroupData {

        public final Fox.Type type;

        public FoxGroupData(Fox.Type entityfox_type) {
            this.setShouldSpawnBaby(false);
            this.type = entityfox_type;
        }
    }

    public class FoxEatBerriesGoal extends MoveToBlockGoal {

        protected int ticksWaited;

        public FoxEatBerriesGoal(double d0, int i, int j) {
            super(Fox.this, d0, i, j);
        }

        @Override
        public double acceptedDistance() {
            return 2.0D;
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 100 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader iworldreader, BlockPos blockposition) {
            BlockState iblockdata = iworldreader.getType(blockposition);

            return iblockdata.is(Blocks.SWEET_BERRY_BUSH) && (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) >= 2;
        }

        @Override
        public void tick() {
            if (this.isReachedTarget()) {
                if (this.ticksWaited >= 40) {
                    this.onReachedTarget();
                } else {
                    ++this.ticksWaited;
                }
            } else if (!this.isReachedTarget() && Fox.this.random.nextFloat() < 0.05F) {
                Fox.this.playSound(SoundEvents.FOX_SNIFF, 1.0F, 1.0F);
            }

            super.tick();
        }

        protected void onReachedTarget() {
            if (Fox.this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                BlockState iblockdata = Fox.this.level.getType(this.blockPos);

                if (iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                    int i = (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE);

                    iblockdata.setValue(SweetBerryBushBlock.AGE, 1);
                    // CraftBukkit start - call EntityChangeBlockEvent
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(Fox.this, this.blockPos, iblockdata.setValue(SweetBerryBushBlock.AGE, 1)).isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    int j = 1 + Fox.this.level.random.nextInt(2) + (i == 3 ? 1 : 0);
                    ItemStack itemstack = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);

                    if (itemstack.isEmpty()) {
                        Fox.this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
                        --j;
                    }

                    if (j > 0) {
                        Block.popResource(Fox.this.level, this.blockPos, new ItemStack(Items.SWEET_BERRIES, j));
                    }

                    Fox.this.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
                    Fox.this.level.setTypeAndData(this.blockPos, (BlockState) iblockdata.setValue(SweetBerryBushBlock.AGE, 1), 2);
                }
            }
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSleeping() && super.canUse();
        }

        @Override
        public void start() {
            this.ticksWaited = 0;
            Fox.this.setSitting(false);
            super.start();
        }
    }

    class PerchAndSearchGoal extends Fox.FoxBehaviorGoal {

        private double relX;
        private double relZ;
        private int lookTime;
        private int looksRemaining;

        public PerchAndSearchGoal() {
            super(); // CraftBukkit - decompile error
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return Fox.this.getLastHurtByMob() == null && Fox.this.getRandom().nextFloat() < 0.02F && !Fox.this.isSleeping() && Fox.this.getTarget() == null && Fox.this.getNavigation().isDone() && !this.alertable() && !Fox.this.isPouncing() && !Fox.this.isCrouching();
        }

        @Override
        public boolean canContinueToUse() {
            return this.looksRemaining > 0;
        }

        @Override
        public void start() {
            this.resetLook();
            this.looksRemaining = 2 + Fox.this.getRandom().nextInt(3);
            Fox.this.setSitting(true);
            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setSitting(false);
        }

        @Override
        public void tick() {
            --this.lookTime;
            if (this.lookTime <= 0) {
                --this.looksRemaining;
                this.resetLook();
            }

            Fox.this.getControllerLook().setLookAt(Fox.this.getX() + this.relX, Fox.this.getEyeY(), Fox.this.getZ() + this.relZ, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
        }

        private void resetLook() {
            double d0 = 6.283185307179586D * Fox.this.getRandom().nextDouble();

            this.relX = Math.cos(d0);
            this.relZ = Math.sin(d0);
            this.lookTime = 80 + Fox.this.getRandom().nextInt(20);
        }
    }

    class SleepGoal extends Fox.FoxBehaviorGoal {

        private int countdown;

        public SleepGoal() {
            super(); // CraftBukkit - decompile error
            this.countdown = Fox.this.random.nextInt(140);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return Fox.this.xxa == 0.0F && Fox.this.yya == 0.0F && Fox.this.zza == 0.0F ? this.canSleep() || Fox.this.isSleeping() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canSleep();
        }

        private boolean canSleep() {
            if (this.countdown > 0) {
                --this.countdown;
                return false;
            } else {
                return Fox.this.level.isDay() && this.hasShelter() && !this.alertable();
            }
        }

        @Override
        public void stop() {
            this.countdown = Fox.this.random.nextInt(140);
            Fox.this.clearStates();
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setIsCrouching(false);
            Fox.this.setIsInterested(false);
            Fox.this.setJumping(false);
            Fox.this.setSleeping(true);
            Fox.this.getNavigation().stop();
            Fox.this.getMoveControl().setWantedPosition(Fox.this.getX(), Fox.this.getY(), Fox.this.getZ(), 0.0D);
        }
    }

    abstract class FoxBehaviorGoal extends Goal {

        private final TargetingConditions alertableTargeting;

        private FoxBehaviorGoal() {
            this.alertableTargeting = (new TargetingConditions()).range(12.0D).allowUnseeable().selector(Fox.this.new FoxAlertableEntitiesSelector());
        }

        protected boolean hasShelter() {
            BlockPos blockposition = new BlockPos(Fox.this.getX(), Fox.this.getBoundingBox().maxY, Fox.this.getZ());

            return !Fox.this.level.canSeeSky(blockposition) && Fox.this.getWalkTargetValue(blockposition) >= 0.0F;
        }

        protected boolean alertable() {
            return !Fox.this.level.getNearbyEntities(LivingEntity.class, this.alertableTargeting, Fox.this, Fox.this.getBoundingBox().inflate(12.0D, 6.0D, 12.0D)).isEmpty();
        }
    }

    public class FoxAlertableEntitiesSelector implements Predicate<LivingEntity> {

        public FoxAlertableEntitiesSelector() {}

        public boolean test(LivingEntity entityliving) {
            return entityliving instanceof Fox ? false : (!(entityliving instanceof Chicken) && !(entityliving instanceof Rabbit) && !(entityliving instanceof Monster) ? (entityliving instanceof TamableAnimal ? !((TamableAnimal) entityliving).isTame() : (entityliving instanceof Player && (entityliving.isSpectator() || ((Player) entityliving).isCreative()) ? false : (Fox.this.trusts(entityliving.getUUID()) ? false : !entityliving.isSleeping() && !entityliving.isDiscrete()))) : true);
        }
    }

    class SeekShelterGoal extends FleeSunGoal {

        private int interval = 100;

        public SeekShelterGoal(double d0) {
            super(Fox.this, d0);
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.isSleeping() && this.mob.getTarget() == null) {
                if (Fox.this.level.isThundering()) {
                    return true;
                } else if (this.interval > 0) {
                    --this.interval;
                    return false;
                } else {
                    this.interval = 100;
                    BlockPos blockposition = this.mob.blockPosition();

                    return Fox.this.level.isDay() && Fox.this.level.canSeeSky(blockposition) && !((ServerLevel) Fox.this.level).isVillage(blockposition) && this.setWantedPos();
                }
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }
    }

    class DefendTrustedTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

        @Nullable
        private LivingEntity trustedLastHurtBy;
        private LivingEntity trustedLastHurt;
        private int timestamp;

        public DefendTrustedTargetGoal(Class oclass, boolean flag, boolean flag1, Predicate<LivingEntity> predicate) { // CraftBukkit - decompile error
            super(Fox.this, oclass, 10, flag, flag1, predicate);
        }

        @Override
        public boolean canUse() {
            if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
                return false;
            } else {
                Iterator iterator = Fox.this.getTrustedUUIDs().iterator();

                while (iterator.hasNext()) {
                    UUID uuid = (UUID) iterator.next();

                    if (uuid != null && Fox.this.level instanceof ServerLevel) {
                        Entity entity = ((ServerLevel) Fox.this.level).getEntity(uuid);

                        if (entity instanceof LivingEntity) {
                            LivingEntity entityliving = (LivingEntity) entity;

                            this.trustedLastHurt = entityliving;
                            this.trustedLastHurtBy = entityliving.getLastHurtByMob();
                            int i = entityliving.getLastHurtByMobTimestamp();

                            return i != this.timestamp && this.canAttack(this.trustedLastHurtBy, this.targetConditions);
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public void start() {
            this.setTarget(this.trustedLastHurtBy);
            this.target = this.trustedLastHurtBy;
            if (this.trustedLastHurt != null) {
                this.timestamp = this.trustedLastHurt.getLastHurtByMobTimestamp();
            }

            Fox.this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
            Fox.this.setDefending(true);
            Fox.this.wakeUp();
            super.start();
        }
    }

    class FoxBreedGoal extends BreedGoal {

        public FoxBreedGoal(double d0) {
            super(Fox.this, d0);
        }

        @Override
        public void start() {
            ((Fox) this.animal).clearStates();
            ((Fox) this.partner).clearStates();
            super.start();
        }

        @Override
        protected void breed() {
            Fox entityfox = (Fox) this.animal.getBreedOffspring(this.partner);

            if (entityfox != null) {
                ServerPlayer entityplayer = this.animal.getLoveCause();
                ServerPlayer entityplayer1 = this.partner.getLoveCause();
                ServerPlayer entityplayer2 = entityplayer;

                if (entityplayer != null) {
                    entityfox.addTrustedUUID(entityplayer.getUUID());
                } else {
                    entityplayer2 = entityplayer1;
                }

                if (entityplayer1 != null && entityplayer != entityplayer1) {
                    entityfox.addTrustedUUID(entityplayer1.getUUID());
                }
                // CraftBukkit start - call EntityBreedEvent
                int experience = this.animal.getRandom().nextInt(7) + 1;
                org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityfox, animal, partner, entityplayer, this.animal.breedItem, experience);
                if (entityBreedEvent.isCancelled()) {
                    return;
                }
                experience = entityBreedEvent.getExperience();
                // CraftBukkit end

                if (entityplayer2 != null) {
                    entityplayer2.awardStat(Stats.ANIMALS_BRED);
                    CriteriaTriggers.BRED_ANIMALS.trigger(entityplayer2, this.animal, this.partner, (AgableMob) entityfox);
                }

                this.animal.setAge(6000);
                this.partner.setAge(6000);
                this.animal.resetLove();
                this.partner.resetLove();
                entityfox.setAge(-24000);
                entityfox.moveTo(this.animal.getX(), this.animal.getY(), this.animal.getZ(), 0.0F, 0.0F);
                this.level.addEntity(entityfox, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - added SpawnReason
                this.level.broadcastEntityEvent(this.animal, (byte) 18);
                if (this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                    // CraftBukkit start - use event experience
                    if (experience > 0) {
                        this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), experience));
                    }
                    // CraftBukkit end
                }

            }
        }
    }

    class FoxMeleeAttackGoal extends MeleeAttackGoal {

        public FoxMeleeAttackGoal(double d0, boolean flag) {
            super(Fox.this, d0, flag);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity entityliving, double d0) {
            double d1 = this.getAttackReachSqr(entityliving);

            if (d0 <= d1 && this.isTimeToAttack()) {
                this.resetAttackCooldown();
                this.mob.doHurtTarget(entityliving);
                Fox.this.playSound(SoundEvents.FOX_BITE, 1.0F, 1.0F);
            }

        }

        @Override
        public void start() {
            Fox.this.setIsInterested(false);
            super.start();
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSitting() && !Fox.this.isSleeping() && !Fox.this.isCrouching() && !Fox.this.isFaceplanted() && super.canUse();
        }
    }

    class StalkPreyGoal extends Goal {

        public StalkPreyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (Fox.this.isSleeping()) {
                return false;
            } else {
                LivingEntity entityliving = Fox.this.getTarget();

                return entityliving != null && entityliving.isAlive() && Fox.STALKABLE_PREY.test(entityliving) && Fox.this.distanceToSqr((Entity) entityliving) > 36.0D && !Fox.this.isCrouching() && !Fox.this.isInterested() && !Fox.this.jumping;
            }
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void stop() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null && Fox.isPathClear((Fox) Fox.this, entityliving)) {
                Fox.this.setIsInterested(true);
                Fox.this.setIsCrouching(true);
                Fox.this.getNavigation().stop();
                Fox.this.getControllerLook().setLookAt(entityliving, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
            } else {
                Fox.this.setIsInterested(false);
                Fox.this.setIsCrouching(false);
            }

        }

        @Override
        public void tick() {
            LivingEntity entityliving = Fox.this.getTarget();

            Fox.this.getControllerLook().setLookAt(entityliving, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
            if (Fox.this.distanceToSqr((Entity) entityliving) <= 36.0D) {
                Fox.this.setIsInterested(true);
                Fox.this.setIsCrouching(true);
                Fox.this.getNavigation().stop();
            } else {
                Fox.this.getNavigation().moveTo((Entity) entityliving, 1.5D);
            }

        }
    }

    class FoxMoveControl extends MoveControl {

        public FoxMoveControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (Fox.this.canMove()) {
                super.tick();
            }

        }
    }

    class FoxSearchForItemsGoal extends Goal {

        public FoxSearchForItemsGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                return false;
            } else if (Fox.this.getTarget() == null && Fox.this.getLastHurtByMob() == null) {
                if (!Fox.this.canMove()) {
                    return false;
                } else if (Fox.this.getRandom().nextInt(10) != 0) {
                    return false;
                } else {
                    List<ItemEntity> list = Fox.this.level.getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);

                    return !list.isEmpty() && Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
                }
            } else {
                return false;
            }
        }

        @Override
        public void tick() {
            List<ItemEntity> list = Fox.this.level.getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);
            ItemStack itemstack = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (itemstack.isEmpty() && !list.isEmpty()) {
                Fox.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            }

        }

        @Override
        public void start() {
            List<ItemEntity> list = Fox.this.level.getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                Fox.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            }

        }
    }

    public static enum Type {

        RED(0, "red", new Biome[]{Biomes.TAIGA, Biomes.TAIGA_HILLS, Biomes.TAIGA_MOUNTAINS, Biomes.GIANT_TREE_TAIGA, Biomes.GIANT_SPRUCE_TAIGA, Biomes.GIANT_TREE_TAIGA_HILLS, Biomes.GIANT_SPRUCE_TAIGA_HILLS}), SNOW(1, "snow", new Biome[]{Biomes.SNOWY_TAIGA, Biomes.SNOWY_TAIGA_HILLS, Biomes.SNOWY_TAIGA_MOUNTAINS});

        private static final Fox.Type[] BY_ID = (Fox.Type[]) Arrays.stream(values()).sorted(Comparator.comparingInt(Fox.Type::getId)).toArray((i) -> {
            return new Fox.Type[i];
        });
        private static final Map<String, Fox.Type> BY_NAME = (Map) Arrays.stream(values()).collect(Collectors.toMap(Fox.Type::getName, (entityfox_type) -> {
            return entityfox_type;
        }));
        private final int id;
        private final String name;
        private final List<Biome> biomes;

        private Type(int i, String s, Biome... abiomebase) {
            this.id = i;
            this.name = s;
            this.biomes = Arrays.asList(abiomebase);
        }

        public String getName() {
            return this.name;
        }

        public List<Biome> getBiomes() {
            return this.biomes;
        }

        public int getId() {
            return this.id;
        }

        public static Fox.Type byName(String s) {
            return (Fox.Type) Fox.Type.BY_NAME.getOrDefault(s, Fox.Type.RED);
        }

        public static Fox.Type byId(int i) {
            if (i < 0 || i > Fox.Type.BY_ID.length) {
                i = 0;
            }

            return Fox.Type.BY_ID[i];
        }

        public static Fox.Type byBiome(Biome biomebase) {
            return Fox.Type.SNOW.getBiomes().contains(biomebase) ? Fox.Type.SNOW : Fox.Type.RED;
        }
    }
}
