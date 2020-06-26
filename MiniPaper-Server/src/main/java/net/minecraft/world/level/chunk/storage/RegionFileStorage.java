package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

public final class RegionFileStorage implements AutoCloseable {

    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap();
    private final File folder;
    private final boolean sync;

    RegionFileStorage(File file, boolean flag) {
        this.folder = file;
        this.sync = flag;
    }

    private RegionFile getFile(ChunkPos chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit
        long i = ChunkPos.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ());
        RegionFile regionfile = (RegionFile) this.regionCache.getAndMoveToFirst(i);

        if (regionfile != null) {
            return regionfile;
        } else {
            if (this.regionCache.size() >= 256) {
                ((RegionFile) this.regionCache.removeLast()).close();
            }

            if (!this.folder.exists()) {
                this.folder.mkdirs();
            }

            File file = new File(this.folder, "r." + chunkcoordintpair.getRegionX() + "." + chunkcoordintpair.getRegionZ() + ".mca");
            if (existingOnly && !file.exists()) return null; // CraftBukkit
            RegionFile regionfile1 = new RegionFile(file, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(i, regionfile1);
            return regionfile1;
        }
    }

    @Nullable
    public CompoundTag read(ChunkPos chunkcoordintpair) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getFile(chunkcoordintpair, true);
        if (regionfile == null) {
            return null;
        }
        // CraftBukkit end
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkcoordintpair);
        Throwable throwable = null;

        CompoundTag nbttagcompound;

        try {
            if (datainputstream != null) {
                nbttagcompound = NbtIo.read(datainputstream);
                return nbttagcompound;
            }

            nbttagcompound = null;
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (datainputstream != null) {
                if (throwable != null) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    datainputstream.close();
                }
            }

        }

        return nbttagcompound;
    }

    protected void write(ChunkPos chunkcoordintpair, CompoundTag nbttagcompound) throws IOException {
        RegionFile regionfile = this.getFile(chunkcoordintpair, false); // CraftBukkit
        DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(chunkcoordintpair);
        Throwable throwable = null;

        try {
            NbtIo.write(nbttagcompound, (DataOutput) dataoutputstream);
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (dataoutputstream != null) {
                if (throwable != null) {
                    try {
                        dataoutputstream.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    dataoutputstream.close();
                }
            }

        }

    }

    public void close() throws IOException {
        ExceptionCollector<IOException> exceptionsuppressor = new ExceptionCollector<>();
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptionsuppressor.add(ioexception);
            }
        }

        exceptionsuppressor.throwIfPresent();
    }

    public void flush() throws IOException {
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            regionfile.flush();
        }

    }
}
