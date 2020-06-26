package net.minecraft.world.entity;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddMobPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.google.common.base.Function;
import org.bukkit.Location;
import org.bukkit.craftbukkit.attribute.CraftAttributeMap;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
// CraftBukkit end

import org.bukkit.craftbukkit.SpigotTimings; // Spigot

public abstract class LivingEntity extends Entity {

    private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final UUID SPEED_MODIFIER_SOUL_SPEED_UUID = UUID.fromString("87f46a96-686f-4796-b035-22e16ee9e038");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SPRINTING_UUID, "Sprinting speed boost", 0.30000001192092896D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_EFFECT_COLOR_ID = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.defineId(net.minecraft.world.entity.LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F);
    private final AttributeMap attributes;
    public CombatTracker combatTracker = new CombatTracker(this);
    public final Map<MobEffect, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks;
    private final NonNullList<ItemStack> lastArmorItemStacks;
    public boolean swinging;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public float hurtDir;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public float animationSpeedOld;
    public float animationSpeed;
    public float animationPosition;
    public int invulnerableDuration;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public float flyingSpeed;
    @Nullable
    public net.minecraft.world.entity.player.Player lastHurtByPlayer;
    protected int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    protected int deathScore;
    public float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lyHeadRot;
    protected int lerpHeadSteps;
    public boolean effectsDirty;
    @Nullable
    public net.minecraft.world.entity.LivingEntity lastHurtByMob;
    public int lastHurtByMobTimestamp;
    private net.minecraft.world.entity.LivingEntity lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem;
    protected int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos;
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    // CraftBukkit start
    public int expToDrop;
    public int maxAirTicks = 300;
    public boolean forceDrops;
    public ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>();
    public final org.bukkit.craftbukkit.attribute.CraftAttributeMap craftAttributes;
    public boolean collides = true;
    public Set<UUID> collidableExemptions = new HashSet<>();
    public boolean canPickUpLoot;

    @Override
    public float getBukkitYaw() {
        return getYHeadRot();
    }
    // CraftBukkit end
    // Spigot start
    public void inactiveTick()
    {
        super.inactiveTick();
        ++this.noActionTime; // Above all the floats
    }
    // Spigot end

    protected LivingEntity(EntityType<? extends net.minecraft.world.entity.LivingEntity> entitytypes, Level world) {
        super(entitytypes, world);
        this.lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
        this.lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
        this.invulnerableDuration = 20;
        this.flyingSpeed = 0.02F;
        this.effectsDirty = true;
        this.useItem = ItemStack.EMPTY;
        this.lastClimbablePos = Optional.empty();
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(entitytypes));
        this.craftAttributes = new CraftAttributeMap(attributes); // CraftBukkit
        // CraftBukkit - setHealth(getMaxHealth()) inlined and simplified to skip the instanceof check for EntityPlayer, as getBukkitEntity() is not initialized in constructor
        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID, (float) this.getAttribute(Attributes.MAX_HEALTH).getValue());
        this.blocksBuilding = true;
        this.rotA = (float) ((Math.random() + 1.0D) * 0.009999999776482582D);
        this.reapplyPosition();
        this.timeOffs = (float) Math.random() * 12398.0F;
        this.yRot = (float) (Math.random() * 6.2831854820251465D);
        this.yHeadRot = this.yRot;
        this.maxUpStep = 0.6F;
        NbtOps dynamicopsnbt = NbtOps.INSTANCE;

        this.brain = this.makeBrain(new Dynamic(dynamicopsnbt, dynamicopsnbt.createMap((Map) ImmutableMap.of(dynamicopsnbt.createString("memories"), dynamicopsnbt.emptyMap()))));
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider((Collection) ImmutableList.of(), (Collection) ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    @Override
    public void kill() {
        this.hurt(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> entitytypes) {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) 0);
        this.entityData.register(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_COLOR_ID, 0);
        this.entityData.register(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_AMBIENCE_ID, false);
        this.entityData.register(net.minecraft.world.entity.LivingEntity.DATA_ARROW_COUNT_ID, 0);
        this.entityData.register(net.minecraft.world.entity.LivingEntity.DATA_STINGER_COUNT_ID, 0);
        this.entityData.register(net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID, 1.0F);
        this.entityData.register(net.minecraft.world.entity.LivingEntity.SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder cK() {
        return AttributeSupplier.a().a(Attributes.MAX_HEALTH).a(Attributes.KNOCKBACK_RESISTANCE).a(Attributes.MOVEMENT_SPEED).a(Attributes.ARMOR).a(Attributes.ARMOR_TOUGHNESS);
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (!this.level.isClientSide && flag && this.fallDistance > 0.0F) {
            this.removeSoulSpeed();
            this.tryAddSoulSpeed();
        }

        if (!this.level.isClientSide && this.fallDistance > 3.0F && flag) {
            float f = (float) Mth.ceil(this.fallDistance - 3.0F);

            if (!iblockdata.isAir()) {
                double d1 = Math.min((double) (0.2F + f / 15.0F), 2.5D);
                int i = (int) (150.0D * d1);

                // CraftBukkit start - visiblity api
                if (this instanceof ServerPlayer) {
                    ((ServerLevel) this.level).sendParticles((ServerPlayer) this, new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), this.getX(), this.getY(), this.getZ(), i, 0.0D, 0.0D, 0.0D, 0.15000000596046448D, false);
                } else {
                    ((ServerLevel) this.level).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), this.getX(), this.getY(), this.getZ(), i, 0.0D, 0.0D, 0.0D, 0.15000000596046448D);
                }
                // CraftBukkit end
            }
        }

        super.checkFallDamage(d0, flag, iblockdata, blockposition);
    }

    public boolean canBreatheUnderwater() {
        return this.getMobType() == MobType.UNDEAD;
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.canSpawnSoulSpeedParticle()) {
            this.spawnSoulSpeedParticle();
        }

        super.baseTick();
        this.level.getProfiler().push("livingEntityBaseTick");
        boolean flag = this instanceof net.minecraft.world.entity.player.Player;

        if (this.isAlive()) {
            if (this.isInWall()) {
                this.hurt(DamageSource.IN_WALL, 1.0F);
            } else if (flag && !this.level.getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                double d0 = this.level.getWorldBorder().getDistanceToBorder((Entity) this) + this.level.getWorldBorder().getDamageSafeZone();

                if (d0 < 0.0D) {
                    double d1 = this.level.getWorldBorder().getDamagePerBlock();

                    if (d1 > 0.0D) {
                        this.hurt(DamageSource.IN_WALL, (float) Math.max(1, Mth.floor(-d0 * d1)));
                    }
                }
            }
        }

        if (this.fireImmune() || this.level.isClientSide) {
            this.clearFire();
        }

        boolean flag1 = flag && ((net.minecraft.world.entity.player.Player) this).abilities.invulnerable;

        if (this.isAlive()) {
            if (this.isEyeInFluid((Tag) FluidTags.WATER) && !this.level.getType(new BlockPos(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                if (!this.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(this) && !flag1) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -20) {
                        this.setAirSupply(0);
                        Vec3 vec3d = this.getDeltaMovement();

                        for (int i = 0; i < 8; ++i) {
                            double d2 = this.random.nextDouble() - this.random.nextDouble();
                            double d3 = this.random.nextDouble() - this.random.nextDouble();
                            double d4 = this.random.nextDouble() - this.random.nextDouble();

                            this.level.addParticle(ParticleTypes.BUBBLE, this.getX() + d2, this.getY() + d3, this.getZ() + d4, vec3d.x, vec3d.y, vec3d.z);
                        }

                        this.hurt(DamageSource.DROWN, 2.0F);
                    }
                }

                if (!this.level.isClientSide && this.isPassenger() && this.getVehicle() != null && !this.getVehicle().rideableUnderWater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (!this.level.isClientSide) {
                BlockPos blockposition = this.blockPosition();

                if (!Objects.equal(this.lastPos, blockposition)) {
                    this.lastPos = blockposition;
                    this.onChangedBlock(blockposition);
                }
            }
        }

        if (this.isAlive() && this.isInWaterRainOrBubble()) {
            this.clearFire();
        }

        if (this.hurtTime > 0) {
            --this.hurtTime;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            --this.invulnerableTime;
        }

        if (this.isDeadOrDying()) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            --this.lastHurtByPlayerTime;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob((net.minecraft.world.entity.LivingEntity) null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob((net.minecraft.world.entity.LivingEntity) null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;
        this.level.getProfiler().pop();
    }

    public boolean canSpawnSoulSpeedParticle() {
        return this.tickCount % 5 == 0 && this.getDeltaMovement().x != 0.0D && this.getDeltaMovement().z != 0.0D && !this.isSpectator() && EnchantmentHelper.hasSoulSpeed(this) && this.onSoulSpeedBlock();
    }

    protected void spawnSoulSpeedParticle() {
        Vec3 vec3d = this.getDeltaMovement();

        this.level.addParticle(ParticleTypes.SOUL, this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(), this.getY() + 0.1D, this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(), vec3d.x * -0.2D, 0.1D, vec3d.z * -0.2D);
        float f = this.random.nextFloat() * 0.4F + this.random.nextFloat() > 0.9F ? 0.6F : 0.0F;

        this.playSound(SoundEvents.SOUL_ESCAPE, f, 0.6F + this.random.nextFloat() * 0.4F);
    }

    protected boolean onSoulSpeedBlock() {
        return this.getBlockStateOn().is((Tag) BlockTags.SOUL_SPEED_BLOCKS);
    }

    @Override
    protected float getBlockSpeedFactor() {
        return this.onSoulSpeedBlock() && EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED, this) > 0 ? 1.0F : super.getBlockSpeedFactor();
    }

    protected boolean shouldRemoveSoulSpeed(BlockState iblockdata) {
        return !iblockdata.isAir() || this.isFallFlying();
    }

    protected void removeSoulSpeed() {
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributemodifiable != null) {
            if (attributemodifiable.getModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SOUL_SPEED_UUID) != null) {
                attributemodifiable.removeModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SOUL_SPEED_UUID);
            }

        }
    }

    protected void tryAddSoulSpeed() {
        if (!this.getBlockStateOn().isAir()) {
            int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED, this);

            if (i > 0 && this.onSoulSpeedBlock()) {
                AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

                if (attributemodifiable == null) {
                    return;
                }

                attributemodifiable.addTransientModifier(new AttributeModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SOUL_SPEED_UUID, "Soul speed boost", (double) (0.03F * (1.0F + (float) i * 0.35F)), AttributeModifier.Operation.ADDITION));
                if (this.getRandom().nextFloat() < 0.04F) {
                    ItemStack itemstack = this.getItemBySlot(EquipmentSlot.FEET);

                    itemstack.hurtAndBreak(1, this, (entityliving) -> {
                        entityliving.broadcastBreakEvent(EquipmentSlot.FEET);
                    });
                }
            }
        }

    }

    protected void onChangedBlock(BlockPos blockposition) {
        int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.FROST_WALKER, this);

        if (i > 0) {
            FrostWalkerEnchantment.onEntityMoved(this, this.level, blockposition, i);
        }

        if (this.shouldRemoveSoulSpeed(this.getBlockStateOn())) {
            this.removeSoulSpeed();
        }

        this.tryAddSoulSpeed();
    }

    public boolean isBaby() {
        return false;
    }

    public float getScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    protected boolean isAffectedByFluids() {
        return true;
    }

    @Override
    public boolean rideableUnderWater() {
        return false;
    }

    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 20 && !this.removed) { // CraftBukkit - (this.deathTicks == 20) -> (this.deathTicks >= 20 && !this.dead)
            this.remove();

            for (int i = 0; i < 20; ++i) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;

                this.level.addParticle(ParticleTypes.POOF, this.getRandomX(1.0D), this.getRandomY(), this.getRandomZ(1.0D), d0, d1, d2);
            }
        }

    }

    protected boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    protected int decreaseAirSupply(int i) {
        int j = EnchantmentHelper.getRespiration(this);

        return j > 0 && this.random.nextInt(j + 1) > 0 ? i : i - 1;
    }

    protected int increaseAirSupply(int i) {
        return Math.min(i + 4, this.getMaxAirSupply());
    }

    protected int getExperienceReward(net.minecraft.world.entity.player.Player entityhuman) {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    public Random getRandom() {
        return this.random;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable net.minecraft.world.entity.player.Player entityhuman) {
        this.lastHurtByPlayer = entityhuman;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    public void setLastHurtByMob(@Nullable net.minecraft.world.entity.LivingEntity entityliving) {
        this.lastHurtByMob = entityliving;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            this.lastHurtMob = (net.minecraft.world.entity.LivingEntity) entity;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int i) {
        this.noActionTime = i;
    }

    protected void playEquipSound(ItemStack itemstack) {
        if (!itemstack.isEmpty()) {
            SoundEvent soundeffect = SoundEvents.ARMOR_EQUIP_GENERIC;
            Item item = itemstack.getItem();

            if (item instanceof ArmorItem) {
                soundeffect = ((ArmorItem) item).getMaterial().getEquipSound();
            } else if (item == Items.ELYTRA) {
                soundeffect = SoundEvents.ARMOR_EQUIP_ELYTRA;
            }

            this.playSound(soundeffect, 1.0F, 1.0F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putFloat("Health", this.getHealth());
        nbttagcompound.putShort("HurtTime", (short) this.hurtTime);
        nbttagcompound.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        nbttagcompound.putShort("DeathTime", (short) this.deathTime);
        nbttagcompound.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        nbttagcompound.put("Attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag nbttaglist = new ListTag();
            Iterator iterator = this.activeEffects.values().iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                nbttaglist.add(mobeffect.save(new CompoundTag()));
            }

            nbttagcompound.put("ActiveEffects", nbttaglist);
        }

        nbttagcompound.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent((blockposition) -> {
            nbttagcompound.putInt("SleepingX", blockposition.getX());
            nbttagcompound.putInt("SleepingY", blockposition.getY());
            nbttagcompound.putInt("SleepingZ", blockposition.getZ());
        });
        DataResult<net.minecraft.nbt.Tag> dataresult = this.brain.serializeStart((DynamicOps) NbtOps.INSTANCE);
        Logger logger = net.minecraft.world.entity.LivingEntity.LOGGER;

        logger.getClass();
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbttagcompound.put("Brain", nbtbase);
        });
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.setAbsorptionAmount(nbttagcompound.getFloat("AbsorptionAmount"));
        if (nbttagcompound.contains("Attributes", 9) && this.level != null && !this.level.isClientSide) {
            this.getAttributes().load(nbttagcompound.getList("Attributes", 10));
        }

        if (nbttagcompound.contains("ActiveEffects", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("ActiveEffects", 10);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);
                MobEffectInstance mobeffect = MobEffectInstance.load(nbttagcompound1);

                if (mobeffect != null) {
                    this.activeEffects.put(mobeffect.getEffect(), mobeffect);
                }
            }
        }

        // CraftBukkit start
        if (nbttagcompound.contains("Bukkit.MaxHealth")) {
            net.minecraft.nbt.Tag nbtbase = nbttagcompound.get("Bukkit.MaxHealth");
            if (nbtbase.getId() == 5) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(((FloatTag) nbtbase).getAsDouble());
            } else if (nbtbase.getId() == 3) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(((IntTag) nbtbase).getAsDouble());
            }
        }
        // CraftBukkit end

        if (nbttagcompound.contains("Health", 99)) {
            this.setHealth(nbttagcompound.getFloat("Health"));
        }

        this.hurtTime = nbttagcompound.getShort("HurtTime");
        this.deathTime = nbttagcompound.getShort("DeathTime");
        this.lastHurtByMobTimestamp = nbttagcompound.getInt("HurtByTimestamp");
        if (nbttagcompound.contains("Team", 8)) {
            String s = nbttagcompound.getString("Team");
            PlayerTeam scoreboardteam = this.level.getScoreboard().getPlayerTeam(s);
            boolean flag = scoreboardteam != null && this.level.getScoreboard().addPlayerToTeam(this.getStringUUID(), scoreboardteam);

            if (!flag) {
                net.minecraft.world.entity.LivingEntity.LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", s);
            }
        }

        if (nbttagcompound.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (nbttagcompound.contains("SleepingX", 99) && nbttagcompound.contains("SleepingY", 99) && nbttagcompound.contains("SleepingZ", 99)) {
            BlockPos blockposition = new BlockPos(nbttagcompound.getInt("SleepingX"), nbttagcompound.getInt("SleepingY"), nbttagcompound.getInt("SleepingZ"));

            this.setSleepingPos(blockposition);
            this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockposition);
            }
        }

        if (nbttagcompound.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("Brain")));
        }

    }

    // CraftBukkit start
    private boolean isTickingEffects = false;
    private List<net.minecraft.world.entity.LivingEntity.ProcessableEffect> effectsToProcess = Lists.newArrayList();

    private static class ProcessableEffect {

        private MobEffect type;
        private MobEffectInstance effect;
        private final EntityPotionEffectEvent.Cause cause;

        private ProcessableEffect(MobEffectInstance effect, EntityPotionEffectEvent.Cause cause) {
            this.effect = effect;
            this.cause = cause;
        }

        private ProcessableEffect(MobEffect type, EntityPotionEffectEvent.Cause cause) {
            this.type = type;
            this.cause = cause;
        }
    }
    // CraftBukkit end

    protected void tickEffects() {
        Iterator iterator = this.activeEffects.keySet().iterator();

        isTickingEffects = true; // CraftBukkit
        try {
            while (iterator.hasNext()) {
                MobEffect mobeffectlist = (MobEffect) iterator.next();
                MobEffectInstance mobeffect = (MobEffectInstance) this.activeEffects.get(mobeffectlist);

                if (!mobeffect.tick(this, () -> {
                    this.onEffectUpdated(mobeffect, true);
                })) {
                    if (!this.level.isClientSide) {
                        // CraftBukkit start
                        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobeffect, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.EXPIRATION);
                        if (event.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end
                        iterator.remove();
                        this.onEffectRemoved(mobeffect);
                    }
                } else if (mobeffect.getDuration() % 600 == 0) {
                    this.onEffectUpdated(mobeffect, false);
                }
            }
        } catch (ConcurrentModificationException concurrentmodificationexception) {
            ;
        }
        // CraftBukkit start
        isTickingEffects = false;
        for (net.minecraft.world.entity.LivingEntity.ProcessableEffect e : effectsToProcess) {
            if (e.effect != null) {
                addEffect(e.effect, e.cause);
            } else {
                removeEffect(e.type, e.cause);
            }
        }
        effectsToProcess.clear();
        // CraftBukkit end

        if (this.effectsDirty) {
            if (!this.level.isClientSide) {
                this.updateInvisibilityStatus();
            }

            this.effectsDirty = false;
        }

        int i = (Integer) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_COLOR_ID);
        boolean flag = (Boolean) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_AMBIENCE_ID);

        if (i > 0) {
            boolean flag1;

            if (this.isInvisible()) {
                flag1 = this.random.nextInt(15) == 0;
            } else {
                flag1 = this.random.nextBoolean();
            }

            if (flag) {
                flag1 &= this.random.nextInt(5) == 0;
            }

            if (flag1 && i > 0) {
                double d0 = (double) (i >> 16 & 255) / 255.0D;
                double d1 = (double) (i >> 8 & 255) / 255.0D;
                double d2 = (double) (i >> 0 & 255) / 255.0D;

                this.level.addParticle(flag ? ParticleTypes.AMBIENT_ENTITY_EFFECT : ParticleTypes.ENTITY_EFFECT, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), d0, d1, d2);
            }
        }

    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            Collection<MobEffectInstance> collection = this.activeEffects.values();

            this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(collection));
            this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_COLOR_ID, PotionUtils.getColor(collection));
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
        }

    }

    public double getVisibilityPercent(@Nullable Entity entity) {
        double d0 = 1.0D;

        if (this.isDiscrete()) {
            d0 *= 0.8D;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();

            if (f < 0.1F) {
                f = 0.1F;
            }

            d0 *= 0.7D * (double) f;
        }

        if (entity != null) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
            Item item = itemstack.getItem();
            EntityType<?> entitytypes = entity.getType();

            if (entitytypes == EntityType.SKELETON && item == Items.SKELETON_SKULL || entitytypes == EntityType.ZOMBIE && item == Items.ZOMBIE_HEAD || entitytypes == EntityType.CREEPER && item == Items.CREEPER_HEAD) {
                d0 *= 0.5D;
            }
        }

        return d0;
    }

    public boolean canAttack(net.minecraft.world.entity.LivingEntity entityliving) {
        return true;
    }

    public boolean canAttack(net.minecraft.world.entity.LivingEntity entityliving, TargetingConditions pathfindertargetcondition) {
        return pathfindertargetcondition.test(this, entityliving);
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> collection) {
        Iterator iterator = collection.iterator();

        MobEffectInstance mobeffect;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            mobeffect = (MobEffectInstance) iterator.next();
        } while (mobeffect.isAmbient());

        return false;
    }

    protected void removeEffectParticles() {
        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_AMBIENCE_ID, false);
        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_EFFECT_COLOR_ID, 0);
    }

    // CraftBukkit start
    public boolean removeAllEffects() {
        return removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeAllEffects(EntityPotionEffectEvent.Cause cause) {
        // CraftBukkit end
        if (this.level.isClientSide) {
            return false;
        } else {
            Iterator<MobEffectInstance> iterator = this.activeEffects.values().iterator();

            boolean flag;

            for (flag = false; iterator.hasNext(); flag = true) {
                // CraftBukkit start
                MobEffectInstance effect = (MobEffectInstance) iterator.next();
                EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause, EntityPotionEffectEvent.Action.CLEARED);
                if (event.isCancelled()) {
                    continue;
                }
                this.onEffectRemoved(effect);
                // CraftBukkit end
                iterator.remove();
            }

            return flag;
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<MobEffect, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(MobEffect mobeffectlist) {
        return this.activeEffects.containsKey(mobeffectlist);
    }

    @Nullable
    public MobEffectInstance getEffect(MobEffect mobeffectlist) {
        return (MobEffectInstance) this.activeEffects.get(mobeffectlist);
    }

    // CraftBukkit start
    public boolean addEffect(MobEffectInstance mobeffect) {
        return addEffect(mobeffect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean addEffect(MobEffectInstance mobeffect, EntityPotionEffectEvent.Cause cause) {
        org.spigotmc.AsyncCatcher.catchOp("effect add"); // Spigot
        if (isTickingEffects) {
            effectsToProcess.add(new net.minecraft.world.entity.LivingEntity.ProcessableEffect(mobeffect, cause));
            return true;
        }
        // CraftBukkit end

        if (!this.canBeAffected(mobeffect)) {
            return false;
        } else {
            MobEffectInstance mobeffect1 = (MobEffectInstance) this.activeEffects.get(mobeffect.getEffect());

            // CraftBukkit start
            boolean override = false;
            if (mobeffect1 != null) {
                override = new MobEffectInstance(mobeffect1).update(mobeffect);
            }

            EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobeffect1, mobeffect, cause, override);
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end

            if (mobeffect1 == null) {
                this.activeEffects.put(mobeffect.getEffect(), mobeffect);
                this.onEffectAdded(mobeffect);
                return true;
                // CraftBukkit start
            } else if (event.isOverride()) {
                mobeffect1.update(mobeffect);
                this.onEffectUpdated(mobeffect1, true);
                // CraftBukkit end
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean canBeAffected(MobEffectInstance mobeffect) {
        if (this.getMobType() == MobType.UNDEAD) {
            MobEffect mobeffectlist = mobeffect.getEffect();

            if (mobeffectlist == MobEffects.REGENERATION || mobeffectlist == MobEffects.POISON) {
                return false;
            }
        }

        return true;
    }

    public boolean isInvertedHealAndHarm() {
        return this.getMobType() == MobType.UNDEAD;
    }

    // CraftBukkit start
    @Nullable
    public MobEffectInstance removeEffectNoUpdate(@Nullable MobEffect mobeffectlist) {
        return c(mobeffectlist, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    @Nullable
    public MobEffectInstance c(@Nullable MobEffect mobeffectlist, EntityPotionEffectEvent.Cause cause) {
        if (isTickingEffects) {
            effectsToProcess.add(new net.minecraft.world.entity.LivingEntity.ProcessableEffect(mobeffectlist, cause));
            return null;
        }

        MobEffectInstance effect = this.activeEffects.get(mobeffectlist);
        if (effect == null) {
            return null;
        }

        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause);
        if (event.isCancelled()) {
            return null;
        }

        return (MobEffectInstance) this.activeEffects.remove(mobeffectlist);
    }

    public boolean removeEffect(MobEffect mobeffectlist) {
        return removeEffect(mobeffectlist, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeEffect(MobEffect mobeffectlist, EntityPotionEffectEvent.Cause cause) {
        MobEffectInstance mobeffect = this.c(mobeffectlist, cause);
        // CraftBukkit end

        if (mobeffect != null) {
            this.onEffectRemoved(mobeffect);
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance mobeffect) {
        this.effectsDirty = true;
        if (!this.level.isClientSide) {
            mobeffect.getEffect().addAttributeModifiers(this, this.getAttributes(), mobeffect.getAmplifier());
        }

    }

    protected void onEffectUpdated(MobEffectInstance mobeffect, boolean flag) {
        this.effectsDirty = true;
        if (flag && !this.level.isClientSide) {
            MobEffect mobeffectlist = mobeffect.getEffect();

            mobeffectlist.removeAttributeModifiers(this, this.getAttributes(), mobeffect.getAmplifier());
            mobeffectlist.addAttributeModifiers(this, this.getAttributes(), mobeffect.getAmplifier());
        }

    }

    protected void onEffectRemoved(MobEffectInstance mobeffect) {
        this.effectsDirty = true;
        if (!this.level.isClientSide) {
            mobeffect.getEffect().removeAttributeModifiers(this, this.getAttributes(), mobeffect.getAmplifier());
        }

    }

    // CraftBukkit start - Delegate so we can handle providing a reason for health being regained
    public void heal(float f) {
        heal(f, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    public void heal(float f, EntityRegainHealthEvent.RegainReason regainReason) {
        float f1 = this.getHealth();

        if (f1 > 0.0F) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), f, regainReason);
            // Suppress during worldgen
            if (this.valid) {
                this.level.getServerOH().getPluginManager().callEvent(event);
            }

            if (!event.isCancelled()) {
                this.setHealth((float) (this.getHealth() + event.getAmount()));
            }
            // CraftBukkit end
        }

    }

    public float getHealth() {
        // CraftBukkit start - Use unscaled health
        if (this instanceof ServerPlayer) {
            return (float) ((ServerPlayer) this).getBukkitEntity().getHealth();
        }
        // CraftBukkit end
        return (Float) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID);
    }

    public void setHealth(float f) {
        // CraftBukkit start - Handle scaled health
        if (this instanceof ServerPlayer) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = ((ServerPlayer) this).getBukkitEntity();
            // Squeeze
            if (f < 0.0F) {
                player.setRealHealth(0.0D);
            } else if (f > player.getMaxHealth()) {
                player.setRealHealth(player.getMaxHealth());
            } else {
                player.setRealHealth(f);
            }

            player.updateScaledHealth(false);
            return;
        }
        // CraftBukkit end
        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID, Mth.clamp(f, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (this.level.isClientSide) {
            return false;
        } else if (this.removed || this.dead || this.getHealth() <= 0.0F) { // CraftBukkit - Don't allow entities that got set to dead/killed elsewhere to get damaged and die
            return false;
        } else if (damagesource.isFire() && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping() && !this.level.isClientSide) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            float f1 = f;

            // CraftBukkit - Moved into damageEntity0(DamageSource, float)
            if (false && (damagesource == DamageSource.ANVIL || damagesource == DamageSource.FALLING_BLOCK) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.getItemBySlot(EquipmentSlot.HEAD).hurtAndBreak((int) (f * 4.0F + this.random.nextFloat() * f * 2.0F), this, (entityliving) -> {
                    entityliving.broadcastBreakEvent(EquipmentSlot.HEAD);
                });
                f *= 0.75F;
            }

            boolean flag = f > 0.0F && this.isDamageSourceBlocked(damagesource); // Copied from below
            float f2 = 0.0F;

            // CraftBukkit - Moved into damageEntity0(DamageSource, float)
            if (false && f > 0.0F && this.isDamageSourceBlocked(damagesource)) {
                this.hurtCurrentlyUsedShield(f);
                f2 = f;
                f = 0.0F;
                if (!damagesource.isProjectile()) {
                    Entity entity = damagesource.getDirectEntity();

                    if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                        this.blockUsingShield((net.minecraft.world.entity.LivingEntity) entity);
                    }
                }

                flag = true;
            }

            this.animationSpeed = 1.5F;
            boolean flag1 = true;

            if ((float) this.invulnerableTime > 10.0F) {
                if (f <= this.lastHurt) {
                    this.forceExplosionKnockback = true; // CraftBukkit - SPIGOT-949 - for vanilla consistency, cooldown does not prevent explosion knockback
                    return false;
                }

                // CraftBukkit start
                if (!this.damageEntity0(damagesource, f - this.lastHurt)) {
                    return false;
                }
                // CraftBukkit end
                this.lastHurt = f;
                flag1 = false;
            } else {
                // CraftBukkit start
                if (!this.damageEntity0(damagesource, f)) {
                    return false;
                }
                this.lastHurt = f;
                this.invulnerableTime = 20;
                // this.damageEntity0(damagesource, f);
                // CraftBukkit end
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            // CraftBukkit start
            if (this instanceof Animal) {
                ((Animal) this).resetLove();
                if (this instanceof TamableAnimal) {
                    ((TamableAnimal) this).setOrderedToSit(false);
                }
            }
            // CraftBukkit end

            this.hurtDir = 0.0F;
            Entity entity1 = damagesource.getEntity();

            if (entity1 != null) {
                if (entity1 instanceof net.minecraft.world.entity.LivingEntity) {
                    this.setLastHurtByMob((net.minecraft.world.entity.LivingEntity) entity1);
                }

                if (entity1 instanceof net.minecraft.world.entity.player.Player) {
                    this.lastHurtByPlayerTime = 100;
                    this.lastHurtByPlayer = (net.minecraft.world.entity.player.Player) entity1;
                } else if (entity1 instanceof Wolf) {
                    Wolf entitywolf = (Wolf) entity1;

                    if (entitywolf.isTame()) {
                        this.lastHurtByPlayerTime = 100;
                        net.minecraft.world.entity.LivingEntity entityliving = entitywolf.getOwner();

                        if (entityliving != null && entityliving.getType() == EntityType.PLAYER) {
                            this.lastHurtByPlayer = (net.minecraft.world.entity.player.Player) entityliving;
                        } else {
                            this.lastHurtByPlayer = null;
                        }
                    }
                }
            }

            if (flag1) {
                if (flag) {
                    this.level.broadcastEntityEvent(this, (byte) 29);
                } else if (damagesource instanceof EntityDamageSource && ((EntityDamageSource) damagesource).isThorns()) {
                    this.level.broadcastEntityEvent(this, (byte) 33);
                } else {
                    byte b0;

                    if (damagesource == DamageSource.DROWN) {
                        b0 = 36;
                    } else if (damagesource.isFire()) {
                        b0 = 37;
                    } else if (damagesource == DamageSource.SWEET_BERRY_BUSH) {
                        b0 = 44;
                    } else {
                        b0 = 2;
                    }

                    this.level.broadcastEntityEvent(this, b0);
                }

                if (damagesource != DamageSource.DROWN && (!flag || f > 0.0F)) {
                    this.markHurt();
                }

                if (entity1 != null) {
                    double d0 = entity1.getX() - this.getX();

                    double d1;

                    for (d1 = entity1.getZ() - this.getZ(); d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
                        d0 = (Math.random() - Math.random()) * 0.01D;
                    }

                    this.hurtDir = (float) (Mth.atan2(d1, d0) * 57.2957763671875D - (double) this.yRot);
                    this.knockback(0.4F, d0, d1);
                } else {
                    this.hurtDir = (float) ((int) (Math.random() * 2.0D) * 180);
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(damagesource)) {
                    SoundEvent soundeffect = this.getSoundDeath();

                    if (flag1 && soundeffect != null) {
                        this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
                    }

                    this.die(damagesource);
                }
            } else if (flag1) {
                this.playHurtSound(damagesource);
            }

            boolean flag2 = !flag || f > 0.0F;

            if (flag2) {
                this.lastDamageSource = damagesource;
                this.lastDamageStamp = this.level.getGameTime();
            }

            if (this instanceof ServerPlayer) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer) this, damagesource, f1, f, flag);
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    ((ServerPlayer) this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f2 * 10.0F));
                }
            }

            if (entity1 instanceof ServerPlayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer) entity1, this, damagesource, f1, f, flag);
            }

            return flag2;
        }
    }

    protected void blockUsingShield(net.minecraft.world.entity.LivingEntity entityliving) {
        entityliving.blockedByShield(this);
    }

    protected void blockedByShield(net.minecraft.world.entity.LivingEntity entityliving) {
        entityliving.knockback(0.5F, entityliving.getX() - this.getX(), entityliving.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource damagesource) {
        if (damagesource.isBypassInvul()) {
            return false;
        } else {
            ItemStack itemstack = null;
            InteractionHand[] aenumhand = InteractionHand.values();
            int i = aenumhand.length;

            // CraftBukkit start
            ItemStack itemstack1 = ItemStack.EMPTY;
            for (int j = 0; j < i; ++j) {
                InteractionHand enumhand = aenumhand[j];
                itemstack1 = this.getItemInHand(enumhand);

                if (itemstack1.getItem() == Items.TOTEM_OF_UNDYING) {
                    itemstack = itemstack1.copy();
                    // itemstack1.subtract(1); // CraftBukkit
                    break;
                }
            }

            EntityResurrectEvent event = new EntityResurrectEvent((org.bukkit.entity.LivingEntity) this.getBukkitEntity());
            event.setCancelled(itemstack == null);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                if (!itemstack1.isEmpty()) {
                    itemstack1.shrink(1);
                }
                if (itemstack != null && this instanceof ServerPlayer) {
                    // CraftBukkit end
                    ServerPlayer entityplayer = (ServerPlayer) this;

                    entityplayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                    CriteriaTriggers.USED_TOTEM.trigger(entityplayer, itemstack);
                }

                this.setHealth(1.0F);
                // CraftBukkit start
                this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TOTEM);
                // CraftBukkit end
                this.level.broadcastEntityEvent(this, (byte) 35);
            }

            return !event.isCancelled();
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level.getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource damagesource) {
        SoundEvent soundeffect = this.getSoundHurt(damagesource);

        if (soundeffect != null) {
            this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    private boolean isDamageSourceBlocked(DamageSource damagesource) {
        Entity entity = damagesource.getDirectEntity();
        boolean flag = false;

        if (entity instanceof AbstractArrow) {
            AbstractArrow entityarrow = (AbstractArrow) entity;

            if (entityarrow.getPierceLevel() > 0) {
                flag = true;
            }
        }

        if (!damagesource.isBypassArmor() && this.isBlocking() && !flag) {
            Vec3 vec3d = damagesource.getSourcePosition();

            if (vec3d != null) {
                Vec3 vec3d1 = this.getViewVector(1.0F);
                Vec3 vec3d2 = vec3d.vectorTo(this.position()).normalize();

                vec3d2 = new Vec3(vec3d2.x, 0.0D, vec3d2.z);
                if (vec3d2.dot(vec3d1) < 0.0D) {
                    return true;
                }
            }
        }

        return false;
    }

    public void die(DamageSource damagesource) {
        if (!this.removed && !this.dead) {
            Entity entity = damagesource.getEntity();
            net.minecraft.world.entity.LivingEntity entityliving = this.getKillCredit();

            if (this.deathScore >= 0 && entityliving != null) {
                entityliving.awardKillScore(this, this.deathScore, damagesource);
            }

            if (entity != null) {
                entity.killed(this);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            if (!this.level.isClientSide) {
                this.dropAllDeathLoot(damagesource);
                this.createWitherRose(entityliving);
            }

            this.level.broadcastEntityEvent(this, (byte) 3);
            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable net.minecraft.world.entity.LivingEntity entityliving) {
        if (!this.level.isClientSide) {
            boolean flag = false;

            if (entityliving instanceof WitherBoss) {
                if (this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    BlockPos blockposition = this.blockPosition();
                    BlockState iblockdata = Blocks.WITHER_ROSE.getBlockData();

                    if (this.level.getType(blockposition).isAir() && iblockdata.canSurvive(this.level, blockposition)) {
                        this.level.setTypeAndData(blockposition, iblockdata, 3);
                        flag = true;
                    }
                }

                if (!flag) {
                    ItemEntity entityitem = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));

                    this.level.addFreshEntity(entityitem);
                }
            }

        }
    }

    protected void dropAllDeathLoot(DamageSource damagesource) {
        Entity entity = damagesource.getEntity();
        int i;

        if (entity instanceof net.minecraft.world.entity.player.Player) {
            i = EnchantmentHelper.getMobLooting((net.minecraft.world.entity.LivingEntity) entity);
        } else {
            i = 0;
        }

        boolean flag = this.lastHurtByPlayerTime > 0;

        this.dropEquipment(); // CraftBukkit - from below
        if (this.shouldDropLoot() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(damagesource, flag);
            this.dropCustomDeathLoot(damagesource, i, flag);
            // CraftBukkit start - Call death event
            CraftEventFactory.callEntityDeathEvent(this, this.drops);
            this.drops = new ArrayList<org.bukkit.inventory.ItemStack>();
        } else {
            CraftEventFactory.callEntityDeathEvent(this);
            // CraftBukkit end
        }

        // this.dropInventory();// CraftBukkit - moved up
        this.dropExperience();
    }

    protected void dropEquipment() {}

    // CraftBukkit start
    public int getExpReward() {
        if (!this.level.isClientSide && (this.isAlwaysExperienceDropper() || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) {
            int i = this.getExperienceReward(this.lastHurtByPlayer);
            return i;
        } else {
            return 0;
        }
    }
    // CraftBukkit end

    protected void dropExperience() {
        // CraftBukkit start - Update getExpReward() above if the removed if() changes!
        if (true) {
            int i = this.expToDrop;
            while (i > 0) {
                int j = ExperienceOrb.getExperienceValue(i);

                i -= j;
                this.level.addFreshEntity(new ExperienceOrb(this.level, this.getX(), this.getY(), this.getZ(), j));
            }
            this.expToDrop = 0;
        }
        // CraftBukkit end

    }

    protected void dropCustomDeathLoot(DamageSource damagesource, int i, boolean flag) {}

    public ResourceLocation do_() {
        return this.getType().getDefaultLootTable();
    }

    protected void dropFromLootTable(DamageSource damagesource, boolean flag) {
        ResourceLocation minecraftkey = this.do_();
        LootTable loottable = this.level.getServer().getLootTables().get(minecraftkey);
        LootContext.Builder loottableinfo_builder = this.createLootContext(flag, damagesource);

        loottable.getRandomItems(loottableinfo_builder.create(LootContextParamSets.ENTITY), this::spawnAtLocation);
    }

    protected LootContext.Builder createLootContext(boolean flag, DamageSource damagesource) {
        LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.level)).withRandom(this.random).set(LootContextParams.THIS_ENTITY, this).set(LootContextParams.BLOCK_POS, this.blockPosition()).set(LootContextParams.DAMAGE_SOURCE, damagesource).setOptional(LootContextParams.KILLER_ENTITY, damagesource.getEntity()).setOptional(LootContextParams.DIRECT_KILLER_ENTITY, damagesource.getDirectEntity());

        if (flag && this.lastHurtByPlayer != null) {
            loottableinfo_builder = loottableinfo_builder.set(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer).withLuck(this.lastHurtByPlayer.getLuck());
        }

        return loottableinfo_builder;
    }

    public void knockback(float f, double d0, double d1) {
        f = (float) ((double) f * (1.0D - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        if (f > 0.0F) {
            this.hasImpulse = true;
            Vec3 vec3d = this.getDeltaMovement();
            Vec3 vec3d1 = (new Vec3(d0, 0.0D, d1)).normalize().scale((double) f);

            this.setDeltaMovement(vec3d.x / 2.0D - vec3d1.x, this.onGround ? Math.min(0.4D, vec3d.y / 2.0D + (double) f) : vec3d.y, vec3d.z / 2.0D - vec3d1.z);
<<<<<<< HEAD
=======

            // Paper start - call EntityKnockbackByEntityEvent
            Vec3 currentMot = this.getDeltaMovement();
            org.bukkit.util.Vector delta = new org.bukkit.util.Vector(currentMot.x - vec3d.x, currentMot.y - vec3d.y, currentMot.z - vec3d.z);
            // Restore old velocity to be able to access it in the event
            this.setDeltaMovement(vec3d);
            if (entity == null || new com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent((org.bukkit.entity.LivingEntity) getBukkitEntity(), entity.getBukkitEntity(), f, delta).callEvent()) {
                this.setDeltaMovement(vec3d.x + delta.getX(), vec3d.y + delta.getY(), vec3d.z + delta.getZ());
            }
            // Paper end
>>>>>>> Toothpick
        }
    }

    @Nullable
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    protected SoundEvent getSoundDeath() {
        return SoundEvents.GENERIC_DEATH;
    }

    protected SoundEvent getSoundFall(int i) {
        return i > 4 ? SoundEvents.GENERIC_BIG_FALL : SoundEvents.GENERIC_SMALL_FALL;
    }

    protected SoundEvent getDrinkingSound(ItemStack itemstack) {
        return itemstack.getDrinkingSound();
    }

    public SoundEvent getEatingSound(ItemStack itemstack) {
        return itemstack.getEatingSound();
    }

    @Override
    public void setOnGround(boolean flag) {
        super.setOnGround(flag);
        if (flag) {
            this.lastClimbablePos = Optional.empty();
        }

    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockposition = this.blockPosition();
            BlockState iblockdata = this.getFeetBlockState();
            Block block = iblockdata.getBlock();

            if (block.is((Tag) BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockposition);
                return true;
            } else if (block instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockposition, iblockdata)) {
                this.lastClimbablePos = Optional.of(blockposition);
                return true;
            } else {
                return false;
            }
        }
    }

    public BlockState getFeetBlockState() {
        return this.level.getType(this.blockPosition());
    }

    private boolean trapdoorUsableAsLadder(BlockPos blockposition, BlockState iblockdata) {
        if ((Boolean) iblockdata.getValue(TrapDoorBlock.OPEN)) {
            BlockState iblockdata1 = this.level.getType(blockposition.below());

            if (iblockdata1.is(Blocks.LADDER) && iblockdata1.getValue(LadderBlock.FACING) == iblockdata.getValue(TrapDoorBlock.FACING)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isAlive() {
        return !this.removed && this.getHealth() > 0.0F;
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        boolean flag = super.causeFallDamage(f, f1);
        int i = this.calculateFallDamage(f, f1);

        if (i > 0) {
            // CraftBukkit start
            if (!this.hurt(DamageSource.FALL, (float) i)) {
                return true;
            }
            // CraftBukkit end
            this.playSound(this.getSoundFall(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            // this.damageEntity(DamageSource.FALL, (float) i); // CraftBukkit - moved up
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(float f, float f1) {
        MobEffectInstance mobeffect = this.getEffect(MobEffects.JUMP);
        float f2 = mobeffect == null ? 0.0F : (float) (mobeffect.getAmplifier() + 1);

        return Mth.ceil((f - 3.0F - f2) * f1);
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - 0.20000000298023224D);
            int k = Mth.floor(this.getZ());
            BlockState iblockdata = this.level.getType(new BlockPos(i, j, k));

            if (!iblockdata.isAir()) {
                SoundType soundeffecttype = iblockdata.getStepSound();

                this.playSound(soundeffecttype.getFallSound(), soundeffecttype.getVolume() * 0.5F, soundeffecttype.getPitch() * 0.75F);
            }

        }
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource damagesource, float f) {}

    protected void hurtCurrentlyUsedShield(float f) {}

    protected float getDamageAfterArmorAbsorb(DamageSource damagesource, float f) {
        if (!damagesource.isBypassArmor()) {
            // this.damageArmor(damagesource, f); // CraftBukkit - Moved into damageEntity0(DamageSource, float)
            f = CombatRules.getDamageAfterAbsorb(f, (float) this.getArmorValue(), (float) this.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }

        return f;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource damagesource, float f) {
        if (damagesource.isBypassMagic()) {
            return f;
        } else {
            int i;

            // CraftBukkit - Moved to damageEntity0(DamageSource, float)
            if (false && this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && damagesource != DamageSource.OUT_OF_WORLD) {
                i = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - i;
                float f1 = f * (float) j;
                float f2 = f;

                f = Math.max(f1 / 25.0F, 0.0F);
                float f3 = f2 - f;

                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damagesource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer) damagesource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            if (f <= 0.0F) {
                return 0.0F;
            } else {
                i = EnchantmentHelper.getDamageProtection(this.getArmorSlots(), damagesource);
                if (i > 0) {
                    f = CombatRules.getDamageAfterMagicAbsorb(f, (float) i);
                }

                return f;
            }
        }
    }

    // CraftBukkit start
    protected boolean damageEntity0(final DamageSource damagesource, float f) { // void -> boolean, add final
       if (!this.isInvulnerableTo(damagesource)) {
            final boolean human = this instanceof net.minecraft.world.entity.player.Player;
            float originalDamage = f;
            Function<Double, Double> hardHat = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    if ((damagesource == DamageSource.ANVIL || damagesource == DamageSource.FALLING_BLOCK) && !net.minecraft.world.entity.LivingEntity.this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                        return -(f - (f * 0.75F));

                    }
                    return -0.0;
                }
            };
            float hardHatModifier = hardHat.apply((double) f).floatValue();
            f += hardHatModifier;

            Function<Double, Double> blocking = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -((net.minecraft.world.entity.LivingEntity.this.isDamageSourceBlocked(damagesource)) ? f : 0.0);
                }
            };
            float blockingModifier = blocking.apply((double) f).floatValue();
            f += blockingModifier;

            Function<Double, Double> armor = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -(f - net.minecraft.world.entity.LivingEntity.this.getDamageAfterArmorAbsorb(damagesource, f.floatValue()));
                }
            };
            float armorModifier = armor.apply((double) f).floatValue();
            f += armorModifier;

            Function<Double, Double> resistance = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    if (!damagesource.isBypassMagic() && net.minecraft.world.entity.LivingEntity.this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && damagesource != DamageSource.OUT_OF_WORLD) {
                        int i = (net.minecraft.world.entity.LivingEntity.this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                        int j = 25 - i;
                        float f1 = f.floatValue() * (float) j;
                        return -(f - (f1 / 25.0F));
                    }
                    return -0.0;
                }
            };
            float resistanceModifier = resistance.apply((double) f).floatValue();
            f += resistanceModifier;

            Function<Double, Double> magic = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -(f - net.minecraft.world.entity.LivingEntity.this.getDamageAfterMagicAbsorb(damagesource, f.floatValue()));
                }
            };
            float magicModifier = magic.apply((double) f).floatValue();
            f += magicModifier;

            Function<Double, Double> absorption = new Function<Double, Double>() {
                @Override
                public Double apply(Double f) {
                    return -(Math.max(f - Math.max(f - net.minecraft.world.entity.LivingEntity.this.getAbsorptionAmount(), 0.0F), 0.0F));
                }
            };
            float absorptionModifier = absorption.apply((double) f).floatValue();

            EntityDamageEvent event = CraftEventFactory.handleLivingEntityDamageEvent(this, damagesource, originalDamage, hardHatModifier, blockingModifier, armorModifier, resistanceModifier, magicModifier, absorptionModifier, hardHat, blocking, armor, resistance, magic, absorption);
            if (damagesource.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                ((net.minecraft.world.entity.player.Player) damagesource.getEntity()).resetAttackStrengthTicker(); // Moved from EntityHuman in order to make the cooldown reset get called after the damage event is fired
            }
            if (event.isCancelled()) {
                return false;
            }

            f = (float) event.getFinalDamage();

            // Resistance
            if (event.getDamage(DamageModifier.RESISTANCE) < 0) {
                float f3 = (float) -event.getDamage(DamageModifier.RESISTANCE);
                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damagesource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer) damagesource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            // Apply damage to helmet
            if ((damagesource == DamageSource.ANVIL || damagesource == DamageSource.FALLING_BLOCK) && this.getItemBySlot(EquipmentSlot.HEAD) != null) {
                this.getItemBySlot(EquipmentSlot.HEAD).hurtAndBreak((int) (event.getDamage() * 4.0F + this.random.nextFloat() * event.getDamage() * 2.0F), this, (entityliving) -> {
                    entityliving.broadcastBreakEvent(EquipmentSlot.HEAD);
                });
            }

            // Apply damage to armor
            if (!damagesource.isBypassArmor()) {
                float armorDamage = (float) (event.getDamage() + event.getDamage(DamageModifier.BLOCKING) + event.getDamage(DamageModifier.HARD_HAT));
                this.hurtArmor(damagesource, armorDamage);
            }

            // Apply blocking code // PAIL: steal from above
            if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                this.level.broadcastEntityEvent(this, (byte) 29); // SPIGOT-4635 - shield damage sound
                this.hurtCurrentlyUsedShield((float) -event.getDamage(DamageModifier.BLOCKING));
                Entity entity = damagesource.getDirectEntity();

                if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                    this.blockUsingShield((net.minecraft.world.entity.LivingEntity) entity);
                }
            }

            absorptionModifier = (float) -event.getDamage(DamageModifier.ABSORPTION);
            this.setAbsorptionAmount(Math.max(this.getAbsorptionAmount() - absorptionModifier, 0.0F));
            float f2 = absorptionModifier;

            if (f2 > 0.0F && f2 < 3.4028235E37F && this instanceof net.minecraft.world.entity.player.Player) {
                ((net.minecraft.world.entity.player.Player) this).awardStat(Stats.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }
            if (f2 > 0.0F && f2 < 3.4028235E37F && damagesource.getEntity() instanceof ServerPlayer) {
                ((ServerPlayer) damagesource.getEntity()).awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f2 * 10.0F));
            }

            if (f > 0 || !human) {
                if (human) {
                    // PAIL: Be sure to drag all this code from the EntityHuman subclass each update.
                    ((net.minecraft.world.entity.player.Player) this).causeFoodExhaustion(damagesource.getFoodExhaustion());
                    if (f < 3.4028235E37F) {
                        ((net.minecraft.world.entity.player.Player) this).awardStat(Stats.DAMAGE_TAKEN, Math.round(f * 10.0F));
                    }
                }
                // CraftBukkit end
                float f3 = this.getHealth();

                this.setHealth(f3 - f);
                this.getCombatTracker().recordDamage(damagesource, f3, f);
                // CraftBukkit start
                if (!human) {
                    this.setAbsorptionAmount(this.getAbsorptionAmount() - f);
                }

                return true;
            } else {
                // Duplicate triggers if blocking
                if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                    if (this instanceof ServerPlayer) {
                        CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer) this, damagesource, f, originalDamage, true);
                        f2 = (float) -event.getDamage(DamageModifier.BLOCKING);
                        if (f2 > 0.0F && f2 < 3.4028235E37F) {
                            ((ServerPlayer) this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(originalDamage * 10.0F));
                        }
                    }

                    if (damagesource.getEntity() instanceof ServerPlayer) {
                        CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer) damagesource.getEntity(), this, damagesource, f, originalDamage, true);
                    }

                    return false;
                } else {
                    return originalDamage > 0;
                }
                // CraftBukkit end
            }
        }
        return false; // CraftBukkit
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getKillCredit() {
        return (net.minecraft.world.entity.LivingEntity) (this.combatTracker.getKiller() != null ? this.combatTracker.getKiller() : (this.lastHurtByPlayer != null ? this.lastHurtByPlayer : (this.lastHurtByMob != null ? this.lastHurtByMob : null)));
    }

    public final float getMaxHealth() {
        return (float) this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final int getArrowCount() {
        return (Integer) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int i) {
        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_ARROW_COUNT_ID, i);
    }

    public final int getStingerCount() {
        return (Integer) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int i) {
        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_STINGER_COUNT_ID, i);
    }

    private int getCurrentSwingDuration() {
        return MobEffectUtil.hasDigSpeed(this) ? 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this)) : (this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6);
    }

    public void swing(InteractionHand enumhand) {
        this.swing(enumhand, false);
    }

    public void swing(InteractionHand enumhand, boolean flag) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = enumhand;
            if (this.level instanceof ServerLevel) {
                ClientboundAnimatePacket packetplayoutanimation = new ClientboundAnimatePacket(this, enumhand == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache chunkproviderserver = ((ServerLevel) this.level).getChunkSourceOH();

                if (flag) {
                    chunkproviderserver.broadcastIncludingSelf(this, packetplayoutanimation);
                } else {
                    chunkproviderserver.broadcast(this, packetplayoutanimation);
                }
            }
        }

    }

    @Override
    protected void outOfWorld() {
        this.hurt(DamageSource.OUT_OF_WORLD, 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();

        if (this.swinging) {
            ++this.swingTime;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float) this.swingTime / (float) i;
    }

    @Nullable
    public AttributeInstance getAttribute(Attribute attributebase) {
        return this.getAttributes().getInstance(attributebase);
    }

    public double getAttributeValue(Attribute attributebase) {
        return this.getAttributes().getValue(attributebase);
    }

    public double getAttributeBaseValue(Attribute attributebase) {
        return this.getAttributes().getBaseValue(attributebase);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    public boolean isHolding(Item item) {
        return this.isHolding((item1) -> {
            return item1 == item;
        });
    }

    public boolean isHolding(Predicate<Item> predicate) {
        return predicate.test(this.getMainHandItem().getItem()) || predicate.test(this.getOffhandItem().getItem());
    }

    public ItemStack getItemInHand(InteractionHand enumhand) {
        if (enumhand == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (enumhand == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + enumhand);
        }
    }

    public void setItemInHand(InteractionHand enumhand, ItemStack itemstack) {
        if (enumhand == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
        } else {
            if (enumhand != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + enumhand);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, itemstack);
        }

    }

    public boolean hasItemInSlot(EquipmentSlot enumitemslot) {
        return !this.getItemBySlot(enumitemslot).isEmpty();
    }

    @Override
    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot enumitemslot);

    public abstract void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack);

    public float getArmorCoverPercentage() {
        Iterable<ItemStack> iterable = this.getArmorSlots();
        int i = 0;
        int j = 0;

        for (Iterator iterator = iterable.iterator(); iterator.hasNext(); ++i) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (!itemstack.isEmpty()) {
                ++j;
            }
        }

        return i > 0 ? (float) j / (float) i : 0.0F;
    }

    @Override
    public void setSprinting(boolean flag) {
        super.setSprinting(flag);
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributemodifiable.getModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SPRINTING_UUID) != null) {
            attributemodifiable.removeModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SPRINTING);
        }

        if (flag) {
            attributemodifiable.addTransientModifier(net.minecraft.world.entity.LivingEntity.SPEED_MODIFIER_SPRINTING);
        }

    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    protected float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }

    }

    private void dismountVehicle(Entity entity) {
        Vec3 vec3d;

        if (!entity.removed && !this.level.getType(entity.blockPosition()).getBlock().is((Tag) BlockTags.PORTALS)) {
            vec3d = entity.getDismountLocationForPassenger(this);
        } else {
            vec3d = new Vec3(entity.getX(), entity.getY() + (double) entity.getBbHeight(), entity.getZ());
        }

        this.teleportTo(vec3d.x, vec3d.y, vec3d.z);
    }

    protected float getJumpPower() {
        return 0.42F * this.getBlockJumpFactor();
    }

    protected void jumpFromGround() {
        float f = this.getJumpPower();

        if (this.hasEffect(MobEffects.JUMP)) {
            f += 0.1F * (float) (this.getEffect(MobEffects.JUMP).getAmplifier() + 1);
        }

        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x, (double) f, vec3d.z);
        if (this.isSprinting()) {
            float f1 = this.yRot * 0.017453292F;

            this.setDeltaMovement(this.getDeltaMovement().add((double) (-Mth.sin(f1) * 0.2F), 0.0D, (double) (Mth.cos(f1) * 0.2F)));
        }

        this.hasImpulse = true;
    }

    protected void jumpInLiquid(Tag<Fluid> tag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03999999910593033D, 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(Fluid fluidtype) {
        return false;
    }

    public void travel(Vec3 vec3d) {
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            double d0 = 0.08D;
            boolean flag = this.getDeltaMovement().y <= 0.0D;

            if (flag && this.hasEffect(MobEffects.SLOW_FALLING)) {
                d0 = 0.01D;
                this.fallDistance = 0.0F;
            }

            FluidState fluid = this.level.getFluidState(this.blockPosition());
            double d1;
            float f;

            if (this.isInWater() && this.isAffectedByFluids() && !this.canStandOnFluid(fluid.getType())) {
                d1 = this.getY();
                f = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f1 = 0.02F;
                float f2 = (float) EnchantmentHelper.getDepthStrider(this);

                if (f2 > 3.0F) {
                    f2 = 3.0F;
                }

                if (!this.onGround) {
                    f2 *= 0.5F;
                }

                if (f2 > 0.0F) {
                    f += (0.54600006F - f) * f2 / 3.0F;
                    f1 += (this.getSpeed() - f1) * f2 / 3.0F;
                }

                if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    f = 0.96F;
                }

                this.moveRelative(f1, vec3d);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec3d1 = this.getDeltaMovement();

                if (this.horizontalCollision && this.onClimbable()) {
                    vec3d1 = new Vec3(vec3d1.x, 0.2D, vec3d1.z);
                }

                this.setDeltaMovement(vec3d1.multiply((double) f, 0.800000011920929D, (double) f));
                Vec3 vec3d2 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());

                this.setDeltaMovement(vec3d2);
                if (this.horizontalCollision && this.isFree(vec3d2.x, vec3d2.y + 0.6000000238418579D - this.getY() + d1, vec3d2.z)) {
                    this.setDeltaMovement(vec3d2.x, 0.30000001192092896D, vec3d2.z);
                }
            } else if (this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluid.getType())) {
                d1 = this.getY();
                this.moveRelative(0.02F, vec3d);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec3d3;

                if (this.getFluidHeight((Tag) FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.800000011920929D, 0.5D));
                    vec3d3 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());
                    this.setDeltaMovement(vec3d3);
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                }

                if (!this.isNoGravity()) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d0 / 4.0D, 0.0D));
                }

                vec3d3 = this.getDeltaMovement();
                if (this.horizontalCollision && this.isFree(vec3d3.x, vec3d3.y + 0.6000000238418579D - this.getY() + d1, vec3d3.z)) {
                    this.setDeltaMovement(vec3d3.x, 0.30000001192092896D, vec3d3.z);
                }
            } else if (this.isFallFlying()) {
                Vec3 vec3d4 = this.getDeltaMovement();

                if (vec3d4.y > -0.5D) {
                    this.fallDistance = 1.0F;
                }

                Vec3 vec3d5 = this.getLookAngle();

                f = this.xRot * 0.017453292F;
                double d2 = Math.sqrt(vec3d5.x * vec3d5.x + vec3d5.z * vec3d5.z);
                double d3 = Math.sqrt(getHorizontalDistanceSqr(vec3d4));
                double d4 = vec3d5.length();
                float f3 = Mth.cos(f);

                f3 = (float) ((double) f3 * (double) f3 * Math.min(1.0D, d4 / 0.4D));
                vec3d4 = this.getDeltaMovement().add(0.0D, d0 * (-1.0D + (double) f3 * 0.75D), 0.0D);
                double d5;

                if (vec3d4.y < 0.0D && d2 > 0.0D) {
                    d5 = vec3d4.y * -0.1D * (double) f3;
                    vec3d4 = vec3d4.add(vec3d5.x * d5 / d2, d5, vec3d5.z * d5 / d2);
                }

                if (f < 0.0F && d2 > 0.0D) {
                    d5 = d3 * (double) (-Mth.sin(f)) * 0.04D;
                    vec3d4 = vec3d4.add(-vec3d5.x * d5 / d2, d5 * 3.2D, -vec3d5.z * d5 / d2);
                }

                if (d2 > 0.0D) {
                    vec3d4 = vec3d4.add((vec3d5.x / d2 * d3 - vec3d4.x) * 0.1D, 0.0D, (vec3d5.z / d2 * d3 - vec3d4.z) * 0.1D);
                }

                this.setDeltaMovement(vec3d4.multiply(0.9900000095367432D, 0.9800000190734863D, 0.9900000095367432D));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision && !this.level.isClientSide) {
                    d5 = Math.sqrt(getHorizontalDistanceSqr(this.getDeltaMovement()));
                    double d6 = d3 - d5;
                    float f4 = (float) (d6 * 10.0D - 3.0D);

                    if (f4 > 0.0F) {
                        this.playSound(this.getSoundFall((int) f4), 1.0F, 1.0F);
                        this.hurt(DamageSource.FLY_INTO_WALL, f4);
                    }
                }

                if (this.onGround && !this.level.isClientSide) {
                    if (getSharedFlag(7) && !CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) // CraftBukkit
                    this.setSharedFlag(7, false);
                }
            } else {
                BlockPos blockposition = this.getBlockPosBelowThatAffectsMyMovement();
                float f5 = this.level.getType(blockposition).getBlock().getFriction();

                f = this.onGround ? f5 * 0.91F : 0.91F;
                Vec3 vec3d6 = this.handleRelativeFrictionAndCalculateMovement(vec3d, f5);
                double d7 = vec3d6.y;

                if (this.hasEffect(MobEffects.LEVITATION)) {
                    d7 += (0.05D * (double) (this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - vec3d6.y) * 0.2D;
                    this.fallDistance = 0.0F;
                } else if (this.level.isClientSide && !this.level.hasChunkAt(blockposition)) {
                    if (this.getY() > 0.0D) {
                        d7 = -0.1D;
                    } else {
                        d7 = 0.0D;
                    }
                } else if (!this.isNoGravity()) {
                    d7 -= d0;
                }

                this.setDeltaMovement(vec3d6.x * (double) f, d7 * 0.9800000190734863D, vec3d6.z * (double) f);
            }
        }

        this.calculateEntityAnimation(this, this instanceof FlyingAnimal);
    }

    public void calculateEntityAnimation(net.minecraft.world.entity.LivingEntity entityliving, boolean flag) {
        entityliving.animationSpeedOld = entityliving.animationSpeed;
        double d0 = entityliving.getX() - entityliving.xo;
        double d1 = flag ? entityliving.getY() - entityliving.yo : 0.0D;
        double d2 = entityliving.getZ() - entityliving.zo;
        float f = Mth.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 4.0F;

        if (f > 1.0F) {
            f = 1.0F;
        }

        entityliving.animationSpeed += (f - entityliving.animationSpeed) * 0.4F;
        entityliving.animationPosition += entityliving.animationSpeed;
    }

    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 vec3d, float f) {
        this.moveRelative(this.getFrictionInfluencedSpeed(f), vec3d);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec3d1 = this.getDeltaMovement();

        if ((this.horizontalCollision || this.jumping) && this.onClimbable()) {
            vec3d1 = new Vec3(vec3d1.x, 0.2D, vec3d1.z);
        }

        return vec3d1;
    }

    public Vec3 getFluidFallingAdjustedMovement(double d0, boolean flag, Vec3 vec3d) {
        if (!this.isNoGravity() && !this.isSprinting()) {
            double d1;

            if (flag && Math.abs(vec3d.y - 0.005D) >= 0.003D && Math.abs(vec3d.y - d0 / 16.0D) < 0.003D) {
                d1 = -0.003D;
            } else {
                d1 = vec3d.y - d0 / 16.0D;
            }

            return new Vec3(vec3d.x, d1, vec3d.z);
        } else {
            return vec3d;
        }
    }

    private Vec3 handleOnClimbable(Vec3 vec3d) {
        if (this.onClimbable()) {
            this.fallDistance = 0.0F;
            float f = 0.15F;
            double d0 = Mth.clamp(vec3d.x, -0.15000000596046448D, 0.15000000596046448D);
            double d1 = Mth.clamp(vec3d.z, -0.15000000596046448D, 0.15000000596046448D);
            double d2 = Math.max(vec3d.y, -0.15000000596046448D);

            if (d2 < 0.0D && !this.getFeetBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof net.minecraft.world.entity.player.Player) {
                d2 = 0.0D;
            }

            vec3d = new Vec3(d0, d2, d1);
        }

        return vec3d;
    }

    private float getFrictionInfluencedSpeed(float f) {
        return this.onGround ? this.getSpeed() * (0.21600002F / (f * f * f)) : this.flyingSpeed;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float f) {
        this.speed = f;
    }

    public boolean doHurtTarget(Entity entity) {
        this.setLastHurtMob(entity);
        return false;
    }

    @Override
    public void tick() {
        SpigotTimings.timerEntityBaseTick.startTiming(); // Spigot
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level.isClientSide) {
            int i = this.getArrowCount();

            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                --this.removeArrowTime;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();

            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                --this.removeStingerTime;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdates();
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (!this.glowing) {
                boolean flag = this.hasEffect(MobEffects.GLOWING);

                if (this.getSharedFlag(6) != flag) {
                    this.setSharedFlag(6, flag);
                }
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        SpigotTimings.timerEntityBaseTick.stopTiming(); // Spigot
        this.aiStep();
        SpigotTimings.timerEntityTickRest.startTiming(); // Spigot
        double d0 = this.getX() - this.xo;
        double d1 = this.getZ() - this.zo;
        float f = (float) (d0 * d0 + d1 * d1);
        float f1 = this.yBodyRot;
        float f2 = 0.0F;

        this.oRun = this.run;
        float f3 = 0.0F;

        if (f > 0.0025000002F) {
            f3 = 1.0F;
            f2 = (float) Math.sqrt((double) f) * 3.0F;
            float f4 = (float) Mth.atan2(d1, d0) * 57.295776F - 90.0F;
            float f5 = Mth.abs(Mth.wrapDegrees(this.yRot) - f4);

            if (95.0F < f5 && f5 < 265.0F) {
                f1 = f4 - 180.0F;
            } else {
                f1 = f4;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.yRot;
        }

        if (!this.onGround) {
            f3 = 0.0F;
        }

        this.run += (f3 - this.run) * 0.3F;
        this.level.getProfiler().push("headTurn");
        f2 = this.tickHeadTurn(f1, f2);
        this.level.getProfiler().pop();
        this.level.getProfiler().push("rangeChecks");

        while (this.yRot - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.yRot - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.xRot - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.xRot - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        this.level.getProfiler().pop();
        this.animStep += f2;
        if (this.isFallFlying()) {
            ++this.fallFlyTicks;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.xRot = 0.0F;
        }

        SpigotTimings.timerEntityTickRest.stopTiming(); // Spigot
    }

    private void detectEquipmentUpdates() {
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();

        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }

    }

    @Nullable
    private Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];
            ItemStack itemstack;

            switch (enumitemslot.getType()) {
                case HAND:
                    itemstack = this.getLastHandItem(enumitemslot);
                    break;
                case ARMOR:
                    itemstack = this.getLastArmorItem(enumitemslot);
                    break;
                default:
                    continue;
            }

            ItemStack itemstack1 = this.getItemBySlot(enumitemslot);

            if (!ItemStack.matches(itemstack1, itemstack)) {
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(enumitemslot, itemstack1);
                if (!itemstack.isEmpty()) {
                    this.getAttributes().removeAttributeModifiers(itemstack.getAttributeModifiers(enumitemslot));
                }

                if (!itemstack1.isEmpty()) {
                    this.getAttributes().addTransientAttributeModifiers(itemstack1.getAttributeModifiers(enumitemslot));
                }
            }
        }

        return map;
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> map) {
        ItemStack itemstack = (ItemStack) map.get(EquipmentSlot.MAINHAND);
        ItemStack itemstack1 = (ItemStack) map.get(EquipmentSlot.OFFHAND);

        if (itemstack != null && itemstack1 != null && ItemStack.matches(itemstack, this.getLastHandItem(EquipmentSlot.OFFHAND)) && ItemStack.matches(itemstack1, this.getLastHandItem(EquipmentSlot.MAINHAND))) {
            ((ServerLevel) this.level).getChunkSourceOH().broadcast(this, new ClientboundEntityEventPacket(this, (byte) 55));
            map.remove(EquipmentSlot.MAINHAND);
            map.remove(EquipmentSlot.OFFHAND);
            this.setLastHandItem(EquipmentSlot.MAINHAND, itemstack.copy());
            this.setLastHandItem(EquipmentSlot.OFFHAND, itemstack1.copy());
        }

    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> map) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(map.size());

        map.forEach((enumitemslot, itemstack) -> {
            ItemStack itemstack1 = itemstack.copy();

            list.add(Pair.of(enumitemslot, itemstack1));
            switch (enumitemslot.getType()) {
                case HAND:
                    this.setLastHandItem(enumitemslot, itemstack1);
                    break;
                case ARMOR:
                    this.setLastArmorItem(enumitemslot, itemstack1);
            }

        });
        ((ServerLevel) this.level).getChunkSourceOH().broadcast(this, new ClientboundSetEquipmentPacket(this.getId(), list));
    }

    private ItemStack getLastArmorItem(EquipmentSlot enumitemslot) {
        return (ItemStack) this.lastArmorItemStacks.get(enumitemslot.getIndex());
    }

    private void setLastArmorItem(EquipmentSlot enumitemslot, ItemStack itemstack) {
        this.lastArmorItemStacks.set(enumitemslot.getIndex(), itemstack);
    }

    private ItemStack getLastHandItem(EquipmentSlot enumitemslot) {
        return (ItemStack) this.lastHandItemStacks.get(enumitemslot.getIndex());
    }

    private void setLastHandItem(EquipmentSlot enumitemslot, ItemStack itemstack) {
        this.lastHandItemStacks.set(enumitemslot.getIndex(), itemstack);
    }

    protected float tickHeadTurn(float f, float f1) {
        float f2 = Mth.wrapDegrees(f - this.yBodyRot);

        this.yBodyRot += f2 * 0.3F;
        float f3 = Mth.wrapDegrees(this.yRot - this.yBodyRot);
        boolean flag = f3 < -90.0F || f3 >= 90.0F;

        if (f3 < -75.0F) {
            f3 = -75.0F;
        }

        if (f3 >= 75.0F) {
            f3 = 75.0F;
        }

        this.yBodyRot = this.yRot - f3;
        if (f3 * f3 > 2500.0F) {
            this.yBodyRot += f3 * 0.2F;
        }

        if (flag) {
            f1 *= -1.0F;
        }

        return f1;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            --this.noJumpDelay;
        }

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.setPacketCoordinates(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            double d0 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
            double d1 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
            double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
            double d3 = Mth.wrapDegrees(this.lerpYRot - (double) this.yRot);

            this.yRot = (float) ((double) this.yRot + d3 / (double) this.lerpSteps);
            this.xRot = (float) ((double) this.xRot + (this.lerpXRot - (double) this.xRot) / (double) this.lerpSteps);
            --this.lerpSteps;
            this.setPos(d0, d1, d2);
            this.setRot(this.yRot, this.xRot);
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }

        if (this.lerpHeadSteps > 0) {
            this.yHeadRot = (float) ((double) this.yHeadRot + Mth.wrapDegrees(this.lyHeadRot - (double) this.yHeadRot) / (double) this.lerpHeadSteps);
            --this.lerpHeadSteps;
        }

        Vec3 vec3d = this.getDeltaMovement();
        double d4 = vec3d.x;
        double d5 = vec3d.y;
        double d6 = vec3d.z;

        if (Math.abs(vec3d.x) < 0.003D) {
            d4 = 0.0D;
        }

        if (Math.abs(vec3d.y) < 0.003D) {
            d5 = 0.0D;
        }

        if (Math.abs(vec3d.z) < 0.003D) {
            d6 = 0.0D;
        }

        this.setDeltaMovement(d4, d5, d6);
        this.level.getProfiler().push("ai");
        SpigotTimings.timerEntityAI.startTiming(); // Spigot
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            this.level.getProfiler().push("newAi");
            this.serverAiStep();
            this.level.getProfiler().pop();
        }
        SpigotTimings.timerEntityAI.stopTiming(); // Spigot

        this.level.getProfiler().pop();
        this.level.getProfiler().push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double d7;

            if (this.isInLava()) {
                d7 = this.getFluidHeight((Tag) FluidTags.LAVA);
            } else {
                d7 = this.getFluidHeight((Tag) FluidTags.WATER);
            }

            boolean flag = this.isInWater() && d7 > 0.0D;
            double d8 = this.getFluidJumpThreshold();

            if (flag && (!this.onGround || d7 > d8)) {
                this.jumpInLiquid((Tag) FluidTags.WATER);
            } else if (this.isInLava() && (!this.onGround || d7 > d8)) {
                this.jumpInLiquid((Tag) FluidTags.LAVA);
            } else if ((this.onGround || flag && d7 <= d8) && this.noJumpDelay == 0) {
                this.jumpFromGround();
                this.noJumpDelay = 10;
            }
        } else {
            this.noJumpDelay = 0;
        }

        this.level.getProfiler().pop();
        this.level.getProfiler().push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        this.updateFallFlying();
        AABB axisalignedbb = this.getBoundingBox();

        SpigotTimings.timerEntityAIMove.startTiming(); // Spigot
        this.travel(new Vec3((double) this.xxa, (double) this.yya, (double) this.zza));
        SpigotTimings.timerEntityAIMove.stopTiming(); // Spigot
        this.level.getProfiler().pop();
        this.level.getProfiler().push("push");
        if (this.autoSpinAttackTicks > 0) {
            --this.autoSpinAttackTicks;
            this.checkAutoSpinAttack(axisalignedbb, this.getBoundingBox());
        }

        SpigotTimings.timerEntityAICollision.startTiming(); // Spigot
        this.pushEntities();
        SpigotTimings.timerEntityAICollision.stopTiming(); // Spigot
        this.level.getProfiler().pop();
        if (!this.level.isClientSide && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurt(DamageSource.DROWN, 1.0F);
        }

    }

    public boolean isSensitiveToWater() {
        return false;
    }

    private void updateFallFlying() {
        boolean flag = this.getSharedFlag(7);

        if (flag && !this.onGround && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);

            if (itemstack.getItem() == Items.ELYTRA && ElytraItem.isFlyEnabled(itemstack)) {
                flag = true;
                if (!this.level.isClientSide && (this.fallFlyTicks + 1) % 20 == 0) {
                    itemstack.hurtAndBreak(1, this, (entityliving) -> {
                        entityliving.broadcastBreakEvent(EquipmentSlot.CHEST);
                    });
                }
            } else {
                flag = false;
            }
        } else {
            flag = false;
        }

        if (!this.level.isClientSide) {
            if (flag != this.getSharedFlag(7) && !CraftEventFactory.callToggleGlideEvent(this, flag).isCancelled()) // CraftBukkit
            this.setSharedFlag(7, flag);
        }

    }

    protected void serverAiStep() {}

    protected void pushEntities() {
        List<Entity> list = this.level.getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));

        if (!list.isEmpty()) {
            int i = this.level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            int j;

            if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                j = 0;

                for (int k = 0; k < list.size(); ++k) {
                    if (!((Entity) list.get(k)).isPassenger()) {
                        ++j;
                    }
                }

                if (j > i - 1) {
                    this.hurt(DamageSource.CRAMMING, 6.0F);
                }
            }

            for (j = 0; j < list.size(); ++j) {
                Entity entity = (Entity) list.get(j);

                this.doPush(entity);
            }
        }

    }

    protected void checkAutoSpinAttack(AABB axisalignedbb, AABB axisalignedbb1) {
        AABB axisalignedbb2 = axisalignedbb.minmax(axisalignedbb1);
        List<Entity> list = this.level.getEntities(this, axisalignedbb2);

        if (!list.isEmpty()) {
            for (int i = 0; i < list.size(); ++i) {
                Entity entity = (Entity) list.get(i);

                if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                    this.doAutoAttackOnTouch((net.minecraft.world.entity.LivingEntity) entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2D));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level.isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
        }

    }

    protected void doPush(Entity entity) {
        entity.push(this);
    }

    protected void doAutoAttackOnTouch(net.minecraft.world.entity.LivingEntity entityliving) {}

    public void startAutoSpinAttack(int i) {
        this.autoSpinAttackTicks = i;
        if (!this.level.isClientSide) {
            this.setLivingEntityFlag(4, true);
        }

    }

    public boolean isAutoSpinAttack() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();

        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level.isClientSide) {
            this.dismountVehicle(entity);
        }

    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.fallDistance = 0.0F;
    }

    public void setJumping(boolean flag) {
        this.jumping = flag;
    }

    public void onItemPickup(ItemEntity entityitem) {
        net.minecraft.world.entity.player.Player entityhuman = entityitem.getThrower() != null ? this.level.getPlayerByUUID(entityitem.getThrower()) : null;

        if (entityhuman instanceof ServerPlayer) {
            CriteriaTriggers.ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer) entityhuman, entityitem.getItem(), this);
        }

    }

    public void take(Entity entity, int i) {
        if (!entity.removed && !this.level.isClientSide && (entity instanceof ItemEntity || entity instanceof AbstractArrow || entity instanceof ExperienceOrb)) {
            ((ServerLevel) this.level).getChunkSourceOH().broadcast(entity, new ClientboundTakeItemEntityPacket(entity.getId(), this.getId(), i));
        }

    }

    public boolean canSee(Entity entity) {
        if (this.level != entity.level) return false; // CraftBukkit - SPIGOT-5675, SPIGOT-5798, MC-149563
        Vec3 vec3d = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        Vec3 vec3d1 = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());

        return this.level.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    public float getViewYRot(float f) {
        return f == 1.0F ? this.yHeadRot : Mth.lerp(f, this.yHeadRotO, this.yHeadRot);
    }

    public boolean isEffectiveAi() {
        return !this.level.isClientSide;
    }

    @Override
    public boolean isPickable() {
        return !this.removed && this.collides; // CraftBukkit
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.onClimbable() && this.collides; // CraftBukkit
    }

    // CraftBukkit start - collidable API
    @Override
    public boolean canCollideWith(Entity entity) {
        return isPushable() && this.collides != this.collidableExemptions.contains(entity.getUUID());
    }
    // CraftBukkit end

    @Override
    protected void markHurt() {
        this.hurtMarked = this.random.nextDouble() >= this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
    }

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    @Override
    public void setYHeadRot(float f) {
        this.yHeadRot = f;
    }

    @Override
    public void setYBodyRot(float f) {
        this.yBodyRot = f;
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public void setAbsorptionAmount(float f) {
        if (f < 0.0F) {
            f = 0.0F;
        }

        this.absorptionAmount = f;
    }

    public void onEnterCombat() {}

    public void onLeaveCombat() {}

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return ((Byte) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSameIgnoreDurability(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.useItem.onUseTick(this.level, this, this.getUseItemRemainingTicks());
                if (this.shouldTriggerItemUseEffects()) {
                    this.triggerItemUseEffects(this.useItem, 5);
                }

                if (--this.useItemRemaining == 0 && !this.level.isClientSide && !this.useItem.useOnRelease()) {
                    this.completeUsingItem();
                }
            } else {
                this.stopUsingItem();
            }
        }

    }

    private boolean shouldTriggerItemUseEffects() {
        int i = this.getUseItemRemainingTicks();
        FoodProperties foodinfo = this.useItem.getItem().getFoodProperties();
        boolean flag = foodinfo != null && foodinfo.isFastFood();

        flag |= i <= this.useItem.getUseDuration() - 7;
        return flag && i % 4 == 0;
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }

    }

    protected void setLivingEntityFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS);
        int j;

        if (flag) {
            j = b0 | i;
        } else {
            j = b0 & ~i;
        }

        this.entityData.set(net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) j);
    }

    public void startUsingItem(InteractionHand enumhand) {
        ItemStack itemstack = this.getItemInHand(enumhand);

        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            this.useItem = itemstack;
            this.useItemRemaining = itemstack.getUseDuration();
            if (!this.level.isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, enumhand == InteractionHand.OFF_HAND);
            }

        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (net.minecraft.world.entity.LivingEntity.SLEEPING_POS_ID.equals(datawatcherobject)) {
            if (this.level.isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (net.minecraft.world.entity.LivingEntity.DATA_LIVING_ENTITY_FLAGS.equals(datawatcherobject) && this.level.isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration();
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor argumentanchor_anchor, Vec3 vec3d) {
        super.lookAt(argumentanchor_anchor, vec3d);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    protected void triggerItemUseEffects(ItemStack itemstack, int i) {
        if (!itemstack.isEmpty() && this.isUsingItem()) {
            if (itemstack.getUseAnimation() == UseAnim.DRINK) {
                this.playSound(this.getDrinkingSound(itemstack), 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }

            if (itemstack.getUseAnimation() == UseAnim.EAT) {
                this.spawnItemParticles(itemstack, i);
                this.playSound(this.getEatingSound(itemstack), 0.5F + 0.5F * (float) this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

        }
    }

    private void spawnItemParticles(ItemStack itemstack, int i) {
        for (int j = 0; j < i; ++j) {
            Vec3 vec3d = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);

            vec3d = vec3d.xRot(-this.xRot * 0.017453292F);
            vec3d = vec3d.yRot(-this.yRot * 0.017453292F);
            double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
            Vec3 vec3d1 = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.3D, d0, 0.6D);

            vec3d1 = vec3d1.xRot(-this.xRot * 0.017453292F);
            vec3d1 = vec3d1.yRot(-this.yRot * 0.017453292F);
            vec3d1 = vec3d1.add(this.getX(), this.getEyeY(), this.getZ());
            this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, itemstack), vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
        }

    }

    protected void completeUsingItem() {
        if (!this.useItem.equals(this.getItemInHand(this.getUsedItemHand()))) {
            this.releaseUsingItem();
        } else {
            if (!this.useItem.isEmpty() && this.isUsingItem()) {
                this.triggerItemUseEffects(this.useItem, 16);
                // CraftBukkit start - fire PlayerItemConsumeEvent
                ItemStack itemstack;
                if (this instanceof ServerPlayer) {
                    org.bukkit.inventory.ItemStack craftItem = CraftItemStack.asBukkitCopy(this.useItem);
                    PlayerItemConsumeEvent event = new PlayerItemConsumeEvent((Player) this.getBukkitEntity(), craftItem);
                    level.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        // Update client
                        ((ServerPlayer) this).getBukkitEntity().updateInventory();
                        ((ServerPlayer) this).getBukkitEntity().updateScaledHealth();
                        return;
                    }

                    itemstack = (craftItem.equals(event.getItem())) ? this.useItem.finishUsingItem(this.level, this) : CraftItemStack.asNMSCopy(event.getItem()).finishUsingItem(level, this);
                } else {
                    itemstack = this.useItem.finishUsingItem(this.level, this);
                }

                this.setItemInHand(this.getUsedItemHand(), itemstack);
                // CraftBukkit end
                this.stopUsingItem();
            }

        }
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration() - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            this.useItem.releaseUsing(this.level, this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level.isClientSide) {
            this.setLivingEntityFlag(1, false);
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item item = this.useItem.getItem();

            return item.getUseAnimation(this.useItem) != UseAnim.BLOCK ? false : item.getUseDuration(this.useItem) - this.useItemRemaining >= 5;
        } else {
            return false;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.getPose() == Pose.FALL_FLYING;
    }

    public boolean randomTeleport(double d0, double d1, double d2, boolean flag) {
        double d3 = this.getX();
        double d4 = this.getY();
        double d5 = this.getZ();
        double d6 = d1;
        boolean flag1 = false;
        BlockPos blockposition = new BlockPos(d0, d1, d2);
        Level world = this.level;

        if (world.hasChunkAt(blockposition)) {
            boolean flag2 = false;

            while (!flag2 && blockposition.getY() > 0) {
                BlockPos blockposition1 = blockposition.below();
                BlockState iblockdata = world.getType(blockposition1);

                if (iblockdata.getMaterial().blocksMotion()) {
                    flag2 = true;
                } else {
                    --d6;
                    blockposition = blockposition1;
                }
            }

            if (flag2) {
                // CraftBukkit start - Teleport event
                // this.enderTeleportTo(d0, d6, d2);
                EntityTeleportEvent teleport = new EntityTeleportEvent(this.getBukkitEntity(), new Location(this.level.getWorld(), d3, d4, d5), new Location(this.level.getWorld(), d0, d6, d2));
                this.level.getServerOH().getPluginManager().callEvent(teleport);
                if (!teleport.isCancelled()) {
                    Location to = teleport.getTo();
                    this.teleportTo(to.getX(), to.getY(), to.getZ());
                    if (world.noCollision(this) && !world.containsAnyLiquid(this.getBoundingBox())) {
                        flag1 = true;
                    }
                }
                // CraftBukkit end
            }
        }

        if (!flag1) {
            this.teleportTo(d3, d4, d5);
            return false;
        } else {
            if (flag) {
                world.broadcastEntityEvent(this, (byte) 46);
            }

            if (this instanceof PathfinderMob) {
                ((PathfinderMob) this).getNavigation().stop();
            }

            return true;
        }
    }

    public boolean isAffectedByPotions() {
        return true;
    }

    public boolean attackable() {
        return true;
    }

    public boolean canTakeItem(ItemStack itemstack) {
        return false;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddMobPacket(this);
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        return entitypose == Pose.SLEEPING ? net.minecraft.world.entity.LivingEntity.SLEEPING_DIMENSIONS : super.getDimensions(entitypose).scale(this.getScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose entitypose) {
        EntityDimensions entitysize = this.getDimensions(entitypose);

        return new AABB((double) (-entitysize.width / 2.0F), 0.0D, (double) (-entitysize.width / 2.0F), (double) (entitysize.width / 2.0F), (double) entitysize.height, (double) (entitysize.width / 2.0F));
    }

    public Optional<BlockPos> getSleepingPos() {
        return (Optional) this.entityData.get(net.minecraft.world.entity.LivingEntity.SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos blockposition) {
        this.entityData.set(net.minecraft.world.entity.LivingEntity.SLEEPING_POS_ID, Optional.of(blockposition));
    }

    public void clearSleepingPos() {
        this.entityData.set(net.minecraft.world.entity.LivingEntity.SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos blockposition) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState iblockdata = this.level.getType(blockposition);

        if (iblockdata.getBlock() instanceof BedBlock) {
            this.level.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(BedBlock.OCCUPIED, true), 3);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(blockposition);
        this.setSleepingPos(blockposition);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPos blockposition) {
        this.setPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.6875D, (double) blockposition.getZ() + 0.5D);
    }

    private boolean checkBedExists() {
        return (Boolean) this.getSleepingPos().map((blockposition) -> {
            return this.level.getType(blockposition).getBlock() instanceof BedBlock;
        }).orElse(false);
    }

    public void stopSleeping() {
        Optional<BlockPos> optional = this.getSleepingPos(); // CraftBukkit - decompile error
        Level world = this.level;

        this.level.getClass();
        optional.filter(world::hasChunkAt).ifPresent((blockposition) -> {
            BlockState iblockdata = this.level.getType(blockposition);

            if (iblockdata.getBlock() instanceof BedBlock) {
                this.level.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(BedBlock.OCCUPIED, false), 3);
                Vec3 vec3d = (Vec3) BedBlock.findStandUpPosition(this.getType(), this.level, blockposition, 0).orElseGet(() -> {
                    BlockPos blockposition1 = blockposition.above();

                    return new Vec3((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.1D, (double) blockposition1.getZ() + 0.5D);
                });

                this.setPos(vec3d.x, vec3d.y, vec3d.z);
            }

        });
        Vec3 vec3d = this.position();

        this.setPose(Pose.STANDING);
        this.setPos(vec3d.x, vec3d.y, vec3d.z);
        this.clearSleepingPos();
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    @Override
    protected final float getEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitypose == Pose.SLEEPING ? 0.2F : this.getStandingEyeHeight(entitypose, entitysize);
    }

    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return super.getEyeHeight(entitypose, entitysize);
    }

    public ItemStack getProjectile(ItemStack itemstack) {
        return ItemStack.EMPTY;
    }

    public ItemStack eat(Level world, ItemStack itemstack) {
        if (itemstack.isEdible()) {
            world.playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), this.getEatingSound(itemstack), SoundSource.NEUTRAL, 1.0F, 1.0F + (world.random.nextFloat() - world.random.nextFloat()) * 0.4F);
            this.addEatEffect(itemstack, world, this);
            if (!(this instanceof net.minecraft.world.entity.player.Player) || !((net.minecraft.world.entity.player.Player) this).abilities.instabuild) {
                itemstack.shrink(1);
            }
        }

        return itemstack;
    }

    private void addEatEffect(ItemStack itemstack, Level world, net.minecraft.world.entity.LivingEntity entityliving) {
        Item item = itemstack.getItem();

        if (item.isEdible()) {
            List<Pair<MobEffectInstance, Float>> list = item.getFoodProperties().getEffects();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Pair<MobEffectInstance, Float> pair = (Pair) iterator.next();

                if (!world.isClientSide && pair.getFirst() != null && world.random.nextFloat() < (Float) pair.getSecond()) {
                    entityliving.addEffect(new MobEffectInstance((MobEffectInstance) pair.getFirst()), EntityPotionEffectEvent.Cause.FOOD); // CraftBukkit
                }
            }
        }

    }

    private static byte entityEventForEquipmentBreak(EquipmentSlot enumitemslot) {
        switch (enumitemslot) {
            case MAINHAND:
                return 47;
            case OFFHAND:
                return 48;
            case HEAD:
                return 49;
            case CHEST:
                return 50;
            case FEET:
                return 52;
            case LEGS:
                return 51;
            default:
                return 47;
        }
    }

    public void broadcastBreakEvent(EquipmentSlot enumitemslot) {
        this.level.broadcastEntityEvent(this, entityEventForEquipmentBreak(enumitemslot));
    }

    public void broadcastBreakEvent(InteractionHand enumhand) {
        this.broadcastBreakEvent(enumhand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
}
