package net.minecraft.world.entity.monster;

import com.mojang.serialization.DynamicOps;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class Zombie extends Monster {

    private static final UUID SPEED_MODIFIER_BABY_UUID = UUID.fromString("B9766B59-9566-4402-BC1F-2EE2A276D836");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(net.minecraft.world.entity.monster.Zombie.SPEED_MODIFIER_BABY_UUID, "Baby speed boost", 0.5D, AttributeModifier.Operation.MULTIPLY_BASE);
    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(net.minecraft.world.entity.monster.Zombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPECIAL_TYPE_ID = SynchedEntityData.defineId(net.minecraft.world.entity.monster.Zombie.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DATA_DROWNED_CONVERSION_ID = SynchedEntityData.defineId(net.minecraft.world.entity.monster.Zombie.class, EntityDataSerializers.BOOLEAN);
    private static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = (enumdifficulty) -> {
        return enumdifficulty == Difficulty.HARD;
    };
    private final BreakDoorGoal breakDoorGoal;
    private boolean canBreakDoors;
    private int inWaterTime;
    public int conversionTime;
    private int lastTick = MinecraftServer.currentTick; // CraftBukkit - add field

    public Zombie(EntityType<? extends net.minecraft.world.entity.monster.Zombie> entitytypes, Level world) {
        super(entitytypes, world);
        this.breakDoorGoal = new BreakDoorGoal(this, net.minecraft.world.entity.monster.Zombie.DOOR_BREAKING_PREDICATE);
    }

    public Zombie(Level world) {
        this(EntityType.ZOMBIE, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.monster.Zombie.ZombieAttackTurtleEggGoal(this, 1.0D, 3));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0D, true, 4, this::canBreakDoors));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        if ( level.spigotConfig.zombieAggressiveTowardsVillager ) this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false)); // Spigot
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder eT() {
        return Monster.eS().a(Attributes.FOLLOW_RANGE, 35.0D).a(Attributes.MOVEMENT_SPEED, 0.23000000417232513D).a(Attributes.ATTACK_DAMAGE, 3.0D).a(Attributes.ARMOR, 2.0D).a(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().register(net.minecraft.world.entity.monster.Zombie.DATA_BABY_ID, false);
        this.getEntityData().register(net.minecraft.world.entity.monster.Zombie.DATA_SPECIAL_TYPE_ID, 0);
        this.getEntityData().register(net.minecraft.world.entity.monster.Zombie.DATA_DROWNED_CONVERSION_ID, false);
    }

    public boolean isUnderWaterConverting() {
        return (Boolean) this.getEntityData().get(net.minecraft.world.entity.monster.Zombie.DATA_DROWNED_CONVERSION_ID);
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean flag) {
        if (this.supportsBreakDoorGoal()) {
            if (this.canBreakDoors != flag) {
                this.canBreakDoors = flag;
                ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(flag);
                if (flag) {
                    this.goalSelector.addGoal(1, this.breakDoorGoal);
                } else {
                    this.goalSelector.removeGoal((Goal) this.breakDoorGoal);
                }
            }
        } else if (this.canBreakDoors) {
            this.goalSelector.removeGoal((Goal) this.breakDoorGoal);
            this.canBreakDoors = false;
        }

    }

    protected boolean supportsBreakDoorGoal() {
        return true;
    }

    @Override
    public boolean isBaby() {
        return (Boolean) this.getEntityData().get(net.minecraft.world.entity.monster.Zombie.DATA_BABY_ID);
    }

    @Override
    protected int getExperienceReward(Player entityhuman) {
        if (this.isBaby()) {
            this.xpReward = (int) ((float) this.xpReward * 2.5F);
        }

        return super.getExperienceReward(entityhuman);
    }

    public void setBaby(boolean flag) {
        this.getEntityData().set(net.minecraft.world.entity.monster.Zombie.DATA_BABY_ID, flag);
        if (this.level != null && !this.level.isClientSide) {
            AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

            attributemodifiable.removeModifier(net.minecraft.world.entity.monster.Zombie.SPEED_MODIFIER_BABY);
            if (flag) {
                attributemodifiable.addTransientModifier(net.minecraft.world.entity.monster.Zombie.SPEED_MODIFIER_BABY);
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (net.minecraft.world.entity.monster.Zombie.DATA_BABY_ID.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    protected boolean convertsInWater() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isUnderWaterConverting()) {
                // CraftBukkit start - Use wall time instead of ticks for conversion
                int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
                this.conversionTime -= elapsedTicks;
                // CraftBukkit end
                if (this.conversionTime < 0) {
                    this.doUnderWaterConversion();
                }
            } else if (this.convertsInWater()) {
                if (this.isEyeInFluid((Tag) FluidTags.WATER)) {
                    ++this.inWaterTime;
                    if (this.inWaterTime >= 600) {
                        this.startUnderWaterConversion(300);
                    }
                } else {
                    this.inWaterTime = -1;
                }
            }
        }

        super.tick();
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public void aiStep() {
        if (this.isAlive()) {
            boolean flag = this.isSunSensitive() && this.isSunBurnTick();

            if (flag) {
                ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);

                if (!itemstack.isEmpty()) {
                    if (itemstack.isDamageableItem()) {
                        itemstack.setDamageValue(itemstack.getDamageValue() + this.random.nextInt(2));
                        if (itemstack.getDamageValue() >= itemstack.getMaxDamage()) {
                            this.broadcastBreakEvent(EquipmentSlot.HEAD);
                            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        }
                    }

                    flag = false;
                }

                if (flag) {
                    this.setSecondsOnFire(8);
                }
            }
        }

        super.aiStep();
    }

    public void startUnderWaterConversion(int i) {
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
        this.conversionTime = i;
        this.getEntityData().set(net.minecraft.world.entity.monster.Zombie.DATA_DROWNED_CONVERSION_ID, true);
    }

    protected void doUnderWaterConversion() {
        this.convertToZombieType(EntityType.DROWNED);
        if (!this.isSilent()) {
            this.level.levelEvent((Player) null, 1040, this.blockPosition(), 0);
        }

    }

    protected void convertToZombieType(EntityType<? extends net.minecraft.world.entity.monster.Zombie> entitytypes) {
        net.minecraft.world.entity.monster.Zombie entityzombie = (net.minecraft.world.entity.monster.Zombie) this.convertTo(entitytypes);

        if (entityzombie != null) {
            entityzombie.handleAttributes(entityzombie.level.getDamageScaler(entityzombie.blockPosition()).getSpecialMultiplier());
            entityzombie.setCanBreakDoors(entityzombie.supportsBreakDoorGoal() && this.canBreakDoors());
<<<<<<< HEAD
=======
            entityzombie.handleAttributes(entityzombie.level.getDamageScaler(new BlockPos(entityzombie)).getSpecialMultiplier());
            entityzombie.setBaby(this.isBaby());
            entityzombie.setNoAi(this.isNoAi());
            EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
            int i = aenumitemslot.length;

            for (int j = 0; j < i; ++j) {
                EquipmentSlot enumitemslot = aenumitemslot[j];
                ItemStack itemstack = this.getItemBySlot(enumitemslot);

                if (!itemstack.isEmpty()) {
                    entityzombie.setItemSlot(enumitemslot, itemstack.copy());
                    entityzombie.setDropChance(enumitemslot, this.getEquipmentDropChance(enumitemslot));
                    itemstack.setCount(0);
                }
            }

            if (this.hasCustomName()) {
                entityzombie.setCustomName(this.getCustomName());
                entityzombie.setCustomNameVisible(this.isCustomNameVisible());
            }

            if (this.isPersistenceRequired()) {
                entityzombie.setPersistenceRequired();
            }

            entityzombie.setInvulnerable(this.isInvulnerable());
            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, entityzombie, EntityTransformEvent.TransformReason.DROWNED).isCancelled()) {
                ((org.bukkit.entity.Zombie) getBukkitEntity()).setConversionTime(-1); // SPIGOT-5208: End conversion to stop event spam
                return;
            }
            // CraftBukkit end
            if (!new com.destroystokyo.paper.event.entity.EntityTransformedEvent(this.getBukkitEntity(), entityzombie.getBukkitEntity(), com.destroystokyo.paper.event.entity.EntityTransformedEvent.TransformedReason.DROWNED).callEvent()) return; // Paper
            this.level.addEntity(entityzombie, CreatureSpawnEvent.SpawnReason.DROWNED); // CraftBukkit - added spawn reason
            this.remove();
>>>>>>> Toothpick
        }
        else { ((Zombie) getBukkitEntity()).setConversionTime(-1); } // SPIGOT-5208: End conversion to stop event spam

    }

    protected boolean isSunSensitive() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (super.hurt(damagesource, f)) {
            LivingEntity entityliving = this.getTarget();

            if (entityliving == null && damagesource.getEntity() instanceof LivingEntity) {
                entityliving = (LivingEntity) damagesource.getEntity();
            }

            if (entityliving != null && this.level.getDifficulty() == Difficulty.HARD && (double) this.random.nextFloat() < this.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                int i = Mth.floor(this.getX());
                int j = Mth.floor(this.getY());
                int k = Mth.floor(this.getZ());
                net.minecraft.world.entity.monster.Zombie entityzombie = new net.minecraft.world.entity.monster.Zombie(this.level);

                for (int l = 0; l < 50; ++l) {
                    int i1 = i + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int j1 = j + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int k1 = k + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    BlockPos blockposition = new BlockPos(i1, j1, k1);
                    EntityType<?> entitytypes = entityzombie.getType();
                    SpawnPlacements.Type entitypositiontypes_surface = SpawnPlacements.getPlacementType(entitytypes);

                    if (NaturalSpawner.isSpawnPositionOk(entitypositiontypes_surface, (LevelReader) this.level, blockposition, entitytypes) && SpawnPlacements.checkSpawnRules(entitytypes, this.level, MobSpawnType.REINFORCEMENT, blockposition, this.level.random)) {
                        entityzombie.setPos((double) i1, (double) j1, (double) k1);
                        if (!this.level.hasNearbyAlivePlayer((double) i1, (double) j1, (double) k1, 7.0D) && this.level.isUnobstructed(entityzombie) && this.level.noCollision(entityzombie) && !this.level.containsAnyLiquid(entityzombie.getBoundingBox())) {
                            this.level.addEntity(entityzombie, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS); // CraftBukkit
                            entityzombie.setGoalTarget(entityliving, EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET, true); // CraftBukkit
                            entityzombie.prepare(this.level, this.level.getDamageScaler(entityzombie.blockPosition()), MobSpawnType.REINFORCEMENT, (SpawnGroupData) null, (CompoundTag) null);
                            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(new AttributeModifier("Zombie reinforcement caller charge", -0.05000000074505806D, AttributeModifier.Operation.ADDITION));
                            entityzombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(new AttributeModifier("Zombie reinforcement callee charge", -0.05000000074505806D, AttributeModifier.Operation.ADDITION));
                            break;
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean flag = super.doHurtTarget(entity);

        if (flag) {
            float f = this.level.getDamageScaler(this.blockPosition()).getEffectiveDifficulty();

            if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < f * 0.3F) {
                // CraftBukkit start
                EntityCombustByEntityEvent event = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), 2 * (int) f); // PAIL: fixme
                this.level.getServerOH().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.setOnFire(event.getDuration(), false);
                }
                // CraftBukkit end
            }
        }

        return flag;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    protected SoundEvent getSoundStep() {
        return SoundEvents.ZOMBIE_STEP;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(this.getSoundStep(), 0.15F, 1.0F);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected void populateDefaultEquipmentSlots(DifficultyInstance difficultydamagescaler) {
        super.populateDefaultEquipmentSlots(difficultydamagescaler);
        if (this.random.nextFloat() < (this.level.getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            int i = this.random.nextInt(3);

            if (i == 0) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (this.isBaby()) {
            nbttagcompound.putBoolean("IsBaby", true);
        }

        nbttagcompound.putBoolean("CanBreakDoors", this.canBreakDoors());
        nbttagcompound.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
        nbttagcompound.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.getBoolean("IsBaby")) {
            this.setBaby(true);
        }

        this.setCanBreakDoors(nbttagcompound.getBoolean("CanBreakDoors"));
        this.inWaterTime = nbttagcompound.getInt("InWaterTime");
        if (nbttagcompound.contains("DrownedConversionTime", 99) && nbttagcompound.getInt("DrownedConversionTime") > -1) {
            this.startUnderWaterConversion(nbttagcompound.getInt("DrownedConversionTime"));
        }

    }

    @Override
    public void killed(LivingEntity entityliving) {
        super.killed(entityliving);
        if ((this.level.getDifficulty() == Difficulty.NORMAL || this.level.getDifficulty() == Difficulty.HARD) && entityliving instanceof Villager) {
            if (this.level.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) {
                return;
            }

            Villager entityvillager = (Villager) entityliving;
            ZombieVillager entityzombievillager = (ZombieVillager) EntityType.ZOMBIE_VILLAGER.create(this.level);

            entityzombievillager.copyPosition(entityvillager);
            // entityvillager.die(); // CraftBukkit - moved down
            entityzombievillager.prepare(this.level, this.level.getDamageScaler(entityzombievillager.blockPosition()), MobSpawnType.CONVERSION, new net.minecraft.world.entity.monster.Zombie.GroupDataZombie(false, true), (CompoundTag) null);
            entityzombievillager.setVillagerData(entityvillager.getVillagerData());
            entityzombievillager.setGossips((net.minecraft.nbt.Tag) entityvillager.getGossips().store((DynamicOps) NbtOps.INSTANCE).getValue());
            entityzombievillager.setTradeOffers(entityvillager.getOffers().createTag());
            entityzombievillager.setVillagerXp(entityvillager.getVillagerXp());
            entityzombievillager.setBaby(entityvillager.isBaby());
            entityzombievillager.setNoAi(entityvillager.isNoAi());
            if (entityvillager.hasCustomName()) {
                entityzombievillager.setCustomName(entityvillager.getCustomName());
                entityzombievillager.setCustomNameVisible(entityvillager.isCustomNameVisible());
            }

            if (entityvillager.isPersistenceRequired()) {
                entityzombievillager.setPersistenceRequired();
            }

            entityzombievillager.setInvulnerable(this.isInvulnerable());
            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, entityzombievillager, EntityTransformEvent.TransformReason.INFECTION).isCancelled()) {
                return;
            }
            entityvillager.remove(); // CraftBukkit - from above
            this.level.addEntity(entityzombievillager, CreatureSpawnEvent.SpawnReason.INFECTION); // CraftBukkit - add SpawnReason
            // CraftBukkit end
            if (!this.isSilent()) {
                this.level.levelEvent((Player) null, 1026, this.blockPosition(), 0);
            }
        }

    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return this.isBaby() ? 0.93F : 1.74F;
    }

    @Override
    public boolean canHoldItem(ItemStack itemstack) {
        return itemstack.getItem() == Items.EGG && this.isBaby() && this.isPassenger() ? false : super.canHoldItem(itemstack);
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        Object object = super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
        float f = difficultydamagescaler.getSpecialMultiplier();

        this.setCanPickUpLoot(this.random.nextFloat() < 0.55F * f);
        if (object == null) {
            object = new net.minecraft.world.entity.monster.Zombie.GroupDataZombie(getSpawnAsBabyOdds(generatoraccess.getRandom()), true);
        }

        if (object instanceof net.minecraft.world.entity.monster.Zombie.GroupDataZombie) {
            net.minecraft.world.entity.monster.Zombie.GroupDataZombie entityzombie_groupdatazombie = (net.minecraft.world.entity.monster.Zombie.GroupDataZombie) object;

            if (entityzombie_groupdatazombie.a) {
                this.setBaby(true);
                if (entityzombie_groupdatazombie.b) {
                    if ((double) generatoraccess.getRandom().nextFloat() < 0.05D) {
                        List<Chicken> list = generatoraccess.getEntitiesOfClass(Chicken.class, this.getBoundingBox().inflate(5.0D, 3.0D, 5.0D), EntitySelector.ENTITY_NOT_BEING_RIDDEN);

                        if (!list.isEmpty()) {
                            Chicken entitychicken = (Chicken) list.get(0);

                            entitychicken.setChickenJockey(true);
                            this.startRiding(entitychicken);
                        }
                    } else if ((double) generatoraccess.getRandom().nextFloat() < 0.05D) {
                        Chicken entitychicken1 = (Chicken) EntityType.CHICKEN.create(this.level);

                        entitychicken1.moveTo(this.getX(), this.getY(), this.getZ(), this.yRot, 0.0F);
                        entitychicken1.prepare(generatoraccess, difficultydamagescaler, MobSpawnType.JOCKEY, (SpawnGroupData) null, (CompoundTag) null);
                        entitychicken1.setChickenJockey(true);
                        this.startRiding(entitychicken1);
                        generatoraccess.addEntity(entitychicken1, CreatureSpawnEvent.SpawnReason.MOUNT); // CraftBukkit
                    }
                }
            }

            this.setCanBreakDoors(this.supportsBreakDoorGoal() && this.random.nextFloat() < f * 0.1F);
            this.populateDefaultEquipmentSlots(difficultydamagescaler);
            this.populateDefaultEquipmentEnchantments(difficultydamagescaler);
        }

        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            LocalDate localdate = LocalDate.now();
            int i = localdate.get(ChronoField.DAY_OF_MONTH);
            int j = localdate.get(ChronoField.MONTH_OF_YEAR);

            if (j == 10 && i == 31 && this.random.nextFloat() < 0.25F) {
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(this.random.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.armorDropChances[EquipmentSlot.HEAD.getIndex()] = 0.0F;
            }
        }

        this.handleAttributes(f);
        return (SpawnGroupData) object;
    }

    public static boolean getSpawnAsBabyOdds(Random random) {
        return random.nextFloat() < 0.05F;
    }

    protected void handleAttributes(float f) {
        this.randomizeReinforcementsChance();
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier("Random spawn bonus", this.random.nextDouble() * 0.05000000074505806D, AttributeModifier.Operation.ADDITION));
        double d0 = this.random.nextDouble() * 1.5D * (double) f;

        if (d0 > 1.0D) {
            this.getAttribute(Attributes.FOLLOW_RANGE).addPermanentModifier(new AttributeModifier("Random zombie-spawn bonus", d0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        if (this.random.nextFloat() < f * 0.05F) {
            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(new AttributeModifier("Leader zombie bonus", this.random.nextDouble() * 0.25D + 0.5D, AttributeModifier.Operation.ADDITION));
            this.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier("Leader zombie bonus", this.random.nextDouble() * 3.0D + 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            this.setCanBreakDoors(this.supportsBreakDoorGoal());
        }

    }

    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * 0.10000000149011612D);
    }

    @Override
    public double getMyRidingOffset() {
        return this.isBaby() ? 0.0D : -0.45D;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damagesource, int i, boolean flag) {
        super.dropCustomDeathLoot(damagesource, i, flag);
        Entity entity = damagesource.getEntity();

        if (entity instanceof Creeper) {
            Creeper entitycreeper = (Creeper) entity;

            if (entitycreeper.canDropMobsSkull()) {
                entitycreeper.increaseDroppedSkulls();
                ItemStack itemstack = this.getSkull();

                if (!itemstack.isEmpty()) {
                    this.spawnAtLocation(itemstack);
                }
            }
        }

    }

    protected ItemStack getSkull() {
        return new ItemStack(Items.ZOMBIE_HEAD);
    }

    class ZombieAttackTurtleEggGoal extends RemoveBlockGoal {

        ZombieAttackTurtleEggGoal(PathfinderMob entitycreature, double d0, int i) {
            super(Blocks.TURTLE_EGG, entitycreature, d0, i);
        }

        @Override
        public void playDestroyProgressSound(LevelAccessor generatoraccess, BlockPos blockposition) {
            generatoraccess.playSound((Player) null, blockposition, SoundEvents.ZOMBIE_DESTROY_EGG, SoundSource.HOSTILE, 0.5F, 0.9F + net.minecraft.world.entity.monster.Zombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(Level world, BlockPos blockposition) {
            world.playSound((Player) null, blockposition, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + world.random.nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() {
            return 1.14D;
        }
    }

    public static class GroupDataZombie implements SpawnGroupData {

        public final boolean a;
        public final boolean b;

        public GroupDataZombie(boolean flag, boolean flag1) {
            this.a = flag;
            this.b = flag1;
        }
    }
}
