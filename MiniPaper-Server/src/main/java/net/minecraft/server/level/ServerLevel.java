package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagManager;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PortalForcer;
import net.minecraft.world.level.ServerTickList;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.TickNextTickData;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.SpigotTimings; // Spigot
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.WorldUUID;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.TimeSkipEvent;
// CraftBukkit end

public class ServerLevel extends net.minecraft.world.level.Level implements WorldGenLevel {

    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    private static final Logger LOGGER = LogManager.getLogger();
    public final Int2ObjectMap<Entity> entitiesById = new Int2ObjectLinkedOpenHashMap();
    private final Map<UUID, Entity> entitiesByUuid = Maps.newHashMap();
    private final Queue<Entity> toAddAfterTick = Queues.newArrayDeque();
    private final List<ServerPlayer> players = Lists.newArrayList();
    private final ServerChunkCache chunkSource;
    boolean tickingEntities;
    private final MinecraftServer server;
    public final PrimaryLevelData serverLevelData; // CraftBukkit - type
    public boolean noSave;
    private boolean allPlayersSleeping;
    private int emptyTime;
    private final PortalForcer portalForcer;
    private final ServerTickList<Block> blockTicks;
    private final ServerTickList<Fluid> liquidTicks;
    private final Set<PathNavigation> navigations;
    protected final Raids raids;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    private boolean handlingTick;
    private final List<CustomSpawner> customSpawners;
    @Nullable
    private final EndDragonFight dragonFight;
    private final StructureFeatureManager structureFeatureManager;
    private final boolean tickTime;


    // CraftBukkit start
    private int tickPosition;
    public final LevelStorageSource.LevelStorageAccess convertable;
    public final UUID uuid;

    public LevelChunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunk(x, z, false);
    }

    // Add env and gen to constructor, WorldData -> WorldDataServer
    public ServerLevel(MinecraftServer minecraftserver, Executor executor, LevelStorageSource.LevelStorageAccess convertable_conversionsession, ServerLevelData iworlddataserver, ResourceKey<net.minecraft.world.level.Level> resourcekey, ResourceKey<DimensionType> resourcekey1, DimensionType dimensionmanager, ChunkProgressListener worldloadlistener, ChunkGenerator chunkgenerator, boolean flag, long i, List<CustomSpawner> list, boolean flag1, org.bukkit.World.Environment env, org.bukkit.generator.ChunkGenerator gen) {
        super(iworlddataserver, resourcekey, resourcekey1, dimensionmanager, minecraftserver::getProfiler, false, flag, i, gen, env);
        this.pvpMode = minecraftserver.isPvpAllowed();
        convertable = convertable_conversionsession;
        uuid = WorldUUID.getUUID(convertable_conversionsession.levelPath.toFile());
        // CraftBukkit end
        this.blockTicks = new ServerTickList<>(this, (block) -> {
            return block == null || block.getBlockData().isAir();
        }, Registry.BLOCK::getKey, this::tickBlock);
        this.liquidTicks = new ServerTickList<>(this, (fluidtype) -> {
            return fluidtype == null || fluidtype == Fluids.EMPTY;
        }, Registry.FLUID::getKey, this::tickLiquid);
        this.navigations = Sets.newHashSet();
        this.blockEvents = new ObjectLinkedOpenHashSet();
        this.tickTime = flag1;
        this.server = minecraftserver;
        this.customSpawners = list;
        // CraftBukkit start
        this.serverLevelData = (PrimaryLevelData) iworlddataserver;
        serverLevelData.world = this;
        if (gen != null) {
            chunkgenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(this, chunkgenerator, gen);
        }

        this.chunkSource = new ServerChunkCache(this, convertable_conversionsession, minecraftserver.getFixerUpper(), minecraftserver.getDefinedStructureManager(), executor, chunkgenerator, this.spigotConfig.viewDistance, minecraftserver.forceSynchronousWrites(), worldloadlistener, () -> { // Spigot
            return minecraftserver.overworld().getDataStorage();
        });
        // CraftBukkit end
        this.portalForcer = new PortalForcer(this);
        this.updateSkyBrightness();
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(minecraftserver.getAbsoluteMaxWorldSize());
        this.raids = (Raids) this.getDataStorage().computeIfAbsent(() -> {
            return new Raids(this);
        }, Raids.getFileId(this.dimensionType()));
        if (!minecraftserver.isSingleplayer()) {
            iworlddataserver.setGameType(minecraftserver.getDefaultGameType());
        }

        this.structureFeatureManager = new StructureFeatureManager(this, minecraftserver.getWorldData().worldGenSettings());
        if (this.dimensionType().createDragonFight()) {
            this.dragonFight = new EndDragonFight(this, minecraftserver.getWorldData().worldGenSettings().seed(), minecraftserver.getWorldData().endDragonFightData());
        } else {
            this.dragonFight = null;
        }
        this.getServerOH().addWorld(this.getWorld()); // CraftBukkit
    }

    // CraftBukkit start
    @Override
    public BlockEntity getTileEntity(BlockPos pos, boolean validate) {
        BlockEntity result = super.getTileEntity(pos, validate);
        if (!validate || Thread.currentThread() != this.thread) {
            // SPIGOT-5378: avoid deadlock, this can be called in loading logic (i.e lighting) but getType() will block on chunk load
            return result;
        }
        Block type = getType(pos).getBlock();

        if (result != null && type != Blocks.AIR) {
            if (!result.getType().isValid(type)) {
                result = fixTileEntity(pos, type, result);
            }
        }

        return result;
    }

    private BlockEntity fixTileEntity(BlockPos pos, Block type, BlockEntity found) {
        this.getServerOH().getLogger().log(Level.SEVERE, "Block at {0}, {1}, {2} is {3} but has {4}" + ". "
                + "Bukkit will attempt to fix this, but there may be additional damage that we cannot recover.", new Object[]{pos.getX(), pos.getY(), pos.getZ(), type, found});

        if (type instanceof EntityBlock) {
            BlockEntity replacement = ((EntityBlock) type).newBlockEntity(this);
            replacement.level = this;
            this.setBlockEntity(pos, replacement);
            return replacement;
        } else {
            return found;
        }
    }
    // CraftBukkit end

    public void setWeatherParameters(int i, int j, boolean flag, boolean flag1) {
        this.serverLevelData.setClearWeatherTime(i);
        this.serverLevelData.setRainTime(j);
        this.serverLevelData.setThunderTime(j);
        this.serverLevelData.setRaining(flag);
        this.serverLevelData.setThundering(flag1);
    }

    @Override
    public Biome getUncachedNoiseBiome(int i, int j, int k) {
        return this.getChunkSourceOH().getGenerator().getWorldChunkManager().getNoiseBiome(i, j, k);
    }

    public StructureFeatureManager getStructureManager() {
        return this.structureFeatureManager;
    }

    public void tick(BooleanSupplier booleansupplier) {
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        this.handlingTick = true;
        gameprofilerfiller.push("world border");
        this.getWorldBorder().tick();
        gameprofilerfiller.popPush("weather");
        boolean flag = this.isRaining();

        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int i = this.serverLevelData.getClearWeatherTime();
                int j = this.serverLevelData.getThunderTime();
                int k = this.serverLevelData.getRainTime();
                boolean flag1 = this.levelData.isThundering();
                boolean flag2 = this.levelData.isRaining();

                if (i > 0) {
                    --i;
                    j = flag1 ? 0 : 1;
                    k = flag2 ? 0 : 1;
                    flag1 = false;
                    flag2 = false;
                } else {
                    if (j > 0) {
                        --j;
                        if (j == 0) {
                            flag1 = !flag1;
                        }
                    } else if (flag1) {
                        j = this.random.nextInt(12000) + 3600;
                    } else {
                        j = this.random.nextInt(168000) + 12000;
                    }

                    if (k > 0) {
                        --k;
                        if (k == 0) {
                            flag2 = !flag2;
                        }
                    } else if (flag2) {
                        k = this.random.nextInt(12000) + 12000;
                    } else {
                        k = this.random.nextInt(168000) + 12000;
                    }
                }

                this.serverLevelData.setThunderTime(j);
                this.serverLevelData.setRainTime(k);
                this.serverLevelData.setClearWeatherTime(i);
                this.serverLevelData.setThundering(flag1);
                this.serverLevelData.setRaining(flag2);
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel = (float) ((double) this.thunderLevel + 0.01D);
            } else {
                this.thunderLevel = (float) ((double) this.thunderLevel - 0.01D);
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel = (float) ((double) this.rainLevel + 0.01D);
            } else {
                this.rainLevel = (float) ((double) this.rainLevel - 0.01D);
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        /* CraftBukkit start
        if (this.lastRainLevel != this.rainLevel) {
            this.server.getPlayerList().a((Packet) (new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.h, this.rainLevel)), this.getDimensionKey());
        }

        if (this.lastThunderLevel != this.thunderLevel) {
            this.server.getPlayerList().a((Packet) (new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.i, this.thunderLevel)), this.getDimensionKey());
        }

        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerList().sendAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.c, 0.0F));
            } else {
                this.server.getPlayerList().sendAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.b, 0.0F));
            }

            this.server.getPlayerList().sendAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.h, this.rainLevel));
            this.server.getPlayerList().sendAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.i, this.thunderLevel));
        }
        // */
        for (int idx = 0; idx < this.players.size(); ++idx) {
            if (((ServerPlayer) this.players.get(idx)).level == this) {
                ((ServerPlayer) this.players.get(idx)).tickWeather();
            }
        }

        if (flag != this.isRaining()) {
            // Only send weather packets to those affected
            for (int idx = 0; idx < this.players.size(); ++idx) {
                if (((ServerPlayer) this.players.get(idx)).level == this) {
                    ((ServerPlayer) this.players.get(idx)).setPlayerWeather((!flag ? WeatherType.DOWNFALL : WeatherType.CLEAR), false);
                }
            }
        }
        for (int idx = 0; idx < this.players.size(); ++idx) {
            if (((ServerPlayer) this.players.get(idx)).level == this) {
                ((ServerPlayer) this.players.get(idx)).updateWeather(this.oRainLevel, this.rainLevel, this.oThunderLevel, this.thunderLevel);
            }
        }
        // CraftBukkit end

        if (this.allPlayersSleeping && this.players.stream().noneMatch((entityplayer) -> {
            return !entityplayer.isSpectator() && !entityplayer.isSleepingLongEnough() && !entityplayer.fauxSleeping; // CraftBukkit
        })) {
            // CraftBukkit start
            long l = this.levelData.getDayTime() + 24000L;
            TimeSkipEvent event = new TimeSkipEvent(this.getWorld(), TimeSkipEvent.SkipReason.NIGHT_SKIP, (l - l % 24000L) - this.getDayTime());
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                getServerOH().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }

            }

            if (!event.isCancelled()) {
                this.allPlayersSleeping = false;
                this.wakeUpAllPlayers();
            }
            // CraftBukkit end
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                this.stopWeather();
            }
        }

        this.updateSkyBrightness();
        this.tickTime();
        gameprofilerfiller.popPush("chunkSource");
        this.getChunkSourceOH().tick(booleansupplier);
        gameprofilerfiller.popPush("tickPending");
        timings.doTickPending.startTiming(); // Spigot
        if (!this.isDebug()) {
            this.blockTicks.tick();
            this.liquidTicks.tick();
        }
        timings.doTickPending.stopTiming(); // Spigot

        gameprofilerfiller.popPush("raid");
        this.raids.tick();
        gameprofilerfiller.popPush("blockEvents");
        timings.doSounds.startTiming(); // Spigot
        this.runBlockEvents();
        timings.doSounds.stopTiming(); // Spigot
        this.handlingTick = false;
        gameprofilerfiller.popPush("entities");
        boolean flag3 = true || !this.players.isEmpty() || !this.getForcedChunks().isEmpty(); // CraftBukkit - this prevents entity cleanup, other issues on servers with no players

        if (flag3) {
            this.resetEmptyTime();
        }

        if (flag3 || this.emptyTime++ < 300) {
            timings.tickEntities.startTiming(); // Spigot
            if (this.dragonFight != null) {
                this.dragonFight.tick();
            }

            this.tickingEntities = true;
            ObjectIterator objectiterator = this.entitiesById.int2ObjectEntrySet().iterator();

            org.spigotmc.ActivationRange.activateEntities(this); // Spigot
            timings.entityTick.startTiming(); // Spigot
            while (objectiterator.hasNext()) {
                Entry<Entity> entry = (Entry) objectiterator.next();
                Entity entity = (Entity) entry.getValue();
                Entity entity1 = entity.getVehicle();

                /* CraftBukkit start - We prevent spawning in general, so this butchering is not needed
                if (!this.server.getSpawnAnimals() && (entity instanceof EntityAnimal || entity instanceof EntityWaterAnimal)) {
                    entity.die();
                }

                if (!this.server.getSpawnNPCs() && entity instanceof NPC) {
                    entity.die();
                }
                // CraftBukkit end */

                gameprofilerfiller.push("checkDespawn");
                if (!entity.removed) {
                    entity.checkDespawn();
                }

                gameprofilerfiller.pop();
                if (entity1 != null) {
                    if (!entity1.removed && entity1.hasPassenger(entity)) {
                        continue;
                    }

                    entity.stopRiding();
                }

                gameprofilerfiller.push("tick");
                if (!entity.removed && !(entity instanceof EnderDragonPart)) {
                    this.guardEntityTick(this::tickNonPassenger, entity);
                }

                gameprofilerfiller.pop();
                gameprofilerfiller.push("remove");
                if (entity.removed) {
                    this.removeFromChunk(entity);
                    objectiterator.remove();
                    this.onEntityRemoved(entity);
                }

                gameprofilerfiller.pop();
            }
            timings.entityTick.stopTiming(); // Spigot

            this.tickingEntities = false;

            Entity entity2;

            while ((entity2 = (Entity) this.toAddAfterTick.poll()) != null) {
                this.add(entity2);
            }

            timings.tickEntities.stopTiming(); // Spigot
            this.tickBlockEntities();
        }

        gameprofilerfiller.pop();
    }

    protected void tickTime() {
        if (this.tickTime) {
            long i = this.levelData.getGameTime() + 1L;

            this.serverLevelData.setGameTime(i);
            this.serverLevelData.getScheduledEvents().tick(this.server, i);
            if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }

        }
    }

    public void setDayTime(long i) {
        this.serverLevelData.setDayTime(i);
    }

    public void tickCustomSpawners(boolean flag, boolean flag1) {
        Iterator iterator = this.customSpawners.iterator();

        while (iterator.hasNext()) {
            CustomSpawner mobspawner = (CustomSpawner) iterator.next();

            mobspawner.tick(this, flag, flag1);
        }

    }

    private void wakeUpAllPlayers() {
        (this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList())).forEach((entityplayer) -> { // CraftBukkit - decompile error
            entityplayer.stopSleepInBed(false, false);
        });
    }

    public void tickChunk(LevelChunk chunk, int i) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        boolean flag = this.isRaining();
        int j = chunkcoordintpair.getMinBlockX();
        int k = chunkcoordintpair.getMinBlockZ();
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        gameprofilerfiller.push("thunder");
        BlockPos blockposition;

        if (flag && this.isThundering() && this.random.nextInt(100000) == 0) {
            blockposition = this.findLightingTargetAround(this.getBlockRandomPos(j, 0, k, 15));
            if (this.isRainingAt(blockposition)) {
                DifficultyInstance difficultydamagescaler = this.getDamageScaler(blockposition);
                boolean flag1 = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && this.random.nextDouble() < (double) difficultydamagescaler.getEffectiveDifficulty() * 0.01D;

                if (flag1) {
                    SkeletonHorse entityhorseskeleton = (SkeletonHorse) EntityType.SKELETON_HORSE.create((net.minecraft.world.level.Level) this);

                    entityhorseskeleton.setTrap(true);
                    entityhorseskeleton.setAge(0);
                    entityhorseskeleton.setPos((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                    this.addEntity(entityhorseskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
                }

                LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create((net.minecraft.world.level.Level) this);

                entitylightning.moveTo(Vec3.atBottomCenterOf((Vec3i) blockposition));
                entitylightning.setVisualOnly(flag1);
                this.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER); // CraftBukkit
            }
        }

        gameprofilerfiller.popPush("iceandsnow");
        if (this.random.nextInt(16) == 0) {
            blockposition = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, this.getBlockRandomPos(j, 0, k, 15));
            BlockPos blockposition1 = blockposition.below();
            Biome biomebase = this.getBiome(blockposition);

            if (biomebase.shouldFreeze((LevelReader) this, blockposition1)) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, Blocks.ICE.getBlockData(), null); // CraftBukkit
            }

            if (flag && biomebase.shouldSnow(this, blockposition)) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition, Blocks.SNOW.getBlockData(), null); // CraftBukkit
            }

            if (flag && this.getBiome(blockposition1).getPrecipitation() == Biome.Precipitation.RAIN) {
                this.getType(blockposition1).getBlock().handleRain((net.minecraft.world.level.Level) this, blockposition1);
            }
        }

        gameprofilerfiller.popPush("tickBlocks");
        if (i > 0) {
            LevelChunkSection[] achunksection = chunk.getSections();
            int l = achunksection.length;

            for (int i1 = 0; i1 < l; ++i1) {
                LevelChunkSection chunksection = achunksection[i1];

                if (chunksection != LevelChunk.EMPTY_SECTION && chunksection.isRandomlyTicking()) {
                    int j1 = chunksection.bottomBlockY();

                    for (int k1 = 0; k1 < i; ++k1) {
                        BlockPos blockposition2 = this.getBlockRandomPos(j, j1, k, 15);

                        gameprofilerfiller.push("randomTick");
                        BlockState iblockdata = chunksection.getType(blockposition2.getX() - j, blockposition2.getY() - j1, blockposition2.getZ() - k);

                        if (iblockdata.isRandomlyTicking()) {
                            iblockdata.randomTick(this, blockposition2, this.random);
                        }

                        FluidState fluid = iblockdata.getFluidState();

                        if (fluid.isRandomlyTicking()) {
                            fluid.randomTick(this, blockposition2, this.random);
                        }

                        gameprofilerfiller.pop();
                    }
                }
            }
        }

        gameprofilerfiller.pop();
    }

    protected BlockPos findLightingTargetAround(BlockPos blockposition) {
        BlockPos blockposition1 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockposition);
        AABB axisalignedbb = (new AABB(blockposition1, new BlockPos(blockposition1.getX(), this.getMaxBuildHeight(), blockposition1.getZ()))).inflate(3.0D);
        List<LivingEntity> list = this.getEntitiesOfClass(LivingEntity.class, axisalignedbb, (java.util.function.Predicate<LivingEntity>) (entityliving) -> { // CraftBukkit - decompile error
            return entityliving != null && entityliving.isAlive() && this.canSeeSky(entityliving.blockPosition());
        });

        if (!list.isEmpty()) {
            return ((LivingEntity) list.get(this.random.nextInt(list.size()))).blockPosition();
        } else {
            if (blockposition1.getY() == -1) {
                blockposition1 = blockposition1.above(2);
            }

            return blockposition1;
        }
    }

    public boolean isHandlingTick() {
        return this.handlingTick;
    }

    public void updateSleepingPlayerList() {
        this.allPlayersSleeping = false;
        if (!this.players.isEmpty()) {
            int i = 0;
            int j = 0;
            Iterator iterator = this.players.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (entityplayer.isSpectator() || (entityplayer.fauxSleeping && !entityplayer.isSleeping())) { // CraftBukkit
                    ++i;
                } else if (entityplayer.isSleeping()) {
                    ++j;
                }
            }

            this.allPlayersSleeping = j > 0 && j >= this.players.size() - i;
        }

    }

    @Override
    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    private void stopWeather() {
        // CraftBukkit start
        this.serverLevelData.setRaining(false);
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isRaining()) {
            this.serverLevelData.setRainTime(0);
        }
        // CraftBukkit end
        this.serverLevelData.setThundering(false);
        // CraftBukkit start
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isThundering()) {
            this.serverLevelData.setThunderTime(0);
        }
        // CraftBukkit end
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickLiquid(TickNextTickData<Fluid> nextticklistentry) {
        FluidState fluid = this.getFluidState(nextticklistentry.pos);

        if (fluid.getType() == nextticklistentry.getType()) {
            fluid.tick((net.minecraft.world.level.Level) this, nextticklistentry.pos);
        }

    }

    private void tickBlock(TickNextTickData<Block> nextticklistentry) {
        BlockState iblockdata = this.getType(nextticklistentry.pos);

        if (iblockdata.is((Block) nextticklistentry.getType())) {
            iblockdata.tick(this, nextticklistentry.pos, this.random);
        }

    }

    public void tickNonPassenger(Entity entity) {
        if (!(entity instanceof Player) && !this.getChunkSourceOH().isEntityTickingChunk(entity)) {
            this.updateChunkPos(entity);
        } else {
            // Spigot start
            if (!org.spigotmc.ActivationRange.checkIfActive(entity)) {
                entity.tickCount++;
                entity.inactiveTick();
                return;
            }
            // Spigot end
            entity.tickTimer.startTiming(); // Spigot
            entity.setPosAndOldPos(entity.getX(), entity.getY(), entity.getZ());
            entity.yRotO = entity.yRot;
            entity.xRotO = entity.xRot;
            if (entity.inChunk) {
                ++entity.tickCount;
                ProfilerFiller gameprofilerfiller = this.getProfiler();

                gameprofilerfiller.push(() -> {
                    return Registry.ENTITY_TYPE.getKey(entity.getType()).toString();
                });
                gameprofilerfiller.incrementCounter("tickNonPassenger");
                entity.tick();
                entity.postTick(); // CraftBukkit
                gameprofilerfiller.pop();
            }

            this.updateChunkPos(entity);
            if (entity.inChunk) {
                Iterator iterator = entity.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity1 = (Entity) iterator.next();

                    this.tickPassenger(entity, entity1);
                }
            }
            entity.tickTimer.stopTiming(); // Spigot

        }
    }

    public void tickPassenger(Entity entity, Entity entity1) {
        if (!entity1.removed && entity1.getVehicle() == entity) {
            if (entity1 instanceof Player || this.getChunkSourceOH().isEntityTickingChunk(entity1)) {
                entity1.setPosAndOldPos(entity1.getX(), entity1.getY(), entity1.getZ());
                entity1.yRotO = entity1.yRot;
                entity1.xRotO = entity1.xRot;
                if (entity1.inChunk) {
                    ++entity1.tickCount;
                    ProfilerFiller gameprofilerfiller = this.getProfiler();

                    gameprofilerfiller.push(() -> {
                        return Registry.ENTITY_TYPE.getKey(entity1.getType()).toString();
                    });
                    gameprofilerfiller.incrementCounter("tickPassenger");
                    entity1.rideTick();
                    gameprofilerfiller.pop();
                }

                this.updateChunkPos(entity1);
                if (entity1.inChunk) {
                    Iterator iterator = entity1.getPassengers().iterator();

                    while (iterator.hasNext()) {
                        Entity entity2 = (Entity) iterator.next();

                        this.tickPassenger(entity1, entity2);
                    }
                }

            }
        } else {
            entity1.stopRiding();
        }
    }

    public void updateChunkPos(Entity entity) {
        if (entity.checkAndResetUpdateChunkPos()) {
            this.getProfiler().push("chunkCheck");
            int i = Mth.floor(entity.getX() / 16.0D);
            int j = Mth.floor(entity.getY() / 16.0D);
            int k = Mth.floor(entity.getZ() / 16.0D);

            if (!entity.inChunk || entity.xChunk != i || entity.yChunk != j || entity.zChunk != k) {
                if (entity.inChunk && this.hasChunk(entity.xChunk, entity.zChunk)) {
                    this.getChunk(entity.xChunk, entity.zChunk).removeEntity(entity, entity.yChunk);
                }

                if (!entity.checkAndResetForcedChunkAdditionFlag() && !this.hasChunk(i, k)) {
                    if (entity.inChunk) {
                        ServerLevel.LOGGER.warn("Entity {} left loaded chunk area", entity);
                    }

                    entity.inChunk = false;
                } else {
                    this.getChunk(i, k).addEntity(entity);
                }
            }

            this.getProfiler().pop();
        }
    }

    @Override
    public boolean mayInteract(Player entityhuman, BlockPos blockposition) {
        return !this.server.isUnderSpawnProtection(this, blockposition, entityhuman) && this.getWorldBorder().isWithinBounds(blockposition);
    }

    public void save(@Nullable ProgressListener iprogressupdate, boolean flag, boolean flag1) {
        ServerChunkCache chunkproviderserver = this.getChunkSourceOH();

        if (!flag1) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(getWorld())); // CraftBukkit
            if (iprogressupdate != null) {
                iprogressupdate.progressStartNoAbort(new TranslatableComponent("menu.savingLevel"));
            }

            this.saveLevelData();
            if (iprogressupdate != null) {
                iprogressupdate.progressStage(new TranslatableComponent("menu.savingChunks"));
            }

            chunkproviderserver.save(flag);
        }

        // CraftBukkit start - moved from MinecraftServer.saveChunks
        ServerLevel worldserver1 = this;

        serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save());
        convertable.saveDataTag(this.server.registryHolder, this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        // CraftBukkit end
    }

    private void saveLevelData() {
        if (this.dragonFight != null) {
            this.server.getWorldData().setEndDragonFightData(this.dragonFight.saveData());
        }

        this.getChunkSourceOH().getDataStorage().save();
    }

    public List<Entity> getEntities(@Nullable EntityType<?> entitytypes, Predicate<? super Entity> predicate) {
        List<Entity> list = Lists.newArrayList();
        ServerChunkCache chunkproviderserver = this.getChunkSourceOH();
        ObjectIterator objectiterator = this.entitiesById.values().iterator();

        while (objectiterator.hasNext()) {
            Entity entity = (Entity) objectiterator.next();

            if ((entitytypes == null || entity.getType() == entitytypes) && chunkproviderserver.hasChunk(Mth.floor(entity.getX()) >> 4, Mth.floor(entity.getZ()) >> 4) && predicate.test(entity)) {
                list.add(entity);
            }
        }

        return list;
    }

    public List<EnderDragon> getDragons() {
        List<EnderDragon> list = Lists.newArrayList();
        ObjectIterator objectiterator = this.entitiesById.values().iterator();

        while (objectiterator.hasNext()) {
            Entity entity = (Entity) objectiterator.next();

            if (entity instanceof EnderDragon && entity.isAlive()) {
                list.add((EnderDragon) entity);
            }
        }

        return list;
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
        List<ServerPlayer> list = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (predicate.test(entityplayer)) {
                list.add(entityplayer);
            }
        }

        return list;
    }

    @Nullable
    public ServerPlayer getRandomPlayer() {
        List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addEntity0(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity0(entity, reason);
        // CraftBukkit end
    }

    public boolean addWithUUID(Entity entity) {
        // CraftBukkit start
        return this.addEntitySerialized(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean addEntitySerialized(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity0(entity, reason);
        // CraftBukkit end
    }

    public void addFromAnotherDimension(Entity entity) {
        boolean flag = entity.forcedLoading;

        entity.forcedLoading = true;
        this.addWithUUID(entity);
        entity.forcedLoading = flag;
        this.updateChunkPos(entity);
    }

    public void addDuringCommandTeleport(ServerPlayer entityplayer) {
        this.addPlayer(entityplayer);
        this.updateChunkPos(entityplayer);
    }

    public void addDuringPortalTeleport(ServerPlayer entityplayer) {
        this.addPlayer(entityplayer);
        this.updateChunkPos(entityplayer);
    }

    public void addNewPlayer(ServerPlayer entityplayer) {
        this.addPlayer(entityplayer);
    }

    public void addRespawnedPlayer(ServerPlayer entityplayer) {
        this.addPlayer(entityplayer);
    }

    private void addPlayer(ServerPlayer entityplayer) {
        Entity entity = (Entity) this.entitiesByUuid.get(entityplayer.getUUID());

        if (entity != null) {
            ServerLevel.LOGGER.warn("Force-added player with duplicate UUID {}", entityplayer.getUUID().toString());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer) entity);
        }

        this.players.add(entityplayer);
        this.updateSleepingPlayerList();
        ChunkAccess ichunkaccess = this.getChunk(Mth.floor(entityplayer.getX() / 16.0D), Mth.floor(entityplayer.getZ() / 16.0D), ChunkStatus.FULL, true);

        if (ichunkaccess instanceof LevelChunk) {
            ichunkaccess.addEntity((Entity) entityplayer);
        }

        this.add(entityplayer);
    }

    // CraftBukkit start
    private boolean addEntity0(Entity entity, CreatureSpawnEvent.SpawnReason spawnReason) {
        org.spigotmc.AsyncCatcher.catchOp("entity add"); // Spigot
        if (entity.removed) {
            // WorldServer.LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityTypes.getName(entity.getEntityType())); // CraftBukkit
            return false;
        } else if (this.isUUIDUsed(entity)) {
            return false;
        } else {
            if (!CraftEventFactory.doEntityAddEventCalling(this, entity, spawnReason)) {
                return false;
            }
            // CraftBukkit end
            ChunkAccess ichunkaccess = this.getChunk(Mth.floor(entity.getX() / 16.0D), Mth.floor(entity.getZ() / 16.0D), ChunkStatus.FULL, entity.forcedLoading);

            if (!(ichunkaccess instanceof LevelChunk)) {
                return false;
            } else {
                ichunkaccess.addEntity(entity);
                this.add(entity);
                return true;
            }
        }
    }

    public boolean loadFromChunk(Entity entity) {
        if (this.isUUIDUsed(entity)) {
            return false;
        } else {
            this.add(entity);
            return true;
        }
    }

    private boolean isUUIDUsed(Entity entity) {
        Entity entity1 = (Entity) this.entitiesByUuid.get(entity.getUUID());

        if (entity1 == null) {
            return false;
        } else {
            // WorldServer.LOGGER.warn("Keeping entity {} that already exists with UUID {}", EntityTypes.getName(entity1.getEntityType()), entity.getUniqueID().toString()); // CraftBukkit
            return true;
        }
    }

    public void unload(LevelChunk chunk) {
        // Spigot Start
        for (BlockEntity tileentity : chunk.getBlockEntities().values()) {
            if (tileentity instanceof Container) {
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((Container) tileentity).getViewers())) {
                    h.closeInventory();
                }
            }
        }
        // Spigot End
        this.blockEntitiesToUnload.addAll(chunk.getBlockEntities().values());
        List[] aentityslice = chunk.getEntitySlices(); // Spigot
        int i = aentityslice.length;

        for (int j = 0; j < i; ++j) {
            List<Entity> entityslice = aentityslice[j]; // Spigot
            Iterator iterator = entityslice.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (!(entity instanceof ServerPlayer)) {
                    if (this.tickingEntities) {
                        throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Removing entity while ticking!"));
                    }

                    this.entitiesById.remove(entity.getId());
                    this.onEntityRemoved(entity);
                }
            }
        }

    }

    public void onEntityRemoved(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity unregister"); // Spigot
        // Spigot start
        if ( entity instanceof Player )
        {
            this.getServer().levels.values().stream().map( ServerLevel::getDataStorage ).forEach( (worldData) ->
            {
                for (Object o : worldData.cache.values() )
                {
                    if ( o instanceof MapItemSavedData )
                    {
                        MapItemSavedData map = (MapItemSavedData) o;
                        map.carriedByPlayers.remove( (Player) entity );
                        for ( Iterator<MapItemSavedData.HoldingPlayer> iter = (Iterator<MapItemSavedData.HoldingPlayer>) map.carriedBy.iterator(); iter.hasNext(); )
                        {
                            if ( iter.next().player == entity )
                            {
                                iter.remove();
                            }
                        }
                    }
                }
            } );
        }
        // Spigot end
        // Spigot Start
        if (entity.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder) {
            for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((org.bukkit.inventory.InventoryHolder) entity.getBukkitEntity()).getInventory().getViewers())) {
                h.closeInventory();
            }
        }
        // Spigot End
        if (entity instanceof EnderDragon) {
            EnderDragonPart[] aentitycomplexpart = ((EnderDragon) entity).getSubEntities();
            int i = aentitycomplexpart.length;

            for (int j = 0; j < i; ++j) {
                EnderDragonPart entitycomplexpart = aentitycomplexpart[j];

                entitycomplexpart.remove();
            }
        }

        this.entitiesByUuid.remove(entity.getUUID());
        this.getChunkSourceOH().removeEntity(entity);
        if (entity instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entity;

            this.players.remove(entityplayer);
        }

        this.getScoreboard().entityRemoved(entity);
        // CraftBukkit start - SPIGOT-5278
        if (entity instanceof Drowned) {
            this.navigations.remove(((Drowned) entity).waterNavigation);
            this.navigations.remove(((Drowned) entity).groundNavigation);
        } else
        // CraftBukkit end
        if (entity instanceof Mob) {
            this.navigations.remove(((Mob) entity).getNavigation());
        }

        entity.valid = false; // CraftBukkit
    }

    private void add(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity register"); // Spigot
        if (this.tickingEntities) {
            this.toAddAfterTick.add(entity);
        } else {
            this.entitiesById.put(entity.getId(), entity);
            if (entity instanceof EnderDragon) {
                EnderDragonPart[] aentitycomplexpart = ((EnderDragon) entity).getSubEntities();
                int i = aentitycomplexpart.length;

                for (int j = 0; j < i; ++j) {
                    EnderDragonPart entitycomplexpart = aentitycomplexpart[j];

                    this.entitiesById.put(entitycomplexpart.getId(), entitycomplexpart);
                }
            }

            this.entitiesByUuid.put(entity.getUUID(), entity);
            this.getChunkSourceOH().addEntity(entity);
            // CraftBukkit start - SPIGOT-5278
            if (entity instanceof Drowned) {
                this.navigations.add(((Drowned) entity).waterNavigation);
                this.navigations.add(((Drowned) entity).groundNavigation);
            } else
            // CraftBukkit end
            if (entity instanceof Mob) {
                this.navigations.add(((Mob) entity).getNavigation());
            }
            entity.valid = true; // CraftBukkit
        }

    }

    public void despawn(Entity entity) {
        if (this.tickingEntities) {
            throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Removing entity while ticking!"));
        } else {
            this.removeFromChunk(entity);
            this.entitiesById.remove(entity.getId());
            this.onEntityRemoved(entity);
        }
    }

    private void removeFromChunk(Entity entity) {
        ChunkAccess ichunkaccess = this.getChunk(entity.xChunk, entity.zChunk, ChunkStatus.FULL, false);

        if (ichunkaccess instanceof LevelChunk) {
            ((LevelChunk) ichunkaccess).removeEntity(entity);
        }

    }

    public void removePlayerImmediately(ServerPlayer entityplayer) {
        entityplayer.remove();
        this.despawn(entityplayer);
        this.updateSleepingPlayerList();
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, LightningStrikeEvent.Cause cause) {
        LightningStrikeEvent lightning = new LightningStrikeEvent(this.getWorld(), (org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);
        this.getServerOH().getPluginManager().callEvent(lightning);

        if (lightning.isCancelled()) {
            return false;
        }

        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int i, BlockPos blockposition, int j) {
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        // CraftBukkit start
        Player entityhuman = null;
        Entity entity = this.getEntity(i);
        if (entity instanceof Player) entityhuman = (Player) entity;
        // CraftBukkit end

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer != null && entityplayer.level == this && entityplayer.getId() != i) {
                double d0 = (double) blockposition.getX() - entityplayer.getX();
                double d1 = (double) blockposition.getY() - entityplayer.getY();
                double d2 = (double) blockposition.getZ() - entityplayer.getZ();

                // CraftBukkit start
                if (entityhuman != null && entityhuman instanceof ServerPlayer && !entityplayer.getBukkitEntity().canSee(((ServerPlayer) entityhuman).getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end

                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0D) {
                    entityplayer.connection.sendPacket(new ClientboundBlockDestructionPacket(i, blockposition, j));
                }
            }
        }

    }

    @Override
    public void playSound(@Nullable Player entityhuman, double d0, double d1, double d2, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1) {
        this.server.getPlayerList().sendPacketNearby(entityhuman, d0, d1, d2, f > 1.0F ? (double) (16.0F * f) : 16.0D, this.getDimensionKey(), new ClientboundSoundPacket(soundeffect, soundcategory, d0, d1, d2, f, f1));
    }

    @Override
    public void playSound(@Nullable Player entityhuman, Entity entity, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1) {
        this.server.getPlayerList().sendPacketNearby(entityhuman, entity.getX(), entity.getY(), entity.getZ(), f > 1.0F ? (double) (16.0F * f) : 16.0D, this.getDimensionKey(), new ClientboundSoundEntityPacket(soundeffect, soundcategory, entity, f, f1));
    }

    @Override
    public void globalLevelEvent(int i, BlockPos blockposition, int j) {
        this.server.getPlayerList().sendAll(new ClientboundLevelEventPacket(i, blockposition, j, true));
    }

    @Override
    public void levelEvent(@Nullable Player entityhuman, int i, BlockPos blockposition, int j) {
        this.server.getPlayerList().sendPacketNearby(entityhuman, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 64.0D, this.getDimensionKey(), new ClientboundLevelEventPacket(i, blockposition, j, false));
    }

    @Override
    public void notify(BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1, int i) {
        this.getChunkSourceOH().blockChanged(blockposition);
        VoxelShape voxelshape = iblockdata.getCollisionShape(this, blockposition);
        VoxelShape voxelshape1 = iblockdata1.getCollisionShape(this, blockposition);

        if (Shapes.joinIsNotEmpty(voxelshape, voxelshape1, BooleanOp.NOT_SAME)) {
            Iterator iterator = this.navigations.iterator();

            while (iterator.hasNext()) {
                PathNavigation navigationabstract = (PathNavigation) iterator.next();

                if (!navigationabstract.hasDelayedRecomputation()) {
                    navigationabstract.recomputePath(blockposition);
                }
            }

        }
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte b0) {
        this.getChunkSourceOH().broadcastIncludingSelf(entity, new ClientboundEntityEventPacket(entity, b0));
    }

//    @Override // Toothpick
    public ServerChunkCache getChunkSourceOH() {
        return this.chunkSource;
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, @Nullable DamageSource damagesource, @Nullable ExplosionDamageCalculator explosiondamagecalculator, double d0, double d1, double d2, float f, boolean flag, Explosion.BlockInteraction explosion_effect) {
        // CraftBukkit start
        Explosion explosion = super.createExplosion(entity, damagesource, explosiondamagecalculator, d0, d1, d2, f, flag, explosion_effect);

        if (explosion.wasCanceled) {
            return explosion;
        }

        /* Remove
        Explosion explosion = new Explosion(this, entity, damagesource, explosiondamagecalculator, d0, d1, d2, f, flag, explosion_effect);

        explosion.a();
        explosion.a(false);
        */
        // CraftBukkit end - TODO: Check if explosions are still properly implemented
        if (explosion_effect == Explosion.BlockInteraction.NONE) {
            explosion.clearToBlow();
        }

        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.distanceToSqr(d0, d1, d2) < 4096.0D) {
                entityplayer.connection.sendPacket(new ClientboundExplodePacket(d0, d1, d2, f, explosion.getToBlow(), (Vec3) explosion.getHitPlayers().get(entityplayer)));
            }
        }

        return explosion;
    }

    @Override
    public void blockEvent(BlockPos blockposition, Block block, int i, int j) {
        this.blockEvents.add(new BlockEventData(blockposition, block, i, j));
    }

    private void runBlockEvents() {
        while (!this.blockEvents.isEmpty()) {
            BlockEventData blockactiondata = (BlockEventData) this.blockEvents.removeFirst();

            if (this.doBlockEvent(blockactiondata)) {
                this.server.getPlayerList().sendPacketNearby((Player) null, (double) blockactiondata.getPos().getX(), (double) blockactiondata.getPos().getY(), (double) blockactiondata.getPos().getZ(), 64.0D, this.getDimensionKey(), new ClientboundBlockEventPacket(blockactiondata.getPos(), blockactiondata.getBlock(), blockactiondata.getParamA(), blockactiondata.getParamB()));
            }
        }

    }

    private boolean doBlockEvent(BlockEventData blockactiondata) {
        BlockState iblockdata = this.getType(blockactiondata.getPos());

        return iblockdata.is(blockactiondata.getBlock()) ? iblockdata.triggerEvent((net.minecraft.world.level.Level) this, blockactiondata.getPos(), blockactiondata.getParamA(), blockactiondata.getParamB()) : false;
    }

    @Override
    public ServerTickList<Block> getBlockTickList() {
        return this.blockTicks;
    }

    @Override
    public ServerTickList<Fluid> getFluidTickList() {
        return this.liquidTicks;
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureManager getStructureManager() {
        return this.server.getDefinedStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6) {
        // CraftBukkit - visibility api support
        return sendParticles(null, t0, d0, d1, d2, i, d3, d4, d5, d6, false);
    }

    public <T extends ParticleOptions> int sendParticles(ServerPlayer sender, T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6, boolean force) {
        ClientboundLevelParticlesPacket packetplayoutworldparticles = new ClientboundLevelParticlesPacket(t0, force, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);
        // CraftBukkit end
        int j = 0;

        for (int k = 0; k < this.players.size(); ++k) {
            ServerPlayer entityplayer = (ServerPlayer) this.players.get(k);
            if (sender != null && !entityplayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit

            if (this.sendParticles(entityplayer, force, d0, d1, d2, packetplayoutworldparticles)) { // CraftBukkit
                ++j;
            }
        }

        return j;
    }

    public <T extends ParticleOptions> boolean sendParticles(ServerPlayer entityplayer, T t0, boolean flag, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(t0, flag, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);

        return this.sendParticles(entityplayer, flag, d0, d1, d2, packet);
    }

    private boolean sendParticles(ServerPlayer entityplayer, boolean flag, double d0, double d1, double d2, Packet<?> packet) {
        if (entityplayer.getLevel() != this) {
            return false;
        } else {
            BlockPos blockposition = entityplayer.blockPosition();

            if (blockposition.closerThan((Position) (new Vec3(d0, d1, d2)), flag ? 512.0D : 32.0D)) {
                entityplayer.connection.sendPacket(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntity(int i) {
        return (Entity) this.entitiesById.get(i);
    }

    @Nullable
    public Entity getEntity(UUID uuid) {
        return (Entity) this.entitiesByUuid.get(uuid);
    }

    @Nullable
    public BlockPos findNearestMapFeature(StructureFeature<?> structuregenerator, BlockPos blockposition, int i, boolean flag) {
        return !this.server.getWorldData().worldGenSettings().generateFeatures() ? null : this.getChunkSourceOH().getGenerator().findNearestMapFeature(this, structuregenerator, blockposition, i, flag);
    }

    @Nullable
    public BlockPos findNearestBiome(Biome biomebase, BlockPos blockposition, int i, int j) {
        return this.getChunkSourceOH().getGenerator().getWorldChunkManager().findBiomeHorizontal(blockposition.getX(), blockposition.getY(), blockposition.getZ(), i, j, ImmutableList.of(biomebase), this.random, true);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.server.getRecipeManager();
    }

    @Override
    public TagManager getTagManager() {
        return this.server.getTags();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSourceOH().getDataStorage();
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(String s) {
        return (MapItemSavedData) this.getServer().overworld().getDataStorage().get(() -> {
            // CraftBukkit start
            // We only get here when the data file exists, but is not a valid map
            MapItemSavedData newMap = new MapItemSavedData(s);
            MapInitializeEvent event = new MapInitializeEvent(newMap.mapView);
            Bukkit.getServer().getPluginManager().callEvent(event);
            return newMap;
            // CraftBukkit end
        }, s);
    }

    @Override
    public void setMapData(MapItemSavedData worldmap) {
        this.getServer().overworld().getDataStorage().set((SavedData) worldmap);
    }

    @Override
    public int getFreeMapId() {
        return ((MapIndex) this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex::new, "idcounts")).getFreeAuxValueForMap();
    }

    public void setDefaultSpawnPos(BlockPos blockposition) {
        ChunkPos chunkcoordintpair = new ChunkPos(new BlockPos(this.levelData.getXSpawn(), 0, this.levelData.getZSpawn()));

        this.levelData.setSpawn(blockposition);
        this.getChunkSourceOH().removeRegionTicket(TicketType.START, chunkcoordintpair, 11, Unit.INSTANCE);
        this.getChunkSourceOH().addRegionTicket(TicketType.START, new ChunkPos(blockposition), 11, Unit.INSTANCE);
        this.getServer().getPlayerList().sendAll(new ClientboundSetDefaultSpawnPositionPacket(blockposition));
    }

    public BlockPos getSharedSpawnPos() {
        BlockPos blockposition = new BlockPos(this.levelData.getXSpawn(), this.levelData.getYSpawn(), this.levelData.getZSpawn());

        if (!this.getWorldBorder().isWithinBounds(blockposition)) {
            blockposition = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(this.getWorldBorder().getCenterX(), 0.0D, this.getWorldBorder().getCenterZ()));
        }

        return blockposition;
    }

    public LongSet getForcedChunks() {
        ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) this.getDataStorage().get(ForcedChunksSavedData::new, "chunks");

        return (LongSet) (forcedchunk != null ? LongSets.unmodifiable(forcedchunk.getChunks()) : LongSets.EMPTY_SET);
    }

    public boolean setChunkForced(int i, int j, boolean flag) {
        ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) this.getDataStorage().computeIfAbsent(ForcedChunksSavedData::new, "chunks");
        ChunkPos chunkcoordintpair = new ChunkPos(i, j);
        long k = chunkcoordintpair.toLong();
        boolean flag1;

        if (flag) {
            flag1 = forcedchunk.getChunks().add(k);
            if (flag1) {
                this.getChunk(i, j);
            }
        } else {
            flag1 = forcedchunk.getChunks().remove(k);
        }

        forcedchunk.setDirty(flag1);
        if (flag1) {
            this.getChunkSourceOH().updateChunkForced(chunkcoordintpair, flag);
        }

        return flag1;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void onBlockStateChange(BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1) {
        Optional<PoiType> optional = PoiType.forState(iblockdata);
        Optional<PoiType> optional1 = PoiType.forState(iblockdata1);

        if (!Objects.equals(optional, optional1)) {
            BlockPos blockposition1 = blockposition.immutable();

            optional.ifPresent((villageplacetype) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().remove(blockposition1);
                    DebugPackets.sendPoiRemovedPacket(this, blockposition1);
                });
            });
            optional1.ifPresent((villageplacetype) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().add(blockposition1, villageplacetype);
                    DebugPackets.sendPoiAddedPacket(this, blockposition1);
                });
            });
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSourceOH().getPoiManager();
    }

    public boolean isVillage(BlockPos blockposition) {
        return this.isCloseToVillage(blockposition, 1);
    }

    public boolean isVillage(SectionPos sectionposition) {
        return this.isVillage(sectionposition.center());
    }

    public boolean isCloseToVillage(BlockPos blockposition, int i) {
        return i > 6 ? false : this.sectionsToVillage(SectionPos.of(blockposition)) <= i;
    }

    public int sectionsToVillage(SectionPos sectionposition) {
        return this.getPoiManager().sectionsToVillage(sectionposition);
    }

    public Raids getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPos blockposition) {
        return this.raids.getNearbyRaid(blockposition, 9216);
    }

    public boolean isRaided(BlockPos blockposition) {
        return this.getRaidAt(blockposition) != null;
    }

    public void onReputationEvent(ReputationEventType reputationevent, Entity entity, ReputationEventHandler reputationhandler) {
        reputationhandler.onReputationEventFrom(reputationevent, entity);
    }

    public void saveDebugReport(java.nio.file.Path java_nio_file_path) throws IOException {
        ChunkMap playerchunkmap = this.getChunkSourceOH().chunkMap;
        BufferedWriter bufferedwriter = Files.newBufferedWriter(java_nio_file_path.resolve("stats.txt"));
        Throwable throwable = null;

        try {
            bufferedwriter.write(String.format("spawning_chunks: %d\n", playerchunkmap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState spawnercreature_d = this.getChunkSourceOH().getLastSpawnState();

            if (spawnercreature_d != null) {
                ObjectIterator objectiterator = spawnercreature_d.getMobCategoryCounts().object2IntEntrySet().iterator();

                while (objectiterator.hasNext()) {
                    it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<MobCategory> it_unimi_dsi_fastutil_objects_object2intmap_entry = (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry) objectiterator.next();

                    bufferedwriter.write(String.format("spawn_count.%s: %d\n", ((MobCategory) it_unimi_dsi_fastutil_objects_object2intmap_entry.getKey()).getName(), it_unimi_dsi_fastutil_objects_object2intmap_entry.getIntValue()));
                }
            }

            bufferedwriter.write(String.format("entities: %d\n", this.entitiesById.size()));
            bufferedwriter.write(String.format("block_entities: %d\n", this.blockEntityList.size()));
            bufferedwriter.write(String.format("block_ticks: %d\n", this.getBlockTickList().size()));
            bufferedwriter.write(String.format("fluid_ticks: %d\n", this.getFluidTickList().size()));
            bufferedwriter.write("distance_manager: " + playerchunkmap.getDistanceManager().getDebugStatus() + "\n");
            bufferedwriter.write(String.format("pending_tasks: %d\n", this.getChunkSourceOH().getPendingTasksCount()));
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (bufferedwriter != null) {
                if (throwable != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    bufferedwriter.close();
                }
            }

        }

        CrashReport crashreport = new CrashReport("Level dump", new Exception("dummy"));

        this.fillReportDetails(crashreport);
        BufferedWriter bufferedwriter1 = Files.newBufferedWriter(java_nio_file_path.resolve("example_crash.txt"));
        Throwable throwable3 = null;

        try {
            bufferedwriter1.write(crashreport.getFriendlyReport());
        } catch (Throwable throwable4) {
            throwable3 = throwable4;
            throw throwable4;
        } finally {
            if (bufferedwriter1 != null) {
                if (throwable3 != null) {
                    try {
                        bufferedwriter1.close();
                    } catch (Throwable throwable5) {
                        throwable3.addSuppressed(throwable5);
                    }
                } else {
                    bufferedwriter1.close();
                }
            }

        }

        java.nio.file.Path java_nio_file_path1 = java_nio_file_path.resolve("chunks.csv");
        BufferedWriter bufferedwriter2 = Files.newBufferedWriter(java_nio_file_path1);
        Throwable throwable6 = null;

        try {
            playerchunkmap.dumpChunks((Writer) bufferedwriter2);
        } catch (Throwable throwable7) {
            throwable6 = throwable7;
            throw throwable7;
        } finally {
            if (bufferedwriter2 != null) {
                if (throwable6 != null) {
                    try {
                        bufferedwriter2.close();
                    } catch (Throwable throwable8) {
                        throwable6.addSuppressed(throwable8);
                    }
                } else {
                    bufferedwriter2.close();
                }
            }

        }

        java.nio.file.Path java_nio_file_path2 = java_nio_file_path.resolve("entities.csv");
        BufferedWriter bufferedwriter3 = Files.newBufferedWriter(java_nio_file_path2);
        Throwable throwable9 = null;

        try {
            dumpEntities((Writer) bufferedwriter3, (Iterable) this.entitiesById.values());
        } catch (Throwable throwable10) {
            throwable9 = throwable10;
            throw throwable10;
        } finally {
            if (bufferedwriter3 != null) {
                if (throwable9 != null) {
                    try {
                        bufferedwriter3.close();
                    } catch (Throwable throwable11) {
                        throwable9.addSuppressed(throwable11);
                    }
                } else {
                    bufferedwriter3.close();
                }
            }

        }

        java.nio.file.Path java_nio_file_path3 = java_nio_file_path.resolve("block_entities.csv");
        BufferedWriter bufferedwriter4 = Files.newBufferedWriter(java_nio_file_path3);
        Throwable throwable12 = null;

        try {
            this.dumpBlockEntities((Writer) bufferedwriter4);
        } catch (Throwable throwable13) {
            throwable12 = throwable13;
            throw throwable13;
        } finally {
            if (bufferedwriter4 != null) {
                if (throwable12 != null) {
                    try {
                        bufferedwriter4.close();
                    } catch (Throwable throwable14) {
                        throwable12.addSuppressed(throwable14);
                    }
                } else {
                    bufferedwriter4.close();
                }
            }

        }

    }

    private static void dumpEntities(Writer writer, Iterable<Entity> iterable) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("uuid").addColumn("type").addColumn("alive").addColumn("display_name").addColumn("custom_name").build(writer);
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();
            Component ichatbasecomponent = entity.getCustomName();
            Component ichatbasecomponent1 = entity.getDisplayName();

            csvwriter.writeRow(entity.getX(), entity.getY(), entity.getZ(), entity.getUUID(), Registry.ENTITY_TYPE.getKey(entity.getType()), entity.isAlive(), ichatbasecomponent1.getString(), ichatbasecomponent != null ? ichatbasecomponent.getString() : null);
        }

    }

    private void dumpBlockEntities(Writer writer) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(writer);
        Iterator iterator = this.blockEntityList.iterator();

        while (iterator.hasNext()) {
            BlockEntity tileentity = (BlockEntity) iterator.next();
            BlockPos blockposition = tileentity.getBlockPos();

            csvwriter.writeRow(blockposition.getX(), blockposition.getY(), blockposition.getZ(), Registry.BLOCK_ENTITY_TYPE.getKey(tileentity.getType()));
        }

    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox structureboundingbox) {
        this.blockEvents.removeIf((blockactiondata) -> {
            return structureboundingbox.isInside((Vec3i) blockactiondata.getPos());
        });
    }

    @Override
    public void blockUpdated(BlockPos blockposition, Block block) {
        if (!this.isDebug()) {
            // CraftBukkit start
            if (populating) {
                return;
            }
            // CraftBukkit end
            this.updateNeighborsAt(blockposition, block);
        }

    }

    public Iterable<Entity> getAllEntities() {
        return Iterables.unmodifiableIterable(this.entitiesById.values());
    }

    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.serverLevelData.worldGenSettings().isFlatWorld(); // CraftBukkit
    }

    @Override
    public long getSeed() {
        return this.serverLevelData.worldGenSettings().seed(); // CraftBukkit
    }

    @Nullable
    public EndDragonFight dragonFight() {
        return this.dragonFight;
    }

    public static void makeObsidianPlatform(ServerLevel worldserver) {
        // CraftBukkit start
        ServerLevel.a(worldserver, null);
    }

    public static void a(ServerLevel worldserver, Entity entity) {
        // CraftBukkit end
        BlockPos blockposition = ServerLevel.END_SPAWN_POINT;
        int i = blockposition.getX();
        int j = blockposition.getY() - 2;
        int k = blockposition.getZ();

        // CraftBukkit start
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(worldserver);
        BlockPos.betweenClosed(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2).forEach((blockposition1) -> {
            blockList.setTypeAndData(blockposition1, Blocks.AIR.getBlockData(), 3);
        });
        BlockPos.betweenClosed(i - 2, j, k - 2, i + 2, j, k + 2).forEach((blockposition1) -> {
            blockList.setTypeAndData(blockposition1, Blocks.OBSIDIAN.getBlockData(), 3);
        });
        org.bukkit.World bworld = worldserver.getWorld();
        org.bukkit.event.world.PortalCreateEvent portalEvent = new org.bukkit.event.world.PortalCreateEvent((List<org.bukkit.block.BlockState>) (List) blockList.getList(), bworld, (entity == null) ? null : entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM);

        worldserver.getServerOH().getPluginManager().callEvent(portalEvent);
        if (!portalEvent.isCancelled()) {
            blockList.updateList();
        }
        // CraftBukkit end
    }
}
