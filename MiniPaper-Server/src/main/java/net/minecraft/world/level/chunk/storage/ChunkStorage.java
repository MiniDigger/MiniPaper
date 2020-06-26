package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable {

    private final IOWorker worker;
    protected final DataFixer fixerUpper;
    @Nullable
    private LegacyStructureDataHandler legacyStructureHandler;

    public ChunkStorage(File file, DataFixer datafixer, boolean flag) {
        this.fixerUpper = datafixer;
        this.worker = new IOWorker(file, flag, "chunk");
    }

    // CraftBukkit start
    private boolean check(ServerChunkCache cps, int x, int z) throws IOException {
        ChunkPos pos = new ChunkPos(x, z);
        if (cps != null) {
            com.google.common.base.Preconditions.checkState(org.bukkit.Bukkit.isPrimaryThread(), "primary thread");
            if (cps.hasChunk(x, z)) {
                return true;
            }
        }

        CompoundTag nbt = read(pos);
        if (nbt != null) {
            CompoundTag level = nbt.getCompound("Level");
            if (level.getBoolean("TerrainPopulated")) {
                return true;
            }

            ChunkStatus status = ChunkStatus.byName(level.getString("Status"));
            if (status != null && status.isOrAfter(ChunkStatus.FEATURES)) {
                return true;
            }
        }

        return false;
    }
    // CraftBukkit end

    public CompoundTag getChunkData(ResourceKey<Level> resourcekey, Supplier<DimensionDataStorage> supplier, CompoundTag nbttagcompound, ChunkPos pos, @Nullable LevelAccessor generatoraccess) throws IOException {
        int i = getVersion(nbttagcompound);
        boolean flag = true;

        // CraftBukkit start
        if (i < 1466) {
            CompoundTag level = nbttagcompound.getCompound("Level");
            if (level.getBoolean("TerrainPopulated") && !level.getBoolean("LightPopulated")) {
                ServerChunkCache cps = (generatoraccess == null) ? null : ((ServerLevel) generatoraccess).getChunkSourceOH();
                if (check(cps, pos.x - 1, pos.z) && check(cps, pos.x - 1, pos.z - 1) && check(cps, pos.x, pos.z - 1)) {
                    level.putBoolean("LightPopulated", true);
                }
            }
        }
        // CraftBukkit end

        if (i < 1493) {
            nbttagcompound = NbtUtils.update(this.fixerUpper, DataFixTypes.CHUNK, nbttagcompound, i, 1493);
            if (nbttagcompound.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                if (this.legacyStructureHandler == null) {
                    this.legacyStructureHandler = LegacyStructureDataHandler.getLegacyStructureHandler(resourcekey, (DimensionDataStorage) supplier.get());
                }

                nbttagcompound = this.legacyStructureHandler.updateFromLegacy(nbttagcompound);
            }
        }

        nbttagcompound = NbtUtils.update(this.fixerUpper, DataFixTypes.CHUNK, nbttagcompound, Math.max(1493, i));
        if (i < SharedConstants.getCurrentVersion().getWorldVersion()) {
            nbttagcompound.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        }

        return nbttagcompound;
    }

    public static int getVersion(CompoundTag nbttagcompound) {
        return nbttagcompound.contains("DataVersion", 99) ? nbttagcompound.getInt("DataVersion") : -1;
    }

    @Nullable
    public CompoundTag read(ChunkPos chunkcoordintpair) throws IOException {
        return this.worker.load(chunkcoordintpair);
    }

    public void write(ChunkPos chunkcoordintpair, CompoundTag nbttagcompound) {
        this.worker.store(chunkcoordintpair, nbttagcompound);
        if (this.legacyStructureHandler != null) {
            this.legacyStructureHandler.removeIndex(chunkcoordintpair.toLong());
        }

    }

    public void flushWorker() {
        this.worker.synchronize().join();
    }

    public void close() throws IOException {
        this.worker.close();
    }
}
