package net.minecraft.util.worldupdate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMaps;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenCustomHashMap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldUpgrader {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder()).setDaemon(true).build();
    private final ImmutableSet<ResourceKey<Level>> levels;
    private final boolean eraseCache;
    private final LevelStorageSource.LevelStorageAccess levelStorage;
    private final Thread thread;
    private final DataFixer dataFixer;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile float progress;
    private volatile int totalChunks;
    private volatile int converted;
    private volatile int skipped;
    private final Object2FloatMap<ResourceKey<Level>> progressMap = Object2FloatMaps.synchronize(new Object2FloatOpenCustomHashMap(Util.identityStrategy()));
    private volatile Component status = new TranslatableComponent("optimizeWorld.stage.counting");
    private static final Pattern REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    private final DimensionDataStorage overworldDataStorage;

    public WorldUpgrader(LevelStorageSource.LevelStorageAccess convertable_conversionsession, DataFixer datafixer, ImmutableSet<ResourceKey<Level>> immutableset, boolean flag) {
        this.levels = immutableset;
        this.eraseCache = flag;
        this.dataFixer = datafixer;
        this.levelStorage = convertable_conversionsession;
        this.overworldDataStorage = new DimensionDataStorage(new File(this.levelStorage.getDimensionPath(Level.OVERWORLD), "data"), datafixer);
        this.thread = WorldUpgrader.THREAD_FACTORY.newThread(this::work);
        this.thread.setUncaughtExceptionHandler((thread, throwable) -> {
            WorldUpgrader.LOGGER.error("Error upgrading world", throwable);
            this.status = new TranslatableComponent("optimizeWorld.stage.failed");
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException interruptedexception) {
            ;
        }

    }

    private void work() {
        this.totalChunks = 0;
        Builder<ResourceKey<Level>, ListIterator<ChunkPos>> builder = ImmutableMap.builder();

        List list;

        for (UnmodifiableIterator unmodifiableiterator = this.levels.iterator(); unmodifiableiterator.hasNext(); this.totalChunks += list.size()) {
            ResourceKey<Level> resourcekey = (ResourceKey) unmodifiableiterator.next();

            list = this.getAllChunkPos(resourcekey);
            builder.put(resourcekey, list.listIterator());
        }

        if (this.totalChunks == 0) {
            this.finished = true;
        } else {
            float f = (float) this.totalChunks;
            ImmutableMap<ResourceKey<Level>, ListIterator<ChunkPos>> immutablemap = builder.build();
            Builder<ResourceKey<Level>, ChunkStorage> builder1 = ImmutableMap.builder();
            UnmodifiableIterator unmodifiableiterator1 = this.levels.iterator();

            while (unmodifiableiterator1.hasNext()) {
                ResourceKey<Level> resourcekey1 = (ResourceKey) unmodifiableiterator1.next();
                File file = this.levelStorage.getDimensionPath(resourcekey1);

                builder1.put(resourcekey1, new ChunkStorage(new File(file, "region"), this.dataFixer, true));
            }

            ImmutableMap<ResourceKey<Level>, ChunkStorage> immutablemap1 = builder1.build();
            long i = Util.getMillis();

            this.status = new TranslatableComponent("optimizeWorld.stage.upgrading");

            while (this.running) {
                boolean flag = false;
                float f1 = 0.0F;

                float f2;

                for (UnmodifiableIterator unmodifiableiterator2 = this.levels.iterator(); unmodifiableiterator2.hasNext(); f1 += f2) {
                    ResourceKey<Level> resourcekey2 = (ResourceKey) unmodifiableiterator2.next();
                    ListIterator<ChunkPos> listiterator = (ListIterator) immutablemap.get(resourcekey2);
                    ChunkStorage ichunkloader = (ChunkStorage) immutablemap1.get(resourcekey2);

                    if (listiterator.hasNext()) {
                        ChunkPos chunkcoordintpair = (ChunkPos) listiterator.next();
                        boolean flag1 = false;

                        try {
                            CompoundTag nbttagcompound = ichunkloader.read(chunkcoordintpair);

                            if (nbttagcompound != null) {
                                int j = ChunkStorage.getVersion(nbttagcompound);
                                CompoundTag nbttagcompound1 = ichunkloader.getChunkData(resourcekey2, () -> {
                                    return this.overworldDataStorage;
                                }, nbttagcompound, chunkcoordintpair, null); // CraftBukkit
                                CompoundTag nbttagcompound2 = nbttagcompound1.getCompound("Level");
                                ChunkPos chunkcoordintpair1 = new ChunkPos(nbttagcompound2.getInt("xPos"), nbttagcompound2.getInt("zPos"));

                                if (!chunkcoordintpair1.equals(chunkcoordintpair)) {
                                    WorldUpgrader.LOGGER.warn("Chunk {} has invalid position {}", chunkcoordintpair, chunkcoordintpair1);
                                }

                                boolean flag2 = j < SharedConstants.getCurrentVersion().getWorldVersion();

                                if (this.eraseCache) {
                                    flag2 = flag2 || nbttagcompound2.contains("Heightmaps");
                                    nbttagcompound2.remove("Heightmaps");
                                    flag2 = flag2 || nbttagcompound2.contains("isLightOn");
                                    nbttagcompound2.remove("isLightOn");
                                }

                                if (flag2) {
                                    ichunkloader.write(chunkcoordintpair, nbttagcompound1);
                                    flag1 = true;
                                }
                            }
                        } catch (ReportedException reportedexception) {
                            Throwable throwable = reportedexception.getCause();

                            if (!(throwable instanceof IOException)) {
                                throw reportedexception;
                            }

                            WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkcoordintpair, throwable);
                        } catch (IOException ioexception) {
                            WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkcoordintpair, ioexception);
                        }

                        if (flag1) {
                            ++this.converted;
                        } else {
                            ++this.skipped;
                        }

                        flag = true;
                    }

                    f2 = (float) listiterator.nextIndex() / f;
                    this.progressMap.put(resourcekey2, f2);
                }

                this.progress = f1;
                if (!flag) {
                    this.running = false;
                }
            }

            this.status = new TranslatableComponent("optimizeWorld.stage.finished");
            UnmodifiableIterator unmodifiableiterator3 = immutablemap1.values().iterator();

            while (unmodifiableiterator3.hasNext()) {
                ChunkStorage ichunkloader1 = (ChunkStorage) unmodifiableiterator3.next();

                try {
                    ichunkloader1.close();
                } catch (IOException ioexception1) {
                    WorldUpgrader.LOGGER.error("Error upgrading chunk", ioexception1);
                }
            }

            this.overworldDataStorage.save();
            i = Util.getMillis() - i;
            WorldUpgrader.LOGGER.info("World optimizaton finished after {} ms", i);
            this.finished = true;
        }
    }

    private List<ChunkPos> getAllChunkPos(ResourceKey<Level> resourcekey) {
        File file = this.levelStorage.getDimensionPath(resourcekey);
        File file1 = new File(file, "region");
        File[] afile = file1.listFiles((file2, s) -> {
            return s.endsWith(".mca");
        });

        if (afile == null) {
            return ImmutableList.of();
        } else {
            List<ChunkPos> list = Lists.newArrayList();
            File[] afile1 = afile;
            int i = afile.length;

            for (int j = 0; j < i; ++j) {
                File file2 = afile1[j];
                Matcher matcher = WorldUpgrader.REGEX.matcher(file2.getName());

                if (matcher.matches()) {
                    int k = Integer.parseInt(matcher.group(1)) << 5;
                    int l = Integer.parseInt(matcher.group(2)) << 5;

                    try {
                        RegionFile regionfile = new RegionFile(file2, file1, true);
                        Throwable throwable = null;

                        try {
                            for (int i1 = 0; i1 < 32; ++i1) {
                                for (int j1 = 0; j1 < 32; ++j1) {
                                    ChunkPos chunkcoordintpair = new ChunkPos(i1 + k, j1 + l);

                                    if (regionfile.doesChunkExist(chunkcoordintpair)) {
                                        list.add(chunkcoordintpair);
                                    }
                                }
                            }
                        } catch (Throwable throwable1) {
                            throwable = throwable1;
                            throw throwable1;
                        } finally {
                            if (regionfile != null) {
                                if (throwable != null) {
                                    try {
                                        regionfile.close();
                                    } catch (Throwable throwable2) {
                                        throwable.addSuppressed(throwable2);
                                    }
                                } else {
                                    regionfile.close();
                                }
                            }

                        }
                    } catch (Throwable throwable3) {
                        ;
                    }
                }
            }

            return list;
        }
    }

    public boolean isFinished() {
        return this.finished;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getConverted() {
        return this.converted;
    }

    public int getSkipped() {
        return this.skipped;
    }

    public Component getStatus() {
        return this.status;
    }
}
