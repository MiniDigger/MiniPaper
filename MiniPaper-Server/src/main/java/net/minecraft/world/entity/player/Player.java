package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;
// CraftBukkit end

public abstract class Player extends LivingEntity {

    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F);
    // CraftBukkit - decompile error
    private static final Map<Pose, EntityDimensions> POSES = ImmutableMap.<Pose, EntityDimensions>builder().put(Pose.STANDING, net.minecraft.world.entity.player.Player.STANDING_DIMENSIONS).put(Pose.SLEEPING, net.minecraft.world.entity.player.Player.SLEEPING_DIMENSIONS).put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6F, 0.6F)).put(Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F)).put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6F, 0.6F)).put(Pose.CROUCHING, EntityDimensions.scalable(0.6F, 1.5F)).put(Pose.DYING, EntityDimensions.fixed(0.2F, 0.2F)).build();
    private static final EntityDataAccessor<Float> DATA_PLAYER_ABSORPTION_ID = SynchedEntityData.defineId(net.minecraft.world.entity.player.Player.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SCORE_ID = SynchedEntityData.defineId(net.minecraft.world.entity.player.Player.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION = SynchedEntityData.defineId(net.minecraft.world.entity.player.Player.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_PLAYER_MAIN_HAND = SynchedEntityData.defineId(net.minecraft.world.entity.player.Player.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<CompoundTag> DATA_SHOULDER_LEFT = SynchedEntityData.defineId(net.minecraft.world.entity.player.Player.class, EntityDataSerializers.COMPOUND_TAG);
    protected static final EntityDataAccessor<CompoundTag> DATA_SHOULDER_RIGHT = SynchedEntityData.defineId(net.minecraft.world.entity.player.Player.class, EntityDataSerializers.COMPOUND_TAG);
    private long timeEntitySatOnShoulder;
    public final Inventory inventory = new Inventory(this);
    protected PlayerEnderChestContainer enderChestInventory = new PlayerEnderChestContainer(this); // CraftBukkit - add "this" to constructor
    public final InventoryMenu inventoryMenu;
    public AbstractContainerMenu containerMenu;
    protected FoodData foodData = new FoodData(this); // CraftBukkit - add "this" to constructor
    protected int jumpTriggerTime;
    public float oBob;
    public float bob;
    public int takeXpDelay;
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public int sleepCounter;
    protected boolean wasUnderwater;
    public final Abilities abilities = new Abilities();
    public int experienceLevel;
    public int totalExperience;
    public float experienceProgress;
    protected int enchantmentSeed;
    protected final float defaultFlySpeed = 0.02F;
    private int lastLevelUpTime;
    private final GameProfile gameProfile;
    private ItemStack lastItemInMainHand;
    private final ItemCooldowns cooldowns;
    @Nullable
    public FishingHook fishing;

    // CraftBukkit start
    public boolean fauxSleeping;
    public int oldLevel = -1;

    @Override
    public CraftHumanEntity getBukkitEntity() {
        return (CraftHumanEntity) super.getBukkitEntity();
    }
    // CraftBukkit end

    public Player(Level world, BlockPos blockposition, GameProfile gameprofile) {
        super(EntityType.PLAYER, world);
        this.lastItemInMainHand = ItemStack.EMPTY;
        this.cooldowns = this.createItemCooldowns();
        this.setUUID(createPlayerUUID(gameprofile));
        this.gameProfile = gameprofile;
        this.inventoryMenu = new InventoryMenu(this.inventory, !world.isClientSide, this);
        this.containerMenu = this.inventoryMenu;
        this.moveTo((double) blockposition.getX() + 0.5D, (double) (blockposition.getY() + 1), (double) blockposition.getZ() + 0.5D, 0.0F, 0.0F);
        this.rotOffs = 180.0F;
    }

    public boolean blockActionRestricted(Level world, BlockPos blockposition, GameType enumgamemode) {
        if (!enumgamemode.isBlockPlacingRestricted()) {
            return false;
        } else if (enumgamemode == GameType.SPECTATOR) {
            return true;
        } else if (this.mayBuild()) {
            return false;
        } else {
            ItemStack itemstack = this.getMainHandItem();

            return itemstack.isEmpty() || !itemstack.hasAdventureModeBreakTagForBlock(world.getTagManager(), new BlockInWorld(world, blockposition, false));
        }
    }

    public static AttributeSupplier.Builder eo() {
        return LivingEntity.cK().a(Attributes.ATTACK_DAMAGE, 1.0D).a(Attributes.MOVEMENT_SPEED, 0.10000000149011612D).a(Attributes.ATTACK_SPEED).a(Attributes.LUCK);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(net.minecraft.world.entity.player.Player.DATA_PLAYER_ABSORPTION_ID, 0.0F);
        this.entityData.register(net.minecraft.world.entity.player.Player.DATA_SCORE_ID, 0);
        this.entityData.register(net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0);
        this.entityData.register(net.minecraft.world.entity.player.Player.DATA_PLAYER_MAIN_HAND, (byte) 1);
        this.entityData.register(net.minecraft.world.entity.player.Player.DATA_SHOULDER_LEFT, new CompoundTag());
        this.entityData.register(net.minecraft.world.entity.player.Player.DATA_SHOULDER_RIGHT, new CompoundTag());
    }

    @Override
    public void tick() {
        this.noPhysics = this.isSpectator();
        if (this.isSpectator()) {
            this.onGround = false;
        }

        if (this.takeXpDelay > 0) {
            --this.takeXpDelay;
        }

        if (this.isSleeping()) {
            ++this.sleepCounter;
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
            }

            if (!this.level.isClientSide && this.level.isDay()) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            ++this.sleepCounter;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();
        super.tick();
        if (!this.level.isClientSide && this.containerMenu != null && !this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        this.moveCloak();
        if (!this.level.isClientSide) {
            this.foodData.tick(this);
            this.awardStat(Stats.PLAY_ONE_MINUTE);
            if (this.isAlive()) {
                this.awardStat(Stats.TIME_SINCE_DEATH);
            }

            if (this.isDiscrete()) {
                this.awardStat(Stats.CROUCH_TIME);
            }

            if (!this.isSleeping()) {
                this.awardStat(Stats.TIME_SINCE_REST);
            }
        }

        int i = 29999999;
        double d0 = Mth.clamp(this.getX(), -2.9999999E7D, 2.9999999E7D);
        double d1 = Mth.clamp(this.getZ(), -2.9999999E7D, 2.9999999E7D);

        if (d0 != this.getX() || d1 != this.getZ()) {
            this.setPos(d0, this.getY(), d1);
        }

        ++this.attackStrengthTicker;
        ItemStack itemstack = this.getMainHandItem();

        if (!ItemStack.matches(this.lastItemInMainHand, itemstack)) {
            if (!ItemStack.isSameIgnoreDurability(this.lastItemInMainHand, itemstack)) {
                this.resetAttackStrengthTicker();
            }

            this.lastItemInMainHand = itemstack.copy();
        }

        this.turtleHelmetTick();
        this.cooldowns.tick();
        this.updatePlayerPose();
    }

    public boolean isSecondaryUseActive() {
        return this.isShiftKeyDown();
    }

    protected boolean wantsToStopRiding() {
        return this.isShiftKeyDown();
    }

    protected boolean isStayingOnGroundSurface() {
        return this.isShiftKeyDown();
    }

    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid((Tag) FluidTags.WATER);
        return this.wasUnderwater;
    }

    private void turtleHelmetTick() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);

        if (itemstack.getItem() == Items.TURTLE_HELMET && !this.isEyeInFluid((Tag) FluidTags.WATER)) {
            this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TURTLE_HELMET); // CraftBukkit
        }

    }

    protected ItemCooldowns createItemCooldowns() {
        return new ItemCooldowns();
    }

    private void moveCloak() {
        this.xCloakO = this.xCloak;
        this.yCloakO = this.yCloak;
        this.zCloakO = this.zCloak;
        double d0 = this.getX() - this.xCloak;
        double d1 = this.getY() - this.yCloak;
        double d2 = this.getZ() - this.zCloak;
        double d3 = 10.0D;

        if (d0 > 10.0D) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 > 10.0D) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 > 10.0D) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        if (d0 < -10.0D) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 < -10.0D) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 < -10.0D) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        this.xCloak += d0 * 0.25D;
        this.zCloak += d2 * 0.25D;
        this.yCloak += d1 * 0.25D;
    }

    protected void updatePlayerPose() {
        if (this.canEnterPose(Pose.SWIMMING)) {
            Pose entitypose;

            if (this.isFallFlying()) {
                entitypose = Pose.FALL_FLYING;
            } else if (this.isSleeping()) {
                entitypose = Pose.SLEEPING;
            } else if (this.isSwimming()) {
                entitypose = Pose.SWIMMING;
            } else if (this.isAutoSpinAttack()) {
                entitypose = Pose.SPIN_ATTACK;
            } else if (this.isShiftKeyDown() && !this.abilities.flying) {
                entitypose = Pose.CROUCHING;
            } else {
                entitypose = Pose.STANDING;
            }

            Pose entitypose1;

            if (!this.isSpectator() && !this.isPassenger() && !this.canEnterPose(entitypose)) {
                if (this.canEnterPose(Pose.CROUCHING)) {
                    entitypose1 = Pose.CROUCHING;
                } else {
                    entitypose1 = Pose.SWIMMING;
                }
            } else {
                entitypose1 = entitypose;
            }

            this.setPose(entitypose1);
        }
    }

    @Override
    public int getPortalWaitTime() {
        return this.abilities.invulnerable ? 1 : 80;
    }

    @Override
    protected SoundEvent getSoundSwim() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSoundSplash() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSoundSplashHighSpeed() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void playSound(SoundEvent soundeffect, float f, float f1) {
        this.level.playSound(this, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), f, f1);
    }

    public void playNotifySound(SoundEvent soundeffect, SoundSource soundcategory, float f, float f1) {}

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public int getFireImmuneTicks() {
        return 20;
    }

    public void closeContainer() {
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    public void rideTick() {
        if (this.wantsToStopRiding() && this.isPassenger()) {
            this.stopRiding();
            this.setShiftKeyDown(false);
        } else {
            double d0 = this.getX();
            double d1 = this.getY();
            double d2 = this.getZ();

            super.rideTick();
            this.oBob = this.bob;
            this.bob = 0.0F;
            this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
        }
    }

    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        this.updateSwingTime();
        this.yHeadRot = this.yRot;
    }

    @Override
    public void aiStep() {
        if (this.jumpTriggerTime > 0) {
            --this.jumpTriggerTime;
        }

        if (this.level.getDifficulty() == Difficulty.PEACEFUL && this.level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            if (this.getHealth() < this.getMaxHealth() && this.tickCount % 20 == 0) {
                // CraftBukkit - added regain reason of "REGEN" for filtering purposes.
                this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN);
            }

            if (this.foodData.needsFood() && this.tickCount % 10 == 0) {
                this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
            }
        }

        this.inventory.tick();
        this.oBob = this.bob;
        super.aiStep();
        this.flyingSpeed = 0.02F;
        if (this.isSprinting()) {
            this.flyingSpeed = (float) ((double) this.flyingSpeed + 0.005999999865889549D);
        }

        this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
        float f;

        if (this.onGround && !this.isDeadOrDying() && !this.isSwimming()) {
            f = Math.min(0.1F, Mth.sqrt(getHorizontalDistanceSqr(this.getDeltaMovement())));
        } else {
            f = 0.0F;
        }

        this.bob += (f - this.bob) * 0.4F;
        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            AABB axisalignedbb;

            if (this.isPassenger() && !this.getVehicle().removed) {
                axisalignedbb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0D, 0.0D, 1.0D);
            } else {
                axisalignedbb = this.getBoundingBox().inflate(1.0D, 0.5D, 1.0D);
            }

            List<Entity> list = this.level.getEntities(this, axisalignedbb);

            for (int i = 0; i < list.size(); ++i) {
                Entity entity = (Entity) list.get(i);

                if (!entity.removed) {
                    this.touch(entity);
                }
            }
        }

        this.playShoulderEntityAmbientSound(this.getShoulderEntityLeft());
        this.playShoulderEntityAmbientSound(this.getShoulderEntityRight());
        if (!this.level.isClientSide && (this.fallDistance > 0.5F || this.isInWater()) || this.abilities.flying || this.isSleeping()) {
            this.removeEntitiesOnShoulder();
        }

    }

    private void playShoulderEntityAmbientSound(@Nullable CompoundTag nbttagcompound) {
        if (nbttagcompound != null && (!nbttagcompound.contains("Silent") || !nbttagcompound.getBoolean("Silent")) && this.level.random.nextInt(200) == 0) {
            String s = nbttagcompound.getString("id");

            EntityType.byString(s).filter((entitytypes) -> {
                return entitytypes == EntityType.PARROT;
            }).ifPresent((entitytypes) -> {
                if (!Parrot.imitateNearbyMobs(this.level, (Entity) this)) {
                    this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), Parrot.getAmbient(this.level, this.level.random), this.getSoundSource(), 1.0F, Parrot.getPitch(this.level.random));
                }

            });
        }

    }

    private void touch(Entity entity) {
        entity.playerTouch(this);
    }

    public int getScore() {
        return (Integer) this.entityData.get(net.minecraft.world.entity.player.Player.DATA_SCORE_ID);
    }

    public void setScore(int i) {
        this.entityData.set(net.minecraft.world.entity.player.Player.DATA_SCORE_ID, i);
    }

    public void increaseScore(int i) {
        int j = this.getScore();

        this.entityData.set(net.minecraft.world.entity.player.Player.DATA_SCORE_ID, j + i);
    }

    @Override
    public void die(DamageSource damagesource) {
        super.die(damagesource);
        this.reapplyPosition();
        if (!this.isSpectator()) {
            this.dropAllDeathLoot(damagesource);
        }

        if (damagesource != null) {
            this.setDeltaMovement((double) (-Mth.cos((this.hurtDir + this.yRot) * 0.017453292F) * 0.1F), 0.10000000149011612D, (double) (-Mth.sin((this.hurtDir + this.yRot) * 0.017453292F) * 0.1F));
        } else {
            this.setDeltaMovement(0.0D, 0.1D, 0.0D);
        }

        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlag(0, false);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (!this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            this.destroyVanishingCursedItems();
            this.inventory.dropAll();
        }

    }

    protected void destroyVanishingCursedItems() {
        for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);

            if (!itemstack.isEmpty() && EnchantmentHelper.hasVanishingCurse(itemstack)) {
                this.inventory.removeItemNoUpdate(i);
            }
        }

    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return damagesource == DamageSource.ON_FIRE ? SoundEvents.PLAYER_HURT_ON_FIRE : (damagesource == DamageSource.DROWN ? SoundEvents.PLAYER_HURT_DROWN : (damagesource == DamageSource.SWEET_BERRY_BUSH ? SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH : SoundEvents.PLAYER_HURT));
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.PLAYER_DEATH;
    }

    public boolean drop(boolean flag) {
        return this.drop(this.inventory.removeItem(this.inventory.selected, flag && !this.inventory.getSelected().isEmpty() ? this.inventory.getSelected().getCount() : 1), false, true) != null;
    }

    @Nullable
    public ItemEntity drop(ItemStack itemstack, boolean flag) {
        return this.drop(itemstack, false, flag);
    }

    @Nullable
    public ItemEntity drop(ItemStack itemstack, boolean flag, boolean flag1) {
        if (itemstack.isEmpty()) {
            return null;
        } else {
            if (this.level.isClientSide) {
                this.swing(InteractionHand.MAIN_HAND);
            }

            double d0 = this.getEyeY() - 0.30000001192092896D;
            ItemEntity entityitem = new ItemEntity(this.level, this.getX(), d0, this.getZ(), itemstack);

            entityitem.setPickUpDelay(40);
            if (flag1) {
                entityitem.setThrower(this.getUUID());
            }

            float f;
            float f1;

            if (flag) {
                f = this.random.nextFloat() * 0.5F;
                f1 = this.random.nextFloat() * 6.2831855F;
                entityitem.setDeltaMovement((double) (-Mth.sin(f1) * f), 0.20000000298023224D, (double) (Mth.cos(f1) * f));
            } else {
                f = 0.3F;
                f1 = Mth.sin(this.xRot * 0.017453292F);
                float f2 = Mth.cos(this.xRot * 0.017453292F);
                float f3 = Mth.sin(this.yRot * 0.017453292F);
                float f4 = Mth.cos(this.yRot * 0.017453292F);
                float f5 = this.random.nextFloat() * 6.2831855F;
                float f6 = 0.02F * this.random.nextFloat();

                entityitem.setDeltaMovement((double) (-f3 * f2 * 0.3F) + Math.cos((double) f5) * (double) f6, (double) (-f1 * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F), (double) (f4 * f2 * 0.3F) + Math.sin((double) f5) * (double) f6);
            }

            // CraftBukkit start - fire PlayerDropItemEvent
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) this.getBukkitEntity();
            Item drop = (Item) entityitem.getBukkitEntity();

            PlayerDropItemEvent event = new PlayerDropItemEvent(player, drop);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                org.bukkit.inventory.ItemStack cur = player.getInventory().getItemInHand();
                if (flag1 && (cur == null || cur.getAmount() == 0)) {
                    // The complete stack was dropped
                    player.getInventory().setItemInHand(drop.getItemStack());
                } else if (flag1 && cur.isSimilar(drop.getItemStack()) && cur.getAmount() < cur.getMaxStackSize() && drop.getItemStack().getAmount() == 1) {
                    // Only one item is dropped
                    cur.setAmount(cur.getAmount() + 1);
                    player.getInventory().setItemInHand(cur);
                } else {
                    // Fallback
                    player.getInventory().addItem(drop.getItemStack());
                }
                return null;
            }
            // CraftBukkit end

            return entityitem;
        }
    }

    public float getDestroySpeed(BlockState iblockdata) {
        float f = this.inventory.getDestroySpeed(iblockdata);

        if (f > 1.0F) {
            int i = EnchantmentHelper.getBlockEfficiency(this);
            ItemStack itemstack = this.getMainHandItem();

            if (i > 0 && !itemstack.isEmpty()) {
                f += (float) (i * i + 1);
            }
        }

        if (MobEffectUtil.hasDigSpeed(this)) {
            f *= 1.0F + (float) (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }

        if (this.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            float f1;

            switch (this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0:
                    f1 = 0.3F;
                    break;
                case 1:
                    f1 = 0.09F;
                    break;
                case 2:
                    f1 = 0.0027F;
                    break;
                case 3:
                default:
                    f1 = 8.1E-4F;
            }

            f *= f1;
        }

        if (this.isEyeInFluid((Tag) FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity((LivingEntity) this)) {
            f /= 5.0F;
        }

        if (!this.onGround) {
            f /= 5.0F;
        }

        return f;
    }

    public boolean hasBlock(BlockState iblockdata) {
        return !iblockdata.requiresCorrectToolForDrops() || this.inventory.getSelected().canDestroySpecialBlock(iblockdata);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setUUID(createPlayerUUID(this.gameProfile));
        ListTag nbttaglist = nbttagcompound.getList("Inventory", 10);

        this.inventory.load(nbttaglist);
        this.inventory.selected = nbttagcompound.getInt("SelectedItemSlot");
        this.sleepCounter = nbttagcompound.getShort("SleepTimer");
        this.experienceProgress = nbttagcompound.getFloat("XpP");
        this.experienceLevel = nbttagcompound.getInt("XpLevel");
        this.totalExperience = nbttagcompound.getInt("XpTotal");
        this.enchantmentSeed = nbttagcompound.getInt("XpSeed");
        if (this.enchantmentSeed == 0) {
            this.enchantmentSeed = this.random.nextInt();
        }

        this.setScore(nbttagcompound.getInt("Score"));
        this.foodData.readAdditionalSaveData(nbttagcompound);
        this.abilities.loadSaveData(nbttagcompound);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) this.abilities.getWalkingSpeed());
        if (nbttagcompound.contains("EnderItems", 9)) {
            this.enderChestInventory.fromTag(nbttagcompound.getList("EnderItems", 10));
        }

        if (nbttagcompound.contains("ShoulderEntityLeft", 10)) {
            this.setShoulderEntityLeft(nbttagcompound.getCompound("ShoulderEntityLeft"));
        }

        if (nbttagcompound.contains("ShoulderEntityRight", 10)) {
            this.setShoulderEntityRight(nbttagcompound.getCompound("ShoulderEntityRight"));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        nbttagcompound.put("Inventory", this.inventory.save(new ListTag()));
        nbttagcompound.putInt("SelectedItemSlot", this.inventory.selected);
        nbttagcompound.putShort("SleepTimer", (short) this.sleepCounter);
        nbttagcompound.putFloat("XpP", this.experienceProgress);
        nbttagcompound.putInt("XpLevel", this.experienceLevel);
        nbttagcompound.putInt("XpTotal", this.totalExperience);
        nbttagcompound.putInt("XpSeed", this.enchantmentSeed);
        nbttagcompound.putInt("Score", this.getScore());
        this.foodData.addAdditionalSaveData(nbttagcompound);
        this.abilities.addSaveData(nbttagcompound);
        nbttagcompound.put("EnderItems", this.enderChestInventory.createTag());
        if (!this.getShoulderEntityLeft().isEmpty()) {
            nbttagcompound.put("ShoulderEntityLeft", this.getShoulderEntityLeft());
        }

        if (!this.getShoulderEntityRight().isEmpty()) {
            nbttagcompound.put("ShoulderEntityRight", this.getShoulderEntityRight());
        }

    }

    @Override
    public boolean isInvulnerableTo(DamageSource damagesource) {
        return super.isInvulnerableTo(damagesource) ? true : (damagesource == DamageSource.DROWN ? !this.level.getGameRules().getBoolean(GameRules.RULE_DROWNING_DAMAGE) : (damagesource == DamageSource.FALL ? !this.level.getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE) : (damagesource.isFire() ? !this.level.getGameRules().getBoolean(GameRules.RULE_FIRE_DAMAGE) : false)));
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (this.abilities.invulnerable && !damagesource.isBypassInvul()) {
            this.forceExplosionKnockback = true; // SPIGOT-5258 - Make invulnerable players get knockback from explosions
            return false;
        } else {
            this.noActionTime = 0;
            if (this.isDeadOrDying()) {
                return false;
            } else {
                // this.releaseShoulderEntities(); // CraftBukkit - moved down
                if (damagesource.scalesWithDifficulty()) {
                    if (this.level.getDifficulty() == Difficulty.PEACEFUL) {
                        return false; // CraftBukkit - f = 0.0f -> return false
                    }

                    if (this.level.getDifficulty() == Difficulty.EASY) {
                        f = Math.min(f / 2.0F + 1.0F, f);
                    }

                    if (this.level.getDifficulty() == Difficulty.HARD) {
                        f = f * 3.0F / 2.0F;
                    }
                }

                // CraftBukkit start - Don't filter out 0 damage
                boolean damaged = super.hurt(damagesource, f);
                if (damaged) {
                    this.removeEntitiesOnShoulder();
                }
                return damaged;
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void blockUsingShield(LivingEntity entityliving) {
        super.blockUsingShield(entityliving);
        if (entityliving.getMainHandItem().getItem() instanceof AxeItem) {
            this.disableShield(true);
        }

    }

    public boolean canHarmPlayer(net.minecraft.world.entity.player.Player entityhuman) {
        // CraftBukkit start - Change to check OTHER player's scoreboard team according to API
        // To summarize this method's logic, it's "Can parameter hurt this"
        org.bukkit.scoreboard.Team team;
        if (entityhuman instanceof ServerPlayer) {
            ServerPlayer thatPlayer = (ServerPlayer) entityhuman;
            team = thatPlayer.getBukkitEntity().getScoreboard().getPlayerTeam(thatPlayer.getBukkitEntity());
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        } else {
            // This should never be called, but is implemented anyway
            org.bukkit.OfflinePlayer thisPlayer = entityhuman.level.getServerOH().getOfflinePlayer(entityhuman.getScoreboardName());
            team = entityhuman.level.getServerOH().getScoreboardManager().getMainScoreboard().getPlayerTeam(thisPlayer);
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        }

        if (this instanceof ServerPlayer) {
            return !team.hasPlayer(((ServerPlayer) this).getBukkitEntity());
        }
        return !team.hasPlayer(this.level.getServerOH().getOfflinePlayer(this.getScoreboardName()));
        // CraftBukkit end
    }

    @Override
    protected void hurtArmor(DamageSource damagesource, float f) {
        this.inventory.hurtArmor(damagesource, f);
    }

    @Override
    protected void hurtCurrentlyUsedShield(float f) {
        if (this.useItem.getItem() == Items.SHIELD) {
            if (!this.level.isClientSide) {
                this.awardStat(Stats.ITEM_USED.get(this.useItem.getItem()));
            }

            if (f >= 3.0F) {
                int i = 1 + Mth.floor(f);
                InteractionHand enumhand = this.getUsedItemHand();

                this.useItem.hurtAndBreak(i, this, (entityhuman) -> {
                    entityhuman.broadcastBreakEvent(enumhand);
                });
                if (this.useItem.isEmpty()) {
                    if (enumhand == InteractionHand.MAIN_HAND) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    } else {
                        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    }

                    this.useItem = ItemStack.EMPTY;
                    this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level.random.nextFloat() * 0.4F);
                }
            }

        }
    }

    // CraftBukkit start
    @Override
    protected boolean damageEntity0(DamageSource damagesource, float f) { // void -> boolean
        if (true) {
            return super.damageEntity0(damagesource, f);
        }
        // CraftBukkit end
        if (!this.isInvulnerableTo(damagesource)) {
            f = this.getDamageAfterArmorAbsorb(damagesource, f);
            f = this.getDamageAfterMagicAbsorb(damagesource, f);
            float f1 = f;

            f = Math.max(f - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (f1 - f));
            float f2 = f1 - f;

            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                this.awardStat(Stats.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }

            if (f != 0.0F) {
                this.causeFoodExhaustion(damagesource.getFoodExhaustion());
                float f3 = this.getHealth();

                this.setHealth(this.getHealth() - f);
                this.getCombatTracker().recordDamage(damagesource, f3, f);
                if (f < 3.4028235E37F) {
                    this.awardStat(Stats.DAMAGE_TAKEN, Math.round(f * 10.0F));
                }

            }
        }
        return false; // CraftBukkit
    }

    @Override
    protected boolean onSoulSpeedBlock() {
        return !this.abilities.flying && super.onSoulSpeedBlock();
    }

    public void openTextEdit(SignBlockEntity tileentitysign) {}

    public void openMinecartCommandBlock(BaseCommandBlock commandblocklistenerabstract) {}

    public void openCommandBlock(CommandBlockEntity tileentitycommand) {}

    public void openStructureBlock(StructureBlockEntity tileentitystructure) {}

    public void openJigsawBlock(JigsawBlockEntity tileentityjigsaw) {}

    public void openHorseInventory(AbstractHorse entityhorseabstract, Container iinventory) {}

    public OptionalInt openMenu(@Nullable MenuProvider itileinventory) {
        return OptionalInt.empty();
    }

    public void openTrade(int i, MerchantOffers merchantrecipelist, int j, int k, boolean flag, boolean flag1) {}

    public void openItemGui(ItemStack itemstack, InteractionHand enumhand) {}

    public InteractionResult interactOn(Entity entity, InteractionHand enumhand) {
        if (this.isSpectator()) {
            if (entity instanceof MenuProvider) {
                this.openMenu((MenuProvider) entity);
            }

            return InteractionResult.PASS;
        } else {
            ItemStack itemstack = this.getItemInHand(enumhand);
            ItemStack itemstack1 = itemstack.copy();
            InteractionResult enuminteractionresult = entity.interact(this, enumhand);

            if (enuminteractionresult.consumesAction()) {
                if (this.abilities.instabuild && itemstack == this.getItemInHand(enumhand) && itemstack.getCount() < itemstack1.getCount()) {
                    itemstack.setCount(itemstack1.getCount());
                }

                return enuminteractionresult;
            } else {
                if (!itemstack.isEmpty() && entity instanceof LivingEntity) {
                    if (this.abilities.instabuild) {
                        itemstack = itemstack1;
                    }

                    InteractionResult enuminteractionresult1 = itemstack.interactLivingEntity(this, (LivingEntity) entity, enumhand);

                    if (enuminteractionresult1.consumesAction()) {
                        if (itemstack.isEmpty() && !this.abilities.instabuild) {
                            this.setItemInHand(enumhand, ItemStack.EMPTY);
                        }

                        return enuminteractionresult1;
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    @Override
    public double getMyRidingOffset() {
        return -0.35D;
    }

    @Override
    public void removeVehicle() {
        super.removeVehicle();
        this.boardingCooldown = 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isSleeping();
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.abilities.flying;
    }

    @Override
    protected Vec3 maybeBackOffFromEdge(Vec3 vec3d, MoverType enummovetype) {
        if ((enummovetype == MoverType.SELF || enummovetype == MoverType.PLAYER) && this.onGround && this.isStayingOnGroundSurface()) {
            double d0 = vec3d.x;
            double d1 = vec3d.z;
            double d2 = 0.05D;

            while (d0 != 0.0D && this.level.noCollision(this, this.getBoundingBox().move(d0, (double) (-this.maxUpStep), 0.0D))) {
                if (d0 < 0.05D && d0 >= -0.05D) {
                    d0 = 0.0D;
                } else if (d0 > 0.0D) {
                    d0 -= 0.05D;
                } else {
                    d0 += 0.05D;
                }
            }

            while (d1 != 0.0D && this.level.noCollision(this, this.getBoundingBox().move(0.0D, (double) (-this.maxUpStep), d1))) {
                if (d1 < 0.05D && d1 >= -0.05D) {
                    d1 = 0.0D;
                } else if (d1 > 0.0D) {
                    d1 -= 0.05D;
                } else {
                    d1 += 0.05D;
                }
            }

            while (d0 != 0.0D && d1 != 0.0D && this.level.noCollision(this, this.getBoundingBox().move(d0, (double) (-this.maxUpStep), d1))) {
                if (d0 < 0.05D && d0 >= -0.05D) {
                    d0 = 0.0D;
                } else if (d0 > 0.0D) {
                    d0 -= 0.05D;
                } else {
                    d0 += 0.05D;
                }

                if (d1 < 0.05D && d1 >= -0.05D) {
                    d1 = 0.0D;
                } else if (d1 > 0.0D) {
                    d1 -= 0.05D;
                } else {
                    d1 += 0.05D;
                }
            }

            vec3d = new Vec3(d0, vec3d.y, d1);
        }

        return vec3d;
    }

    public void attack(Entity entity) {
        if (entity.isAttackable()) {
            if (!entity.skipAttackInteraction(this)) {
                float f = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float f1;

                if (entity instanceof LivingEntity) {
                    f1 = EnchantmentHelper.getDamageBonus(this.getMainHandItem(), ((LivingEntity) entity).getMobType());
                } else {
                    f1 = EnchantmentHelper.getDamageBonus(this.getMainHandItem(), MobType.UNDEFINED);
                }

                float f2 = this.getAttackStrengthScale(0.5F);

                f *= 0.2F + f2 * f2 * 0.8F;
                f1 *= f2;
<<<<<<< HEAD
                // this.resetAttackCooldown(); // CraftBukkit - Moved to EntityLiving to reset the cooldown after the damage is dealt
=======
                // Paper start - PlayerAttackEntityCooldownResetEvent
                if (new com.destroystokyo.paper.event.player.PlayerAttackEntityCooldownResetEvent((org.bukkit.entity.Player) this.getBukkitEntity(), entity.getBukkitEntity(), this.getCooledAttackStrength(0F)).callEvent()) {
                    this.resetCooldown(); // reset it like normal
                }
                // Paper end
>>>>>>> Toothpick
                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = f2 > 0.9F;
                    boolean flag1 = false;
                    byte b0 = 0;
                    int i = b0 + EnchantmentHelper.getKnockbackBonus((LivingEntity) this);

                    if (this.isSprinting() && flag) {
                        this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, this.getSoundSource(), 1.0F, 1.0F);
                        ++i;
                        flag1 = true;
                    }

                    boolean flag2 = flag && this.fallDistance > 0.0F && !this.onGround && !this.onClimbable() && !this.isInWater() && !this.hasEffect(MobEffects.BLINDNESS) && !this.isPassenger() && entity instanceof LivingEntity;

                    flag2 = flag2 && !this.isSprinting();
                    if (flag2) {
                        f *= 1.5F;
                    }

                    f += f1;
                    boolean flag3 = false;
                    double d0 = (double) (this.walkDist - this.walkDistO);

                    if (flag && !flag2 && !flag1 && this.onGround && d0 < (double) this.getSpeed()) {
                        ItemStack itemstack = this.getItemInHand(InteractionHand.MAIN_HAND);

                        if (itemstack.getItem() instanceof SwordItem) {
                            flag3 = true;
                        }
                    }

                    float f3 = 0.0F;
                    boolean flag4 = false;
                    int j = EnchantmentHelper.getFireAspect(this);

                    if (entity instanceof LivingEntity) {
                        f3 = ((LivingEntity) entity).getHealth();
                        if (j > 0 && !entity.isOnFire()) {
                            // CraftBukkit start - Call a combust event when somebody hits with a fire enchanted item
                            EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), 1);
                            org.bukkit.Bukkit.getPluginManager().callEvent(combustEvent);

                            if (!combustEvent.isCancelled()) {
                                flag4 = true;
                                entity.setOnFire(combustEvent.getDuration(), false);
                            }
                            // CraftBukkit end
                        }
                    }

                    Vec3 vec3d = entity.getDeltaMovement();
                    boolean flag5 = entity.hurt(DamageSource.playerAttack(this), f);

                    if (flag5) {
                        if (i > 0) {
                            if (entity instanceof LivingEntity) {
                                ((LivingEntity) entity).knockback((float) i * 0.5F, (double) Mth.sin(this.yRot * 0.017453292F), (double) (-Mth.cos(this.yRot * 0.017453292F)));
                            } else {
                                entity.push((double) (-Mth.sin(this.yRot * 0.017453292F) * (float) i * 0.5F), 0.1D, (double) (Mth.cos(this.yRot * 0.017453292F) * (float) i * 0.5F));
                            }

                            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
                            this.setSprinting(false);
                        }

                        if (flag3) {
                            float f4 = 1.0F + EnchantmentHelper.getSweepingDamageRatio((LivingEntity) this) * f;
                            List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(1.0D, 0.25D, 1.0D));
                            Iterator iterator = list.iterator();

                            while (iterator.hasNext()) {
                                LivingEntity entityliving = (LivingEntity) iterator.next();

                                if (entityliving != this && entityliving != entity && !this.isAlliedTo(entityliving) && (!(entityliving instanceof ArmorStand) || !((ArmorStand) entityliving).isMarker()) && this.distanceToSqr((Entity) entityliving) < 9.0D) {
                                    // CraftBukkit start - Only apply knockback if the damage hits
                                    if (entityliving.hurt(DamageSource.playerAttack(this).sweep(), f4)) {
                                    entityliving.knockback(0.4F, (double) Mth.sin(this.yRot * 0.017453292F), (double) (-Mth.cos(this.yRot * 0.017453292F)));
                                    }
                                    // CraftBukkit end
                                }
                            }

                            this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, this.getSoundSource(), 1.0F, 1.0F);
                            this.sweepAttack();
                        }

                        if (entity instanceof ServerPlayer && entity.hurtMarked) {
                            // CraftBukkit start - Add Velocity Event
                            boolean cancelled = false;
                            org.bukkit.entity.Player player = (org.bukkit.entity.Player) entity.getBukkitEntity();
                            org.bukkit.util.Vector velocity = CraftVector.toBukkit(vec3d);

                            PlayerVelocityEvent event = new PlayerVelocityEvent(player, velocity.clone());
                            level.getServerOH().getPluginManager().callEvent(event);

                            if (event.isCancelled()) {
                                cancelled = true;
                            } else if (!velocity.equals(event.getVelocity())) {
                                player.setVelocity(event.getVelocity());
                            }

                            if (!cancelled) {
                            ((ServerPlayer) entity).connection.sendPacket(new ClientboundSetEntityMotionPacket(entity));
                            entity.hurtMarked = false;
                            entity.setDeltaMovement(vec3d);
                            }
                            // CraftBukkit end
                        }

                        if (flag2) {
                            this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, this.getSoundSource(), 1.0F, 1.0F);
                            this.crit(entity);
                        }

                        if (!flag2 && !flag3) {
                            if (flag) {
                                this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, this.getSoundSource(), 1.0F, 1.0F);
                            } else {
                                this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, this.getSoundSource(), 1.0F, 1.0F);
                            }
                        }

                        if (f1 > 0.0F) {
                            this.magicCrit(entity);
                        }

                        this.setLastHurtMob(entity);
                        if (entity instanceof LivingEntity) {
                            EnchantmentHelper.doPostHurtEffects((LivingEntity) entity, (Entity) this);
                        }

                        EnchantmentHelper.doPostDamageEffects((LivingEntity) this, entity);
                        ItemStack itemstack1 = this.getMainHandItem();
                        Object object = entity;

                        if (entity instanceof EnderDragonPart) {
                            object = ((EnderDragonPart) entity).parentMob;
                        }

                        if (!this.level.isClientSide && !itemstack1.isEmpty() && object instanceof LivingEntity) {
                            itemstack1.hurtEnemy((LivingEntity) object, this);
                            if (itemstack1.isEmpty()) {
                                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            }
                        }

                        if (entity instanceof LivingEntity) {
                            float f5 = f3 - ((LivingEntity) entity).getHealth();

                            this.awardStat(Stats.DAMAGE_DEALT, Math.round(f5 * 10.0F));
                            if (j > 0) {
                                // CraftBukkit start - Call a combust event when somebody hits with a fire enchanted item
                                EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), j * 4);
                                org.bukkit.Bukkit.getPluginManager().callEvent(combustEvent);

                                if (!combustEvent.isCancelled()) {
                                    entity.setOnFire(combustEvent.getDuration(), false);
                                }
                                // CraftBukkit end
                            }

                            if (this.level instanceof ServerLevel && f5 > 2.0F) {
                                int k = (int) ((double) f5 * 0.5D);

                                ((ServerLevel) this.level).sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY(0.5D), entity.getZ(), k, 0.1D, 0.0D, 0.1D, 0.2D);
                            }
                        }

                        this.causeFoodExhaustion(level.spigotConfig.combatExhaustion); // Spigot - Change to use configurable value
                    } else {
                        this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, this.getSoundSource(), 1.0F, 1.0F);
                        if (flag4) {
                            entity.clearFire();
                        }
                        // CraftBukkit start - resync on cancelled event
                        if (this instanceof ServerPlayer) {
                            ((ServerPlayer) this).getBukkitEntity().updateInventory();
                        }
                        // CraftBukkit end
                    }
                }

            }
        }
    }

    @Override
    protected void doAutoAttackOnTouch(LivingEntity entityliving) {
        this.attack(entityliving);
    }

    public void disableShield(boolean flag) {
        float f = 0.25F + (float) EnchantmentHelper.getBlockEfficiency(this) * 0.05F;

        if (flag) {
            f += 0.75F;
        }

        if (this.random.nextFloat() < f) {
            this.getCooldowns().addCooldown(Items.SHIELD, 100);
            this.stopUsingItem();
            this.level.broadcastEntityEvent(this, (byte) 30);
        }

    }

    public void crit(Entity entity) {}

    public void magicCrit(Entity entity) {}

    public void sweepAttack() {
        double d0 = (double) (-Mth.sin(this.yRot * 0.017453292F));
        double d1 = (double) Mth.cos(this.yRot * 0.017453292F);

        if (this.level instanceof ServerLevel) {
            ((ServerLevel) this.level).sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + d0, this.getY(0.5D), this.getZ() + d1, 0, d0, 0.0D, d1, 0.0D);
        }

    }

    @Override
    public void remove() {
        super.remove();
        this.inventoryMenu.removed(this);
        if (this.containerMenu != null) {
            this.containerMenu.removed(this);
        }

    }

    public boolean isLocalPlayer() {
        return false;
    }

    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    public Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos blockposition) {
        // CraftBukkit start
        return this.sleep(blockposition, false);
    }

    public Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> sleep(BlockPos blockposition, boolean force) {
<<<<<<< HEAD
=======
        Direction enumdirection = (Direction) this.level.getBlockState(blockposition).getValue(HorizontalDirectionalBlock.FACING);
        Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> bedResult = this.getBedResult(blockposition, enumdirection);

        if (bedResult.left().orElse(null) == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        if (this.getBukkitEntity() instanceof org.bukkit.entity.Player) {
            bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, blockposition, bedResult);

            if (bedResult.left().isPresent()) {
                return bedResult;
            }
        }
>>>>>>> Toothpick
        // CraftBukkit end
        this.startSleeping(blockposition);
        this.sleepCounter = 0;
        return Either.right(Unit.INSTANCE);
    }

    public void stopSleepInBed(boolean flag, boolean flag1) {
        BlockPos bedPosition = this.getSleepingPos().orElse(null); // CraftBukkit
        super.stopSleeping();
        if (this.level instanceof ServerLevel && flag1) {
            ((ServerLevel) this.level).updateSleepingPlayerList();
        }

        // CraftBukkit start - fire PlayerBedLeaveEvent
        if (this.getBukkitEntity() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) this.getBukkitEntity();

            org.bukkit.block.Block bed;
            if (bedPosition != null) {
                bed = this.level.getWorld().getBlockAt(bedPosition.getX(), bedPosition.getY(), bedPosition.getZ());
            } else {
                bed = this.level.getWorld().getBlockAt(player.getLocation());
            }

            PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(player, bed, true);
            this.level.getServerOH().getPluginManager().callEvent(event);
        }
        // CraftBukkit end

        this.sleepCounter = flag ? 0 : 100;
    }

    @Override
    public void stopSleeping() {
        this.stopSleepInBed(true, true);
    }

    public static Optional<Vec3> findRespawnPositionAndUseSpawnBlock(ServerLevel worldserver, BlockPos blockposition, boolean flag, boolean flag1) {
        BlockState iblockdata = worldserver.getType(blockposition);
        Block block = iblockdata.getBlock();

        if (block instanceof RespawnAnchorBlock && (Integer) iblockdata.getValue(RespawnAnchorBlock.CHARGE) > 0 && RespawnAnchorBlock.canSetSpawn((Level) worldserver)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, (LevelReader) worldserver, blockposition);

            if (!flag1 && optional.isPresent()) {
                worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(RespawnAnchorBlock.CHARGE, (Integer) iblockdata.getValue(RespawnAnchorBlock.CHARGE) - 1), 3);
            }

            return optional;
        } else if (block instanceof BedBlock && BedBlock.canSetSpawn((Level) worldserver)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, worldserver, blockposition, 0);
        } else if (!flag) {
            return Optional.empty();
        } else {
            boolean flag2 = block.isPossibleToRespawnInThis();
            boolean flag3 = worldserver.getType(blockposition.above()).getBlock().isPossibleToRespawnInThis();

            return flag2 && flag3 ? Optional.of(new Vec3((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.1D, (double) blockposition.getZ() + 0.5D)) : Optional.empty();
        }
    }

    public boolean isSleepingLongEnough() {
        return this.isSleeping() && this.sleepCounter >= 100;
    }

    public int getSleepTimer() {
        return this.sleepCounter;
    }

    public void displayClientMessage(Component ichatbasecomponent, boolean flag) {}

    public void awardStat(ResourceLocation minecraftkey) {
        this.awardStat(Stats.CUSTOM.get(minecraftkey));
    }

    public void awardStat(ResourceLocation minecraftkey, int i) {
        this.awardStat(Stats.CUSTOM.get(minecraftkey), i);
    }

    public void awardStat(Stat<?> statistic) {
        this.awardStat(statistic, 1);
    }

    public void awardStat(Stat<?> statistic, int i) {}

    public void resetStat(Stat<?> statistic) {}

    public int awardRecipes(Collection<Recipe<?>> collection) {
        return 0;
    }

    public void awardRecipesByKey(ResourceLocation[] aminecraftkey) {}

    public int resetRecipes(Collection<Recipe<?>> collection) {
        return 0;
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        this.awardStat(Stats.JUMP);
        if (this.isSprinting()) {
            this.causeFoodExhaustion(level.spigotConfig.jumpSprintExhaustion); // Spigot - Change to use configurable value
        } else {
            this.causeFoodExhaustion(level.spigotConfig.jumpWalkExhaustion); // Spigot - Change to use configurable value
        }

    }

    @Override
    public void travel(Vec3 vec3d) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        double d3;

        if (this.isSwimming() && !this.isPassenger()) {
            d3 = this.getLookAngle().y;
            double d4 = d3 < -0.2D ? 0.085D : 0.06D;

            if (d3 <= 0.0D || this.jumping || !this.level.getType(new BlockPos(this.getX(), this.getY() + 1.0D - 0.1D, this.getZ())).getFluidState().isEmpty()) {
                Vec3 vec3d1 = this.getDeltaMovement();

                this.setDeltaMovement(vec3d1.add(0.0D, (d3 - vec3d1.y) * d4, 0.0D));
            }
        }

        if (this.abilities.flying && !this.isPassenger()) {
            d3 = this.getDeltaMovement().y;
            float f = this.flyingSpeed;

            this.flyingSpeed = this.abilities.getFlyingSpeed() * (float) (this.isSprinting() ? 2 : 1);
            super.travel(vec3d);
            Vec3 vec3d2 = this.getDeltaMovement();

            this.setDeltaMovement(vec3d2.x, d3 * 0.6D, vec3d2.z);
            this.flyingSpeed = f;
            this.fallDistance = 0.0F;
            // CraftBukkit start
            if (getSharedFlag(7) && !org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) {
                this.setSharedFlag(7, false);
            }
            // CraftBukkit end
        } else {
            super.travel(vec3d);
        }

        this.checkMovementStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    @Override
    public void updateSwimming() {
        if (this.abilities.flying) {
            this.setSwimming(false);
        } else {
            super.updateSwimming();
        }

    }

    protected boolean freeAt(BlockPos blockposition) {
        return !this.level.getType(blockposition).isSuffocating(this.level, blockposition);
    }

    @Override
    public float getSpeed() {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    public void checkMovementStatistics(double d0, double d1, double d2) {
        if (!this.isPassenger()) {
            int i;

            if (this.isSwimming()) {
                i = Math.round(Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(0.01F * (float) i * 0.01F);
                }
            } else if (this.isEyeInFluid((Tag) FluidTags.WATER)) {
                i = Math.round(Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, i);
                    this.causeFoodExhaustion(level.spigotConfig.swimMultiplier * (float) i * 0.01F); // Spigot
                }
            } else if (this.isInWater()) {
                i = Math.round(Mth.sqrt(d0 * d0 + d2 * d2) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, i);
                    this.causeFoodExhaustion(level.spigotConfig.swimMultiplier * (float) i * 0.01F); // Spigot
                }
            } else if (this.onClimbable()) {
                if (d1 > 0.0D) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int) Math.round(d1 * 100.0D));
                }
            } else if (this.onGround) {
                i = Math.round(Mth.sqrt(d0 * d0 + d2 * d2) * 100.0F);
                if (i > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, i);
                        this.causeFoodExhaustion(level.spigotConfig.sprintMultiplier * (float) i * 0.01F); // Spigot
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, i);
                        this.causeFoodExhaustion(level.spigotConfig.otherMultiplier * (float) i * 0.01F); // Spigot
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, i);
                        this.causeFoodExhaustion(level.spigotConfig.otherMultiplier * (float) i * 0.01F); // Spigot
                    }
                }
            } else if (this.isFallFlying()) {
                i = Math.round(Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, i);
            } else {
                i = Math.round(Mth.sqrt(d0 * d0 + d2 * d2) * 100.0F);
                if (i > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, i);
                }
            }

        }
    }

    private void checkRidingStatistics(double d0, double d1, double d2) {
        if (this.isPassenger()) {
            int i = Math.round(Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);

            if (i > 0) {
                Entity entity = this.getVehicle();

                if (entity instanceof AbstractMinecart) {
                    this.awardStat(Stats.MINECART_ONE_CM, i);
                } else if (entity instanceof Boat) {
                    this.awardStat(Stats.BOAT_ONE_CM, i);
                } else if (entity instanceof Pig) {
                    this.awardStat(Stats.PIG_ONE_CM, i);
                } else if (entity instanceof AbstractHorse) {
                    this.awardStat(Stats.HORSE_ONE_CM, i);
                } else if (entity instanceof Strider) {
                    this.awardStat(Stats.STRIDER_ONE_CM, i);
                }
            }
        }

    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        if (this.abilities.mayfly) {
            return false;
        } else {
            if (f >= 2.0F) {
                this.awardStat(Stats.FALL_ONE_CM, (int) Math.round((double) f * 100.0D));
            }

            return super.causeFallDamage(f, f1);
        }
    }

    public boolean tryToStartFallFlying() {
        if (!this.onGround && !this.isFallFlying() && !this.isInWater() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);

            if (itemstack.getItem() == Items.ELYTRA && ElytraItem.isFlyEnabled(itemstack)) {
                this.startFallFlying();
                return true;
            }
        }

        return false;
    }

    public void startFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, true).isCancelled()) {
            this.setSharedFlag(7, true);
        } else {
            // SPIGOT-5542: must toggle like below
            this.setSharedFlag(7, true);
            this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    public void stopFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) {
        this.setSharedFlag(7, true);
        this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    @Override
    protected void doWaterSplashEffect() {
        if (!this.isSpectator()) {
            super.doWaterSplashEffect();
        }

    }

    @Override
    protected SoundEvent getSoundFall(int i) {
        return i > 4 ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL;
    }

    @Override
    public void killed(LivingEntity entityliving) {
        this.awardStat(Stats.ENTITY_KILLED.get(entityliving.getType()));
    }

    @Override
    public void makeStuckInBlock(BlockState iblockdata, Vec3 vec3d) {
        if (!this.abilities.flying) {
            super.makeStuckInBlock(iblockdata, vec3d);
        }

    }

    public void giveExperiencePoints(int i) {
        this.increaseScore(i);
        this.experienceProgress += (float) i / (float) this.getXpNeededForNextLevel();
        this.totalExperience = Mth.clamp(this.totalExperience + i, 0, Integer.MAX_VALUE);

        while (this.experienceProgress < 0.0F) {
            float f = this.experienceProgress * (float) this.getXpNeededForNextLevel();

            if (this.experienceLevel > 0) {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 1.0F + f / (float) this.getXpNeededForNextLevel();
            } else {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 0.0F;
            }
        }

        while (this.experienceProgress >= 1.0F) {
            this.experienceProgress = (this.experienceProgress - 1.0F) * (float) this.getXpNeededForNextLevel();
            this.giveExperienceLevels(1);
            this.experienceProgress /= (float) this.getXpNeededForNextLevel();
        }

    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void onEnchantmentPerformed(ItemStack itemstack, int i) {
        this.experienceLevel -= i;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        this.enchantmentSeed = this.random.nextInt();
    }

    public void giveExperienceLevels(int i) {
        this.experienceLevel += i;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        if (i > 0 && this.experienceLevel % 5 == 0 && (float) this.lastLevelUpTime < (float) this.tickCount - 100.0F) {
            float f = this.experienceLevel > 30 ? 1.0F : (float) this.experienceLevel / 30.0F;

            this.level.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), f * 0.75F, 1.0F);
            this.lastLevelUpTime = this.tickCount;
        }

    }

    public int getXpNeededForNextLevel() {
        return this.experienceLevel >= 30 ? 112 + (this.experienceLevel - 30) * 9 : (this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2);
    }

    public void causeFoodExhaustion(float f) {
        if (!this.abilities.invulnerable) {
            if (!this.level.isClientSide) {
                this.foodData.addExhaustion(f);
            }

        }
    }

    public FoodData getFoodData() {
        return this.foodData;
    }

    public boolean canEat(boolean flag) {
        return this.abilities.invulnerable || flag || this.foodData.needsFood();
    }

    public boolean isHurt() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public boolean mayBuild() {
        return this.abilities.mayBuild;
    }

    public boolean mayUseItemAt(BlockPos blockposition, Direction enumdirection, ItemStack itemstack) {
        if (this.abilities.mayBuild) {
            return true;
        } else {
            BlockPos blockposition1 = blockposition.relative(enumdirection.getOpposite());
            BlockInWorld shapedetectorblock = new BlockInWorld(this.level, blockposition1, false);

            return itemstack.hasAdventureModePlaceTagForBlock(this.level.getTagManager(), shapedetectorblock);
        }
    }

    @Override
    protected int getExperienceReward(net.minecraft.world.entity.player.Player entityhuman) {
        if (!this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && !this.isSpectator()) {
            int i = this.experienceLevel * 7;

            return i > 100 ? 100 : i;
        } else {
            return 0;
        }
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    protected boolean isMovementNoisy() {
        return !this.abilities.flying && (!this.onGround || !this.isDiscrete());
    }

    public void onUpdateAbilities() {}

    public void setGameMode(GameType enumgamemode) {}

    @Override
    public Component getName() {
        return new TextComponent(this.gameProfile.getName());
    }

    public PlayerEnderChestContainer getEnderChest() {
        return this.enderChestInventory;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot enumitemslot) {
        return enumitemslot == EquipmentSlot.MAINHAND ? this.inventory.getSelected() : (enumitemslot == EquipmentSlot.OFFHAND ? (ItemStack) this.inventory.offhand.get(0) : (enumitemslot.getType() == EquipmentSlot.Type.ARMOR ? (ItemStack) this.inventory.armor.get(enumitemslot.getIndex()) : ItemStack.EMPTY));
    }

    @Override
    public void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack) {
        if (enumitemslot == EquipmentSlot.MAINHAND) {
            this.playEquipSound(itemstack);
            this.inventory.items.set(this.inventory.selected, itemstack);
        } else if (enumitemslot == EquipmentSlot.OFFHAND) {
            this.playEquipSound(itemstack);
            this.inventory.offhand.set(0, itemstack);
        } else if (enumitemslot.getType() == EquipmentSlot.Type.ARMOR) {
            this.playEquipSound(itemstack);
            this.inventory.armor.set(enumitemslot.getIndex(), itemstack);
        }

    }

    public boolean addItem(ItemStack itemstack) {
        this.playEquipSound(itemstack);
        return this.inventory.add(itemstack);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return Lists.newArrayList(new ItemStack[]{this.getMainHandItem(), this.getOffhandItem()});
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.inventory.armor;
    }

    public boolean setEntityOnShoulder(CompoundTag nbttagcompound) {
        if (!this.isPassenger() && this.onGround && !this.isInWater()) {
            if (this.getShoulderEntityLeft().isEmpty()) {
                this.setShoulderEntityLeft(nbttagcompound);
                this.timeEntitySatOnShoulder = this.level.getGameTime();
                return true;
            } else if (this.getShoulderEntityRight().isEmpty()) {
                this.setShoulderEntityRight(nbttagcompound);
                this.timeEntitySatOnShoulder = this.level.getGameTime();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void removeEntitiesOnShoulder() {
        if (this.timeEntitySatOnShoulder + 20L < this.level.getGameTime()) {
            // CraftBukkit start
            if (this.spawnEntityFromShoulder(this.getShoulderEntityLeft())) {
                this.setShoulderEntityLeft(new CompoundTag());
            }
            if (this.spawnEntityFromShoulder(this.getShoulderEntityRight())) {
                this.setShoulderEntityRight(new CompoundTag());
            }
            // CraftBukkit end
        }

    }

    private boolean spawnEntityFromShoulder(CompoundTag nbttagcompound) { // CraftBukkit void->boolean
        if (!this.level.isClientSide && !nbttagcompound.isEmpty()) {
            return EntityType.create(nbttagcompound, this.level).map((entity) -> { // CraftBukkit
                if (entity instanceof TamableAnimal) {
                    ((TamableAnimal) entity).setOwnerUUID(this.uuid);
                }

                entity.setPos(this.getX(), this.getY() + 0.699999988079071D, this.getZ());
                return ((ServerLevel) this.level).addEntitySerialized(entity, CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY); // CraftBukkit
            }).orElse(true); // CraftBukkit
        }

        return true; // CraftBukkit
    }

    @Override
    public abstract boolean isSpectator();

    @Override
    public boolean isSwimming() {
        return !this.abilities.flying && !this.isSpectator() && super.isSwimming();
    }

    public abstract boolean isCreative();

    @Override
    public boolean isPushedByFluid() {
        return !this.abilities.flying;
    }

    public Scoreboard getScoreboard() {
        return this.level.getScoreboard();
    }

    @Override
    public Component getDisplayName() {
        MutableComponent ichatmutablecomponent = PlayerTeam.formatNameForTeam(this.getTeam(), this.getName());

        return this.decorateDisplayNameComponent(ichatmutablecomponent);
    }

    public Component getDisplayNameWithUuid() {
        return (new TextComponent("")).append(this.getName()).append(" (").append(this.gameProfile.getId().toString()).append(")");
    }

    private MutableComponent decorateDisplayNameComponent(MutableComponent ichatmutablecomponent) {
        String s = this.getGameProfile().getName();

        return ichatmutablecomponent.withStyle((chatmodifier) -> {
            return chatmodifier.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + s + " ")).withHoverEvent(this.createHoverEvent()).withInsertion(s);
        });
    }

    @Override
    public String getScoreboardName() {
        return this.getGameProfile().getName();
    }

    @Override
    public float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        switch (entitypose) {
            case SWIMMING:
            case FALL_FLYING:
            case SPIN_ATTACK:
                return 0.4F;
            case CROUCHING:
                return 1.27F;
            default:
                return 1.62F;
        }
    }

    @Override
    public void setAbsorptionAmount(float f) {
        if (f < 0.0F) {
            f = 0.0F;
        }

        this.getEntityData().set(net.minecraft.world.entity.player.Player.DATA_PLAYER_ABSORPTION_ID, f);
    }

    @Override
    public float getAbsorptionAmount() {
        return (Float) this.getEntityData().get(net.minecraft.world.entity.player.Player.DATA_PLAYER_ABSORPTION_ID);
    }

    public static UUID createPlayerUUID(GameProfile gameprofile) {
        UUID uuid = gameprofile.getId();

        if (uuid == null) {
            uuid = createPlayerUUID(gameprofile.getName());
        }

        return uuid;
    }

    public static UUID createPlayerUUID(String s) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + s).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean setSlot(int i, ItemStack itemstack) {
        if (i >= 0 && i < this.inventory.items.size()) {
            this.inventory.setItem(i, itemstack);
            return true;
        } else {
            EquipmentSlot enumitemslot;

            if (i == 100 + EquipmentSlot.HEAD.getIndex()) {
                enumitemslot = EquipmentSlot.HEAD;
            } else if (i == 100 + EquipmentSlot.CHEST.getIndex()) {
                enumitemslot = EquipmentSlot.CHEST;
            } else if (i == 100 + EquipmentSlot.LEGS.getIndex()) {
                enumitemslot = EquipmentSlot.LEGS;
            } else if (i == 100 + EquipmentSlot.FEET.getIndex()) {
                enumitemslot = EquipmentSlot.FEET;
            } else {
                enumitemslot = null;
            }

            if (i == 98) {
                this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
                return true;
            } else if (i == 99) {
                this.setItemSlot(EquipmentSlot.OFFHAND, itemstack);
                return true;
            } else if (enumitemslot == null) {
                int j = i - 200;

                if (j >= 0 && j < this.enderChestInventory.getContainerSize()) {
                    this.enderChestInventory.setItem(j, itemstack);
                    return true;
                } else {
                    return false;
                }
            } else {
                if (!itemstack.isEmpty()) {
                    if (!(itemstack.getItem() instanceof ArmorItem) && !(itemstack.getItem() instanceof ElytraItem)) {
                        if (enumitemslot != EquipmentSlot.HEAD) {
                            return false;
                        }
                    } else if (Mob.getEquipmentSlotForItem(itemstack) != enumitemslot) {
                        return false;
                    }
                }

                this.inventory.setItem(enumitemslot.getIndex() + this.inventory.items.size(), itemstack);
                return true;
            }
        }
    }

    @Override
    public void setRemainingFireTicks(int i) {
        super.setRemainingFireTicks(this.abilities.invulnerable ? Math.min(i, 1) : i);
    }

    @Override
    public HumanoidArm getMainArm() {
        return (Byte) this.entityData.get(net.minecraft.world.entity.player.Player.DATA_PLAYER_MAIN_HAND) == 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public void setMainArm(HumanoidArm enummainhand) {
        this.entityData.set(net.minecraft.world.entity.player.Player.DATA_PLAYER_MAIN_HAND, (byte) (enummainhand == HumanoidArm.LEFT ? 0 : 1));
    }

    public CompoundTag getShoulderEntityLeft() {
        return (CompoundTag) this.entityData.get(net.minecraft.world.entity.player.Player.DATA_SHOULDER_LEFT);
    }

    public void setShoulderEntityLeft(CompoundTag nbttagcompound) {
        this.entityData.set(net.minecraft.world.entity.player.Player.DATA_SHOULDER_LEFT, nbttagcompound);
    }

    public CompoundTag getShoulderEntityRight() {
        return (CompoundTag) this.entityData.get(net.minecraft.world.entity.player.Player.DATA_SHOULDER_RIGHT);
    }

    public void setShoulderEntityRight(CompoundTag nbttagcompound) {
        this.entityData.set(net.minecraft.world.entity.player.Player.DATA_SHOULDER_RIGHT, nbttagcompound);
    }

    public float getCurrentItemAttackStrengthDelay() {
        return (float) (1.0D / this.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0D);
    }

    public float getAttackStrengthScale(float f) {
        return Mth.clamp(((float) this.attackStrengthTicker + f) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public void resetAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
    }

    public ItemCooldowns getCooldowns() {
        return this.cooldowns;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return !this.abilities.flying && !this.isFallFlying() ? super.getBlockSpeedFactor() : 1.0F;
    }

    public float getLuck() {
        return (float) this.getAttributeValue(Attributes.LUCK);
    }

    public boolean canUseGameMasterBlocks() {
        return this.abilities.instabuild && this.getPermissionLevel() >= 2;
    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

        return this.getItemBySlot(enumitemslot).isEmpty();
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        return (EntityDimensions) net.minecraft.world.entity.player.Player.POSES.getOrDefault(entitypose, net.minecraft.world.entity.player.Player.STANDING_DIMENSIONS);
    }

    @Override
    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING);
    }

    @Override
    public ItemStack getProjectile(ItemStack itemstack) {
        if (!(itemstack.getItem() instanceof ProjectileWeaponItem)) {
            return ItemStack.EMPTY;
        } else {
            Predicate<ItemStack> predicate = ((ProjectileWeaponItem) itemstack.getItem()).getSupportedHeldProjectiles();
            ItemStack itemstack1 = ProjectileWeaponItem.getHeldProjectile((LivingEntity) this, predicate);

            if (!itemstack1.isEmpty()) {
                return itemstack1;
            } else {
                predicate = ((ProjectileWeaponItem) itemstack.getItem()).getAllSupportedProjectiles();

                for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                    ItemStack itemstack2 = this.inventory.getItem(i);

                    if (predicate.test(itemstack2)) {
                        return itemstack2;
                    }
                }

                return this.abilities.instabuild ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
            }
        }
    }

    @Override
    public ItemStack eat(Level world, ItemStack itemstack) {
        this.getFoodData().eat(itemstack.getItem(), itemstack);
        this.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
        world.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
        if (this instanceof ServerPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger((ServerPlayer) this, itemstack);
        }

        return super.eat(world, itemstack);
    }

    @Override
    protected boolean shouldRemoveSoulSpeed(BlockState iblockdata) {
        return this.abilities.flying || super.shouldRemoveSoulSpeed(iblockdata);
    }

    public static enum BedSleepingProblem {

        NOT_POSSIBLE_HERE, NOT_POSSIBLE_NOW(new TranslatableComponent("block.minecraft.bed.no_sleep")), TOO_FAR_AWAY(new TranslatableComponent("block.minecraft.bed.too_far_away")), OBSTRUCTED(new TranslatableComponent("block.minecraft.bed.obstructed")), OTHER_PROBLEM, NOT_SAFE(new TranslatableComponent("block.minecraft.bed.not_safe"));

        @Nullable
        private final Component message;

        private BedSleepingProblem() {
            this.message = null;
        }

        private BedSleepingProblem(Component ichatbasecomponent) {
            this.message = ichatbasecomponent;
        }

        @Nullable
        public Component getMessage() {
            return this.message;
        }
    }
}
