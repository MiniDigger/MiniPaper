package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

public class Parrot extends ShoulderRidingEntity implements FlyingAnimal {

    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.defineId(Parrot.class, EntityDataSerializers.INT);
    private static final Predicate<Mob> NOT_PARROT_PREDICATE = new Predicate<Mob>() {
        public boolean test(@Nullable Mob entityinsentient) {
            return entityinsentient != null && Parrot.MOB_SOUND_MAP.containsKey(entityinsentient.getType());
        }
    };
    private static final Item POISONOUS_FOOD = Items.COOKIE;
    private static final Set<Item> TAME_FOOD = Sets.newHashSet(new Item[]{Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS});
    private static final Map<EntityType<?>, SoundEvent> MOB_SOUND_MAP = (Map) Util.make(Maps.newHashMap(), (hashmap) -> { // CraftBukkit - decompile error
        hashmap.put(EntityType.BLAZE, SoundEvents.PARROT_IMITATE_BLAZE);
        hashmap.put(EntityType.CAVE_SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
        hashmap.put(EntityType.CREEPER, SoundEvents.PARROT_IMITATE_CREEPER);
        hashmap.put(EntityType.DROWNED, SoundEvents.PARROT_IMITATE_DROWNED);
        hashmap.put(EntityType.ELDER_GUARDIAN, SoundEvents.PARROT_IMITATE_ELDER_GUARDIAN);
        hashmap.put(EntityType.ENDER_DRAGON, SoundEvents.PARROT_IMITATE_ENDER_DRAGON);
        hashmap.put(EntityType.ENDERMITE, SoundEvents.PARROT_IMITATE_ENDERMITE);
        hashmap.put(EntityType.EVOKER, SoundEvents.PARROT_IMITATE_EVOKER);
        hashmap.put(EntityType.GHAST, SoundEvents.PARROT_IMITATE_GHAST);
        hashmap.put(EntityType.GUARDIAN, SoundEvents.PARROT_IMITATE_GUARDIAN);
        hashmap.put(EntityType.HOGLIN, SoundEvents.PARROT_IMITATE_HOGLIN);
        hashmap.put(EntityType.HUSK, SoundEvents.PARROT_IMITATE_HUSK);
        hashmap.put(EntityType.ILLUSIONER, SoundEvents.PARROT_IMITATE_ILLUSIONER);
        hashmap.put(EntityType.MAGMA_CUBE, SoundEvents.PARROT_IMITATE_MAGMA_CUBE);
        hashmap.put(EntityType.PHANTOM, SoundEvents.PARROT_IMITATE_PHANTOM);
        hashmap.put(EntityType.PIGLIN, SoundEvents.PARROT_IMITATE_PIGLIN);
        hashmap.put(EntityType.PILLAGER, SoundEvents.PARROT_IMITATE_PILLAGER);
        hashmap.put(EntityType.RAVAGER, SoundEvents.PARROT_IMITATE_RAVAGER);
        hashmap.put(EntityType.SHULKER, SoundEvents.PARROT_IMITATE_SHULKER);
        hashmap.put(EntityType.SILVERFISH, SoundEvents.PARROT_IMITATE_SILVERFISH);
        hashmap.put(EntityType.SKELETON, SoundEvents.PARROT_IMITATE_SKELETON);
        hashmap.put(EntityType.SLIME, SoundEvents.PARROT_IMITATE_SLIME);
        hashmap.put(EntityType.SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
        hashmap.put(EntityType.STRAY, SoundEvents.PARROT_IMITATE_STRAY);
        hashmap.put(EntityType.VEX, SoundEvents.PARROT_IMITATE_VEX);
        hashmap.put(EntityType.VINDICATOR, SoundEvents.PARROT_IMITATE_VINDICATOR);
        hashmap.put(EntityType.WITCH, SoundEvents.PARROT_IMITATE_WITCH);
        hashmap.put(EntityType.WITHER, SoundEvents.PARROT_IMITATE_WITHER);
        hashmap.put(EntityType.WITHER_SKELETON, SoundEvents.PARROT_IMITATE_WITHER_SKELETON);
        hashmap.put(EntityType.ZOGLIN, SoundEvents.PARROT_IMITATE_ZOGLIN);
        hashmap.put(EntityType.ZOMBIE, SoundEvents.PARROT_IMITATE_ZOMBIE);
        hashmap.put(EntityType.ZOMBIE_VILLAGER, SoundEvents.PARROT_IMITATE_ZOMBIE_VILLAGER);
    });
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    private float flapping = 1.0F;
    private boolean partyParrot;
    private BlockPos jukebox;

    public Parrot(EntityType<? extends Parrot> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.setVariant(this.random.nextInt(5));
        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
            ((AgableMob.AgableMobGroupData) groupdataentity).setShouldSpawnBaby(false);
        }

        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 5.0F, 1.0F, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LandOnOwnersShoulderGoal(this));
        this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0D, 3.0F, 7.0F));
    }

    public static AttributeSupplier.Builder eV() {
        return Mob.p().a(Attributes.MAX_HEALTH, 6.0D).a(Attributes.FLYING_SPEED, 0.4000000059604645D).a(Attributes.MOVEMENT_SPEED, 0.20000000298023224D);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation navigationflying = new FlyingPathNavigation(this, world);

        navigationflying.setCanOpenDoors(false);
        navigationflying.setCanFloat(true);
        navigationflying.setCanPassDoors(true);
        return navigationflying;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * 0.6F;
    }

    @Override
    public void aiStep() {
        if (this.jukebox == null || !this.jukebox.closerThan((Position) this.position(), 3.46D) || !this.level.getType(this.jukebox).is(Blocks.JUKEBOX)) {
            this.partyParrot = false;
            this.jukebox = null;
        }

        if (this.level.random.nextInt(400) == 0) {
            imitateNearbyMobs(this.level, (Entity) this);
        }

        super.aiStep();
        this.calculateFlapping();
    }

    private void calculateFlapping() {
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed = (float) ((double) this.flapSpeed + (double) (!this.onGround && !this.isPassenger() ? 4 : -1) * 0.3D);
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping = (float) ((double) this.flapping * 0.9D);
        Vec3 vec3d = this.getDeltaMovement();

        if (!this.onGround && vec3d.y < 0.0D) {
            this.setDeltaMovement(vec3d.multiply(1.0D, 0.6D, 1.0D));
        }

        this.flap += this.flapping * 2.0F;
    }

    public static boolean imitateNearbyMobs(Level world, Entity entity) {
        if (entity.isAlive() && !entity.isSilent() && world.random.nextInt(2) == 0) {
            List<Mob> list = world.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(20.0D), Parrot.NOT_PARROT_PREDICATE);

            if (!list.isEmpty()) {
                Mob entityinsentient = (Mob) list.get(world.random.nextInt(list.size()));

                if (!entityinsentient.isSilent()) {
                    SoundEvent soundeffect = getImitatedSound(entityinsentient.getType());

                    world.playSound((Player) null, entity.getX(), entity.getY(), entity.getZ(), soundeffect, entity.getSoundSource(), 0.7F, getPitch(world.random));
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (!this.isTame() && Parrot.TAME_FOOD.contains(itemstack.getItem())) {
            if (!entityhuman.abilities.instabuild) {
                itemstack.shrink(1);
            }

            if (!this.isSilent()) {
                this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PARROT_EAT, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }

            if (!this.level.isClientSide) {
                if (this.random.nextInt(10) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) { // CraftBukkit
                    this.tame(entityhuman);
                    this.level.broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level.broadcastEntityEvent(this, (byte) 6);
                }
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else if (itemstack.getItem() == Parrot.POISONOUS_FOOD) {
            if (!entityhuman.abilities.instabuild) {
                itemstack.shrink(1);
            }

            this.addEffect(new MobEffectInstance(MobEffects.POISON, 900), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // CraftBukkit
            if (entityhuman.isCreative() || !this.isInvulnerable()) {
                this.hurt(DamageSource.playerAttack(entityhuman), Float.MAX_VALUE);
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else if (!this.isFlying() && this.isTame() && this.isOwnedBy((LivingEntity) entityhuman)) {
            if (!this.level.isClientSide) {
                this.setOrderedToSit(!this.isOrderedToSit());
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return false;
    }

    public static boolean checkParrotSpawnRules(EntityType<Parrot> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        BlockState iblockdata = generatoraccess.getType(blockposition.below());

        return (iblockdata.is((Tag) BlockTags.LEAVES) || iblockdata.is(Blocks.GRASS_BLOCK) || iblockdata.is((Tag) BlockTags.LOGS) || iblockdata.is(Blocks.AIR)) && generatoraccess.getRawBrightness(blockposition, 0) > 8;
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        return false;
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {}

    @Override
    public boolean canMate(Animal entityanimal) {
        return false;
    }

    @Nullable
    @Override
    public AgableMob getBreedOffspring(AgableMob entityageable) {
        return null;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        return entity.hurt(DamageSource.mobAttack(this), 3.0F);
    }

    @Nullable
    @Override
    public SoundEvent getSoundAmbient() {
        return getAmbient(this.level, this.level.random);
    }

    public static SoundEvent getAmbient(Level world, Random random) {
        if (world.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(1000) == 0) {
            List<EntityType<?>> list = Lists.newArrayList(Parrot.MOB_SOUND_MAP.keySet());

            return getImitatedSound((EntityType) list.get(random.nextInt(list.size())));
        } else {
            return SoundEvents.PARROT_AMBIENT;
        }
    }

    private static SoundEvent getImitatedSound(EntityType<?> entitytypes) {
        return (SoundEvent) Parrot.MOB_SOUND_MAP.getOrDefault(entitytypes, SoundEvents.PARROT_AMBIENT);
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(SoundEvents.PARROT_STEP, 0.15F, 1.0F);
    }

    @Override
    protected float playFlySound(float f) {
        this.playSound(SoundEvents.PARROT_FLY, 0.15F, 1.0F);
        return f + this.flapSpeed / 2.0F;
    }

    @Override
    protected boolean makeFlySound() {
        return true;
    }

    @Override
    protected float getVoicePitch() {
        return getPitch(this.random);
    }

    public static float getPitch(Random random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public boolean isPushable() {
        return super.isPushable(); // CraftBukkit - collidable API
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof Player)) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            // this.setWillSit(false); // CraftBukkit - moved into EntityLiving.damageEntity(DamageSource, float)
            return super.hurt(damagesource, f);
        }
    }

    public int getVariant() {
        return Mth.clamp((Integer) this.entityData.get(Parrot.DATA_VARIANT_ID), 0, 4);
    }

    public void setVariant(int i) {
        this.entityData.set(Parrot.DATA_VARIANT_ID, i);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Parrot.DATA_VARIANT_ID, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Variant", this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setVariant(nbttagcompound.getInt("Variant"));
    }

    public boolean isFlying() {
        return !this.onGround;
    }
}
