package net.minecraft.world.entity.animal.horse;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason; // CraftBukkit

public abstract class AbstractHorse extends Animal implements ContainerListener, PlayerRideableJumping, Saddleable {

    private static final Predicate<LivingEntity> PARENT_HORSE_SELECTOR = (entityliving) -> {
        return entityliving instanceof AbstractHorse && ((AbstractHorse) entityliving).isBred();
    };
    private static final TargetingConditions MOMMY_TARGETING = (new TargetingConditions()).range(16.0D).allowInvulnerable().allowSameTeam().allowUnseeable().selector(AbstractHorse.PARENT_HORSE_SELECTOR);
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.WHEAT, Items.SUGAR, Blocks.HAY_BLOCK.asItem(), Items.APPLE, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(AbstractHorse.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<UUID>> DATA_ID_OWNER_UUID = SynchedEntityData.defineId(AbstractHorse.class, EntityDataSerializers.OPTIONAL_UUID);
    private int eatingCounter;
    private int mouthCounter;
    private int standCounter;
    public int tailCounter;
    public int sprintCounter;
    protected boolean isJumping;
    public SimpleContainer inventory;
    protected int temper;
    protected float playerJumpPendingScale;
    private boolean allowStandSliding;
    private float eatAnim;
    private float eatAnimO;
    private float standAnim;
    private float standAnimO;
    private float mouthAnim;
    private float mouthAnimO;
    protected boolean canGallop = true;
    protected int gallopSoundCounter;
    public int maxDomestication = 100; // CraftBukkit - store max domestication value

    protected AbstractHorse(EntityType<? extends AbstractHorse> entitytypes, Level world) {
        super(entitytypes, world);
        this.maxUpStep = 1.0F;
        this.createInventory();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.2D));
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D, AbstractHorse.class));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(AbstractHorse.DATA_ID_FLAGS, (byte) 0);
        this.entityData.register(AbstractHorse.DATA_ID_OWNER_UUID, Optional.empty());
    }

    protected boolean getFlag(int i) {
        return ((Byte) this.entityData.get(AbstractHorse.DATA_ID_FLAGS) & i) != 0;
    }

    protected void setFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(AbstractHorse.DATA_ID_FLAGS);

        if (flag) {
            this.entityData.set(AbstractHorse.DATA_ID_FLAGS, (byte) (b0 | i));
        } else {
            this.entityData.set(AbstractHorse.DATA_ID_FLAGS, (byte) (b0 & ~i));
        }

    }

    public boolean isTamed() {
        return this.getFlag(2);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return (UUID) ((Optional) this.entityData.get(AbstractHorse.DATA_ID_OWNER_UUID)).orElse((Object) null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(AbstractHorse.DATA_ID_OWNER_UUID, Optional.ofNullable(uuid));
    }

    public boolean isJumping() {
        return this.isJumping;
    }

    public void setTamed(boolean flag) {
        this.setFlag(2, flag);
    }

    public void setIsJumping(boolean flag) {
        this.isJumping = flag;
    }

    @Override
    protected void onLeashDistance(float f) {
        if (f > 6.0F && this.isEating()) {
            this.setEating(false);
        }

    }

    public boolean isEating() {
        return this.getFlag(16);
    }

    public boolean isStanding() {
        return this.getFlag(32);
    }

    public boolean isBred() {
        return this.getFlag(8);
    }

    public void setBred(boolean flag) {
        this.setFlag(8, flag);
    }

    @Override
    public boolean isSaddleable() {
        return this.isAlive() && !this.isBaby() && this.isTamed();
    }

    @Override
    public void equipSaddle(@Nullable SoundSource soundcategory) {
        this.inventory.setItem(0, new ItemStack(Items.SADDLE));
        if (soundcategory != null) {
            this.level.playSound((Player) null, (Entity) this, SoundEvents.HORSE_SADDLE, soundcategory, 0.5F, 1.0F);
        }

    }

    @Override
    public boolean isSaddled() {
        return this.getFlag(4);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int i) {
        this.temper = i;
    }

    public int modifyTemper(int i) {
        int j = Mth.clamp(this.getTemper() + i, 0, this.getMaxTemper());

        this.setTemper(j);
        return j;
    }

    @Override
    public boolean isPushable() {
        return !this.isVehicle();
    }

    private void eating() {
        this.openMouth();
        if (!this.isSilent()) {
            SoundEvent soundeffect = this.getEatingSound();

            if (soundeffect != null) {
                this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        if (f > 1.0F) {
            this.playSound(SoundEvents.HORSE_LAND, 0.4F, 1.0F);
        }

        int i = this.calculateFallDamage(f, f1);

        if (i <= 0) {
            return false;
        } else {
            this.hurt(DamageSource.FALL, (float) i);
            if (this.isVehicle()) {
                Iterator iterator = this.getIndirectPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();

                    entity.hurt(DamageSource.FALL, (float) i);
                }
            }

            this.playBlockFallSound();
            return true;
        }
    }

    @Override
    protected int calculateFallDamage(float f, float f1) {
        return Mth.ceil((f * 0.5F - 3.0F) * f1);
    }

    protected int getInventorySize() {
        return 2;
    }

    public void createInventory() {
        SimpleContainer inventorysubcontainer = this.inventory;

        this.inventory = new SimpleContainer(this.getInventorySize(), (org.bukkit.entity.AbstractHorse) this.getBukkitEntity()); // CraftBukkit
        if (inventorysubcontainer != null) {
            inventorysubcontainer.removeListener((ContainerListener) this);
            int i = Math.min(inventorysubcontainer.getContainerSize(), this.inventory.getContainerSize());

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = inventorysubcontainer.getItem(j);

                if (!itemstack.isEmpty()) {
                    this.inventory.setItem(j, itemstack.copy());
                }
            }
        }

        this.inventory.addListener((ContainerListener) this);
        this.updateContainerEquipment();
    }

    protected void updateContainerEquipment() {
        if (!this.level.isClientSide) {
            this.setFlag(4, !this.inventory.getItem(0).isEmpty());
        }
    }

    @Override
    public void containerChanged(Container iinventory) {
        boolean flag = this.isSaddled();

        this.updateContainerEquipment();
        if (this.tickCount > 20 && !flag && this.isSaddled()) {
            this.playSound(SoundEvents.HORSE_SADDLE, 0.5F, 1.0F);
        }

    }

    public double getCustomJump() {
        return this.getAttributeValue(Attributes.JUMP_STRENGTH);
    }

    @Nullable
    protected SoundEvent getEatingSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        if (this.random.nextInt(3) == 0) {
            this.stand();
        }

        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        if (this.random.nextInt(10) == 0 && !this.isImmobile()) {
            this.stand();
        }

        return null;
    }

    @Nullable
    protected SoundEvent getSoundAngry() {
        this.stand();
        return null;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        if (!iblockdata.getMaterial().isLiquid()) {
            BlockState iblockdata1 = this.level.getType(blockposition.above());
            SoundType soundeffecttype = iblockdata.getStepSound();

            if (iblockdata1.is(Blocks.SNOW)) {
                soundeffecttype = iblockdata1.getStepSound();
            }

            if (this.isVehicle() && this.canGallop) {
                ++this.gallopSoundCounter;
                if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                    this.playGallopSound(soundeffecttype);
                } else if (this.gallopSoundCounter <= 5) {
                    this.playSound(SoundEvents.HORSE_STEP_WOOD, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
                }
            } else if (soundeffecttype == SoundType.WOOD) {
                this.playSound(SoundEvents.HORSE_STEP_WOOD, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
            } else {
                this.playSound(SoundEvents.HORSE_STEP, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
            }

        }
    }

    protected void playGallopSound(SoundType soundeffecttype) {
        this.playSound(SoundEvents.HORSE_GALLOP, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
    }

    public static AttributeSupplier.Builder fj() {
        return Mob.p().a(Attributes.JUMP_STRENGTH).a(Attributes.MAX_HEALTH, 53.0D).a(Attributes.MOVEMENT_SPEED, 0.22499999403953552D);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 6;
    }

    public int getMaxTemper() {
        return this.maxDomestication; // CraftBukkit - return stored max domestication instead of 100
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    public void openInventory(Player entityhuman) {
        if (!this.level.isClientSide && (!this.isVehicle() || this.hasPassenger(entityhuman)) && this.isTamed()) {
            entityhuman.openHorseInventory(this, this.inventory);
        }

    }

    public InteractionResult fedFood(Player entityhuman, ItemStack itemstack) {
        boolean flag = this.handleEating(entityhuman, itemstack);

        if (!entityhuman.abilities.instabuild) {
            itemstack.shrink(1);
        }

        return this.level.isClientSide ? InteractionResult.CONSUME : (flag ? InteractionResult.SUCCESS : InteractionResult.PASS);
    }

    protected boolean handleEating(Player entityhuman, ItemStack itemstack) {
        boolean flag = false;
        float f = 0.0F;
        short short0 = 0;
        byte b0 = 0;
        Item item = itemstack.getItem();

        if (item == Items.WHEAT) {
            f = 2.0F;
            short0 = 20;
            b0 = 3;
        } else if (item == Items.SUGAR) {
            f = 1.0F;
            short0 = 30;
            b0 = 3;
        } else if (item == Blocks.HAY_BLOCK.asItem()) {
            f = 20.0F;
            short0 = 180;
        } else if (item == Items.APPLE) {
            f = 3.0F;
            short0 = 60;
            b0 = 3;
        } else if (item == Items.GOLDEN_CARROT) {
            f = 4.0F;
            short0 = 60;
            b0 = 5;
            if (!this.level.isClientSide && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(entityhuman);
            }
        } else if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            f = 10.0F;
            short0 = 240;
            b0 = 10;
            if (!this.level.isClientSide && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(entityhuman);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f, RegainReason.EATING); // CraftBukkit
            flag = true;
        }

        if (this.isBaby() && short0 > 0) {
            this.level.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.level.isClientSide) {
                this.ageUp(short0);
            }

            flag = true;
        }

        if (b0 > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper()) {
            flag = true;
            if (!this.level.isClientSide) {
                this.modifyTemper(b0);
            }
        }

        if (flag) {
            this.eating();
        }

        return flag;
    }

    protected void doPlayerRide(Player entityhuman) {
        this.setEating(false);
        this.setStanding(false);
        if (!this.level.isClientSide) {
            entityhuman.yRot = this.yRot;
            entityhuman.xRot = this.xRot;
            entityhuman.startRiding(this);
        }

    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() && this.isVehicle() && this.isSaddled() || this.isEating() || this.isStanding();
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return AbstractHorse.FOOD_ITEMS.test(itemstack);
    }

    private void moveTail() {
        this.tailCounter = 1;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);

                if (!itemstack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemstack)) {
                    this.spawnAtLocation(itemstack);
                }
            }

        }
    }

    @Override
    public void aiStep() {
        if (this.random.nextInt(200) == 0) {
            this.moveTail();
        }

        super.aiStep();
        if (!this.level.isClientSide && this.isAlive()) {
            if (this.random.nextInt(900) == 0 && this.deathTime == 0) {
                this.heal(1.0F, RegainReason.REGEN); // CraftBukkit
            }

            if (this.canEatGrass()) {
                if (!this.isEating() && !this.isVehicle() && this.random.nextInt(300) == 0 && this.level.getType(this.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
                    this.setEating(true);
                }

                if (this.isEating() && ++this.eatingCounter > 50) {
                    this.eatingCounter = 0;
                    this.setEating(false);
                }
            }

            this.followMommy();
        }
    }

    protected void followMommy() {
        if (this.isBred() && this.isBaby() && !this.isEating()) {
            LivingEntity entityliving = this.level.getNearestEntity(AbstractHorse.class, AbstractHorse.MOMMY_TARGETING, this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(16.0D));

            if (entityliving != null && this.distanceToSqr((Entity) entityliving) > 4.0D) {
                this.navigation.createPath((Entity) entityliving, 0);
            }
        }

    }

    public boolean canEatGrass() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mouthCounter > 0 && ++this.mouthCounter > 30) {
            this.mouthCounter = 0;
            this.setFlag(64, false);
        }

        if ((this.isControlledByLocalInstance() || this.isEffectiveAi()) && this.standCounter > 0 && ++this.standCounter > 20) {
            this.standCounter = 0;
            this.setStanding(false);
        }

        if (this.tailCounter > 0 && ++this.tailCounter > 8) {
            this.tailCounter = 0;
        }

        if (this.sprintCounter > 0) {
            ++this.sprintCounter;
            if (this.sprintCounter > 300) {
                this.sprintCounter = 0;
            }
        }

        this.eatAnimO = this.eatAnim;
        if (this.isEating()) {
            this.eatAnim += (1.0F - this.eatAnim) * 0.4F + 0.05F;
            if (this.eatAnim > 1.0F) {
                this.eatAnim = 1.0F;
            }
        } else {
            this.eatAnim += (0.0F - this.eatAnim) * 0.4F - 0.05F;
            if (this.eatAnim < 0.0F) {
                this.eatAnim = 0.0F;
            }
        }

        this.standAnimO = this.standAnim;
        if (this.isStanding()) {
            this.eatAnim = 0.0F;
            this.eatAnimO = this.eatAnim;
            this.standAnim += (1.0F - this.standAnim) * 0.4F + 0.05F;
            if (this.standAnim > 1.0F) {
                this.standAnim = 1.0F;
            }
        } else {
            this.allowStandSliding = false;
            this.standAnim += (0.8F * this.standAnim * this.standAnim * this.standAnim - this.standAnim) * 0.6F - 0.05F;
            if (this.standAnim < 0.0F) {
                this.standAnim = 0.0F;
            }
        }

        this.mouthAnimO = this.mouthAnim;
        if (this.getFlag(64)) {
            this.mouthAnim += (1.0F - this.mouthAnim) * 0.7F + 0.05F;
            if (this.mouthAnim > 1.0F) {
                this.mouthAnim = 1.0F;
            }
        } else {
            this.mouthAnim += (0.0F - this.mouthAnim) * 0.7F - 0.05F;
            if (this.mouthAnim < 0.0F) {
                this.mouthAnim = 0.0F;
            }
        }

    }

    private void openMouth() {
        if (!this.level.isClientSide) {
            this.mouthCounter = 1;
            this.setFlag(64, true);
        }

    }

    public void setEating(boolean flag) {
        this.setFlag(16, flag);
    }

    public void setStanding(boolean flag) {
        if (flag) {
            this.setEating(false);
        }

        this.setFlag(32, flag);
    }

    private void stand() {
        if (this.isControlledByLocalInstance() || this.isEffectiveAi()) {
            this.standCounter = 1;
            this.setStanding(true);
        }

    }

    public void makeMad() {
        if (!this.isStanding()) {
            this.stand();
            SoundEvent soundeffect = this.getSoundAngry();

            if (soundeffect != null) {
                this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
            }
        }

    }

    public boolean tameWithName(Player entityhuman) {
        this.setOwnerUUID(entityhuman.getUUID());
        this.setTamed(true);
        if (entityhuman instanceof ServerPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer) entityhuman, (Animal) this);
        }

        this.level.broadcastEntityEvent(this, (byte) 7);
        return true;
    }

    @Override
    public void travel(Vec3 vec3d) {
        if (this.isAlive()) {
            if (this.isVehicle() && this.canBeControlledByRider() && this.isSaddled()) {
                LivingEntity entityliving = (LivingEntity) this.getControllingPassenger();

                this.yRot = entityliving.yRot;
                this.yRotO = this.yRot;
                this.xRot = entityliving.xRot * 0.5F;
                this.setRot(this.yRot, this.xRot);
                this.yBodyRot = this.yRot;
                this.yHeadRot = this.yBodyRot;
                float f = entityliving.xxa * 0.5F;
                float f1 = entityliving.zza;

                if (f1 <= 0.0F) {
                    f1 *= 0.25F;
                    this.gallopSoundCounter = 0;
                }

                if (this.onGround && this.playerJumpPendingScale == 0.0F && this.isStanding() && !this.allowStandSliding) {
                    f = 0.0F;
                    f1 = 0.0F;
                }

                if (this.playerJumpPendingScale > 0.0F && !this.isJumping() && this.onGround) {
                    double d0 = this.getCustomJump() * (double) this.playerJumpPendingScale * (double) this.getBlockJumpFactor();
                    double d1;

                    if (this.hasEffect(MobEffects.JUMP)) {
                        d1 = d0 + (double) ((float) (this.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 0.1F);
                    } else {
                        d1 = d0;
                    }

                    Vec3 vec3d1 = this.getDeltaMovement();

                    this.setDeltaMovement(vec3d1.x, d1, vec3d1.z);
                    this.setIsJumping(true);
                    this.hasImpulse = true;
                    if (f1 > 0.0F) {
                        float f2 = Mth.sin(this.yRot * 0.017453292F);
                        float f3 = Mth.cos(this.yRot * 0.017453292F);

                        this.setDeltaMovement(this.getDeltaMovement().add((double) (-0.4F * f2 * this.playerJumpPendingScale), 0.0D, (double) (0.4F * f3 * this.playerJumpPendingScale)));
                    }

                    this.playerJumpPendingScale = 0.0F;
                }

                this.flyingSpeed = this.getSpeed() * 0.1F;
                if (this.isControlledByLocalInstance()) {
                    this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    super.travel(new Vec3((double) f, vec3d.y, (double) f1));
                } else if (entityliving instanceof Player) {
                    this.setDeltaMovement(Vec3.ZERO);
                }

                if (this.onGround) {
                    this.playerJumpPendingScale = 0.0F;
                    this.setIsJumping(false);
                }

                this.calculateEntityAnimation((LivingEntity) this, false);
            } else {
                this.flyingSpeed = 0.02F;
                super.travel(vec3d);
            }
        }
    }

    protected void playJumpSound() {
        this.playSound(SoundEvents.HORSE_JUMP, 0.4F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("EatingHaystack", this.isEating());
        nbttagcompound.putBoolean("Bred", this.isBred());
        nbttagcompound.putInt("Temper", this.getTemper());
        nbttagcompound.putBoolean("Tame", this.isTamed());
        if (this.getOwnerUUID() != null) {
            nbttagcompound.putUUID("Owner", this.getOwnerUUID());
        }
        nbttagcompound.putInt("Bukkit.MaxDomestication", this.maxDomestication); // CraftBukkit

        if (!this.inventory.getItem(0).isEmpty()) {
            nbttagcompound.put("SaddleItem", this.inventory.getItem(0).save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setEating(nbttagcompound.getBoolean("EatingHaystack"));
        this.setBred(nbttagcompound.getBoolean("Bred"));
        this.setTemper(nbttagcompound.getInt("Temper"));
        this.setTamed(nbttagcompound.getBoolean("Tame"));
        UUID uuid;

        if (nbttagcompound.hasUUID("Owner")) {
            uuid = nbttagcompound.getUUID("Owner");
        } else {
            String s = nbttagcompound.getString("Owner");

            uuid = OldUsersConverter.convertMobOwnerIfNecessary(this.getServer(), s);
        }

        if (uuid != null) {
            this.setOwnerUUID(uuid);
        }
        // CraftBukkit start
        if (nbttagcompound.contains("Bukkit.MaxDomestication")) {
            this.maxDomestication = nbttagcompound.getInt("Bukkit.MaxDomestication");
        }
        // CraftBukkit end

        if (nbttagcompound.contains("SaddleItem", 10)) {
            ItemStack itemstack = ItemStack.of(nbttagcompound.getCompound("SaddleItem"));

            if (itemstack.getItem() == Items.SADDLE) {
                this.inventory.setItem(0, itemstack);
            }
        }

        this.updateContainerEquipment();
    }

    @Override
    public boolean canMate(Animal entityanimal) {
        return false;
    }

    protected boolean canParent() {
        return !this.isVehicle() && !this.isPassenger() && this.isTamed() && !this.isBaby() && this.getHealth() >= this.getMaxHealth() && this.isInLove();
    }

    @Nullable
    @Override
    public AgableMob getBreedOffspring(AgableMob entityageable) {
        return null;
    }

    protected void setOffspringAttributes(AgableMob entityageable, AbstractHorse entityhorseabstract) {
        double d0 = this.getAttributeBaseValue(Attributes.MAX_HEALTH) + entityageable.getAttributeBaseValue(Attributes.MAX_HEALTH) + (double) this.generateRandomMaxHealth();

        entityhorseabstract.getAttribute(Attributes.MAX_HEALTH).setBaseValue(d0 / 3.0D);
        double d1 = this.getAttributeBaseValue(Attributes.JUMP_STRENGTH) + entityageable.getAttributeBaseValue(Attributes.JUMP_STRENGTH) + this.generateRandomJumpStrength();

        entityhorseabstract.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(d1 / 3.0D);
        double d2 = this.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) + entityageable.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) + this.generateRandomSpeed();

        entityhorseabstract.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(d2 / 3.0D);
    }

    @Override
    public boolean canBeControlledByRider() {
        return this.getControllingPassenger() instanceof LivingEntity;
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void handleStartJump(int i) {
        // CraftBukkit start
        float power;
        if (i >= 90) {
            power = 1.0F;
        } else {
            power = 0.4F + 0.4F * (float) i / 90.0F;
        }
        org.bukkit.event.entity.HorseJumpEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callHorseJumpEvent(this, power);
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end
        this.allowStandSliding = true;
        this.stand();
        this.playJumpSound();
    }

    @Override
    public void handleStopJump() {}

    @Override
    public void positionRider(Entity entity) {
        super.positionRider(entity);
        if (entity instanceof Mob) {
            Mob entityinsentient = (Mob) entity;

            this.yBodyRot = entityinsentient.yBodyRot;
        }

        if (this.standAnimO > 0.0F) {
            float f = Mth.sin(this.yBodyRot * 0.017453292F);
            float f1 = Mth.cos(this.yBodyRot * 0.017453292F);
            float f2 = 0.7F * this.standAnimO;
            float f3 = 0.15F * this.standAnimO;

            entity.setPos(this.getX() + (double) (f2 * f), this.getY() + this.getPassengersRidingOffset() + entity.getMyRidingOffset() + (double) f3, this.getZ() - (double) (f2 * f1));
            if (entity instanceof LivingEntity) {
                ((LivingEntity) entity).yBodyRot = this.yBodyRot;
            }
        }

    }

    protected float generateRandomMaxHealth() {
        return 15.0F + (float) this.random.nextInt(8) + (float) this.random.nextInt(9);
    }

    protected double generateRandomJumpStrength() {
        return 0.4000000059604645D + this.random.nextDouble() * 0.2D + this.random.nextDouble() * 0.2D + this.random.nextDouble() * 0.2D;
    }

    protected double generateRandomSpeed() {
        return (0.44999998807907104D + this.random.nextDouble() * 0.3D + this.random.nextDouble() * 0.3D + this.random.nextDouble() * 0.3D) * 0.25D;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * 0.95F;
    }

    public boolean canWearArmor() {
        return false;
    }

    public boolean isWearingArmor() {
        return !this.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
    }

    public boolean isArmor(ItemStack itemstack) {
        return false;
    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        int j = i - 400;

        if (j >= 0 && j < 2 && j < this.inventory.getContainerSize()) {
            if (j == 0 && itemstack.getItem() != Items.SADDLE) {
                return false;
            } else if (j == 1 && (!this.canWearArmor() || !this.isArmor(itemstack))) {
                return false;
            } else {
                this.inventory.setItem(j, itemstack);
                this.updateContainerEquipment();
                return true;
            }
        } else {
            int k = i - 500 + 2;

            if (k >= 2 && k < this.inventory.getContainerSize()) {
                this.inventory.setItem(k, itemstack);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getControllingPassenger() {
        return this.getPassengers().isEmpty() ? null : (Entity) this.getPassengers().get(0);
    }

    @Nullable
    private Vec3 getDismountLocationInDirection(Vec3 vec3d, LivingEntity entityliving) {
        double d0 = this.getX() + vec3d.x;
        double d1 = this.getBoundingBox().minY;
        double d2 = this.getZ() + vec3d.z;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        UnmodifiableIterator unmodifiableiterator = entityliving.getDismountPoses().iterator();

        while (unmodifiableiterator.hasNext()) {
            Pose entitypose = (Pose) unmodifiableiterator.next();

            blockposition_mutableblockposition.c(d0, d1, d2);
            double d3 = this.getBoundingBox().maxY + 0.75D;

            while (true) {
                double d4 = this.level.getRelativeFloorHeight(blockposition_mutableblockposition);

                if ((double) blockposition_mutableblockposition.getY() + d4 > d3) {
                    break;
                }

                if (DismountHelper.isFloorValid(d4)) {
                    AABB axisalignedbb = entityliving.getLocalBoundsForPose(entitypose);
                    Vec3 vec3d1 = new Vec3(d0, (double) blockposition_mutableblockposition.getY() + d4, d2);

                    if (DismountHelper.canDismountTo(this.level, entityliving, axisalignedbb.move(vec3d1))) {
                        entityliving.setPose(entitypose);
                        return vec3d1;
                    }
                }

                blockposition_mutableblockposition.c(Direction.UP);
                if ((double) blockposition_mutableblockposition.getY() >= d3) {
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity entityliving) {
        Vec3 vec3d = getCollisionHorizontalEscapeVector((double) this.getBbWidth(), (double) entityliving.getBbWidth(), this.yRot + (entityliving.getMainArm() == HumanoidArm.RIGHT ? 90.0F : -90.0F));
        Vec3 vec3d1 = this.getDismountLocationInDirection(vec3d, entityliving);

        if (vec3d1 != null) {
            return vec3d1;
        } else {
            Vec3 vec3d2 = getCollisionHorizontalEscapeVector((double) this.getBbWidth(), (double) entityliving.getBbWidth(), this.yRot + (entityliving.getMainArm() == HumanoidArm.LEFT ? 90.0F : -90.0F));
            Vec3 vec3d3 = this.getDismountLocationInDirection(vec3d2, entityliving);

            return vec3d3 != null ? vec3d3 : this.position();
        }
    }

    protected void randomizeAttributes() {}

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
            ((AgableMob.AgableMobGroupData) groupdataentity).setBabySpawnChance(0.2F);
        }

        this.randomizeAttributes();
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }
}
