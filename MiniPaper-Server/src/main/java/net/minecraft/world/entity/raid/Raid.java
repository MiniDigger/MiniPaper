package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class Raid {

    private static final TranslatableComponent RAID_NAME_COMPONENT = new TranslatableComponent("event.minecraft.raid");
    private static final TranslatableComponent VICTORY = new TranslatableComponent("event.minecraft.raid.victory");
    private static final TranslatableComponent DEFEAT = new TranslatableComponent("event.minecraft.raid.defeat");
    private static final Component RAID_BAR_VICTORY_COMPONENT = Raid.RAID_NAME_COMPONENT.copy().append(" - ").append(Raid.VICTORY);
    private static final Component RAID_BAR_DEFEAT_COMPONENT = Raid.RAID_NAME_COMPONENT.copy().append(" - ").append(Raid.DEFEAT);
    private final Map<Integer, Raider> groupToLeaderMap = Maps.newHashMap();
    private final Map<Integer, Set<Raider>> groupRaiderMap = Maps.newHashMap();
    public final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
    public long ticksActive;
    private BlockPos center;
    private final ServerLevel level;
    private boolean started;
    private final int id;
    public float totalHealth;
    public int badOmenLevel;
    private boolean active;
    private int groupsSpawned;
    private final ServerBossEvent raidEvent;
    private int postRaidTicks;
    private int raidCooldownTicks;
    private final Random random;
    public final int numGroups;
    private Raid.RaidStatus status;
    private int celebrationTicks;
    private Optional<BlockPos> waveSpawnPos;

    public Raid(int i, ServerLevel worldserver, BlockPos blockposition) {
        this.raidEvent = new ServerBossEvent(Raid.RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.random = new Random();
        this.waveSpawnPos = Optional.empty();
        this.id = i;
        this.level = worldserver;
        this.active = true;
        this.raidCooldownTicks = 300;
        this.raidEvent.setPercent(0.0F);
        this.center = blockposition;
        this.numGroups = this.getNumGroups(worldserver.getDifficulty());
        this.status = Raid.RaidStatus.ONGOING;
    }

    public Raid(ServerLevel worldserver, CompoundTag nbttagcompound) {
        this.raidEvent = new ServerBossEvent(Raid.RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.random = new Random();
        this.waveSpawnPos = Optional.empty();
        this.level = worldserver;
        this.id = nbttagcompound.getInt("Id");
        this.started = nbttagcompound.getBoolean("Started");
        this.active = nbttagcompound.getBoolean("Active");
        this.ticksActive = nbttagcompound.getLong("TicksActive");
        this.badOmenLevel = nbttagcompound.getInt("BadOmenLevel");
        this.groupsSpawned = nbttagcompound.getInt("GroupsSpawned");
        this.raidCooldownTicks = nbttagcompound.getInt("PreRaidTicks");
        this.postRaidTicks = nbttagcompound.getInt("PostRaidTicks");
        this.totalHealth = nbttagcompound.getFloat("TotalHealth");
        this.center = new BlockPos(nbttagcompound.getInt("CX"), nbttagcompound.getInt("CY"), nbttagcompound.getInt("CZ"));
        this.numGroups = nbttagcompound.getInt("NumGroups");
        this.status = Raid.RaidStatus.getByName(nbttagcompound.getString("Status"));
        this.heroesOfTheVillage.clear();
        if (nbttagcompound.contains("HeroesOfTheVillage", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("HeroesOfTheVillage", 11);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                this.heroesOfTheVillage.add(NbtUtils.loadUUID(nbttaglist.get(i)));
            }
        }

    }

    public boolean isOver() {
        return this.isVictory() || this.isLoss();
    }

    public boolean isBetweenWaves() {
        return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
    }

    public boolean hasFirstWaveSpawned() {
        return this.groupsSpawned > 0;
    }

    public boolean isStopped() {
        return this.status == Raid.RaidStatus.STOPPED;
    }

    public boolean isVictory() {
        return this.status == Raid.RaidStatus.VICTORY;
    }

    public boolean isLoss() {
        return this.status == Raid.RaidStatus.LOSS;
    }

    // CraftBukkit start
    public boolean isInProgress() {
        return this.status == RaidStatus.ONGOING;
    }
    // CraftBukkit end

    public Level getLevel() {
        return this.level;
    }

    public boolean isStarted() {
        return this.started;
    }

    public int getGroupsSpawned() {
        return this.groupsSpawned;
    }

    private Predicate<ServerPlayer> validPlayer() {
        return (entityplayer) -> {
            BlockPos blockposition = entityplayer.blockPosition();

            return entityplayer.isAlive() && this.level.getRaidAt(blockposition) == this;
        };
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
        List<ServerPlayer> list = this.level.getPlayers(this.validPlayer());
        Iterator iterator = list.iterator();

        ServerPlayer entityplayer;

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            if (!set.contains(entityplayer)) {
                this.raidEvent.addPlayer(entityplayer);
            }
        }

        iterator = set.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            if (!list.contains(entityplayer)) {
                this.raidEvent.removePlayer(entityplayer);
            }
        }

    }

    public int getMaxBadOmenLevel() {
        return 5;
    }

    public int getBadOmenLevel() {
        return this.badOmenLevel;
    }

    public void absorbBadOmen(Player entityhuman) {
        if (entityhuman.hasEffect(MobEffects.BAD_OMEN)) {
            this.badOmenLevel += entityhuman.getEffect(MobEffects.BAD_OMEN).getAmplifier() + 1;
            this.badOmenLevel = Mth.clamp(this.badOmenLevel, 0, this.getMaxBadOmenLevel());
        }

        entityhuman.removeEffect(MobEffects.BAD_OMEN);
    }

    public void stop() {
        this.active = false;
        this.raidEvent.removeAllPlayers();
        this.status = Raid.RaidStatus.STOPPED;
    }

    public void tick() {
        if (!this.isStopped()) {
            if (this.status == Raid.RaidStatus.ONGOING) {
                boolean flag = this.active;

                this.active = this.level.hasChunkAt(this.center);
                if (this.level.getDifficulty() == Difficulty.PEACEFUL) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.PEACE); // CraftBukkit
                    this.stop();
                    return;
                }

                if (flag != this.active) {
                    this.raidEvent.setVisible(this.active);
                }

                if (!this.active) {
                    return;
                }

                if (!this.level.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection();
                }

                if (!this.level.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.RaidStatus.LOSS;
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(this, new java.util.ArrayList<>()); // CraftBukkit
                    } else {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.NOT_IN_VILLAGE); // CraftBukkit
                        this.stop();
                    }
                }

                ++this.ticksActive;
                if (this.ticksActive >= 48000L) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.TIMEOUT); // CraftBukkit
                    this.stop();
                    return;
                }

                int i = this.getTotalRaidersAlive();
                boolean flag1;

                if (i == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks > 0) {
                        flag1 = this.waveSpawnPos.isPresent();
                        boolean flag2 = !flag1 && this.raidCooldownTicks % 5 == 0;

                        if (flag1 && !this.level.getChunkSourceOH().isEntityTickingChunk(new ChunkPos((BlockPos) this.waveSpawnPos.get()))) {
                            flag2 = true;
                        }

                        if (flag2) {
                            byte b0 = 0;

                            if (this.raidCooldownTicks < 100) {
                                b0 = 1;
                            } else if (this.raidCooldownTicks < 40) {
                                b0 = 2;
                            }

                            this.waveSpawnPos = this.getValidSpawnPos(b0);
                        }

                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers();
                        }

                        --this.raidCooldownTicks;
                        this.raidEvent.setPercent(Mth.clamp((float) (300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    } else if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                        this.raidCooldownTicks = 300;
                        this.raidEvent.setName((Component) Raid.RAID_NAME_COMPONENT);
                        return;
                    }
                }

                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers();
                    this.updateRaiders();
                    if (i > 0) {
                        if (i <= 2) {
                            this.raidEvent.setName((Component) Raid.RAID_NAME_COMPONENT.copy().append(" - ").append(new TranslatableComponent("event.minecraft.raid.raiders_remaining", new Object[]{i})));
                        } else {
                            this.raidEvent.setName((Component) Raid.RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName((Component) Raid.RAID_NAME_COMPONENT);
                    }
                }

                flag1 = false;
                int j = 0;

                while (this.shouldSpawnGroup()) {
                    BlockPos blockposition = this.waveSpawnPos.isPresent() ? (BlockPos) this.waveSpawnPos.get() : this.findRandomSpawnPos(j, 20);

                    if (blockposition != null) {
                        this.started = true;
                        this.spawnGroup(blockposition);
                        if (!flag1) {
                            this.playSound(blockposition);
                            flag1 = true;
                        }
                    } else {
                        ++j;
                    }

                    if (j > 3) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.UNSPAWNABLE);  // CraftBukkit
                        this.stop();
                        break;
                    }
                }

                if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        ++this.postRaidTicks;
                    } else {
                        this.status = Raid.RaidStatus.VICTORY;
                        Iterator iterator = this.heroesOfTheVillage.iterator();

                        List<org.bukkit.entity.Player> winners = new java.util.ArrayList<>(); // CraftBukkit
                        while (iterator.hasNext()) {
                            UUID uuid = (UUID) iterator.next();
                            Entity entity = this.level.getEntity(uuid);

                            if (entity instanceof LivingEntity && !entity.isSpectator()) {
                                LivingEntity entityliving = (LivingEntity) entity;

                                entityliving.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.badOmenLevel - 1, false, false, true));
                                if (entityliving instanceof ServerPlayer) {
                                    ServerPlayer entityplayer = (ServerPlayer) entityliving;

                                    entityplayer.awardStat(Stats.RAID_WIN);
                                    CriteriaTriggers.RAID_WIN.trigger(entityplayer);
                                    winners.add(entityplayer.getBukkitEntity()); // CraftBukkit
                                }
                            }
                        }
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(this, winners); // CraftBukkit
                    }
                }

                this.setDirty();
            } else if (this.isOver()) {
                ++this.celebrationTicks;
                if (this.celebrationTicks >= 600) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.FINISHED); // CraftBukkit
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers();
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setPercent(0.0F);
                        this.raidEvent.setName(Raid.RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(Raid.RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }

        }
    }

    private void moveRaidCenterToNearbyVillageSection() {
        Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);
        ServerLevel worldserver = this.level;

        this.level.getClass();
        stream.filter(worldserver::isVillage).map(SectionPos::center).min(Comparator.comparingDouble((blockposition) -> {
            return blockposition.distSqr(this.center);
        })).ifPresent(this::setCenter);
    }

    private Optional<BlockPos> getValidSpawnPos(int i) {
        for (int j = 0; j < 3; ++j) {
            BlockPos blockposition = this.findRandomSpawnPos(i, 1);

            if (blockposition != null) {
                return Optional.of(blockposition);
            }
        }

        return Optional.empty();
    }

    private boolean hasMoreWaves() {
        return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
    }

    private boolean isFinalWave() {
        return this.getGroupsSpawned() == this.numGroups;
    }

    private boolean hasBonusWave() {
        return this.badOmenLevel > 1;
    }

    private boolean hasSpawnedBonusWave() {
        return this.getGroupsSpawned() > this.numGroups;
    }

    private boolean shouldSpawnBonusGroup() {
        return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
    }

    private void updateRaiders() {
        Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
        HashSet hashset = Sets.newHashSet();

        while (iterator.hasNext()) {
            Set<Raider> set = (Set) iterator.next();
            Iterator iterator1 = set.iterator();

            while (iterator1.hasNext()) {
                Raider entityraider = (Raider) iterator1.next();
                BlockPos blockposition = entityraider.blockPosition();

                if (!entityraider.removed && entityraider.level.getDimensionKey() == this.level.getDimensionKey() && this.center.distSqr(blockposition) < 12544.0D) {
                    if (entityraider.tickCount > 600) {
                        if (this.level.getEntity(entityraider.getUUID()) == null) {
                            hashset.add(entityraider);
                        }

                        if (!this.level.isVillage(blockposition) && entityraider.getNoActionTime() > 2400) {
                            entityraider.setTicksOutsideRaid(entityraider.getTicksOutsideRaid() + 1);
                        }

                        if (entityraider.getTicksOutsideRaid() >= 30) {
                            hashset.add(entityraider);
                        }
                    }
                } else {
                    hashset.add(entityraider);
                }
            }
        }

        Iterator iterator2 = hashset.iterator();

        while (iterator2.hasNext()) {
            Raider entityraider1 = (Raider) iterator2.next();

            this.removeFromRaid(entityraider1, true);
        }

    }

    private void playSound(BlockPos blockposition) {
        float f = 13.0F;
        boolean flag = true;
        Collection<ServerPlayer> collection = this.raidEvent.getPlayers();
        Iterator iterator = this.level.players().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            Vec3 vec3d = entityplayer.position();
            Vec3 vec3d1 = Vec3.atCenterOf((Vec3i) blockposition);
            float f1 = Mth.sqrt((vec3d1.x - vec3d.x) * (vec3d1.x - vec3d.x) + (vec3d1.z - vec3d.z) * (vec3d1.z - vec3d.z));
            double d0 = vec3d.x + (double) (13.0F / f1) * (vec3d1.x - vec3d.x);
            double d1 = vec3d.z + (double) (13.0F / f1) * (vec3d1.z - vec3d.z);

            if (f1 <= 64.0F || collection.contains(entityplayer)) {
                entityplayer.connection.sendPacket(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, d0, entityplayer.getY(), d1, 64.0F, 1.0F));
            }
        }

    }

    private void spawnGroup(BlockPos blockposition) {
        boolean flag = false;
        int i = this.groupsSpawned + 1;

        this.totalHealth = 0.0F;
        DifficultyInstance difficultydamagescaler = this.level.getDamageScaler(blockposition);
        boolean flag1 = this.shouldSpawnBonusGroup();
        Raid.RaiderType[] araid_wave = Raid.RaiderType.VALUES;
        int j = araid_wave.length;

        // CraftBukkit start
        Raider leader = null;
        List<Raider> raiders = new java.util.ArrayList<>();
        // CraftBukkit end
        for (int k = 0; k < j; ++k) {
            Raid.RaiderType raid_wave = araid_wave[k];
            int l = this.getDefaultNumSpawns(raid_wave, i, flag1) + this.getPotentialBonusSpawns(raid_wave, this.random, i, difficultydamagescaler, flag1);
            int i1 = 0;

            for (int j1 = 0; j1 < l; ++j1) {
                Raider entityraider = (Raider) raid_wave.entityType.create((Level) this.level);

                if (!flag && entityraider.canBeLeader()) {
                    entityraider.setPatrolLeader(true);
                    this.setLeader(i, entityraider);
                    flag = true;
                    leader = entityraider; // CraftBukkit
                }

                this.joinRaid(i, entityraider, blockposition, false);
                raiders.add(entityraider); // CraftBukkit
                if (raid_wave.entityType == EntityType.RAVAGER) {
                    Raider entityraider1 = null;

                    if (i == this.getNumGroups(Difficulty.NORMAL)) {
                        entityraider1 = (Raider) EntityType.PILLAGER.create((Level) this.level);
                    } else if (i >= this.getNumGroups(Difficulty.HARD)) {
                        if (i1 == 0) {
                            entityraider1 = (Raider) EntityType.EVOKER.create((Level) this.level);
                        } else {
                            entityraider1 = (Raider) EntityType.VINDICATOR.create((Level) this.level);
                        }
                    }

                    ++i1;
                    if (entityraider1 != null) {
                        this.joinRaid(i, entityraider1, blockposition, false);
                        entityraider1.moveTo(blockposition, 0.0F, 0.0F);
                        entityraider1.startRiding(entityraider);
                        raiders.add(entityraider); // CraftBukkit
                    }
                }
            }
        }

        this.waveSpawnPos = Optional.empty();
        ++this.groupsSpawned;
        this.updateBossbar();
        this.setDirty();
        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidSpawnWaveEvent(this, leader, raiders); // CraftBukkit
    }

    public void joinRaid(int i, Raider entityraider, @Nullable BlockPos blockposition, boolean flag) {
        boolean flag1 = this.addWaveMob(i, entityraider);

        if (flag1) {
            entityraider.setCurrentRaid(this);
            entityraider.setWave(i);
            entityraider.setCanJoinRaid(true);
            entityraider.setTicksOutsideRaid(0);
            if (!flag && blockposition != null) {
                entityraider.setPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 1.0D, (double) blockposition.getZ() + 0.5D);
                entityraider.prepare(this.level, this.level.getDamageScaler(blockposition), MobSpawnType.EVENT, (SpawnGroupData) null, (CompoundTag) null);
                entityraider.applyRaidBuffs(i, false);
                entityraider.setOnGround(true);
                this.level.addEntity(entityraider, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID); // CraftBukkit
            }
        }

    }

    public void updateBossbar() {
        this.raidEvent.setPercent(Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;
        Iterator iterator = this.groupRaiderMap.values().iterator();

        while (iterator.hasNext()) {
            Set<Raider> set = (Set) iterator.next();

            Raider entityraider;

            for (Iterator iterator1 = set.iterator(); iterator1.hasNext(); f += entityraider.getHealth()) {
                entityraider = (Raider) iterator1.next();
            }
        }

        return f;
    }

    private boolean shouldSpawnGroup() {
        return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
    }

    public void removeFromRaid(Raider entityraider, boolean flag) {
        Set<Raider> set = (Set) this.groupRaiderMap.get(entityraider.getWave());

        if (set != null) {
            boolean flag1 = set.remove(entityraider);

            if (flag1) {
                if (flag) {
                    this.totalHealth -= entityraider.getHealth();
                }

                entityraider.setCurrentRaid((Raid) null);
                this.updateBossbar();
                this.setDirty();
            }
        }

    }

    private void setDirty() {
        this.level.getRaids().setDirty();
    }

    public static ItemStack getLeaderBannerInstance() {
        ItemStack itemstack = new ItemStack(Items.WHITE_BANNER);
        CompoundTag nbttagcompound = itemstack.getOrCreateTagElement("BlockEntityTag");
        ListTag nbttaglist = (new BannerPattern.Builder()).addPattern(BannerPattern.RHOMBUS_MIDDLE, DyeColor.CYAN).addPattern(BannerPattern.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY).addPattern(BannerPattern.STRIPE_CENTER, DyeColor.GRAY).addPattern(BannerPattern.BORDER, DyeColor.LIGHT_GRAY).addPattern(BannerPattern.STRIPE_MIDDLE, DyeColor.BLACK).addPattern(BannerPattern.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY).addPattern(BannerPattern.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY).addPattern(BannerPattern.BORDER, DyeColor.BLACK).toListTag();

        nbttagcompound.put("Patterns", nbttaglist);
        itemstack.getOrCreateTag().putInt("HideFlags", 32);
        itemstack.setHoverName((Component) (new TranslatableComponent("block.minecraft.ominous_banner")).withStyle(ChatFormatting.GOLD));
        return itemstack;
    }

    @Nullable
    public Raider getLeader(int i) {
        return (Raider) this.groupToLeaderMap.get(i);
    }

    @Nullable
    private BlockPos findRandomSpawnPos(int i, int j) {
        int k = i == 0 ? 2 : 2 - i;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        for (int l = 0; l < j; ++l) {
            float f = this.level.random.nextFloat() * 6.2831855F;
            int i1 = this.center.getX() + Mth.floor(Mth.cos(f) * 32.0F * (float) k) + this.level.random.nextInt(5);
            int j1 = this.center.getZ() + Mth.floor(Mth.sin(f) * 32.0F * (float) k) + this.level.random.nextInt(5);
            int k1 = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, i1, j1);

            blockposition_mutableblockposition.d(i1, k1, j1);
            if ((!this.level.isVillage(blockposition_mutableblockposition) || i >= 2) && this.level.hasChunksAt(blockposition_mutableblockposition.getX() - 10, blockposition_mutableblockposition.getY() - 10, blockposition_mutableblockposition.getZ() - 10, blockposition_mutableblockposition.getX() + 10, blockposition_mutableblockposition.getY() + 10, blockposition_mutableblockposition.getZ() + 10) && this.level.getChunkSourceOH().isEntityTickingChunk(new ChunkPos(blockposition_mutableblockposition)) && (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, (LevelReader) this.level, blockposition_mutableblockposition, EntityType.RAVAGER) || this.level.getType(blockposition_mutableblockposition.below()).is(Blocks.SNOW) && this.level.getType(blockposition_mutableblockposition).isAir())) {
                return blockposition_mutableblockposition;
            }
        }

        return null;
    }

    private boolean addWaveMob(int i, Raider entityraider) {
        return this.addWaveMob(i, entityraider, true);
    }

    public boolean addWaveMob(int i, Raider entityraider, boolean flag) {
        this.groupRaiderMap.computeIfAbsent(i, (integer) -> {
            return Sets.newHashSet();
        });
        Set<Raider> set = (Set) this.groupRaiderMap.get(i);
        Raider entityraider1 = null;
        Iterator iterator = set.iterator();

        while (iterator.hasNext()) {
            Raider entityraider2 = (Raider) iterator.next();

            if (entityraider2.getUUID().equals(entityraider.getUUID())) {
                entityraider1 = entityraider2;
                break;
            }
        }

        if (entityraider1 != null) {
            set.remove(entityraider1);
            set.add(entityraider);
        }

        set.add(entityraider);
        if (flag) {
            this.totalHealth += entityraider.getHealth();
        }

        this.updateBossbar();
        this.setDirty();
        return true;
    }

    public void setLeader(int i, Raider entityraider) {
        this.groupToLeaderMap.put(i, entityraider);
        entityraider.setItemSlot(EquipmentSlot.HEAD, getLeaderBannerInstance());
        entityraider.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    public void removeLeader(int i) {
        this.groupToLeaderMap.remove(i);
    }

    public BlockPos getCenter() {
        return this.center;
    }

    private void setCenter(BlockPos blockposition) {
        this.center = blockposition;
    }

    public int getId() {
        return this.id;
    }

    private int getDefaultNumSpawns(Raid.RaiderType raid_wave, int i, boolean flag) {
        return flag ? raid_wave.spawnsPerWaveBeforeBonus[this.numGroups] : raid_wave.spawnsPerWaveBeforeBonus[i];
    }

    private int getPotentialBonusSpawns(Raid.RaiderType raid_wave, Random random, int i, DifficultyInstance difficultydamagescaler, boolean flag) {
        Difficulty enumdifficulty = difficultydamagescaler.getDifficulty();
        boolean flag1 = enumdifficulty == Difficulty.EASY;
        boolean flag2 = enumdifficulty == Difficulty.NORMAL;
        int j;

        switch (raid_wave) {
            case WITCH:
                if (flag1 || i <= 2 || i == 4) {
                    return 0;
                }

                j = 1;
                break;
            case PILLAGER:
            case VINDICATOR:
                if (flag1) {
                    j = random.nextInt(2);
                } else if (flag2) {
                    j = 1;
                } else {
                    j = 2;
                }
                break;
            case RAVAGER:
                j = !flag1 && flag ? 1 : 0;
                break;
            default:
                return 0;
        }

        return j > 0 ? random.nextInt(j + 1) : 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public CompoundTag save(CompoundTag nbttagcompound) {
        nbttagcompound.putInt("Id", this.id);
        nbttagcompound.putBoolean("Started", this.started);
        nbttagcompound.putBoolean("Active", this.active);
        nbttagcompound.putLong("TicksActive", this.ticksActive);
        nbttagcompound.putInt("BadOmenLevel", this.badOmenLevel);
        nbttagcompound.putInt("GroupsSpawned", this.groupsSpawned);
        nbttagcompound.putInt("PreRaidTicks", this.raidCooldownTicks);
        nbttagcompound.putInt("PostRaidTicks", this.postRaidTicks);
        nbttagcompound.putFloat("TotalHealth", this.totalHealth);
        nbttagcompound.putInt("NumGroups", this.numGroups);
        nbttagcompound.putString("Status", this.status.getName());
        nbttagcompound.putInt("CX", this.center.getX());
        nbttagcompound.putInt("CY", this.center.getY());
        nbttagcompound.putInt("CZ", this.center.getZ());
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.heroesOfTheVillage.iterator();

        while (iterator.hasNext()) {
            UUID uuid = (UUID) iterator.next();

            nbttaglist.add(NbtUtils.createUUID(uuid));
        }

        nbttagcompound.put("HeroesOfTheVillage", nbttaglist);
        return nbttagcompound;
    }

    public int getNumGroups(Difficulty enumdifficulty) {
        switch (enumdifficulty) {
            case EASY:
                return 3;
            case NORMAL:
                return 5;
            case HARD:
                return 7;
            default:
                return 0;
        }
    }

    public float getEnchantOdds() {
        int i = this.getBadOmenLevel();

        return i == 2 ? 0.1F : (i == 3 ? 0.25F : (i == 4 ? 0.5F : (i == 5 ? 0.75F : 0.0F)));
    }

    public void addHeroOfTheVillage(Entity entity) {
        this.heroesOfTheVillage.add(entity.getUUID());
    }

    // CraftBukkit start - a method to get all raiders
    public java.util.Collection<Raider> getRaiders() {
        return this.groupRaiderMap.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
    }
    // CraftBukkit end

    static enum RaiderType {

        VINDICATOR(EntityType.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}), EVOKER(EntityType.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}), PILLAGER(EntityType.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}), WITCH(EntityType.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}), RAVAGER(EntityType.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

        private static final Raid.RaiderType[] VALUES = values();
        private final EntityType<? extends Raider> entityType;
        private final int[] spawnsPerWaveBeforeBonus;

        private RaiderType(EntityType entitytypes, int[] aint) {
            this.entityType = entitytypes;
            this.spawnsPerWaveBeforeBonus = aint;
        }
    }

    static enum RaidStatus {

        ONGOING, VICTORY, LOSS, STOPPED;

        private static final Raid.RaidStatus[] VALUES = values();

        private RaidStatus() {}

        private static Raid.RaidStatus getByName(String s) {
            Raid.RaidStatus[] araid_status = Raid.RaidStatus.VALUES;
            int i = araid_status.length;

            for (int j = 0; j < i; ++j) {
                Raid.RaidStatus raid_status = araid_status[j];

                if (s.equalsIgnoreCase(raid_status.name())) {
                    return raid_status;
                }
            }

            return Raid.RaidStatus.ONGOING;
        }

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
