package net.minecraft.server.level;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ChunkHolder {

    public static final Either<ChunkAccess, ChunkHolder.Failure> UNLOADED_CHUNK = Either.right(ChunkHolder.Failure.b);
    public static final CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_CHUNK);
    public static final Either<LevelChunk, ChunkHolder.Failure> UNLOADED_LEVEL_CHUNK = Either.right(ChunkHolder.Failure.b);
    public static final CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    public static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    public static final ChunkHolder.FullChunkStatus[] FULL_CHUNK_STATUSES = ChunkHolder.FullChunkStatus.values();
    public final AtomicReferenceArray<CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>>> futures;
    public volatile CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> fullChunkFuture;
    public volatile CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> tickingChunkFuture;
    public volatile CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> entityTickingChunkFuture;
    public CompletableFuture<ChunkAccess> chunkToSave;
    public int oldTicketLevel;
    public int ticketLevel;
    public int queueLevel;
    public final ChunkPos pos;
    public final short[] changedBlocks;
    public int changes;
    public int changedSectionFilter;
    public int blockChangedLightSectionFilter;
    public int skyChangedLightSectionFilter;
    public final LevelLightEngine lightEngine;
    public final ChunkHolder.LevelChangeListener onLevelChange;
    public final ChunkHolder.PlayerProvider playerProvider;
    public boolean wasAccessibleSinceLastSave;

    public ChunkHolder(ChunkPos chunkcoordintpair, int i, LevelLightEngine lightengine, ChunkHolder.LevelChangeListener playerchunk_c, ChunkHolder.PlayerProvider playerchunk_d) {
        this.futures = new AtomicReferenceArray(ChunkHolder.CHUNK_STATUSES.size());
        this.fullChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.tickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.entityTickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.chunkToSave = CompletableFuture.completedFuture(null); // CraftBukkit - decompile error
        this.changedBlocks = new short[64];
        this.pos = chunkcoordintpair;
        this.lightEngine = lightengine;
        this.onLevelChange = playerchunk_c;
        this.playerProvider = playerchunk_d;
        this.oldTicketLevel = ChunkMap.MAX_CHUNK_DISTANCE + 1;
        this.ticketLevel = this.oldTicketLevel;
        this.queueLevel = this.oldTicketLevel;
        this.setTicketLevel(i);
    }

    // CraftBukkit start
    public LevelChunk getFullChunk() {
        if (!getFullChunkStatus(this.oldTicketLevel).isOrAfter(ChunkHolder.FullChunkStatus.BORDER)) return null; // note: using oldTicketLevel for isLoaded checks
        CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> statusFuture = this.getFutureIfPresentUnchecked(ChunkStatus.FULL);
        Either<ChunkAccess, ChunkHolder.Failure> either = (Either<ChunkAccess, ChunkHolder.Failure>) statusFuture.getNow(null);
        return either == null ? null : (LevelChunk) either.left().orElse(null);
    }
    // CraftBukkit end

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> getFutureIfPresentUnchecked(ChunkStatus chunkstatus) {
        CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = (CompletableFuture) this.futures.get(chunkstatus.getIndex());

        return completablefuture == null ? ChunkHolder.UNLOADED_CHUNK_FUTURE : completablefuture;
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> getFutureIfPresent(ChunkStatus chunkstatus) {
        return getStatus(this.ticketLevel).isOrAfter(chunkstatus) ? this.getFutureIfPresentUnchecked(chunkstatus) : ChunkHolder.UNLOADED_CHUNK_FUTURE;
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> getTickingChunkFuture() {
        return this.tickingChunkFuture;
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> getEntityTickingChunkFuture() {
        return this.entityTickingChunkFuture;
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> getFullChunkFuture() {
        return this.fullChunkFuture;
    }

    @Nullable
    public LevelChunk getTickingChunk() {
        CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> completablefuture = this.getTickingChunkFuture();
        Either<LevelChunk, ChunkHolder.Failure> either = (Either) completablefuture.getNow(null); // CraftBukkit - decompile error

        return either == null ? null : (LevelChunk) either.left().orElse(null); // CraftBukkit - decompile error
    }

    @Nullable
    public ChunkAccess getLastAvailable() {
        for (int i = ChunkHolder.CHUNK_STATUSES.size() - 1; i >= 0; --i) {
            ChunkStatus chunkstatus = (ChunkStatus) ChunkHolder.CHUNK_STATUSES.get(i);
            CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = this.getFutureIfPresentUnchecked(chunkstatus);

            if (!completablefuture.isCompletedExceptionally()) {
                Optional<ChunkAccess> optional = ((Either) completablefuture.getNow(ChunkHolder.UNLOADED_CHUNK)).left();

                if (optional.isPresent()) {
                    return (ChunkAccess) optional.get();
                }
            }
        }

        return null;
    }

    public CompletableFuture<ChunkAccess> getChunkToSave() {
        return this.chunkToSave;
    }

    public void blockChanged(int i, int j, int k) {
        LevelChunk chunk = this.getTickingChunk();

        if (chunk != null) {
            this.changedSectionFilter |= 1 << (j >> 4);
            if (this.changes < 64) {
                short short0 = (short) (i << 12 | k << 8 | j);

                for (int l = 0; l < this.changes; ++l) {
                    if (this.changedBlocks[l] == short0) {
                        return;
                    }
                }

                this.changedBlocks[this.changes++] = short0;
            }

        }
    }

    public void sectionLightChanged(LightLayer enumskyblock, int i) {
        LevelChunk chunk = this.getTickingChunk();

        if (chunk != null) {
            chunk.setUnsaved(true);
            if (enumskyblock == LightLayer.SKY) {
                this.skyChangedLightSectionFilter |= 1 << i - -1;
            } else {
                this.blockChangedLightSectionFilter |= 1 << i - -1;
            }

        }
    }

    public void broadcastChanges(LevelChunk chunk) {
        if (this.changes != 0 || this.skyChangedLightSectionFilter != 0 || this.blockChangedLightSectionFilter != 0) {
            Level world = chunk.getLevel();

            if (this.changes < 64 && (this.skyChangedLightSectionFilter != 0 || this.blockChangedLightSectionFilter != 0)) {
                this.broadcast(new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter, false), true);
                this.skyChangedLightSectionFilter = 0;
                this.blockChangedLightSectionFilter = 0;
            }

            int i;
            int j;
            int k;

            if (this.changes == 1) {
                i = (this.changedBlocks[0] >> 12 & 15) + this.pos.x * 16;
                j = this.changedBlocks[0] & 255;
                k = (this.changedBlocks[0] >> 8 & 15) + this.pos.z * 16;
                BlockPos blockposition = new BlockPos(i, j, k);

                this.broadcast(new ClientboundBlockUpdatePacket(world, blockposition), false);
                if (world.getType(blockposition).getBlock().isEntityBlock()) {
                    this.broadcastBlockEntity(world, blockposition);
                }
            } else if (this.changes == 64) {
                this.broadcast(new ClientboundLevelChunkPacket(chunk, this.changedSectionFilter, false), false);
            } else if (this.changes != 0) {
                this.broadcast(new ClientboundChunkBlocksUpdatePacket(this.changes, this.changedBlocks, chunk), false);

                for (i = 0; i < this.changes; ++i) {
                    j = (this.changedBlocks[i] >> 12 & 15) + this.pos.x * 16;
                    k = this.changedBlocks[i] & 255;
                    int l = (this.changedBlocks[i] >> 8 & 15) + this.pos.z * 16;
                    BlockPos blockposition1 = new BlockPos(j, k, l);

                    if (world.getType(blockposition1).getBlock().isEntityBlock()) {
                        this.broadcastBlockEntity(world, blockposition1);
                    }
                }
            }

            this.changes = 0;
            this.changedSectionFilter = 0;
        }
    }

    private void broadcastBlockEntity(Level world, BlockPos blockposition) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity != null) {
            ClientboundBlockEntityDataPacket packetplayouttileentitydata = tileentity.getUpdatePacket();

            if (packetplayouttileentitydata != null) {
                this.broadcast(packetplayouttileentitydata, false);
            }
        }

    }

    private void broadcast(Packet<?> packet, boolean flag) {
        this.playerProvider.getPlayers(this.pos, flag).forEach((entityplayer) -> {
            entityplayer.connection.sendPacket(packet);
        });
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> getOrScheduleFuture(ChunkStatus chunkstatus, ChunkMap playerchunkmap) {
        int i = chunkstatus.getIndex();
        CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = (CompletableFuture) this.futures.get(i);

        if (completablefuture != null) {
            Either<ChunkAccess, ChunkHolder.Failure> either = (Either) completablefuture.getNow(null); // CraftBukkit - decompile error

            if (either == null || either.left().isPresent()) {
                return completablefuture;
            }
        }

        if (getStatus(this.ticketLevel).isOrAfter(chunkstatus)) {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture1 = playerchunkmap.schedule(this, chunkstatus);

            this.updateChunkToSave(completablefuture1);
            this.futures.set(i, completablefuture1);
            return completablefuture1;
        } else {
            return completablefuture == null ? ChunkHolder.UNLOADED_CHUNK_FUTURE : completablefuture;
        }
    }

    private void updateChunkToSave(CompletableFuture<? extends Either<? extends ChunkAccess, ChunkHolder.Failure>> completablefuture) {
        this.chunkToSave = this.chunkToSave.thenCombine(completablefuture, (ichunkaccess, either) -> {
            return (ChunkAccess) either.map((ichunkaccess1) -> {
                return ichunkaccess1;
            }, (playerchunk_failure) -> {
                return ichunkaccess;
            });
        });
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    public int getQueueLevel() {
        return this.queueLevel;
    }

    private void setQueueLevel(int i) {
        this.queueLevel = i;
    }

    public void setTicketLevel(int i) {
        this.ticketLevel = i;
    }

    protected void updateFutures(ChunkMap playerchunkmap) {
        ChunkStatus chunkstatus = getStatus(this.oldTicketLevel);
        ChunkStatus chunkstatus1 = getStatus(this.ticketLevel);
        boolean flag = this.oldTicketLevel <= ChunkMap.MAX_CHUNK_DISTANCE;
        boolean flag1 = this.ticketLevel <= ChunkMap.MAX_CHUNK_DISTANCE;
        ChunkHolder.FullChunkStatus playerchunk_state = getFullChunkStatus(this.oldTicketLevel);
        ChunkHolder.FullChunkStatus playerchunk_state1 = getFullChunkStatus(this.ticketLevel);
        // CraftBukkit start
        // ChunkUnloadEvent: Called before the chunk is unloaded: isChunkLoaded is still true and chunk can still be modified by plugins.
        if (playerchunk_state.isOrAfter(ChunkHolder.FullChunkStatus.BORDER) && !playerchunk_state1.isOrAfter(ChunkHolder.FullChunkStatus.BORDER)) {
            this.getFutureIfPresentUnchecked(ChunkStatus.FULL).thenAccept((either) -> {
                LevelChunk chunk = (LevelChunk)either.left().orElse(null);
                if (chunk != null) {
                    playerchunkmap.callbackExecutor.execute(() -> {
                        // Minecraft will apply the chunks tick lists to the world once the chunk got loaded, and then store the tick
                        // lists again inside the chunk once the chunk becomes inaccessible and set the chunk's needsSaving flag.
                        // These actions may however happen deferred, so we manually set the needsSaving flag already here.
                        chunk.setUnsaved(true);
                        chunk.unloadCallback();
                    });
                }
            }).exceptionally((throwable) -> {
                // ensure exceptions are printed, by default this is not the case
                MinecraftServer.LOGGER.fatal("Failed to schedule unload callback for chunk " + ChunkHolder.this.pos, throwable);
                return null;
            });

            // Run callback right away if the future was already done
            playerchunkmap.callbackExecutor.run();
        }
        // CraftBukkit end
        CompletableFuture completablefuture;

        if (flag) {
            Either<ChunkAccess, ChunkHolder.Failure> either = Either.right(new ChunkHolder.Failure() {
                public String toString() {
                    return "Unloaded ticket level " + ChunkHolder.this.pos.toString();
                }
            });

            for (int i = flag1 ? chunkstatus1.getIndex() + 1 : 0; i <= chunkstatus.getIndex(); ++i) {
                completablefuture = (CompletableFuture) this.futures.get(i);
                if (completablefuture != null) {
                    completablefuture.complete(either);
                } else {
                    this.futures.set(i, CompletableFuture.completedFuture(either));
                }
            }
        }

        boolean flag2 = playerchunk_state.isOrAfter(ChunkHolder.FullChunkStatus.BORDER);
        boolean flag3 = playerchunk_state1.isOrAfter(ChunkHolder.FullChunkStatus.BORDER);

        this.wasAccessibleSinceLastSave |= flag3;
        if (!flag2 && flag3) {
            this.fullChunkFuture = playerchunkmap.unpackTicks(this);
            this.updateChunkToSave(this.fullChunkFuture);
        }

        if (flag2 && !flag3) {
            completablefuture = this.fullChunkFuture;
            this.fullChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
            this.updateChunkToSave(((CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>>) completablefuture).thenApply((either1) -> { // CraftBukkit - decompile error
                playerchunkmap.getClass();
                return either1.ifLeft(playerchunkmap::packTicks);
            }));
        }

        boolean flag4 = playerchunk_state.isOrAfter(ChunkHolder.FullChunkStatus.TICKING);
        boolean flag5 = playerchunk_state1.isOrAfter(ChunkHolder.FullChunkStatus.TICKING);

        if (!flag4 && flag5) {
            this.tickingChunkFuture = playerchunkmap.postProcess(this);
            this.updateChunkToSave(this.tickingChunkFuture);
        }

        if (flag4 && !flag5) {
            this.tickingChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
            this.tickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean flag6 = playerchunk_state.isOrAfter(ChunkHolder.FullChunkStatus.ENTITY_TICKING);
        boolean flag7 = playerchunk_state1.isOrAfter(ChunkHolder.FullChunkStatus.ENTITY_TICKING);

        if (!flag6 && flag7) {
            if (this.entityTickingChunkFuture != ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException());
            }

            this.entityTickingChunkFuture = playerchunkmap.getEntityTickingRangeFuture(this.pos);
            this.updateChunkToSave(this.entityTickingChunkFuture);
        }

        if (flag6 && !flag7) {
            this.entityTickingChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
            this.entityTickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        this.onLevelChange.onLevelChange(this.pos, this::getQueueLevel, this.ticketLevel, this::setQueueLevel);
        this.oldTicketLevel = this.ticketLevel;
        // CraftBukkit start
        // ChunkLoadEvent: Called after the chunk is loaded: isChunkLoaded returns true and chunk is ready to be modified by plugins.
        if (!playerchunk_state.isOrAfter(ChunkHolder.FullChunkStatus.BORDER) && playerchunk_state1.isOrAfter(ChunkHolder.FullChunkStatus.BORDER)) {
            this.getFutureIfPresentUnchecked(ChunkStatus.FULL).thenAccept((either) -> {
                LevelChunk chunk = (LevelChunk)either.left().orElse(null);
                if (chunk != null) {
                    playerchunkmap.callbackExecutor.execute(() -> {
                        chunk.loadCallback();
                    });
                }
            }).exceptionally((throwable) -> {
                // ensure exceptions are printed, by default this is not the case
                MinecraftServer.LOGGER.fatal("Failed to schedule load callback for chunk " + ChunkHolder.this.pos, throwable);
                return null;
            });

            // Run callback right away if the future was already done
            playerchunkmap.callbackExecutor.run();
        }
        // CraftBukkit end
    }

    public static ChunkStatus getStatus(int i) {
        return i < 33 ? ChunkStatus.FULL : ChunkStatus.getStatus(i - 33);
    }

    public static ChunkHolder.FullChunkStatus getFullChunkStatus(int i) {
        return ChunkHolder.FULL_CHUNK_STATUSES[Mth.clamp(33 - i + 1, 0, ChunkHolder.FULL_CHUNK_STATUSES.length - 1)];
    }

    public boolean wasAccessibleSinceLastSave() {
        return this.wasAccessibleSinceLastSave;
    }

    public void refreshAccessibility() {
        this.wasAccessibleSinceLastSave = getFullChunkStatus(this.ticketLevel).isOrAfter(ChunkHolder.FullChunkStatus.BORDER);
    }

    public void replaceProtoChunk(ImposterProtoChunk protochunkextension) {
        for (int i = 0; i < this.futures.length(); ++i) {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.Failure>> completablefuture = (CompletableFuture) this.futures.get(i);

            if (completablefuture != null) {
                Optional<ChunkAccess> optional = ((Either) completablefuture.getNow(ChunkHolder.UNLOADED_CHUNK)).left();

                if (optional.isPresent() && optional.get() instanceof ProtoChunk) {
                    this.futures.set(i, CompletableFuture.completedFuture(Either.left(protochunkextension)));
                }
            }
        }

        this.updateChunkToSave(CompletableFuture.completedFuture(Either.left(protochunkextension.getWrapped())));
    }

    public interface PlayerProvider {

        Stream<ServerPlayer> getPlayers(ChunkPos chunkcoordintpair, boolean flag);
    }

    public interface LevelChangeListener {

        void onLevelChange(ChunkPos chunkcoordintpair, IntSupplier intsupplier, int i, IntConsumer intconsumer);
    }

    public interface Failure {

        ChunkHolder.Failure b = new ChunkHolder.Failure() {
            public String toString() {
                return "UNLOADED";
            }
        };
    }

    public static enum FullChunkStatus {

        INACCESSIBLE, BORDER, TICKING, ENTITY_TICKING;

        private FullChunkStatus() {}

        public boolean isOrAfter(ChunkHolder.FullChunkStatus playerchunk_state) {
            return this.ordinal() >= playerchunk_state.ordinal();
        }
    }
}
