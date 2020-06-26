package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.entity.Player; // CraftBukkit

public class ChunkMap extends ChunkStorage implements ChunkHolder.PlayerProvider {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final int MAX_CHUNK_DISTANCE = 33 + ChunkStatus.maxDistance();
    public final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap = new Long2ObjectLinkedOpenHashMap();
    public volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
    public final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    public final LongSet entitiesInLevel;
    public final ServerLevel level;
    public final ThreadedLevelLightEngine lightEngine;
    public final BlockableEventLoop<Runnable> mainThreadExecutor;
    public final ChunkGenerator generator;
    public final Supplier<DimensionDataStorage> overworldDataStorage;
    public final PoiManager poiManager;
    public final LongSet toDrop;
    public boolean modified;
    public final ChunkTaskPriorityQueueSorter queueSorter;
    public final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    public final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> mainThreadMailbox;
    public final ChunkProgressListener progressListener;
    public final ChunkMap.DistanceManagerOH distanceManager;
    public final AtomicInteger tickingGenerated;
    public final StructureManager structureManager;
    public final File storageFolder;
    public final PlayerMap playerMap;
    public final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;
    public final Long2ByteMap chunkTypeCache;
    public final Queue<Runnable> unloadQueue;
    public int viewDistance;

    // CraftBukkit start - recursion-safe executor for Chunk loadCallback() and unloadCallback()
    public final CallbackExecutor callbackExecutor = new CallbackExecutor();
    public static final class CallbackExecutor implements java.util.concurrent.Executor, Runnable {

        private Runnable queued;

        @Override
        public void execute(Runnable runnable) {
            if (queued != null) {
                throw new IllegalStateException("Already queued");
            }
            queued = runnable;
        }

        @Override
        public void run() {
            Runnable task = queued;
            queued = null;
            if (task != null) {
                task.run();
            }
        }
    };
    // CraftBukkit end

    public ChunkMap(ServerLevel worldserver, LevelStorageSource.LevelStorageAccess convertable_conversionsession, DataFixer datafixer, StructureManager definedstructuremanager, Executor executor, BlockableEventLoop<Runnable> iasynctaskhandler, LightChunkGetter ilightaccess, ChunkGenerator chunkgenerator, ChunkProgressListener worldloadlistener, Supplier<DimensionDataStorage> supplier, int i, boolean flag) {
        super(new File(convertable_conversionsession.getDimensionPath(worldserver.getDimensionKey()), "region"), datafixer, flag);
        this.visibleChunkMap = this.updatingChunkMap.clone();
        this.pendingUnloads = new Long2ObjectLinkedOpenHashMap();
        this.entitiesInLevel = new LongOpenHashSet();
        this.toDrop = new LongOpenHashSet();
        this.tickingGenerated = new AtomicInteger();
        this.playerMap = new PlayerMap();
        this.entityMap = new Int2ObjectOpenHashMap();
        this.chunkTypeCache = new Long2ByteOpenHashMap();
        this.unloadQueue = Queues.newConcurrentLinkedQueue();
        this.structureManager = definedstructuremanager;
        this.storageFolder = convertable_conversionsession.getDimensionPath(worldserver.getDimensionKey());
        this.level = worldserver;
        this.generator = chunkgenerator;
        this.mainThreadExecutor = iasynctaskhandler;
        ProcessorMailbox<Runnable> threadedmailbox = ProcessorMailbox.create(executor, "worldgen");

        iasynctaskhandler.getClass();
        ProcessorHandle<Runnable> mailbox = ProcessorHandle.of("main", iasynctaskhandler::tell);

        this.progressListener = worldloadlistener;
        ProcessorMailbox<Runnable> threadedmailbox1 = ProcessorMailbox.create(executor, "light");

        this.queueSorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(threadedmailbox, mailbox, threadedmailbox1), executor, Integer.MAX_VALUE);
        this.worldgenMailbox = this.queueSorter.getProcessor(threadedmailbox, false);
        this.mainThreadMailbox = this.queueSorter.getProcessor(mailbox, false);
        this.lightEngine = new ThreadedLevelLightEngine(ilightaccess, this, this.level.dimensionType().hasSkyLight(), threadedmailbox1, this.queueSorter.getProcessor(threadedmailbox1, false));
        this.distanceManager = new ChunkMap.DistanceManagerOH(executor, iasynctaskhandler);
        this.overworldDataStorage = supplier;
        this.poiManager = new PoiManager(new File(this.storageFolder, "poi"), datafixer, flag);
        this.setViewDistance(i);
    }

    private static double euclideanDistanceSquared(ChunkPos chunkcoordintpair, Entity entity) {
        double d0 = (double) (chunkcoordintpair.x * 16 + 8);
        double d1 = (double) (chunkcoordintpair.z * 16 + 8);
        double d2 = d0 - entity.getX();
        double d3 = d1 - entity.getZ();

        return d2 * d2 + d3 * d3;
    }

    private static int checkerboardDistance(ChunkPos chunkcoordintpair, ServerPlayer entityplayer, boolean flag) {
        int i;
        int j;

        if (flag) {
            SectionPos sectionposition = entityplayer.getLastSectionPos();

            i = sectionposition.x();
            j = sectionposition.z();
        } else {
            i = Mth.floor(entityplayer.getX() / 16.0D);
            j = Mth.floor(entityplayer.getZ() / 16.0D);
        }

        return checkerboardDistance(chunkcoordintpair, i, j);
    }

    private static int checkerboardDistance(ChunkPos chunkcoordintpair, int i, int j) {
        int k = chunkcoordintpair.x - i;
        int l = chunkcoordintpair.z - j;

        return Math.max(Math.abs(k), Math.abs(l));
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    public ChunkHolder getUpdatingChunkIfPresent(long i) {
        return (ChunkHolder) this.updatingChunkMap.get(i);
    }

    @Nullable
    protected ChunkHolder getVisibleChunkIfPresent(long i) {
        return (ChunkHolder) this.visibleChunkMap.get(i);
    }

    protected IntSupplier getChunkQueueLevel(long i) {
        return () -> {
            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(i);

            return playerchunk == null ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1 : Math.min(playerchunk.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    private CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.Failure>> getChunkRangeFuture(ChunkPos chunkcoordintpair, int i, IntFunction<ChunkStatus> intfunction) {
        List<CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>>> list = Lists.newArrayList();
        int j = chunkcoordintpair.x;
        int k = chunkcoordintpair.z;

        for (int l = -i; l <= i; ++l) {
            for (int i1 = -i; i1 <= i; ++i1) {
                int j1 = Math.max(Math.abs(i1), Math.abs(l));
                final ChunkPos chunkcoordintpair1 = new ChunkPos(j + i1, k + l);
                long k1 = chunkcoordintpair1.toLong();
                ChunkHolder playerchunk = this.getUpdatingChunkIfPresent(k1);

                if (playerchunk == null) {
                    return CompletableFuture.completedFuture(Either.right(new ChunkHolder.Failure() {
                        public String toString() {
                            return "Unloaded " + chunkcoordintpair1.toString();
                        }
                    }));
                }

                ChunkStatus chunkstatus = (ChunkStatus) intfunction.apply(j1);
                CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = playerchunk.getOrScheduleFuture(chunkstatus, this);

                list.add(completablefuture);
            }
        }

        CompletableFuture<List<Either<ChunkAccess, ChunkHolder.Failure>>> completablefuture1 = Util.sequence((List) list);

        return completablefuture1.thenApply((list1) -> {
            List<ChunkAccess> list2 = Lists.newArrayList();
            // CraftBukkit start - decompile error
            int cnt = 0;

            for (Iterator iterator = list1.iterator(); iterator.hasNext(); ++cnt) {
                final int l1 = cnt;
                // CraftBukkit end
                final Either<ChunkAccess, ChunkHolder.Failure> either = (Either) iterator.next();
                Optional<ChunkAccess> optional = either.left();

                if (!optional.isPresent()) {
                    return Either.right(new ChunkHolder.Failure() {
                        public String toString() {
                            return "Unloaded " + new ChunkPos(j + l1 % (i * 2 + 1), k + l1 / (i * 2 + 1)) + " " + ((ChunkHolder.Failure) either.right().get()).toString();
                        }
                    });
                }

                list2.add(optional.get());
            }

            return Either.left(list2);
        });
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> getEntityTickingRangeFuture(ChunkPos chunkcoordintpair) {
        return this.getChunkRangeFuture(chunkcoordintpair, 2, (i) -> {
            return ChunkStatus.FULL;
        }).thenApplyAsync((either) -> {
            return either.mapLeft((list) -> {
                return (LevelChunk) list.get(list.size() / 2);
            });
        }, this.mainThreadExecutor);
    }

    @Nullable
    private ChunkHolder updateChunkScheduling(long i, int j, @Nullable ChunkHolder playerchunk, int k) {
        if (k > ChunkMap.MAX_CHUNK_DISTANCE && j > ChunkMap.MAX_CHUNK_DISTANCE) {
            return playerchunk;
        } else {
            if (playerchunk != null) {
                playerchunk.setTicketLevel(j);
            }

            if (playerchunk != null) {
                if (j > ChunkMap.MAX_CHUNK_DISTANCE) {
                    this.toDrop.add(i);
                } else {
                    this.toDrop.remove(i);
                }
            }

            if (j <= ChunkMap.MAX_CHUNK_DISTANCE && playerchunk == null) {
                playerchunk = (ChunkHolder) this.pendingUnloads.remove(i);
                if (playerchunk != null) {
                    playerchunk.setTicketLevel(j);
                } else {
                    playerchunk = new ChunkHolder(new ChunkPos(i), j, this.lightEngine, this.queueSorter, this);
                }

                this.updatingChunkMap.put(i, playerchunk);
                this.modified = true;
            }

            return playerchunk;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.queueSorter.close();
            this.poiManager.close();
        } finally {
            super.close();
        }

    }

    protected void saveAllChunks(boolean flag) {
        if (flag) {
            List<ChunkHolder> list = (List) this.visibleChunkMap.values().stream().filter(ChunkHolder::wasAccessibleSinceLastSave).peek(ChunkHolder::refreshAccessibility).collect(Collectors.toList());
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream().map((playerchunk) -> {
                    CompletableFuture completablefuture;

                    do {
                        completablefuture = playerchunk.getChunkToSave();
                        this.mainThreadExecutor.managedBlock(completablefuture::isDone);
                    } while (completablefuture != playerchunk.getChunkToSave());

                    return (ChunkAccess) completablefuture.join();
                }).filter((ichunkaccess) -> {
                    return ichunkaccess instanceof ImposterProtoChunk || ichunkaccess instanceof LevelChunk;
                }).filter(this::save).forEach((ichunkaccess) -> {
                    mutableboolean.setTrue();
                });
            } while (mutableboolean.isTrue());

            this.processUnloads(() -> {
                return true;
            });
            this.flushWorker();
            ChunkMap.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", this.storageFolder.getName());
        } else {
            this.visibleChunkMap.values().stream().filter(ChunkHolder::wasAccessibleSinceLastSave).forEach((playerchunk) -> {
                ChunkAccess ichunkaccess = (ChunkAccess) playerchunk.getChunkToSave().getNow(null); // CraftBukkit - decompile error

                if (ichunkaccess instanceof ImposterProtoChunk || ichunkaccess instanceof LevelChunk) {
                    this.save(ichunkaccess);
                    playerchunk.refreshAccessibility();
                }

            });
        }

    }

    public static final double UNLOAD_QUEUE_RESIZE_FACTOR = 0.96; // Spigot

    protected void tick(BooleanSupplier booleansupplier) {
        ProfilerFiller gameprofilerfiller = this.level.getProfiler();

        gameprofilerfiller.push("poi");
        this.poiManager.tick(booleansupplier);
        gameprofilerfiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(booleansupplier);
        }

        gameprofilerfiller.pop();
    }

    private void processUnloads(BooleanSupplier booleansupplier) {
        LongIterator longiterator = this.toDrop.iterator();
        // Spigot start
        org.spigotmc.SlackActivityAccountant activityAccountant = this.level.getServer().slackActivityAccountant;
        activityAccountant.startActivity(0.5);
        int targetSize = (int) (this.toDrop.size() * UNLOAD_QUEUE_RESIZE_FACTOR);
        // Spigot end
        while (longiterator.hasNext()) { // Spigot
            long j = longiterator.nextLong();
            longiterator.remove(); // Spigot
            ChunkHolder playerchunk = (ChunkHolder) this.updatingChunkMap.remove(j);

            if (playerchunk != null) {
                this.pendingUnloads.put(j, playerchunk);
                this.modified = true;
                // Spigot start
                if (!booleansupplier.getAsBoolean() && this.toDrop.size() <= targetSize && activityAccountant.activityTimeIsExhausted()) {
                    break;
                }
                // Spigot end
                this.scheduleUnload(j, playerchunk);
            }
        }
        activityAccountant.endActivity(); // Spigot

        Runnable runnable;

        while ((booleansupplier.getAsBoolean() || this.unloadQueue.size() > 2000) && (runnable = (Runnable) this.unloadQueue.poll()) != null) {
            runnable.run();
        }

    }

    private void scheduleUnload(long i, ChunkHolder playerchunk) {
        CompletableFuture<ChunkAccess> completablefuture = playerchunk.getChunkToSave();
        Consumer<ChunkAccess> consumer = (ichunkaccess) -> { // CraftBukkit - decompile error
            CompletableFuture<ChunkAccess> completablefuture1 = playerchunk.getChunkToSave();

            if (completablefuture1 != completablefuture) {
                this.scheduleUnload(i, playerchunk);
            } else {
                if (this.pendingUnloads.remove(i, playerchunk) && ichunkaccess != null) {
                    if (ichunkaccess instanceof LevelChunk) {
                        ((LevelChunk) ichunkaccess).setLoaded(false);
                    }

                    this.save(ichunkaccess);
                    if (this.entitiesInLevel.remove(i) && ichunkaccess instanceof LevelChunk) {
                        LevelChunk chunk = (LevelChunk) ichunkaccess;

                        this.level.unload(chunk);
                    }

                    this.lightEngine.updateChunkStatus(ichunkaccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.progressListener.onStatusChange(ichunkaccess.getPos(), (ChunkStatus) null);
                }

            }
        };
        Queue queue = this.unloadQueue;

        this.unloadQueue.getClass();
        completablefuture.thenAcceptAsync(consumer, queue::add).whenComplete((ovoid, throwable) -> {
            if (throwable != null) {
                ChunkMap.LOGGER.error("Failed to save chunk " + playerchunk.getPos(), throwable);
            }

        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        } else {
            this.visibleChunkMap = this.updatingChunkMap.clone();
            this.modified = false;
            return true;
        }
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> schedule(ChunkHolder playerchunk, ChunkStatus chunkstatus) {
        ChunkPos chunkcoordintpair = playerchunk.getPos();

        if (chunkstatus == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkcoordintpair);
        } else {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = playerchunk.getOrScheduleFuture(chunkstatus.getParent(), this);

            return completablefuture.thenComposeAsync((either) -> {
                Optional<ChunkAccess> optional = either.left();

                if (!optional.isPresent()) {
                    return CompletableFuture.completedFuture(either);
                } else {
                    if (chunkstatus == ChunkStatus.LIGHT) {
                        this.distanceManager.addTicket(TicketType.LIGHT, chunkcoordintpair, 33 + ChunkStatus.getDistance(ChunkStatus.FEATURES), chunkcoordintpair);
                    }

                    ChunkAccess ichunkaccess = (ChunkAccess) optional.get();

                    if (ichunkaccess.getStatus().isOrAfter(chunkstatus)) {
                        CompletableFuture completablefuture1;

                        if (chunkstatus == ChunkStatus.LIGHT) {
                            completablefuture1 = this.scheduleChunkGeneration(playerchunk, chunkstatus);
                        } else {
                            completablefuture1 = chunkstatus.load(this.level, this.structureManager, this.lightEngine, (ichunkaccess1) -> {
                                return this.protoChunkToFullChunk(playerchunk);
                            }, ichunkaccess);
                        }

                        this.progressListener.onStatusChange(chunkcoordintpair, chunkstatus);
                        return completablefuture1;
                    } else {
                        return this.scheduleChunkGeneration(playerchunk, chunkstatus);
                    }
                }
            }, this.mainThreadExecutor);
        }
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> scheduleChunkLoad(ChunkPos chunkcoordintpair) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.level.getProfiler().incrementCounter("chunkLoad");
                CompoundTag nbttagcompound = this.readChunk(chunkcoordintpair);

                if (nbttagcompound != null) {
                    boolean flag = nbttagcompound.contains("Level", 10) && nbttagcompound.getCompound("Level").contains("Status", 8);

                    if (flag) {
                        ProtoChunk protochunk = ChunkSerializer.loadChunk(this.level, this.structureManager, this.poiManager, chunkcoordintpair, nbttagcompound);

                        protochunk.setLastSaveTime(this.level.getGameTime());
                        this.markPosition(chunkcoordintpair, protochunk.getStatus().getChunkType());
                        return Either.left(protochunk);
                    }

                    ChunkMap.LOGGER.error("Chunk file at {} is missing level data, skipping", chunkcoordintpair);
                }
            } catch (ReportedException reportedexception) {
                Throwable throwable = reportedexception.getCause();

                if (!(throwable instanceof IOException)) {
                    this.markPositionReplaceable(chunkcoordintpair);
                    throw reportedexception;
                }

                ChunkMap.LOGGER.error("Couldn't load chunk {}", chunkcoordintpair, throwable);
            } catch (Exception exception) {
                ChunkMap.LOGGER.error("Couldn't load chunk {}", chunkcoordintpair, exception);
            }

            this.markPositionReplaceable(chunkcoordintpair);
            return Either.left(new ProtoChunk(chunkcoordintpair, UpgradeData.EMPTY));
        }, this.mainThreadExecutor);
    }

    private void markPositionReplaceable(ChunkPos chunkcoordintpair) {
        this.chunkTypeCache.put(chunkcoordintpair.toLong(), (byte) -1);
    }

    private byte markPosition(ChunkPos chunkcoordintpair, ChunkStatus.ChunkType chunkstatus_type) {
        return this.chunkTypeCache.put(chunkcoordintpair.toLong(), (byte) (chunkstatus_type == ChunkStatus.ChunkType.PROTOCHUNK ? -1 : 1));
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> scheduleChunkGeneration(ChunkHolder playerchunk, ChunkStatus chunkstatus) {
        ChunkPos chunkcoordintpair = playerchunk.getPos();
        CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.Failure>> completablefuture = this.getChunkRangeFuture(chunkcoordintpair, chunkstatus.getRange(), (i) -> {
            return this.getDependencyStatus(chunkstatus, i);
        });

        this.level.getProfiler().incrementCounter(() -> {
            return "chunkGenerate " + chunkstatus.getName();
        });
        return completablefuture.thenComposeAsync((either) -> {
            return (CompletableFuture) either.map((list) -> {
                try {
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture1 = chunkstatus.generate(this.level, this.generator, this.structureManager, this.lightEngine, (ichunkaccess) -> {
                        return this.protoChunkToFullChunk(playerchunk);
                    }, list);

                    this.progressListener.onStatusChange(chunkcoordintpair, chunkstatus);
                    return completablefuture1;
                } catch (Exception exception) {
                    CrashReport crashreport = CrashReport.forThrowable(exception, "Exception generating new chunk");
                    CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Chunk to be generated");

                    crashreportsystemdetails.setDetail("Location", (Object) String.format("%d,%d", chunkcoordintpair.x, chunkcoordintpair.z));
                    crashreportsystemdetails.setDetail("Position hash", (Object) ChunkPos.asLong(chunkcoordintpair.x, chunkcoordintpair.z));
                    crashreportsystemdetails.setDetail("Generator", (Object) this.generator);
                    throw new ReportedException(crashreport);
                }
            }, (playerchunk_failure) -> {
                this.releaseLightTicket(chunkcoordintpair);
                return CompletableFuture.completedFuture(Either.right(playerchunk_failure));
            });
        }, (runnable) -> {
            this.worldgenMailbox.tell(ChunkTaskPriorityQueueSorter.message(playerchunk, runnable));
        });
    }

    protected void releaseLightTicket(ChunkPos chunkcoordintpair) {
        this.mainThreadExecutor.tell(Util.name(() -> {
            this.distanceManager.removeTicket(TicketType.LIGHT, chunkcoordintpair, 33 + ChunkStatus.getDistance(ChunkStatus.FEATURES), chunkcoordintpair);
        }, () -> {
            return "release light ticket " + chunkcoordintpair;
        }));
    }

    private ChunkStatus getDependencyStatus(ChunkStatus chunkstatus, int i) {
        ChunkStatus chunkstatus1;

        if (i == 0) {
            chunkstatus1 = chunkstatus.getParent();
        } else {
            chunkstatus1 = ChunkStatus.getStatus(ChunkStatus.getDistance(chunkstatus) + i);
        }

        return chunkstatus1;
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> protoChunkToFullChunk(ChunkHolder playerchunk) {
        CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = playerchunk.getFutureIfPresentUnchecked(ChunkStatus.FULL.getParent());

        return completablefuture.thenApplyAsync((either) -> {
            ChunkStatus chunkstatus = ChunkHolder.getStatus(playerchunk.getTicketLevel());

            return !chunkstatus.isOrAfter(ChunkStatus.FULL) ? ChunkHolder.UNLOADED_CHUNK : either.mapLeft((ichunkaccess) -> {
                ChunkPos chunkcoordintpair = playerchunk.getPos();
                LevelChunk chunk;

                if (ichunkaccess instanceof ImposterProtoChunk) {
                    chunk = ((ImposterProtoChunk) ichunkaccess).getWrapped();
                } else {
                    chunk = new LevelChunk(this.level, (ProtoChunk) ichunkaccess);
                    playerchunk.replaceProtoChunk(new ImposterProtoChunk(chunk));
                }

                chunk.setFullStatus(() -> {
                    return ChunkHolder.getFullChunkStatus(playerchunk.getTicketLevel());
                });
                chunk.runPostLoad();
                if (this.entitiesInLevel.add(chunkcoordintpair.toLong())) {
                    chunk.setLoaded(true);
                    this.level.addAllPendingBlockEntities(chunk.getBlockEntities().values());
                    List<Entity> list = null;
                    List<Entity>[] aentityslice = chunk.getEntitySlices(); // Spigot
                    int i = aentityslice.length;

                    for (int j = 0; j < i; ++j) {
                        List<Entity> entityslice = aentityslice[j]; // Spigot
                        Iterator iterator = entityslice.iterator();

                        while (iterator.hasNext()) {
                            Entity entity = (Entity) iterator.next();
                            // CraftBukkit start - these are spawned serialized (DefinedStructure) and we don't call an add event below at the moment due to ordering complexities
                            boolean needsRemoval = false;
                            if (chunk.needsDecoration && !this.level.getServerOH().getServer().areNpcsEnabled() && entity instanceof Npc) {
                                entity.remove();
                                needsRemoval = true;
                            }

                            if (!(entity instanceof net.minecraft.world.entity.player.Player) && (needsRemoval || !this.level.loadFromChunk(entity))) {
                                // CraftBukkit end
                                if (list == null) {
                                    list = Lists.newArrayList(new Entity[]{entity});
                                } else {
                                    list.add(entity);
                                }
                            }
                        }
                    }

                    if (list != null) {
                        list.forEach(chunk::removeEntity);
                    }
                }

                return chunk;
            });
        }, (runnable) -> {
            ProcessorHandle mailbox = this.mainThreadMailbox;
            long i = playerchunk.getPos().toLong();

            playerchunk.getClass();
            mailbox.tell(ChunkTaskPriorityQueueSorter.message(runnable, i, playerchunk::getTicketLevel));
        });
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> postProcess(ChunkHolder playerchunk) {
        ChunkPos chunkcoordintpair = playerchunk.getPos();
        CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.Failure>> completablefuture = this.getChunkRangeFuture(chunkcoordintpair, 1, (i) -> {
            return ChunkStatus.FULL;
        });
        CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> completablefuture1 = completablefuture.thenApplyAsync((either) -> {
            return either.flatMap((list) -> {
                LevelChunk chunk = (LevelChunk) list.get(list.size() / 2);

                chunk.postProcessGeneration();
                return Either.left(chunk);
            });
        }, (runnable) -> {
            this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(playerchunk, runnable));
        });

        completablefuture1.thenAcceptAsync((either) -> {
            either.mapLeft((chunk) -> {
                this.tickingGenerated.getAndIncrement();
                Packet<?>[] apacket = new Packet[2];

                this.getPlayers(chunkcoordintpair, false).forEach((entityplayer) -> {
                    this.playerLoadedChunk(entityplayer, apacket, chunk);
                });
                return Either.left(chunk);
            });
        }, (runnable) -> {
            this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(playerchunk, runnable));
        });
        return completablefuture1;
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> unpackTicks(ChunkHolder playerchunk) {
        return playerchunk.getOrScheduleFuture(ChunkStatus.FULL, this).thenApplyAsync((either) -> {
            return either.mapLeft((ichunkaccess) -> {
                LevelChunk chunk = (LevelChunk) ichunkaccess;

                chunk.unpackTicks();
                return chunk;
            });
        }, (runnable) -> {
            this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(playerchunk, runnable));
        });
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    public boolean save(ChunkAccess ichunkaccess) {
        this.poiManager.flush(ichunkaccess.getPos());
        if (!ichunkaccess.isUnsaved()) {
            return false;
        } else {
            ichunkaccess.setLastSaveTime(this.level.getGameTime());
            ichunkaccess.setUnsaved(false);
            ChunkPos chunkcoordintpair = ichunkaccess.getPos();

            try {
                ChunkStatus chunkstatus = ichunkaccess.getStatus();

                if (chunkstatus.getChunkType() != ChunkStatus.ChunkType.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkcoordintpair)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && ichunkaccess.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                this.level.getProfiler().incrementCounter("chunkSave");
                CompoundTag nbttagcompound = ChunkSerializer.write(this.level, ichunkaccess);

                this.write(chunkcoordintpair, nbttagcompound);
                this.markPosition(chunkcoordintpair, chunkstatus.getChunkType());
                return true;
            } catch (Exception exception) {
                ChunkMap.LOGGER.error("Failed to save chunk {},{}", chunkcoordintpair.x, chunkcoordintpair.z, exception);
                return false;
            }
        }
    }

    private boolean isExistingChunkFull(ChunkPos chunkcoordintpair) {
        byte b0 = this.chunkTypeCache.get(chunkcoordintpair.toLong());

        if (b0 != 0) {
            return b0 == 1;
        } else {
            CompoundTag nbttagcompound;

            try {
                nbttagcompound = this.readChunk(chunkcoordintpair);
                if (nbttagcompound == null) {
                    this.markPositionReplaceable(chunkcoordintpair);
                    return false;
                }
            } catch (Exception exception) {
                ChunkMap.LOGGER.error("Failed to read chunk {}", chunkcoordintpair, exception);
                this.markPositionReplaceable(chunkcoordintpair);
                return false;
            }

            ChunkStatus.ChunkType chunkstatus_type = ChunkSerializer.getChunkTypeFromTag(nbttagcompound);

            return this.markPosition(chunkcoordintpair, chunkstatus_type) == 1;
        }
    }

    protected void setViewDistance(int i) {
        int j = Mth.clamp(i + 1, 3, 33);

        if (j != this.viewDistance) {
            int k = this.viewDistance;

            this.viewDistance = j;
            this.distanceManager.updatePlayerTickets(this.viewDistance);
            ObjectIterator objectiterator = this.updatingChunkMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkHolder playerchunk = (ChunkHolder) objectiterator.next();
                ChunkPos chunkcoordintpair = playerchunk.getPos();
                Packet<?>[] apacket = new Packet[2];

                this.getPlayers(chunkcoordintpair, false).forEach((entityplayer) -> {
                    int l = checkerboardDistance(chunkcoordintpair, entityplayer, true);
                    boolean flag = l <= k;
                    boolean flag1 = l <= this.viewDistance;

                    this.sendChunk(entityplayer, chunkcoordintpair, apacket, flag, flag1);
                });
            }
        }

    }

    protected void sendChunk(ServerPlayer entityplayer, ChunkPos chunkcoordintpair, Packet<?>[] apacket, boolean flag, boolean flag1) {
        if (entityplayer.level == this.level) {
            if (flag1 && !flag) {
                ChunkHolder playerchunk = this.getVisibleChunkIfPresent(chunkcoordintpair.toLong());

                if (playerchunk != null) {
                    LevelChunk chunk = playerchunk.getTickingChunk();

                    if (chunk != null) {
                        this.playerLoadedChunk(entityplayer, apacket, chunk);
                    }

                    DebugPackets.sendPoiPacketsForChunk(this.level, chunkcoordintpair);
                }
            }

            if (!flag1 && flag) {
                entityplayer.untrackChunk(chunkcoordintpair);
            }

        }
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    protected ChunkMap.DistanceManagerOH getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<ChunkHolder> getChunks() {
        return Iterables.unmodifiableIterable(this.visibleChunkMap.values());
    }

    void dumpChunks(Writer writer) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("z").addColumn("level").addColumn("in_memory").addColumn("status").addColumn("full_status").addColumn("accessible_ready").addColumn("ticking_ready").addColumn("entity_ticking_ready").addColumn("ticket").addColumn("spawning").addColumn("entity_count").addColumn("block_entity_count").build(writer);
        ObjectBidirectionalIterator objectbidirectionaliterator = this.visibleChunkMap.long2ObjectEntrySet().iterator();

        while (objectbidirectionaliterator.hasNext()) {
            Entry<ChunkHolder> entry = (Entry) objectbidirectionaliterator.next();
            ChunkPos chunkcoordintpair = new ChunkPos(entry.getLongKey());
            ChunkHolder playerchunk = (ChunkHolder) entry.getValue();
            Optional<ChunkAccess> optional = Optional.ofNullable(playerchunk.getLastAvailable());
            Optional<LevelChunk> optional1 = optional.flatMap((ichunkaccess) -> {
                return ichunkaccess instanceof LevelChunk ? Optional.of((LevelChunk) ichunkaccess) : Optional.empty();
            });

            // CraftBukkit - decompile error
            csvwriter.writeRow(chunkcoordintpair.x, chunkcoordintpair.z, playerchunk.getTicketLevel(), optional.isPresent(), optional.map(ChunkAccess::getStatus).orElse(null), optional1.map(LevelChunk::getFullStatus).orElse(null), printFuture(playerchunk.getFullChunkFuture()), printFuture(playerchunk.getTickingChunkFuture()), printFuture(playerchunk.getEntityTickingChunkFuture()), this.distanceManager.getTicketDebugString(entry.getLongKey()), !this.noPlayersCloseForSpawning(chunkcoordintpair), optional1.map((chunk) -> {
                return Stream.of(chunk.getEntitySlices()).mapToInt(List::size).sum(); // Spigot
            }).orElse(0), optional1.map((chunk) -> {
                return chunk.getBlockEntities().size();
            }).orElse(0));
        }

    }

    private static String printFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> completablefuture) {
        try {
            Either<LevelChunk, ChunkHolder.Failure> either = (Either) completablefuture.getNow(null); // CraftBukkit - decompile error

            return either != null ? (String) either.map((chunk) -> {
                return "done";
            }, (playerchunk_failure) -> {
                return "unloaded";
            }) : "not completed";
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    @Nullable
    private CompoundTag readChunk(ChunkPos chunkcoordintpair) throws IOException {
        CompoundTag nbttagcompound = this.read(chunkcoordintpair);

        return nbttagcompound == null ? null : this.getChunkData(this.level.getDimensionKey(), this.overworldDataStorage, nbttagcompound, chunkcoordintpair, level); // CraftBukkit
    }

    boolean noPlayersCloseForSpawning(ChunkPos chunkcoordintpair) {
        // Spigot start
        return isOutsideOfRange(chunkcoordintpair, false);
    }

    boolean isOutsideOfRange(ChunkPos chunkcoordintpair, boolean reducedRange) {
        int chunkRange = level.spigotConfig.mobSpawnRange;
        chunkRange = (chunkRange > level.spigotConfig.viewDistance) ? (byte) level.spigotConfig.viewDistance : chunkRange;
        chunkRange = (chunkRange > 8) ? 8 : chunkRange;

        double blockRange = (reducedRange) ? Math.pow(chunkRange << 4, 2) : 16384.0D;
        // Spigot end
        long i = chunkcoordintpair.toLong();

        return !this.distanceManager.hasPlayersNearby(i) ? true : this.playerMap.getPlayers(i).noneMatch((entityplayer) -> {
            return !entityplayer.isSpectator() && euclideanDistanceSquared(chunkcoordintpair, (Entity) entityplayer) < blockRange; // Spigot
        });
    }

    private boolean skipPlayer(ServerPlayer entityplayer) {
        return entityplayer.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(ServerPlayer entityplayer, boolean flag) {
        boolean flag1 = this.skipPlayer(entityplayer);
        boolean flag2 = this.playerMap.ignoredOrUnknown(entityplayer);
        int i = Mth.floor(entityplayer.getX()) >> 4;
        int j = Mth.floor(entityplayer.getZ()) >> 4;

        if (flag) {
            this.playerMap.addPlayer(ChunkPos.asLong(i, j), entityplayer, flag1);
            this.updatePlayerPos(entityplayer);
            if (!flag1) {
                this.distanceManager.addPlayer(SectionPos.of((Entity) entityplayer), entityplayer);
            }
        } else {
            SectionPos sectionposition = entityplayer.getLastSectionPos();

            this.playerMap.removePlayer(sectionposition.chunk().toLong(), entityplayer);
            if (!flag2) {
                this.distanceManager.removePlayer(sectionposition, entityplayer);
            }
        }

        for (int k = i - this.viewDistance; k <= i + this.viewDistance; ++k) {
            for (int l = j - this.viewDistance; l <= j + this.viewDistance; ++l) {
                ChunkPos chunkcoordintpair = new ChunkPos(k, l);

                this.sendChunk(entityplayer, chunkcoordintpair, new Packet[2], !flag, flag);
            }
        }

    }

    private SectionPos updatePlayerPos(ServerPlayer entityplayer) {
        SectionPos sectionposition = SectionPos.of((Entity) entityplayer);

        entityplayer.setLastSectionPos(sectionposition);
        entityplayer.connection.sendPacket(new ClientboundSetChunkCacheCenterPacket(sectionposition.x(), sectionposition.z()));
        return sectionposition;
    }

    public void move(ServerPlayer entityplayer) {
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();

            if (playerchunkmap_entitytracker.entity == entityplayer) {
                playerchunkmap_entitytracker.updatePlayers(this.level.players());
            } else {
                playerchunkmap_entitytracker.updatePlayer(entityplayer);
            }
        }

        int i = Mth.floor(entityplayer.getX()) >> 4;
        int j = Mth.floor(entityplayer.getZ()) >> 4;
        SectionPos sectionposition = entityplayer.getLastSectionPos();
        SectionPos sectionposition1 = SectionPos.of((Entity) entityplayer);
        long k = sectionposition.chunk().toLong();
        long l = sectionposition1.chunk().toLong();
        boolean flag = this.playerMap.ignored(entityplayer);
        boolean flag1 = this.skipPlayer(entityplayer);
        boolean flag2 = sectionposition.asLong() != sectionposition1.asLong();

        if (flag2 || flag != flag1) {
            this.updatePlayerPos(entityplayer);
            if (!flag) {
                this.distanceManager.removePlayer(sectionposition, entityplayer);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionposition1, entityplayer);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(entityplayer);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(entityplayer);
            }

            if (k != l) {
                this.playerMap.updatePlayer(k, l, entityplayer);
            }
        }

        int i1 = sectionposition.x();
        int j1 = sectionposition.z();
        int k1;
        int l1;

        if (Math.abs(i1 - i) <= this.viewDistance * 2 && Math.abs(j1 - j) <= this.viewDistance * 2) {
            k1 = Math.min(i, i1) - this.viewDistance;
            l1 = Math.min(j, j1) - this.viewDistance;
            int i2 = Math.max(i, i1) + this.viewDistance;
            int j2 = Math.max(j, j1) + this.viewDistance;

            for (int k2 = k1; k2 <= i2; ++k2) {
                for (int l2 = l1; l2 <= j2; ++l2) {
                    ChunkPos chunkcoordintpair = new ChunkPos(k2, l2);
                    boolean flag3 = checkerboardDistance(chunkcoordintpair, i1, j1) <= this.viewDistance;
                    boolean flag4 = checkerboardDistance(chunkcoordintpair, i, j) <= this.viewDistance;

                    this.sendChunk(entityplayer, chunkcoordintpair, new Packet[2], flag3, flag4);
                }
            }
        } else {
            ChunkPos chunkcoordintpair1;
            boolean flag5;
            boolean flag6;

            for (k1 = i1 - this.viewDistance; k1 <= i1 + this.viewDistance; ++k1) {
                for (l1 = j1 - this.viewDistance; l1 <= j1 + this.viewDistance; ++l1) {
                    chunkcoordintpair1 = new ChunkPos(k1, l1);
                    flag5 = true;
                    flag6 = false;
                    this.sendChunk(entityplayer, chunkcoordintpair1, new Packet[2], true, false);
                }
            }

            for (k1 = i - this.viewDistance; k1 <= i + this.viewDistance; ++k1) {
                for (l1 = j - this.viewDistance; l1 <= j + this.viewDistance; ++l1) {
                    chunkcoordintpair1 = new ChunkPos(k1, l1);
                    flag5 = false;
                    flag6 = true;
                    this.sendChunk(entityplayer, chunkcoordintpair1, new Packet[2], false, true);
                }
            }
        }

    }

    @Override
    public Stream<ServerPlayer> getPlayers(ChunkPos chunkcoordintpair, boolean flag) {
        return this.playerMap.getPlayers(chunkcoordintpair.toLong()).filter((entityplayer) -> {
            int i = checkerboardDistance(chunkcoordintpair, entityplayer, true);

            return i > this.viewDistance ? false : !flag || i == this.viewDistance;
        });
    }

    public void addEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity track"); // Spigot
        if (!(entity instanceof EnderDragonPart)) {
            EntityType<?> entitytypes = entity.getType();
            int i = entitytypes.clientTrackingRange() * 16;
            i = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, i); // Spigot
            int j = entitytypes.updateInterval();

            if (this.entityMap.containsKey(entity.getId())) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
            } else {
                ChunkMap.TrackedEntity playerchunkmap_entitytracker = new ChunkMap.TrackedEntity(entity, i, j, entitytypes.trackDeltas());

                this.entityMap.put(entity.getId(), playerchunkmap_entitytracker);
                playerchunkmap_entitytracker.updatePlayers(this.level.players());
                if (entity instanceof ServerPlayer) {
                    ServerPlayer entityplayer = (ServerPlayer) entity;

                    this.updatePlayerStatus(entityplayer, true);
                    ObjectIterator objectiterator = this.entityMap.values().iterator();

                    while (objectiterator.hasNext()) {
                        ChunkMap.TrackedEntity playerchunkmap_entitytracker1 = (ChunkMap.TrackedEntity) objectiterator.next();

                        if (playerchunkmap_entitytracker1.entity != entityplayer) {
                            playerchunkmap_entitytracker1.updatePlayer(entityplayer);
                        }
                    }
                }

            }
        }
    }

    protected void removeEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity untrack"); // Spigot
        if (entity instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entity;

            this.updatePlayerStatus(entityplayer, false);
            ObjectIterator objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();

                playerchunkmap_entitytracker.removePlayer(entityplayer);
            }
        }

        ChunkMap.TrackedEntity playerchunkmap_entitytracker1 = (ChunkMap.TrackedEntity) this.entityMap.remove(entity.getId());

        if (playerchunkmap_entitytracker1 != null) {
            playerchunkmap_entitytracker1.broadcastRemoved();
        }

    }

    protected void tick() {
        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list1 = this.level.players();

        ChunkMap.TrackedEntity playerchunkmap_entitytracker;
        ObjectIterator objectiterator;

        for (objectiterator = this.entityMap.values().iterator(); objectiterator.hasNext(); playerchunkmap_entitytracker.serverEntity.sendChanges()) {
            playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
            SectionPos sectionposition = playerchunkmap_entitytracker.lastSectionPos;
            SectionPos sectionposition1 = SectionPos.of(playerchunkmap_entitytracker.entity);

            if (!Objects.equals(sectionposition, sectionposition1)) {
                playerchunkmap_entitytracker.updatePlayers(list1);
                Entity entity = playerchunkmap_entitytracker.entity;

                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer) entity);
                }

                playerchunkmap_entitytracker.lastSectionPos = sectionposition1;
            }
        }

        if (!list.isEmpty()) {
            objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
                playerchunkmap_entitytracker.updatePlayers(list);
            }
        }

    }

    protected void broadcast(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcast(packet);
        }

    }

    protected void broadcastIncludingSelf(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcastIncludingSelf(packet);
        }

    }

    private void playerLoadedChunk(ServerPlayer entityplayer, Packet<?>[] apacket, LevelChunk chunk) {
        if (apacket[0] == null) {
            apacket[0] = new ClientboundLevelChunkPacket(chunk, 65535, true);
            apacket[1] = new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, true);
        }

        entityplayer.trackChunk(chunk.getPos(), apacket[0], apacket[1]);
        DebugPackets.sendPoiPacketsForChunk(this.level, chunk.getPos());
        List<Entity> list = Lists.newArrayList();
        List<Entity> list1 = Lists.newArrayList();
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
            Entity entity = playerchunkmap_entitytracker.entity;

            if (entity != entityplayer && entity.xChunk == chunk.getPos().x && entity.zChunk == chunk.getPos().z) {
                playerchunkmap_entitytracker.updatePlayer(entityplayer);
                if (entity instanceof Mob && ((Mob) entity).getLeashHolder() != null) {
                    list.add(entity);
                }

                if (!entity.getPassengers().isEmpty()) {
                    list1.add(entity);
                }
            }
        }

        Iterator iterator;
        Entity entity1;

        if (!list.isEmpty()) {
            iterator = list.iterator();

            while (iterator.hasNext()) {
                entity1 = (Entity) iterator.next();
                entityplayer.connection.sendPacket(new ClientboundSetEntityLinkPacket(entity1, ((Mob) entity1).getLeashHolder()));
            }
        }

        if (!list1.isEmpty()) {
            iterator = list1.iterator();

            while (iterator.hasNext()) {
                entity1 = (Entity) iterator.next();
                entityplayer.connection.sendPacket(new ClientboundSetPassengersPacket(entity1));
            }
        }

    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public CompletableFuture<Void> packTicks(LevelChunk chunk) {
        return this.mainThreadExecutor.submit(() -> {
            chunk.packTicks(this.level);
        });
    }

    public class TrackedEntity {

        private final ServerEntity serverEntity;
        private final Entity entity;
        private final int range;
        private SectionPos lastSectionPos;
        public final Set<ServerPlayer> seenBy = Sets.newHashSet();

        public TrackedEntity(Entity entity, int i, int j, boolean flag) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, j, flag, this::broadcast, seenBy); // CraftBukkit
            this.entity = entity;
            this.range = i;
            this.lastSectionPos = SectionPos.of(entity);
        }

        public boolean equals(Object object) {
            return object instanceof ChunkMap.TrackedEntity ? ((ChunkMap.TrackedEntity) object).entity.getId() == this.entity.getId() : false;
        }

        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> packet) {
            Iterator iterator = this.seenBy.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                entityplayer.connection.sendPacket(packet);
            }

        }

        public void broadcastIncludingSelf(Packet<?> packet) {
            this.broadcast(packet);
            if (this.entity instanceof ServerPlayer) {
                ((ServerPlayer) this.entity).connection.sendPacket(packet);
            }

        }

        public void broadcastRemoved() {
            Iterator iterator = this.seenBy.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                this.serverEntity.removePairing(entityplayer);
            }

        }

        public void removePlayer(ServerPlayer entityplayer) {
            org.spigotmc.AsyncCatcher.catchOp("player tracker clear"); // Spigot
            if (this.seenBy.remove(entityplayer)) {
                this.serverEntity.removePairing(entityplayer);
            }

        }

        public void updatePlayer(ServerPlayer entityplayer) {
            org.spigotmc.AsyncCatcher.catchOp("player tracker update"); // Spigot
            if (entityplayer != this.entity) {
                Vec3 vec3d = entityplayer.position().subtract(this.entity.position()); // MC-155077, SPIGOT-5113
                int i = Math.min(this.getEffectiveRange(), (ChunkMap.this.viewDistance - 1) * 16);
                boolean flag = vec3d.x >= (double) (-i) && vec3d.x <= (double) i && vec3d.z >= (double) (-i) && vec3d.z <= (double) i && this.entity.broadcastToPlayer(entityplayer);

                if (flag) {
                    boolean flag1 = this.entity.forcedLoading;

                    if (!flag1) {
                        ChunkPos chunkcoordintpair = new ChunkPos(this.entity.xChunk, this.entity.zChunk);
                        ChunkHolder playerchunk = ChunkMap.this.getVisibleChunkIfPresent(chunkcoordintpair.toLong());

                        if (playerchunk != null && playerchunk.getTickingChunk() != null) {
                            flag1 = ChunkMap.checkerboardDistance(chunkcoordintpair, entityplayer, false) <= ChunkMap.this.viewDistance;
                        }
                    }

                    // CraftBukkit start - respect vanish API
                    if (this.entity instanceof ServerPlayer) {
                        Player player = ((ServerPlayer) this.entity).getBukkitEntity();
                        if (!entityplayer.getBukkitEntity().canSee(player)) {
                            flag1 = false;
                        }
                    }

                    entityplayer.entitiesToRemove.remove(Integer.valueOf(this.entity.getId()));
                    // CraftBukkit end

                    if (flag1 && this.seenBy.add(entityplayer)) {
                        this.serverEntity.addPairing(entityplayer);
                    }
                } else if (this.seenBy.remove(entityplayer)) {
                    this.serverEntity.removePairing(entityplayer);
                }

            }
        }

        private int scaledRange(int i) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(i);
        }

        private int getEffectiveRange() {
            Collection<Entity> collection = this.entity.getIndirectPassengers();
            int i = this.range;
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();
                int j = entity.getType().clientTrackingRange() * 16;

                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> list) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                this.updatePlayer(entityplayer);
            }

        }
    }

    class DistanceManagerOH extends DistanceManager {

        protected DistanceManagerOH(Executor executor, Executor executor1) {
            super(executor, executor1);
        }

        @Override
        protected boolean isChunkToRemove(long i) {
            return ChunkMap.this.toDrop.contains(i);
        }

        @Nullable
        @Override
        protected ChunkHolder getChunk(long i) {
            return ChunkMap.this.getUpdatingChunkIfPresent(i);
        }

        @Nullable
        @Override
        protected ChunkHolder updateChunkScheduling(long i, int j, @Nullable ChunkHolder playerchunk, int k) {
            return ChunkMap.this.updateChunkScheduling(i, j, playerchunk, k);
        }
    }
}
