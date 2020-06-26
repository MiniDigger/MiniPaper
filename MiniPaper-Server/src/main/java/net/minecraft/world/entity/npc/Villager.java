package net.minecraft.world.entity.npc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
// CraftBukkit end

public class Villager extends AbstractVillager implements ReputationEventHandler, VillagerDataHolder {

    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(net.minecraft.world.entity.npc.Villager.class, EntityDataSerializers.VILLAGER_DATA);
    public static final Map<Item, Integer> FOOD_POINTS = ImmutableMap.of(Items.BREAD, 4, Items.POTATO, 1, Items.CARROT, 1, Items.BEETROOT, 1);
    private static final Set<Item> WANTED_ITEMS = ImmutableSet.of(Items.BREAD, Items.POTATO, Items.CARROT, Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT, new Item[]{Items.BEETROOT_SEEDS});
    private int updateMerchantTimer;
    private boolean increaseProfessionLevelOnUpdate;
    @Nullable
    private Player lastTradedPlayer;
    private byte foodLevel;
    private final GossipContainer gossips;
    private long lastGossipTime;
    private long lastGossipDecayTime;
    private int villagerXp;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;
    private long lastRestockCheckDayTime;
    private boolean assignProfessionWhenSpawned;
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT, MemoryModuleType.LIVING_ENTITIES, MemoryModuleType.VISIBLE_LIVING_ENTITIES, MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.WALK_TARGET, new MemoryModuleType[]{MemoryModuleType.LOOK_TARGET, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.BREED_TARGET, MemoryModuleType.PATH, MemoryModuleType.INTERACTABLE_DOORS, MemoryModuleType.OPENED_DOORS, MemoryModuleType.NEAREST_BED, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_HOSTILE, MemoryModuleType.SECONDARY_JOB_SITE, MemoryModuleType.HIDING_PLACE, MemoryModuleType.HEARD_BELL_TIME, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.LAST_SLEPT, MemoryModuleType.LAST_WOKEN, MemoryModuleType.LAST_WORKED_AT_POI, MemoryModuleType.GOLEM_LAST_SEEN_TIME});
    private static final ImmutableList<SensorType<? extends Sensor<? super net.minecraft.world.entity.npc.Villager>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.INTERACTABLE_DOORS, SensorType.NEAREST_BED, SensorType.HURT_BY, SensorType.VILLAGER_HOSTILES, SensorType.VILLAGER_BABIES, SensorType.SECONDARY_POIS, SensorType.GOLEM_LAST_SEEN);
    public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<net.minecraft.world.entity.npc.Villager, PoiType>> POI_MEMORIES = ImmutableMap.of(MemoryModuleType.HOME, (entityvillager, villageplacetype) -> {
        return villageplacetype == PoiType.HOME;
    }, MemoryModuleType.JOB_SITE, (entityvillager, villageplacetype) -> {
        return entityvillager.getVillagerData().getProfession().getJobPoiType() == villageplacetype;
    }, MemoryModuleType.POTENTIAL_JOB_SITE, (entityvillager, villageplacetype) -> {
        return PoiType.ALL_JOBS.test(villageplacetype);
    }, MemoryModuleType.MEETING_POINT, (entityvillager, villageplacetype) -> {
        return villageplacetype == PoiType.MEETING;
    });

    public Villager(EntityType<? extends net.minecraft.world.entity.npc.Villager> entitytypes, Level world) {
        this(entitytypes, world, VillagerType.PLAINS);
    }

    public Villager(EntityType<? extends net.minecraft.world.entity.npc.Villager> entitytypes, Level world, VillagerType villagertype) {
        super(entitytypes, world);
        this.gossips = new GossipContainer();
        ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);
        this.setCanPickUpLoot(true);
        this.setVillagerData(this.getVillagerData().setType(villagertype).setProfession(VillagerProfession.NONE));
    }

    @Override
    public Brain<net.minecraft.world.entity.npc.Villager> getBrain() {
        return (Brain<net.minecraft.world.entity.npc.Villager>) super.getBrain(); // CraftBukkit - decompile error
    }

    @Override
    protected Brain.Provider<net.minecraft.world.entity.npc.Villager> brainProvider() {
        return Brain.provider((Collection) net.minecraft.world.entity.npc.Villager.MEMORY_TYPES, (Collection) net.minecraft.world.entity.npc.Villager.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<net.minecraft.world.entity.npc.Villager> behaviorcontroller = this.brainProvider().makeBrain(dynamic);

        this.registerBrainGoals(behaviorcontroller);
        return behaviorcontroller;
    }

    public void refreshBrain(ServerLevel worldserver) {
        Brain<net.minecraft.world.entity.npc.Villager> behaviorcontroller = this.getBrain();

        behaviorcontroller.stopAll(worldserver, this); // CraftBukkit - decompile error
        this.brain = behaviorcontroller.copyWithoutBehaviors();
        this.registerBrainGoals(this.getBrain());
    }

    private void registerBrainGoals(Brain<net.minecraft.world.entity.npc.Villager> behaviorcontroller) {
        VillagerProfession villagerprofession = this.getVillagerData().getProfession();

        if (this.isBaby()) {
            behaviorcontroller.setSchedule(Schedule.VILLAGER_BABY);
            behaviorcontroller.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
        } else {
            behaviorcontroller.setSchedule(Schedule.VILLAGER_DEFAULT);
            behaviorcontroller.addActivityWithConditions(Activity.WORK, VillagerGoalPackages.getWorkPackage(villagerprofession, 0.5F), (Set) ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
        }

        behaviorcontroller.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(villagerprofession, 0.5F));
        behaviorcontroller.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(villagerprofession, 0.5F), (Set) ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        behaviorcontroller.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(villagerprofession, 0.5F));
        behaviorcontroller.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(villagerprofession, 0.5F));
        behaviorcontroller.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(villagerprofession, 0.5F));
        behaviorcontroller.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(villagerprofession, 0.5F));
        behaviorcontroller.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(villagerprofession, 0.5F));
        behaviorcontroller.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(villagerprofession, 0.5F));
        behaviorcontroller.setCoreActivities((Set) ImmutableSet.of(Activity.CORE));
        behaviorcontroller.setDefaultActivity(Activity.IDLE);
        behaviorcontroller.setActiveActivityIfPossible(Activity.IDLE);
        behaviorcontroller.updateActivityFromSchedule(this.level.getDayTime(), this.level.getGameTime());
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (this.level instanceof ServerLevel) {
            this.refreshBrain((ServerLevel) this.level);
        }

    }

    public static AttributeSupplier.Builder eX() {
        return Mob.p().a(Attributes.MOVEMENT_SPEED, 0.5D).a(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public boolean assignProfessionWhenSpawned() {
        return this.assignProfessionWhenSpawned;
    }

    // Spigot Start
    @Override
    public void inactiveTick() {
        // SPIGOT-3874, SPIGOT-3894, SPIGOT-3846, SPIGOT-5286 :(
        if (level.spigotConfig.tickInactiveVillagers && this.isEffectiveAi()) {
            this.customServerAiStep();
        }
        super.inactiveTick();
    }
    // Spigot End

    @Override
    protected void customServerAiStep() {
        this.level.getProfiler().push("villagerBrain");
        this.getBrain().tick((ServerLevel) this.level, this); // CraftBukkit - decompile error
        this.level.getProfiler().pop();
        if (this.assignProfessionWhenSpawned) {
            this.assignProfessionWhenSpawned = false;
        }

        if (!this.isTrading() && this.updateMerchantTimer > 0) {
            --this.updateMerchantTimer;
            if (this.updateMerchantTimer <= 0) {
                if (this.increaseProfessionLevelOnUpdate) {
                    this.increaseMerchantCareer();
                    this.increaseProfessionLevelOnUpdate = false;
                }

                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.VILLAGER_TRADE); // CraftBukkit
            }
        }

        if (this.lastTradedPlayer != null && this.level instanceof ServerLevel) {
            ((ServerLevel) this.level).onReputationEvent(ReputationEventType.TRADE, (Entity) this.lastTradedPlayer, (ReputationEventHandler) this);
            this.level.broadcastEntityEvent(this, (byte) 14);
            this.lastTradedPlayer = null;
        }

        if (!this.isNoAi() && this.random.nextInt(100) == 0) {
            Raid raid = ((ServerLevel) this.level).getRaidAt(this.blockPosition());

            if (raid != null && raid.isActive() && !raid.isOver()) {
                this.level.broadcastEntityEvent(this, (byte) 42);
            }
        }

        if (this.getVillagerData().getProfession() == VillagerProfession.NONE && this.isTrading()) {
            this.stopTrading();
        }

        super.customServerAiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getUnhappyCounter() > 0) {
            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }

        this.maybeDecayGossip();
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.getItem() != Items.VILLAGER_SPAWN_EGG && this.isAlive() && !this.isTrading() && !this.isSleeping()) {
            if (this.isBaby()) {
                this.setUnhappy();
                return InteractionResult.sidedSuccess(this.level.isClientSide);
            } else {
                boolean flag = this.getOffers().isEmpty();

                if (enumhand == InteractionHand.MAIN_HAND) {
                    if (flag && !this.level.isClientSide) {
                        this.setUnhappy();
                    }

                    entityhuman.awardStat(Stats.TALKED_TO_VILLAGER);
                }

                if (flag) {
                    return InteractionResult.sidedSuccess(this.level.isClientSide);
                } else {
                    if (!this.level.isClientSide && !this.offers.isEmpty()) {
                        this.startTrading(entityhuman);
                    }

                    return InteractionResult.sidedSuccess(this.level.isClientSide);
                }
            }
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    private void setUnhappy() {
        this.setUnhappyCounter(40);
        if (!this.level.isClientSide()) {
            this.playSound(SoundEvents.VILLAGER_NO, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    private void startTrading(Player entityhuman) {
        this.updateSpecialPrices(entityhuman);
        this.setTradingPlayer(entityhuman);
        this.openTradingScreen(entityhuman, this.getDisplayName(), this.getVillagerData().getLevel());
    }

    @Override
    public void setTradingPlayer(@Nullable Player entityhuman) {
        boolean flag = this.getTradingPlayer() != null && entityhuman == null;

        super.setTradingPlayer(entityhuman);
        if (flag) {
            this.stopTrading();
        }

    }

    @Override
    protected void stopTrading() {
        super.stopTrading();
        this.resetSpecialPrices();
    }

    private void resetSpecialPrices() {
        Iterator iterator = this.getOffers().iterator();

        while (iterator.hasNext()) {
            MerchantOffer merchantrecipe = (MerchantOffer) iterator.next();

            merchantrecipe.resetSpecialPriceDiff();
        }

    }

    @Override
    public boolean canRestock() {
        return true;
    }

    public void restock() {
        this.updateDemand();
        Iterator iterator = this.getOffers().iterator();

        while (iterator.hasNext()) {
            MerchantOffer merchantrecipe = (MerchantOffer) iterator.next();

            merchantrecipe.resetUses();
        }

        this.lastRestockGameTime = this.level.getGameTime();
        ++this.numberOfRestocksToday;
    }

    private boolean needsToRestock() {
        Iterator iterator = this.getOffers().iterator();

        MerchantOffer merchantrecipe;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            merchantrecipe = (MerchantOffer) iterator.next();
        } while (!merchantrecipe.needsRestock());

        return true;
    }

    private boolean allowedToRestock() {
        return this.numberOfRestocksToday == 0 || this.numberOfRestocksToday < 2 && this.level.getGameTime() > this.lastRestockGameTime + 2400L;
    }

    public boolean shouldRestock() {
        long i = this.lastRestockGameTime + 12000L;
        long j = this.level.getGameTime();
        boolean flag = j > i;
        long k = this.level.getDayTime();

        if (this.lastRestockCheckDayTime > 0L) {
            long l = this.lastRestockCheckDayTime / 24000L;
            long i1 = k / 24000L;

            flag |= i1 > l;
        }

        this.lastRestockCheckDayTime = k;
        if (flag) {
            this.lastRestockGameTime = j;
            this.resetNumberOfRestocks();
        }

        return this.allowedToRestock() && this.needsToRestock();
    }

    private void catchUpDemand() {
        int i = 2 - this.numberOfRestocksToday;

        if (i > 0) {
            Iterator iterator = this.getOffers().iterator();

            while (iterator.hasNext()) {
                MerchantOffer merchantrecipe = (MerchantOffer) iterator.next();

                merchantrecipe.resetUses();
            }
        }

        for (int j = 0; j < i; ++j) {
            this.updateDemand();
        }

    }

    private void updateDemand() {
        Iterator iterator = this.getOffers().iterator();

        while (iterator.hasNext()) {
            MerchantOffer merchantrecipe = (MerchantOffer) iterator.next();

            merchantrecipe.updateDemand();
        }

    }

    private void updateSpecialPrices(Player entityhuman) {
        int i = this.getPlayerReputation(entityhuman);

        if (i != 0) {
            Iterator iterator = this.getOffers().iterator();

            while (iterator.hasNext()) {
                MerchantOffer merchantrecipe = (MerchantOffer) iterator.next();

                // CraftBukkit start
                int bonus = -Mth.floor((float) i * merchantrecipe.getPriceMultiplier());
                VillagerReplenishTradeEvent event = new VillagerReplenishTradeEvent((org.bukkit.entity.Villager) this.getBukkitEntity(), merchantrecipe.asBukkit(), bonus);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    merchantrecipe.addToSpecialPriceDiff(event.getBonus());
                }
                // CraftBukkit end
            }
        }

        if (entityhuman.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            MobEffectInstance mobeffect = entityhuman.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
            int j = mobeffect.getAmplifier();
            Iterator iterator1 = this.getOffers().iterator();

            while (iterator1.hasNext()) {
                MerchantOffer merchantrecipe1 = (MerchantOffer) iterator1.next();
                double d0 = 0.3D + 0.0625D * (double) j;
                int k = (int) Math.floor(d0 * (double) merchantrecipe1.getBaseCostA().getCount());

                merchantrecipe1.addToSpecialPriceDiff(-Math.max(k, 1));
            }
        }

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(net.minecraft.world.entity.npc.Villager.DATA_VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        DataResult<Tag> dataresult = VillagerData.CODEC.encodeStart(NbtOps.INSTANCE, this.getVillagerData()); // CraftBukkit - decompile error
        Logger logger = net.minecraft.world.entity.npc.Villager.LOGGER;

        logger.getClass();
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbttagcompound.put("VillagerData", nbtbase);
        });
        nbttagcompound.putByte("FoodLevel", this.foodLevel);
        nbttagcompound.put("Gossips", (Tag) this.gossips.store((DynamicOps) NbtOps.INSTANCE).getValue());
        nbttagcompound.putInt("Xp", this.villagerXp);
        nbttagcompound.putLong("LastRestock", this.lastRestockGameTime);
        nbttagcompound.putLong("LastGossipDecay", this.lastGossipDecayTime);
        nbttagcompound.putInt("RestocksToday", this.numberOfRestocksToday);
        if (this.assignProfessionWhenSpawned) {
            nbttagcompound.putBoolean("AssignProfessionWhenSpawned", true);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("VillagerData", 10)) {
            DataResult<VillagerData> dataresult = VillagerData.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("VillagerData")));
            Logger logger = net.minecraft.world.entity.npc.Villager.LOGGER;

            logger.getClass();
            dataresult.resultOrPartial(logger::error).ifPresent(this::setVillagerData);
        }

        if (nbttagcompound.contains("Offers", 10)) {
            this.offers = new MerchantOffers(nbttagcompound.getCompound("Offers"));
        }

        if (nbttagcompound.contains("FoodLevel", 1)) {
            this.foodLevel = nbttagcompound.getByte("FoodLevel");
        }

        ListTag nbttaglist = nbttagcompound.getList("Gossips", 10);

        this.gossips.update(new Dynamic(NbtOps.INSTANCE, nbttaglist));
        if (nbttagcompound.contains("Xp", 3)) {
            this.villagerXp = nbttagcompound.getInt("Xp");
        }

        this.lastRestockGameTime = nbttagcompound.getLong("LastRestock");
        this.lastGossipDecayTime = nbttagcompound.getLong("LastGossipDecay");
        this.setCanPickUpLoot(true);
        if (this.level instanceof ServerLevel) {
            this.refreshBrain((ServerLevel) this.level);
        }

        this.numberOfRestocksToday = nbttagcompound.getInt("RestocksToday");
        if (nbttagcompound.contains("AssignProfessionWhenSpawned")) {
            this.assignProfessionWhenSpawned = nbttagcompound.getBoolean("AssignProfessionWhenSpawned");
        }

    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isSleeping() ? null : (this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT);
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.VILLAGER_DEATH;
    }

    public void playWorkSound() {
        SoundEvent soundeffect = this.getVillagerData().getProfession().getWorkSound();

        if (soundeffect != null) {
            this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    public void setVillagerData(VillagerData villagerdata) {
        VillagerData villagerdata1 = this.getVillagerData();

        if (villagerdata1.getProfession() != villagerdata.getProfession()) {
            this.offers = null;
        }

        this.entityData.set(net.minecraft.world.entity.npc.Villager.DATA_VILLAGER_DATA, villagerdata);
    }

    @Override
    public VillagerData getVillagerData() {
        return (VillagerData) this.entityData.get(net.minecraft.world.entity.npc.Villager.DATA_VILLAGER_DATA);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer merchantrecipe) {
        int i = 3 + this.random.nextInt(4);

        this.villagerXp += merchantrecipe.getXp();
        this.lastTradedPlayer = this.getTradingPlayer();
        if (this.shouldIncreaseLevel()) {
            this.updateMerchantTimer = 40;
            this.increaseProfessionLevelOnUpdate = true;
            i += 5;
        }

        if (merchantrecipe.shouldRewardExp()) {
            this.level.addFreshEntity(new ExperienceOrb(this.level, this.getX(), this.getY() + 0.5D, this.getZ(), i));
        }

    }

    @Override
    public void setLastHurtByMob(@Nullable LivingEntity entityliving) {
        if (entityliving != null && this.level instanceof ServerLevel) {
            ((ServerLevel) this.level).onReputationEvent(ReputationEventType.VILLAGER_HURT, (Entity) entityliving, (ReputationEventHandler) this);
            if (this.isAlive() && entityliving instanceof Player) {
                this.level.broadcastEntityEvent(this, (byte) 13);
            }
        }

        super.setLastHurtByMob(entityliving);
    }

    @Override
    public void die(DamageSource damagesource) {
        if (org.spigotmc.SpigotConfig.logVillagerDeaths) net.minecraft.world.entity.npc.Villager.LOGGER.info("Villager {} died, message: '{}'", this, damagesource.getLocalizedDeathMessage(this).getString()); // Spigot
        Entity entity = damagesource.getEntity();

        if (entity != null) {
            this.tellWitnessesThatIWasMurdered(entity);
        }

        this.releasePoi(MemoryModuleType.HOME);
        this.releasePoi(MemoryModuleType.JOB_SITE);
        this.releasePoi(MemoryModuleType.MEETING_POINT);
        super.die(damagesource);
    }

    private void tellWitnessesThatIWasMurdered(Entity entity) {
        if (this.level instanceof ServerLevel) {
            Optional<List<LivingEntity>> optional = this.brain.getMemory(MemoryModuleType.VISIBLE_LIVING_ENTITIES);

            if (optional.isPresent()) {
                ServerLevel worldserver = (ServerLevel) this.level;

                ((List) optional.get()).stream().filter((entityliving) -> {
                    return entityliving instanceof ReputationEventHandler;
                }).forEach((entityliving) -> {
                    worldserver.onReputationEvent(ReputationEventType.VILLAGER_KILLED, entity, (ReputationEventHandler) entityliving);
                });
            }
        }
    }

    public void releasePoi(MemoryModuleType<GlobalPos> memorymoduletype) {
        if (this.level instanceof ServerLevel) {
            MinecraftServer minecraftserver = ((ServerLevel) this.level).getServer();

            this.brain.getMemory(memorymoduletype).ifPresent((globalpos) -> {
                ServerLevel worldserver = minecraftserver.getWorldServer(globalpos.getDimensionManager());

                if (worldserver != null) {
                    PoiManager villageplace = worldserver.getPoiManager();
                    Optional<PoiType> optional = villageplace.getType(globalpos.pos());
                    BiPredicate<net.minecraft.world.entity.npc.Villager, PoiType> bipredicate = (BiPredicate) net.minecraft.world.entity.npc.Villager.POI_MEMORIES.get(memorymoduletype);

                    if (optional.isPresent() && bipredicate.test(this, optional.get())) {
                        villageplace.release(globalpos.pos());
                        DebugPackets.sendPoiTicketCountPacket(worldserver, globalpos.pos());
                    }

                }
            });
        }
    }

    @Override
    public boolean canBreed() {
        return this.foodLevel + this.countFoodPointsInInventory() >= 12 && this.getAge() == 0;
    }

    private boolean hungry() {
        return this.foodLevel < 12;
    }

    private void eatUntilFull() {
        if (this.hungry() && this.countFoodPointsInInventory() != 0) {
            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                ItemStack itemstack = this.getInventory().getItem(i);

                if (!itemstack.isEmpty()) {
                    Integer integer = (Integer) net.minecraft.world.entity.npc.Villager.FOOD_POINTS.get(itemstack.getItem());

                    if (integer != null) {
                        int j = itemstack.getCount();

                        for (int k = j; k > 0; --k) {
                            this.foodLevel = (byte) (this.foodLevel + integer);
                            this.getInventory().removeItem(i, 1);
                            if (!this.hungry()) {
                                return;
                            }
                        }
                    }
                }
            }

        }
    }

    public int getPlayerReputation(Player entityhuman) {
        return this.gossips.getReputation(entityhuman.getUUID(), (reputationtype) -> {
            return true;
        });
    }

    private void digestFood(int i) {
        this.foodLevel = (byte) (this.foodLevel - i);
    }

    public void eatAndDigestFood() {
        this.eatUntilFull();
        this.digestFood(12);
    }

    public void setOffers(MerchantOffers merchantrecipelist) {
        this.offers = merchantrecipelist;
    }

    private boolean shouldIncreaseLevel() {
        int i = this.getVillagerData().getLevel();

        return VillagerData.canLevelUp(i) && this.villagerXp >= VillagerData.getMaxXpPerLevel(i);
    }

    public void increaseMerchantCareer() {
        this.setVillagerData(this.getVillagerData().setLevel(this.getVillagerData().getLevel() + 1));
        this.updateTrades();
    }

    @Override
    protected Component getTypeName() {
        return new TranslatableComponent(this.getType().getDescriptionId() + '.' + Registry.VILLAGER_PROFESSION.getKey(this.getVillagerData().getProfession()).getPath());
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        if (enummobspawn == MobSpawnType.BREEDING) {
            this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.NONE));
        }

        if (enummobspawn == MobSpawnType.COMMAND || enummobspawn == MobSpawnType.SPAWN_EGG || enummobspawn == MobSpawnType.SPAWNER || enummobspawn == MobSpawnType.DISPENSER) {
            this.setVillagerData(this.getVillagerData().setType(VillagerType.byBiome(generatoraccess.getBiome(this.blockPosition()))));
        }

        if (enummobspawn == MobSpawnType.STRUCTURE) {
            this.assignProfessionWhenSpawned = true;
        }

        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    @Override
    public net.minecraft.world.entity.npc.Villager getBreedOffspring(AgableMob entityageable) {
        double d0 = this.random.nextDouble();
        VillagerType villagertype;

        if (d0 < 0.5D) {
            villagertype = VillagerType.byBiome(this.level.getBiome(this.blockPosition()));
        } else if (d0 < 0.75D) {
            villagertype = this.getVillagerData().getType();
        } else {
            villagertype = ((net.minecraft.world.entity.npc.Villager) entityageable).getVillagerData().getType();
        }

        net.minecraft.world.entity.npc.Villager entityvillager = new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER, this.level, villagertype);

        entityvillager.prepare(this.level, this.level.getDamageScaler(entityvillager.blockPosition()), MobSpawnType.BREEDING, (SpawnGroupData) null, (CompoundTag) null);
        return entityvillager;
    }

    @Override
    public void thunderHit(LightningBolt entitylightning) {
        if (this.level.getDifficulty() != Difficulty.PEACEFUL) {
            net.minecraft.world.entity.npc.Villager.LOGGER.info("Villager {} was struck by lightning {}.", this, entitylightning);
            Witch entitywitch = (Witch) EntityType.WITCH.create(this.level);

            entitywitch.moveTo(this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
            entitywitch.prepare(this.level, this.level.getDamageScaler(entitywitch.blockPosition()), MobSpawnType.CONVERSION, (SpawnGroupData) null, (CompoundTag) null);
            entitywitch.setNoAi(this.isNoAi());
            if (this.hasCustomName()) {
                entitywitch.setCustomName(this.getCustomName());
                entitywitch.setCustomNameVisible(this.isCustomNameVisible());
            }

            entitywitch.setPersistenceRequired();
            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, entitywitch, EntityTransformEvent.TransformReason.LIGHTNING).isCancelled()) {
                return;
            }
            this.level.addEntity(entitywitch, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING);
            // CraftBukkit end
            this.remove();
        } else {
            super.thunderHit(entitylightning);
        }

    }

    @Override
    protected void pickUpItem(ItemEntity entityitem) {
        ItemStack itemstack = entityitem.getItem();

        if (this.wantsToPickUp(itemstack)) {
            SimpleContainer inventorysubcontainer = this.getInventory();
            boolean flag = inventorysubcontainer.canAddItem(itemstack);

            if (!flag) {
                return;
            }

            this.onItemPickup(entityitem);
            this.take(entityitem, itemstack.getCount());
            ItemStack itemstack1 = inventorysubcontainer.addItem(itemstack);

            if (itemstack1.isEmpty()) {
                entityitem.remove();
            } else {
                itemstack.setCount(itemstack1.getCount());
            }
        }

    }

    @Override
    public boolean wantsToPickUp(ItemStack itemstack) {
        Item item = itemstack.getItem();

        return (net.minecraft.world.entity.npc.Villager.WANTED_ITEMS.contains(item) || this.getVillagerData().getProfession().getRequestedItems().contains(item)) && this.getInventory().canAddItem(itemstack);
    }

    public boolean hasExcessFood() {
        return this.countFoodPointsInInventory() >= 24;
    }

    public boolean wantsMoreFood() {
        return this.countFoodPointsInInventory() < 12;
    }

    private int countFoodPointsInInventory() {
        SimpleContainer inventorysubcontainer = this.getInventory();

        return net.minecraft.world.entity.npc.Villager.FOOD_POINTS.entrySet().stream().mapToInt((entry) -> {
            return inventorysubcontainer.countItem((Item) entry.getKey()) * (Integer) entry.getValue();
        }).sum();
    }

    public boolean hasFarmSeeds() {
        return this.getInventory().hasAnyOf((Set) ImmutableSet.of(Items.WHEAT_SEEDS, Items.POTATO, Items.CARROT, Items.BEETROOT_SEEDS));
    }

    @Override
    protected void updateTrades() {
        VillagerData villagerdata = this.getVillagerData();
        Int2ObjectMap<VillagerTrades.ItemListing[]> int2objectmap = (Int2ObjectMap) VillagerTrades.TRADES.get(villagerdata.getProfession());

        if (int2objectmap != null && !int2objectmap.isEmpty()) {
            VillagerTrades.ItemListing[] avillagertrades_imerchantrecipeoption = (VillagerTrades.ItemListing[]) int2objectmap.get(villagerdata.getLevel());

            if (avillagertrades_imerchantrecipeoption != null) {
                MerchantOffers merchantrecipelist = this.getOffers();

                this.a(merchantrecipelist, avillagertrades_imerchantrecipeoption, 2);
            }
        }
    }

    public void gossip(net.minecraft.world.entity.npc.Villager entityvillager, long i) {
        if ((i < this.lastGossipTime || i >= this.lastGossipTime + 1200L) && (i < entityvillager.lastGossipTime || i >= entityvillager.lastGossipTime + 1200L)) {
            this.gossips.transferFrom(entityvillager.gossips, this.random, 10);
            this.lastGossipTime = i;
            entityvillager.lastGossipTime = i;
            this.spawnGolemIfNeeded(i, 5);
        }
    }

    private void maybeDecayGossip() {
        long i = this.level.getGameTime();

        if (this.lastGossipDecayTime == 0L) {
            this.lastGossipDecayTime = i;
        } else if (i >= this.lastGossipDecayTime + 24000L) {
            this.gossips.decay();
            this.lastGossipDecayTime = i;
        }
    }

    public void spawnGolemIfNeeded(long i, int j) {
        if (this.wantsToSpawnGolem(i)) {
            AABB axisalignedbb = this.getBoundingBox().inflate(10.0D, 10.0D, 10.0D);
            List<net.minecraft.world.entity.npc.Villager> list = this.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, axisalignedbb);
            List<net.minecraft.world.entity.npc.Villager> list1 = (List) list.stream().filter((entityvillager) -> {
                return entityvillager.wantsToSpawnGolem(i);
            }).limit(5L).collect(Collectors.toList());

            if (list1.size() >= j) {
                IronGolem entityirongolem = this.trySpawnGolem();

                if (entityirongolem != null) {
                    list.forEach((entityvillager) -> {
                        entityvillager.sawGolem(i);
                    });
                }
            }
        }
    }

    private void sawGolem(long i) {
        this.brain.setMemory(MemoryModuleType.GOLEM_LAST_SEEN_TIME, i); // CraftBukkit - decompile error
    }

    private boolean hasSeenGolemRecently(long i) {
        Optional<Long> optional = this.brain.getMemory(MemoryModuleType.GOLEM_LAST_SEEN_TIME);

        if (!optional.isPresent()) {
            return false;
        } else {
            Long olong = (Long) optional.get();

            return i - olong <= 600L;
        }
    }

    public boolean wantsToSpawnGolem(long i) {
        return !this.golemSpawnConditionsMet(this.level.getGameTime()) ? false : !this.hasSeenGolemRecently(i);
    }

    @Nullable
    private IronGolem trySpawnGolem() {
        BlockPos blockposition = this.blockPosition();

        for (int i = 0; i < 10; ++i) {
            double d0 = (double) (this.level.random.nextInt(16) - 8);
            double d1 = (double) (this.level.random.nextInt(16) - 8);
            BlockPos blockposition1 = this.findSpawnPositionForGolemInColumn(blockposition, d0, d1);

            if (blockposition1 != null) {
                IronGolem entityirongolem = (IronGolem) EntityType.IRON_GOLEM.create(this.level, (CompoundTag) null, (Component) null, (Player) null, blockposition1, MobSpawnType.MOB_SUMMONED, false, false);

                if (entityirongolem != null) {
                    if (entityirongolem.checkSpawnRules((LevelAccessor) this.level, MobSpawnType.MOB_SUMMONED) && entityirongolem.checkSpawnObstruction((LevelReader) this.level)) {
                        this.level.addEntity(entityirongolem, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE); // CraftBukkit
                        return entityirongolem;
                    }

                    entityirongolem.remove();
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findSpawnPositionForGolemInColumn(BlockPos blockposition, double d0, double d1) {
        boolean flag = true;
        BlockPos blockposition1 = blockposition.offset(d0, 6.0D, d1);
        BlockState iblockdata = this.level.getType(blockposition1);

        for (int i = 6; i >= -6; --i) {
            BlockPos blockposition2 = blockposition1;
            BlockState iblockdata1 = iblockdata;

            blockposition1 = blockposition1.below();
            iblockdata = this.level.getType(blockposition1);
            if ((iblockdata1.isAir() || iblockdata1.getMaterial().isLiquid()) && iblockdata.getMaterial().isSolidBlocking()) {
                return blockposition2;
            }
        }

        return null;
    }

    @Override
    public void onReputationEventFrom(ReputationEventType reputationevent, Entity entity) {
        if (reputationevent == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
            this.gossips.add(entity.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            this.gossips.add(entity.getUUID(), GossipType.MINOR_POSITIVE, 25);
        } else if (reputationevent == ReputationEventType.TRADE) {
            this.gossips.add(entity.getUUID(), GossipType.TRADING, 2);
        } else if (reputationevent == ReputationEventType.VILLAGER_HURT) {
            this.gossips.add(entity.getUUID(), GossipType.MINOR_NEGATIVE, 25);
        } else if (reputationevent == ReputationEventType.VILLAGER_KILLED) {
            this.gossips.add(entity.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
        }

    }

    @Override
    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void overrideXp(int i) {
        this.villagerXp = i;
    }

    private void resetNumberOfRestocks() {
        this.catchUpDemand();
        this.numberOfRestocksToday = 0;
    }

    public GossipContainer getGossips() {
        return this.gossips;
    }

    public void setGossips(Tag nbtbase) {
        this.gossips.update(new Dynamic(NbtOps.INSTANCE, nbtbase));
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain((LivingEntity) this);
    }

    @Override
    public void startSleeping(BlockPos blockposition) {
        super.startSleeping(blockposition);
        this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level.getGameTime()); // CraftBukkit - decompile error
        this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        this.brain.setMemory(MemoryModuleType.LAST_WOKEN, this.level.getGameTime()); // CraftBukkit - decompile error
    }

    private boolean golemSpawnConditionsMet(long i) {
        Optional<Long> optional = this.brain.getMemory(MemoryModuleType.LAST_SLEPT);

        return optional.isPresent() ? i - (Long) optional.get() < 24000L : false;
    }
}
