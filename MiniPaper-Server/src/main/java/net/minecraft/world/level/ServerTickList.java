package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class ServerTickList<T> implements TickList<T> {

    protected final Predicate<T> ignore;
    private final Function<T, ResourceLocation> toId;
    private final Set<TickNextTickData<T>> tickNextTickSet = Sets.newHashSet();
    private final TreeSet<TickNextTickData<T>> tickNextTickList = Sets.newTreeSet(TickNextTickData.createTimeComparator());
    private final ServerLevel level;
    private final Queue<TickNextTickData<T>> currentlyTicking = Queues.newArrayDeque();
    private final List<TickNextTickData<T>> alreadyTicked = Lists.newArrayList();
    private final Consumer<TickNextTickData<T>> ticker;

    public ServerTickList(ServerLevel worldserver, Predicate<T> predicate, Function<T, ResourceLocation> function, Consumer<TickNextTickData<T>> consumer) {
        this.ignore = predicate;
        this.toId = function;
        this.level = worldserver;
        this.ticker = consumer;
    }

    public void tick() {
        int i = this.tickNextTickList.size();

        if (false) { // CraftBukkit
            throw new IllegalStateException("TickNextTick list out of synch");
        } else {
            if (i > 65536) {
                // CraftBukkit start - If the server has too much to process over time, try to alleviate that
                if (i > 20 * 65536) {
                    i = i / 20;
                } else {
                    i = 65536;
                }
                // CraftBukkit end
            }

            ServerChunkCache chunkproviderserver = this.level.getChunkSourceOH();
            Iterator<TickNextTickData<T>> iterator = this.tickNextTickList.iterator();

            this.level.getProfiler().push("cleaning");

            TickNextTickData nextticklistentry;

            while (i > 0 && iterator.hasNext()) {
                nextticklistentry = (TickNextTickData) iterator.next();
                if (nextticklistentry.triggerTick > this.level.getGameTime()) {
                    break;
                }

                if (chunkproviderserver.isTickingChunk(nextticklistentry.pos)) {
                    iterator.remove();
                    this.tickNextTickSet.remove(nextticklistentry);
                    this.currentlyTicking.add(nextticklistentry);
                    --i;
                }
            }

            this.level.getProfiler().popPush("ticking");

            while ((nextticklistentry = (TickNextTickData) this.currentlyTicking.poll()) != null) {
                if (chunkproviderserver.isTickingChunk(nextticklistentry.pos)) {
                    try {
                        this.alreadyTicked.add(nextticklistentry);
                        this.ticker.accept(nextticklistentry);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while ticking");
                        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being ticked");

                        CrashReportCategory.populateBlockDetails(crashreportsystemdetails, nextticklistentry.pos, (BlockState) null);
                        throw new ReportedException(crashreport);
                    }
                } else {
                    this.scheduleTick(nextticklistentry.pos, (T) nextticklistentry.getType(), 0); // CraftBukkit - decompile error
                }
            }

            this.level.getProfiler().pop();
            this.alreadyTicked.clear();
            this.currentlyTicking.clear();
        }
    }

    @Override
    public boolean willTickThisTick(BlockPos blockposition, T t0) {
        return this.currentlyTicking.contains(new TickNextTickData<>(blockposition, t0));
    }

    public List<TickNextTickData<T>> fetchTicksInChunk(ChunkPos chunkcoordintpair, boolean flag, boolean flag1) {
        int i = (chunkcoordintpair.x << 4) - 2;
        int j = i + 16 + 2;
        int k = (chunkcoordintpair.z << 4) - 2;
        int l = k + 16 + 2;

        return this.fetchTicksInArea(new BoundingBox(i, 0, k, j, 256, l), flag, flag1);
    }

    public List<TickNextTickData<T>> fetchTicksInArea(BoundingBox structureboundingbox, boolean flag, boolean flag1) {
        List<TickNextTickData<T>> list = this.fetchTicksInArea((List) null, this.tickNextTickList, structureboundingbox, flag);

        if (flag && list != null) {
            this.tickNextTickSet.removeAll(list);
        }

        list = this.fetchTicksInArea(list, this.currentlyTicking, structureboundingbox, flag);
        if (!flag1) {
            list = this.fetchTicksInArea(list, this.alreadyTicked, structureboundingbox, flag);
        }

        return list == null ? Collections.emptyList() : list;
    }

    @Nullable
    private List<TickNextTickData<T>> fetchTicksInArea(@Nullable List<TickNextTickData<T>> list, Collection<TickNextTickData<T>> collection, BoundingBox structureboundingbox, boolean flag) {
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            TickNextTickData<T> nextticklistentry = (TickNextTickData) iterator.next();
            BlockPos blockposition = nextticklistentry.pos;

            if (blockposition.getX() >= structureboundingbox.x0 && blockposition.getX() < structureboundingbox.x1 && blockposition.getZ() >= structureboundingbox.z0 && blockposition.getZ() < structureboundingbox.z1) {
                if (flag) {
                    iterator.remove();
                }

                if (list == null) {
                    list = Lists.newArrayList();
                }

                ((List) list).add(nextticklistentry);
            }
        }

        return (List) list;
    }

    public void copy(BoundingBox structureboundingbox, BlockPos blockposition) {
        List<TickNextTickData<T>> list = this.fetchTicksInArea(structureboundingbox, false, false);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            TickNextTickData<T> nextticklistentry = (TickNextTickData) iterator.next();

            if (structureboundingbox.isInside((Vec3i) nextticklistentry.pos)) {
                BlockPos blockposition1 = nextticklistentry.pos.offset((Vec3i) blockposition);
                T t0 = nextticklistentry.getType();

                this.addTickData(new TickNextTickData<>(blockposition1, t0, nextticklistentry.triggerTick, nextticklistentry.priority));
            }
        }

    }

    public ListTag save(ChunkPos chunkcoordintpair) {
        List<TickNextTickData<T>> list = this.fetchTicksInChunk(chunkcoordintpair, false, true);

        return saveTickList(this.toId, list, this.level.getGameTime());
    }

    private static <T> ListTag saveTickList(Function<T, ResourceLocation> function, Iterable<TickNextTickData<T>> iterable, long i) {
        ListTag nbttaglist = new ListTag();
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            TickNextTickData<T> nextticklistentry = (TickNextTickData) iterator.next();
            CompoundTag nbttagcompound = new CompoundTag();

            nbttagcompound.putString("i", ((ResourceLocation) function.apply(nextticklistentry.getType())).toString());
            nbttagcompound.putInt("x", nextticklistentry.pos.getX());
            nbttagcompound.putInt("y", nextticklistentry.pos.getY());
            nbttagcompound.putInt("z", nextticklistentry.pos.getZ());
            nbttagcompound.putInt("t", (int) (nextticklistentry.triggerTick - i));
            nbttagcompound.putInt("p", nextticklistentry.priority.getValue());
            nbttaglist.add(nbttagcompound);
        }

        return nbttaglist;
    }

    @Override
    public boolean hasScheduledTick(BlockPos blockposition, T t0) {
        return this.tickNextTickSet.contains(new TickNextTickData<>(blockposition, t0));
    }

    @Override
    public void scheduleTick(BlockPos blockposition, T t0, int i, TickPriority ticklistpriority) {
        if (!this.ignore.test(t0)) {
            this.addTickData(new TickNextTickData<>(blockposition, t0, (long) i + this.level.getGameTime(), ticklistpriority));
        }

    }

    private void addTickData(TickNextTickData<T> nextticklistentry) {
        if (!this.tickNextTickSet.contains(nextticklistentry)) {
            this.tickNextTickSet.add(nextticklistentry);
            this.tickNextTickList.add(nextticklistentry);
        }

    }

    public int size() {
        return this.tickNextTickSet.size();
    }
}
