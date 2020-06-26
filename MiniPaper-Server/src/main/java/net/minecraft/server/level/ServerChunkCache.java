package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;

public class ServerChunkCache extends ChunkSource {

    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private final DistanceManager distanceManager;
    public final ChunkGenerator generator;
    private final ServerLevel level;
    private final Thread mainThread;
    private final ThreadedLevelLightEngine lightEngine;
    private final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    private long lastInhabitedUpdate;
    public boolean spawnEnemies = true;
    public boolean spawnFriendlies = true;
    private final long[] lastChunkPos = new long[4];
    private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final ChunkAccess[] lastChunk = new ChunkAccess[4];
    @Nullable
    private NaturalSpawner.SpawnState lastSpawnState;

    public ServerChunkCache(ServerLevel worldserver, LevelStorageSource.LevelStorageAccess convertable_conversionsession, DataFixer datafixer, StructureManager definedstructuremanager, Executor executor, ChunkGenerator chunkgenerator, int i, boolean flag, ChunkProgressListener worldloadlistener, Supplier<DimensionDataStorage> supplier) {
        this.level = worldserver;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(worldserver);
        this.generator = chunkgenerator;
        this.mainThread = Thread.currentThread();
        File file = convertable_conversionsession.getDimensionPath(worldserver.getDimensionKey());
        File file1 = new File(file, "data");

        file1.mkdirs();
        this.dataStorage = new DimensionDataStorage(file1, datafixer);
        this.chunkMap = new ChunkMap(worldserver, convertable_conversionsession, datafixer, definedstructuremanager, executor, this.mainThreadProcessor, this, this.getGenerator(), worldloadlistener, supplier, i, flag);
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.clearCache();
    }

    // CraftBukkit start - properly implement isChunkLoaded
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        ChunkHolder chunk = this.chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        return chunk.getFullChunk() != null;
    }
    // CraftBukkit end

    @Override
    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long i) {
        return this.chunkMap.getVisibleChunkIfPresent(i);
    }

    public int getTickingGenerated() {
        return this.chunkMap.getTickingGenerated();
    }

    private void storeInCache(long i, ChunkAccess ichunkaccess, ChunkStatus chunkstatus) {
        for (int j = 3; j > 0; --j) {
            this.lastChunkPos[j] = this.lastChunkPos[j - 1];
            this.lastChunkStatus[j] = this.lastChunkStatus[j - 1];
            this.lastChunk[j] = this.lastChunk[j - 1];
        }

        this.lastChunkPos[0] = i;
        this.lastChunkStatus[0] = chunkstatus;
        this.lastChunk[0] = ichunkaccess;
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        if (Thread.currentThread() != this.mainThread) {
            return (ChunkAccess) CompletableFuture.supplyAsync(() -> {
                return this.getChunk(i, j, chunkstatus, flag);
            }, this.mainThreadProcessor).join();
        } else {
            ProfilerFiller gameprofilerfiller = this.level.getProfiler();

            gameprofilerfiller.incrementCounter("getChunk");
            long k = ChunkPos.asLong(i, j);

            ChunkAccess ichunkaccess;

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && chunkstatus == this.lastChunkStatus[l]) {
                    ichunkaccess = this.lastChunk[l];
                    if (ichunkaccess != null) { // CraftBukkit - the chunk can become accessible in the meantime TODO for non-null chunks it might also make sense to check that the chunk's state hasn't changed in the meantime
                        return ichunkaccess;
                    }
                }
            }

            gameprofilerfiller.incrementCounter("getChunkCacheMiss");
            level.timings.syncChunkLoadTimer.startTiming(); // Spigot
            CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = this.getChunkFutureMainThread(i, j, chunkstatus, flag);

            this.mainThreadProcessor.managedBlock(completablefuture::isDone);
            level.timings.syncChunkLoadTimer.stopTiming(); // Spigot
            ichunkaccess = (ChunkAccess) ((Either) completablefuture.join()).map((ichunkaccess1) -> {
                return ichunkaccess1;
            }, (playerchunk_failure) -> {
                if (flag) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + playerchunk_failure));
                } else {
                    return null;
                }
            });
            this.storeInCache(k, ichunkaccess, chunkstatus);
            return ichunkaccess;
        }
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int i, int j) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        } else {
            this.level.getProfiler().incrementCounter("getChunkNow");
            long k = ChunkPos.asLong(i, j);

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && this.lastChunkStatus[l] == ChunkStatus.FULL) {
                    ChunkAccess ichunkaccess = this.lastChunk[l];

                    return ichunkaccess instanceof LevelChunk ? (LevelChunk) ichunkaccess : null;
                }
            }

            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

            if (playerchunk == null) {
                return null;
            } else {
                Either<ChunkAccess, ChunkHolder.Failure> either = (Either) playerchunk.getFutureIfPresent(ChunkStatus.FULL).getNow(null); // CraftBukkit - decompile error

                if (either == null) {
                    return null;
                } else {
                    ChunkAccess ichunkaccess1 = (ChunkAccess) either.left().orElse(null); // CraftBukkit - decompile error

                    if (ichunkaccess1 != null) {
                        this.storeInCache(k, ichunkaccess1, ChunkStatus.FULL);
                        if (ichunkaccess1 instanceof LevelChunk) {
                            return (LevelChunk) ichunkaccess1;
                        }
                    }

                    return null;
                }
            }
        }
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, (Object) null);
        Arrays.fill(this.lastChunk, (Object) null);
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> getChunkFutureMainThread(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        ChunkPos chunkcoordintpair = new ChunkPos(i, j);
        long k = chunkcoordintpair.toLong();
        int l = 33 + ChunkStatus.getDistance(chunkstatus);
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

        // CraftBukkit start - don't add new ticket for currently unloading chunk
        boolean currentlyUnloading = false;
        if (playerchunk != null) {
            ChunkHolder.FullChunkStatus oldChunkState = ChunkHolder.getFullChunkStatus(playerchunk.oldTicketLevel);
            ChunkHolder.FullChunkStatus currentChunkState = ChunkHolder.getFullChunkStatus(playerchunk.getTicketLevel());
            currentlyUnloading = (oldChunkState.isOrAfter(ChunkHolder.FullChunkStatus.BORDER) && !currentChunkState.isOrAfter(ChunkHolder.FullChunkStatus.BORDER));
        }
        if (flag && !currentlyUnloading) {
            // CraftBukkit end
            this.distanceManager.addTicket(TicketType.UNKNOWN, chunkcoordintpair, l, chunkcoordintpair);
            if (this.chunkAbsent(playerchunk, l)) {
                ProfilerFiller gameprofilerfiller = this.level.getProfiler();

                gameprofilerfiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                playerchunk = this.getVisibleChunkIfPresent(k);
                gameprofilerfiller.pop();
                if (this.chunkAbsent(playerchunk, l)) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(playerchunk, l) ? ChunkHolder.UNLOADED_CHUNK_FUTURE : playerchunk.getOrScheduleFuture(chunkstatus, this.chunkMap);
    }

    private boolean chunkAbsent(@Nullable ChunkHolder playerchunk, int i) {
        return playerchunk == null || playerchunk.oldTicketLevel > i; // CraftBukkit using oldTicketLevel for isLoaded checks
    }

    public boolean hasChunk(int i, int j) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent((new ChunkPos(i, j)).toLong());
        int k = 33 + ChunkStatus.getDistance(ChunkStatus.FULL);

        return !this.chunkAbsent(playerchunk, k);
    }

    @Override
    public BlockGetter getChunkForLighting(int i, int j) {
        long k = ChunkPos.asLong(i, j);
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

        if (playerchunk == null) {
            return null;
        } else {
            int l = ServerChunkCache.CHUNK_STATUSES.size() - 1;

            while (true) {
                ChunkStatus chunkstatus = (ChunkStatus) ServerChunkCache.CHUNK_STATUSES.get(l);
                Optional<ChunkAccess> optional = ((Either) playerchunk.getFutureIfPresentUnchecked(chunkstatus).getNow(ChunkHolder.UNLOADED_CHUNK)).left();

                if (optional.isPresent()) {
                    return (BlockGetter) optional.get();
                }

                if (chunkstatus == ChunkStatus.LIGHT.getParent()) {
                    return null;
                }

                --l;
            }
        }
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    private boolean runDistanceManagerUpdates() {
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = this.chunkMap.promoteChunkMap();

        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCache();
            return true;
        }
    }

    public final boolean isInEntityTickingChunk(Entity entity) { return this.isEntityTickingChunk(entity); } // Paper - OBFHELPER
    @Override public boolean isEntityTickingChunk(Entity entity) {
        long i = ChunkPos.asLong(Mth.floor(entity.getX()) >> 4, Mth.floor(entity.getZ()) >> 4);

        return this.checkChunkFuture(i, (Function<ChunkHolder, CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>>>) ChunkHolder::getEntityTickingChunkFuture); // CraftBukkit - decompile error
    }

    @Override public boolean isEntityTickingChunk(ChunkPos chunkcoordintpair) {
        return this.checkChunkFuture(chunkcoordintpair.toLong(), (Function<ChunkHolder, CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>>>) ChunkHolder::getEntityTickingChunkFuture); // CraftBukkit - decompile error
    }

    @Override
    public boolean isTickingChunk(BlockPos blockposition) {
        long i = ChunkPos.asLong(blockposition.getX() >> 4, blockposition.getZ() >> 4);

        return this.checkChunkFuture(i, (Function<ChunkHolder, CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>>>) ChunkHolder::getTickingChunkFuture); // CraftBukkit - decompile error
    }

    private boolean checkChunkFuture(long i, Function<ChunkHolder, CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>>> function) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(i);

        if (playerchunk == null) {
            return false;
        } else {
            Either<LevelChunk, ChunkHolder.Failure> either = (Either) ((CompletableFuture) function.apply(playerchunk)).getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK);

            return either.left().isPresent();
        }
    }

    public void save(boolean flag) {
        this.runDistanceManagerUpdates();
        this.chunkMap.saveAllChunks(flag);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        close(true);
    }

    public void close(boolean save) throws IOException {
        if (save) {
            this.save(true);
        }
        // CraftBukkit end
        this.lightEngine.close();
        this.chunkMap.close();
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        this.level.getProfiler().push("purge");
        this.distanceManager.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(() -> true);
        this.level.getProfiler().pop();
        this.clearCache();
    }
    // CraftBukkit end

    public void tick(BooleanSupplier booleansupplier) {
        this.level.getProfiler().push("purge");
        this.level.timings.doChunkMap.startTiming(); // Spigot
        this.distanceManager.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        this.level.timings.doChunkMap.stopTiming(); // Spigot
        this.level.getProfiler().popPush("chunks");
        this.tickChunks();
        this.level.timings.doChunkUnload.startTiming(); // Spigot
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(booleansupplier);
        this.level.timings.doChunkUnload.stopTiming(); // Spigot
        this.level.getProfiler().pop();
        this.clearCache();
    }

    private void tickChunks() {
        long i = this.level.getGameTime();
        long j = i - this.lastInhabitedUpdate;

        this.lastInhabitedUpdate = i;
        LevelData worlddata = this.level.getLevelData();
        boolean flag = this.level.isDebug();
        boolean flag1 = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && !level.players().isEmpty(); // CraftBukkit

        if (!flag) {
            this.level.getProfiler().push("pollingChunks");
            int k = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            boolean flag2 = level.ticksPerAnimalSpawns != 0L && worlddata.getGameTime() % level.ticksPerAnimalSpawns == 0L; // CraftBukkit

            this.level.getProfiler().push("naturalSpawnCount");
            int l = this.distanceManager.getNaturalSpawnChunkCount();
            NaturalSpawner.SpawnState spawnercreature_d = NaturalSpawner.createState(l, this.level.getAllEntities(), this::getFullChunk);

            this.lastSpawnState = spawnercreature_d;
            this.level.getProfiler().pop();
            List<ChunkHolder> list = Lists.newArrayList(this.chunkMap.getChunks());

            Collections.shuffle(list);
            list.forEach((playerchunk) -> {
                Optional<LevelChunk> optional = ((Either) playerchunk.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).left();

                if (optional.isPresent()) {
                    this.level.getProfiler().push("broadcast");
                    playerchunk.broadcastChanges((LevelChunk) optional.get());
                    this.level.getProfiler().pop();
                    Optional<LevelChunk> optional1 = ((Either) playerchunk.getEntityTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).left();

                    if (optional1.isPresent()) {
                        LevelChunk chunk = (LevelChunk) optional1.get();
                        ChunkPos chunkcoordintpair = playerchunk.getPos();

                        if (!this.chunkMap.noPlayersCloseForSpawning(chunkcoordintpair)) {
                            chunk.setInhabitedTime(chunk.getInhabitedTime() + j);
                            if (flag1 && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(chunk.getPos()) && !this.chunkMap.isOutsideOfRange(chunkcoordintpair, true)) { // Spigot
                                NaturalSpawner.spawnForChunk(this.level, chunk, spawnercreature_d, this.spawnFriendlies, this.spawnEnemies, flag2);
                            }

                            this.level.timings.doTickTiles.startTiming(); // Spigot
                            this.level.tickChunk(chunk, k);
                            this.level.timings.doTickTiles.stopTiming(); // Spigot
                        }
                    }
                }
            });
            this.level.getProfiler().push("customSpawners");
            if (flag1) {
                this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
            }

            this.level.getProfiler().pop();
            this.level.getProfiler().pop();
        }

        this.level.timings.tracker.startTiming(); // Spigot
        this.chunkMap.tick();
        this.level.timings.tracker.stopTiming(); // Spigot
    }

    private void getFullChunk(long i, Consumer<LevelChunk> consumer) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(i);

        if (playerchunk != null) {
            ((Either) playerchunk.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).left().ifPresent(consumer);
        }

    }

    @Override
    public String gatherStats() {
        return "ServerChunkCache: " + this.getLoadedChunksCount();
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.generator;
    }

    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos blockposition) {
        int i = blockposition.getX() >> 4;
        int j = blockposition.getZ() >> 4;
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));

        if (playerchunk != null) {
            playerchunk.blockChanged(blockposition.getX() & 15, blockposition.getY(), blockposition.getZ() & 15);
        }

    }

    @Override
    public void onLightUpdate(LightLayer enumskyblock, SectionPos sectionposition) {
        this.mainThreadProcessor.execute(() -> {
            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(sectionposition.chunk().toLong());

            if (playerchunk != null) {
                playerchunk.sectionLightChanged(enumskyblock, sectionposition.y());
            }

        });
    }

    public <T> void addRegionTicket(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        this.distanceManager.addRegionTicket(tickettype, chunkcoordintpair, i, t0);
    }

    public <T> void removeRegionTicket(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        this.distanceManager.removeRegionTicket(tickettype, chunkcoordintpair, i, t0);
    }

    @Override
    public void updateChunkForced(ChunkPos chunkcoordintpair, boolean flag) {
        this.distanceManager.updateChunkForced(chunkcoordintpair, flag);
    }

    public void move(ServerPlayer entityplayer) {
        this.chunkMap.move(entityplayer);
    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void broadcastIncludingSelf(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcastIncludingSelf(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int i) {
        this.chunkMap.setViewDistance(i);
    }

    @Override
    public void setSpawnSettings(boolean flag, boolean flag1) {
        this.spawnEnemies = flag;
        this.spawnFriendlies = flag1;
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    @Nullable
    public NaturalSpawner.SpawnState getLastSpawnState() {
        return this.lastSpawnState;
    }

    public final class MainThreadExecutor extends BlockableEventLoop<Runnable> {

        private MainThreadExecutor(Level world) {
            super("Chunk source main thread executor for " + world.getDimensionKey().location());
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        @Override
        protected void doRunTask(Runnable runnable) {
            ServerChunkCache.this.level.getProfiler().incrementCounter("runTask");
            super.doRunTask(runnable);
        }

        @Override
        protected boolean pollTask() {
        // CraftBukkit start - process pending Chunk loadCallback() and unloadCallback() after each run task
        try {
            if (ServerChunkCache.this.runDistanceManagerUpdates()) {
                return true;
            } else {
                ServerChunkCache.this.lightEngine.tryScheduleUpdate();
                return super.pollTask();
            }
        } finally {
            chunkMap.callbackExecutor.run();
        }
        // CraftBukkit end
        }
    }
}
