package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class DistanceManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int PLAYER_TICKET_LEVEL = 33 + ChunkStatus.getDistance(ChunkStatus.FULL) - 2;
    private final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap();
    public final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = new Long2ObjectOpenHashMap();
    private final DistanceManager.ChunkTicketTracker ticketTracker = new DistanceManager.ChunkTicketTracker();
    private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);
    private final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(33);
    private final Set<ChunkHolder> chunksToUpdateFutures = Sets.newHashSet();
    private final ChunkTaskPriorityQueueSorter ticketThrottler;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> ticketThrottlerInput;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Release> ticketThrottlerReleaser;
    private final LongSet ticketsToRelease = new LongOpenHashSet();
    private final Executor mainThreadExecutor;
    private long ticketTickCounter;

    protected DistanceManager(Executor executor, Executor executor1) {
        executor1.getClass();
        ProcessorHandle<Runnable> mailbox = ProcessorHandle.of("player ticket throttler", executor1::execute);
        ChunkTaskPriorityQueueSorter chunktaskqueuesorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(mailbox), executor, 4);

        this.ticketThrottler = chunktaskqueuesorter;
        this.ticketThrottlerInput = chunktaskqueuesorter.getProcessor(mailbox, true);
        this.ticketThrottlerReleaser = chunktaskqueuesorter.getReleaseProcessor(mailbox);
        this.mainThreadExecutor = executor1;
    }

    protected void purgeStaleTickets() {
        ++this.ticketTickCounter;
        ObjectIterator objectiterator = this.tickets.long2ObjectEntrySet().fastIterator();

        while (objectiterator.hasNext()) {
            Entry<SortedArraySet<Ticket<?>>> entry = (Entry) objectiterator.next();

            if ((entry.getValue()).removeIf((ticket) -> { // CraftBukkit - decompile error
                return ticket.timedOut(this.ticketTickCounter);
            })) {
                this.ticketTracker.update(entry.getLongKey(), getLowestTicketLevel((SortedArraySet) entry.getValue()), false);
            }

            if (((SortedArraySet) entry.getValue()).isEmpty()) {
                objectiterator.remove();
            }
        }

    }

    private static int getLowestTicketLevel(SortedArraySet<Ticket<?>> arraysetsorted) {
        return !arraysetsorted.isEmpty() ? ((Ticket) arraysetsorted.first()).getTicketLevel() : ChunkMap.MAX_CHUNK_DISTANCE + 1;
    }

    protected abstract boolean isChunkToRemove(long i);

    @Nullable
    protected abstract ChunkHolder getChunk(long i);

    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(long i, int j, @Nullable ChunkHolder playerchunk, int k);

    public boolean runAllUpdates(ChunkMap playerchunkmap) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        this.playerTicketManager.runAllUpdates();
        int i = Integer.MAX_VALUE - this.ticketTracker.runDistnaceUpdates(Integer.MAX_VALUE);
        boolean flag = i != 0;

        if (flag) {
            ;
        }

        if (!this.chunksToUpdateFutures.isEmpty()) {
            // CraftBukkit start
            // Iterate pending chunk updates with protection against concurrent modification exceptions
            java.util.Iterator<ChunkHolder> iter = this.chunksToUpdateFutures.iterator();
            int expectedSize = this.chunksToUpdateFutures.size();
            do {
                ChunkHolder playerchunk = iter.next();
                iter.remove();
                expectedSize--;

                playerchunk.updateFutures(playerchunkmap);

                // Reset iterator if set was modified using add()
                if (this.chunksToUpdateFutures.size() != expectedSize) {
                    expectedSize = this.chunksToUpdateFutures.size();
                    iter = this.chunksToUpdateFutures.iterator();
                }
            } while (iter.hasNext());
            // CraftBukkit end

            return true;
        } else {
            if (!this.ticketsToRelease.isEmpty()) {
                LongIterator longiterator = this.ticketsToRelease.iterator();

                while (longiterator.hasNext()) {
                    long j = longiterator.nextLong();

                    if (this.getTickets(j).stream().anyMatch((ticket) -> {
                        return ticket.getType() == TicketType.PLAYER;
                    })) {
                        ChunkHolder playerchunk = playerchunkmap.getUpdatingChunkIfPresent(j);

                        if (playerchunk == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<Either<LevelChunk, ChunkHolder.Failure>> completablefuture = playerchunk.getEntityTickingChunkFuture();

                        completablefuture.thenAccept((either) -> {
                            this.mainThreadExecutor.execute(() -> {
                                this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                                }, j, false));
                            });
                        });
                    }
                }

                this.ticketsToRelease.clear();
            }

            return flag;
        }
    }

    private boolean addTicket(long i, Ticket<?> ticket) { // CraftBukkit - void -> boolean
        SortedArraySet<Ticket<?>> arraysetsorted = this.getTickets(i);
        int j = getLowestTicketLevel(arraysetsorted);
        Ticket<?> ticket1 = (Ticket) arraysetsorted.addOrGet(ticket); // CraftBukkit - decompile error

        ticket1.setCreatedTick(this.ticketTickCounter);
        if (ticket.getTicketLevel() < j) {
            this.ticketTracker.update(i, ticket.getTicketLevel(), true);
        }

        return ticket == ticket1; // CraftBukkit
    }

    private boolean removeTicket(long i, Ticket<?> ticket) { // CraftBukkit - void -> boolean
        SortedArraySet<Ticket<?>> arraysetsorted = this.getTickets(i);

        boolean removed = false; // CraftBukkit
        if (arraysetsorted.remove(ticket)) {
            removed = true; // CraftBukkit
        }

        if (arraysetsorted.isEmpty()) {
            this.tickets.remove(i);
        }

        this.ticketTracker.update(i, getLowestTicketLevel(arraysetsorted), false);
        return removed; // CraftBukkit
    }

    public <T> void addTicket(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        // CraftBukkit start
        this.addTicketAtLevel(tickettype, chunkcoordintpair, i, t0);
    }

    public <T> boolean addTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkcoordintpair, int level, T identifier) {
        return this.addTicket(chunkcoordintpair.toLong(), new Ticket<>(ticketType, level, identifier));
        // CraftBukkit end
    }

    public <T> void removeTicket(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        // CraftBukkit start
        this.removeTicketAtLevel(tickettype, chunkcoordintpair, i, t0);
    }

    public <T> boolean removeTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkcoordintpair, int level, T identifier) {
        Ticket<T> ticket = new Ticket<>(ticketType, level, identifier);

        return this.removeTicket(chunkcoordintpair.toLong(), ticket);
        // CraftBukkit end
    }

    public <T> void addRegionTicket(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        this.addTicket(chunkcoordintpair.toLong(), new Ticket<>(tickettype, 33 - i, t0));
    }

    public <T> void removeRegionTicket(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        Ticket<T> ticket = new Ticket<>(tickettype, 33 - i, t0);

        this.removeTicket(chunkcoordintpair.toLong(), ticket);
    }

    private SortedArraySet<Ticket<?>> getTickets(long i) {
        return (SortedArraySet) this.tickets.computeIfAbsent(i, (j) -> {
            return SortedArraySet.create(4);
        });
    }

    protected void updateChunkForced(ChunkPos chunkcoordintpair, boolean flag) {
        Ticket<ChunkPos> ticket = new Ticket<>(TicketType.FORCED, 31, chunkcoordintpair);

        if (flag) {
            this.addTicket(chunkcoordintpair.toLong(), ticket);
        } else {
            this.removeTicket(chunkcoordintpair.toLong(), ticket);
        }

    }

    public void addPlayer(SectionPos sectionposition, ServerPlayer entityplayer) {
        long i = sectionposition.chunk().toLong();

        ((ObjectSet) this.playersPerChunk.computeIfAbsent(i, (j) -> {
            return new ObjectOpenHashSet();
        })).add(entityplayer);
        this.naturalSpawnChunkCounter.update(i, 0, true);
        this.playerTicketManager.update(i, 0, true);
    }

    public void removePlayer(SectionPos sectionposition, ServerPlayer entityplayer) {
        long i = sectionposition.chunk().toLong();
        ObjectSet<ServerPlayer> objectset = (ObjectSet) this.playersPerChunk.get(i);

        objectset.remove(entityplayer);
        if (objectset.isEmpty()) {
            this.playersPerChunk.remove(i);
            this.naturalSpawnChunkCounter.update(i, Integer.MAX_VALUE, false);
            this.playerTicketManager.update(i, Integer.MAX_VALUE, false);
        }

    }

    protected String getTicketDebugString(long i) {
        SortedArraySet<Ticket<?>> arraysetsorted = (SortedArraySet) this.tickets.get(i);
        String s;

        if (arraysetsorted != null && !arraysetsorted.isEmpty()) {
            s = ((Ticket) arraysetsorted.first()).toString();
        } else {
            s = "no_ticket";
        }

        return s;
    }

    protected void updatePlayerTickets(int i) {
        this.playerTicketManager.updateViewDistance(i);
    }

    public int getNaturalSpawnChunkCount() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.size();
    }

    public boolean hasPlayersNearby(long i) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.containsKey(i);
    }

    public String getDebugStatus() {
        return this.ticketThrottler.getDebugStatus();
    }

    // CraftBukkit start
    public <T> void removeAllTicketsFor(TicketType<T> ticketType, int ticketLevel, T ticketIdentifier) {
        Ticket<T> target = new Ticket<>(ticketType, ticketLevel, ticketIdentifier);

        for (java.util.Iterator<Entry<SortedArraySet<Ticket<?>>>> iterator = this.tickets.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            Entry<SortedArraySet<Ticket<?>>> entry = iterator.next();
            SortedArraySet<Ticket<?>> tickets = entry.getValue();
            if (tickets.remove(target)) {
                // copied from removeTicket
                this.ticketTracker.update(entry.getLongKey(), getLowestTicketLevel(tickets), false);

                // can't use entry after it's removed
                if (tickets.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }
    // CraftBukkit end

    class ChunkTicketTracker extends ChunkTracker {

        public ChunkTicketTracker() {
            super(ChunkMap.MAX_CHUNK_DISTANCE + 2, 16, 256);
        }

        @Override
        protected int getLevelFromSource(long i) {
            SortedArraySet<Ticket<?>> arraysetsorted = (SortedArraySet) DistanceManager.this.tickets.get(i);

            return arraysetsorted == null ? Integer.MAX_VALUE : (arraysetsorted.isEmpty() ? Integer.MAX_VALUE : ((Ticket) arraysetsorted.first()).getTicketLevel());
        }

        @Override
        protected int getLevel(long i) {
            if (!DistanceManager.this.isChunkToRemove(i)) {
                ChunkHolder playerchunk = DistanceManager.this.getChunk(i);

                if (playerchunk != null) {
                    return playerchunk.getTicketLevel();
                }
            }

            return ChunkMap.MAX_CHUNK_DISTANCE + 1;
        }

        @Override
        protected void setLevel(long i, int j) {
            ChunkHolder playerchunk = DistanceManager.this.getChunk(i);
            int k = playerchunk == null ? ChunkMap.MAX_CHUNK_DISTANCE + 1 : playerchunk.getTicketLevel();

            if (k != j) {
                playerchunk = DistanceManager.this.updateChunkScheduling(i, j, playerchunk, k);
                if (playerchunk != null) {
                    DistanceManager.this.chunksToUpdateFutures.add(playerchunk);
                }

            }
        }

        public int runDistnaceUpdates(int i) {
            return this.runUpdates(i);
        }
    }

    class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {

        private int viewDistance = 0;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(int i) {
            super(i);
            this.queueLevels.defaultReturnValue(i + 2);
        }

        @Override
        protected void onLevelChange(long i, int j, int k) {
            this.toUpdate.add(i);
        }

        public void updateViewDistance(int i) {
            ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

            while (objectiterator.hasNext()) {
                it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry it_unimi_dsi_fastutil_longs_long2bytemap_entry = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry) objectiterator.next();
                byte b0 = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getByteValue();
                long j = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getLongKey();

                this.onLevelChange(j, b0, this.haveTicketFor(b0), b0 <= i - 2);
            }

            this.viewDistance = i;
        }

        private void onLevelChange(long i, int j, boolean flag, boolean flag1) {
            if (flag != flag1) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, DistanceManager.PLAYER_TICKET_LEVEL, new ChunkPos(i));

                if (flag1) {
                    DistanceManager.this.ticketThrottlerInput.tell(ChunkTaskPriorityQueueSorter.message(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            if (this.haveTicketFor(this.getLevel(i))) {
                                DistanceManager.this.addTicket(i, ticket);
                                DistanceManager.this.ticketsToRelease.add(i);
                            } else {
                                DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                                }, i, false));
                            }

                        });
                    }, i, () -> {
                        return j;
                    }));
                } else {
                    DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            DistanceManager.this.removeTicket(i, ticket);
                        });
                    }, i, true));
                }
            }

        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.queueLevels.get(i);
                    int k = this.getLevel(i);

                    if (j != k) {
                        DistanceManager.this.ticketThrottler.onLevelChange(new ChunkPos(i), () -> {
                            return this.queueLevels.get(i);
                        }, k, (l) -> {
                            if (l >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(i);
                            } else {
                                this.queueLevels.put(i, l);
                            }

                        });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }

        }

        private boolean haveTicketFor(int i) {
            return i <= this.viewDistance - 2;
        }
    }

    class FixedPlayerDistanceChunkTracker extends ChunkTracker {

        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(int i) {
            super(i + 2, 16, 256);
            this.maxDistance = i;
            this.chunks.defaultReturnValue((byte) (i + 2));
        }

        @Override
        protected int getLevel(long i) {
            return this.chunks.get(i);
        }

        @Override
        protected void setLevel(long i, int j) {
            byte b0;

            if (j > this.maxDistance) {
                b0 = this.chunks.remove(i);
            } else {
                b0 = this.chunks.put(i, (byte) j);
            }

            this.onLevelChange(i, b0, j);
        }

        protected void onLevelChange(long i, int j, int k) {}

        @Override
        protected int getLevelFromSource(long i) {
            return this.havePlayer(i) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long i) {
            ObjectSet<ServerPlayer> objectset = (ObjectSet) DistanceManager.this.playersPerChunk.get(i);

            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }
    }
}
