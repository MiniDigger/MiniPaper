package net.minecraft.world.level.chunk;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkTickList;
import net.minecraft.world.level.EmptyTickList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LevelChunk implements ChunkAccess {

    private static final Logger LOGGER = LogManager.getLogger();
    @Nullable
    public static final LevelChunkSection EMPTY_SECTION = null;
    private final LevelChunkSection[] sections;
    private ChunkBiomeContainer biomes;
    private final Map<BlockPos, CompoundTag> pendingBlockEntities;
    public boolean loaded;
    public final ServerLevel level; // CraftBukkit - type
    public final Map<Heightmap.Types, Heightmap> heightmaps;
    private final UpgradeData upgradeData;
    public final Map<BlockPos, BlockEntity> blockEntities;
    public final List<Entity>[] entitySections; // Spigot
    private final Map<StructureFeature<?>, StructureStart<?>> structureStarts;
    private final Map<StructureFeature<?>, LongSet> structuresRefences;
    private final ShortList[] postProcessing;
    private TickList<Block> blockTicks;
    private TickList<Fluid> liquidTicks;
    private boolean lastSaveHadEntities;
    private long lastSaveTime;
    private volatile boolean unsaved;
    private long inhabitedTime;
    @Nullable
    private Supplier<ChunkHolder.FullChunkStatus> fullStatus;
    @Nullable
    private Consumer<LevelChunk> postLoad;
    private final ChunkPos chunkPos;
    private volatile boolean isLightCorrect;

    public LevelChunk(Level world, ChunkPos chunkcoordintpair, ChunkBiomeContainer biomestorage) {
        this(world, chunkcoordintpair, biomestorage, UpgradeData.EMPTY, EmptyTickList.empty(), EmptyTickList.empty(), 0L, (LevelChunkSection[]) null, (Consumer) null);
    }

    public LevelChunk(Level world, ChunkPos chunkcoordintpair, ChunkBiomeContainer biomestorage, UpgradeData chunkconverter, TickList<Block> ticklist, TickList<Fluid> ticklist1, long i, @Nullable LevelChunkSection[] achunksection, @Nullable Consumer<LevelChunk> consumer) {
        this.sections = new LevelChunkSection[16];
        this.pendingBlockEntities = Maps.newHashMap();
        this.heightmaps = Maps.newEnumMap(Heightmap.Types.class);
        this.blockEntities = Maps.newHashMap();
        this.structureStarts = Maps.newHashMap();
        this.structuresRefences = Maps.newHashMap();
        this.postProcessing = new ShortList[16];
        this.entitySections = (List[]) (new List[16]); // Spigot
        this.level = (ServerLevel) world; // CraftBukkit - type
        this.chunkPos = chunkcoordintpair;
        this.upgradeData = chunkconverter;
        Heightmap.Types[] aheightmap_type = Heightmap.Types.values();
        int j = aheightmap_type.length;

        for (int k = 0; k < j; ++k) {
            Heightmap.Types heightmap_type = aheightmap_type[k];

            if (ChunkStatus.FULL.heightmapsAfter().contains(heightmap_type)) {
                this.heightmaps.put(heightmap_type, new Heightmap(this, heightmap_type));
            }
        }

        for (int l = 0; l < this.entitySections.length; ++l) {
            this.entitySections[l] = new org.bukkit.craftbukkit.util.UnsafeList(); // Spigot
        }

        this.biomes = biomestorage;
        this.blockTicks = ticklist;
        this.liquidTicks = ticklist1;
        this.inhabitedTime = i;
        this.postLoad = consumer;
        if (achunksection != null) {
            if (this.sections.length == achunksection.length) {
                System.arraycopy(achunksection, 0, this.sections, 0, this.sections.length);
            } else {
                LevelChunk.LOGGER.warn("Could not set level chunk sections, array length is {} instead of {}", achunksection.length, this.sections.length);
            }
        }

        // CraftBukkit start
        this.bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
    }

    public org.bukkit.Chunk bukkitChunk;
    public org.bukkit.Chunk getBukkitChunk() {
        return bukkitChunk;
    }

    public boolean mustNotSave;
    public boolean needsDecoration;
    // CraftBukkit end

    public LevelChunk(Level world, ProtoChunk protochunk) {
        this(world, protochunk.getPos(), protochunk.getBiomeIndex(), protochunk.getUpgradeData(), protochunk.getBlockTicks(), protochunk.getLiquidTicks(), protochunk.getInhabitedTime(), protochunk.getSections(), (Consumer) null);
        Iterator iterator = protochunk.getEntities().iterator();

        while (iterator.hasNext()) {
            CompoundTag nbttagcompound = (CompoundTag) iterator.next();

            EntityType.loadEntityRecursive(nbttagcompound, world, (entity) -> {
                this.addEntity(entity);
                return entity;
            });
        }

        iterator = protochunk.getBlockEntities().values().iterator();

        while (iterator.hasNext()) {
            BlockEntity tileentity = (BlockEntity) iterator.next();

            this.addBlockEntity(tileentity);
        }

        this.pendingBlockEntities.putAll(protochunk.getBlockEntityNbts());

        for (int i = 0; i < protochunk.getPostProcessing().length; ++i) {
            this.postProcessing[i] = protochunk.getPostProcessing()[i];
        }

        this.setAllStarts(protochunk.getAllStarts());
        this.setAllReferences(protochunk.getAllReferences());
        iterator = protochunk.getHeightmaps().iterator();

        while (iterator.hasNext()) {
            Entry<Heightmap.Types, Heightmap> entry = (Entry) iterator.next();

            if (ChunkStatus.FULL.heightmapsAfter().contains(entry.getKey())) {
                this.getOrCreateHeightmapUnprimed((Heightmap.Types) entry.getKey()).setRawData(((Heightmap) entry.getValue()).getRawData());
            }
        }

        this.setLightCorrect(protochunk.isLightCorrect());
        this.unsaved = true;
        this.needsDecoration = true; // CraftBukkit
    }

    @Override
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types heightmap_type) {
        return (Heightmap) this.heightmaps.computeIfAbsent(heightmap_type, (heightmap_type1) -> {
            return new Heightmap(this, heightmap_type1);
        });
    }

    @Override
    public Set<BlockPos> getBlockEntitiesPos() {
        Set<BlockPos> set = Sets.newHashSet(this.pendingBlockEntities.keySet());

        set.addAll(this.blockEntities.keySet());
        return set;
    }

    @Override
    public LevelChunkSection[] getSections() {
        return this.sections;
    }

    @Override
    public BlockState getType(BlockPos blockposition) {
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();

        if (this.level.isDebug()) {
            BlockState iblockdata = null;

            if (j == 60) {
                iblockdata = Blocks.BARRIER.getBlockData();
            }

            if (j == 70) {
                iblockdata = DebugLevelSource.getBlockStateFor(i, k);
            }

            return iblockdata == null ? Blocks.AIR.getBlockData() : iblockdata;
        } else {
            try {
                if (j >= 0 && j >> 4 < this.sections.length) {
                    LevelChunkSection chunksection = this.sections[j >> 4];

                    if (!LevelChunkSection.isEmptyOH(chunksection)) {
                        return chunksection.getType(i & 15, j & 15, k & 15);
                    }
                }

                return Blocks.AIR.getBlockData();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting block state");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being got");

                crashreportsystemdetails.setDetail("Location", () -> {
                    return CrashReportCategory.formatLocation(i, j, k);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockPos blockposition) {
        return this.getFluidState(blockposition.getX(), blockposition.getY(), blockposition.getZ());
    }

    public FluidState getFluidState(int i, int j, int k) {
        try {
            if (j >= 0 && j >> 4 < this.sections.length) {
                LevelChunkSection chunksection = this.sections[j >> 4];

                if (!LevelChunkSection.isEmptyOH(chunksection)) {
                    return chunksection.getFluidState(i & 15, j & 15, k & 15);
                }
            }

            return Fluids.EMPTY.defaultFluidState();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting fluid state");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being got");

            crashreportsystemdetails.setDetail("Location", () -> {
                return CrashReportCategory.formatLocation(i, j, k);
            });
            throw new ReportedException(crashreport);
        }
    }

    // CraftBukkit start
    @Nullable
    @Override
    public BlockState setType(BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return this.setType(blockposition, iblockdata, flag, true);
    }

    @Nullable
    public BlockState setType(BlockPos blockposition, BlockState iblockdata, boolean flag, boolean doPlace) {
        // CraftBukkit end
        int i = blockposition.getX() & 15;
        int j = blockposition.getY();
        int k = blockposition.getZ() & 15;
        LevelChunkSection chunksection = this.sections[j >> 4];

        if (chunksection == LevelChunk.EMPTY_SECTION) {
            if (iblockdata.isAir()) {
                return null;
            }

            chunksection = new LevelChunkSection(j >> 4 << 4);
            this.sections[j >> 4] = chunksection;
        }

        boolean flag1 = chunksection.isEmpty();
        BlockState iblockdata1 = chunksection.setType(i, j & 15, k, iblockdata);

        if (iblockdata1 == iblockdata) {
            return null;
        } else {
            Block block = iblockdata.getBlock();
            Block block1 = iblockdata1.getBlock();

            ((Heightmap) this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING)).update(i, j, k, iblockdata);
            ((Heightmap) this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)).update(i, j, k, iblockdata);
            ((Heightmap) this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR)).update(i, j, k, iblockdata);
            ((Heightmap) this.heightmaps.get(Heightmap.Types.WORLD_SURFACE)).update(i, j, k, iblockdata);
            boolean flag2 = chunksection.isEmpty();

            if (flag1 != flag2) {
                this.level.getChunkSourceOH().getLightEngine().updateSectionStatus(blockposition, flag2);
            }

            if (!this.level.isClientSide) {
                iblockdata1.remove(this.level, blockposition, iblockdata, flag);
            } else if (block1 != block && block1 instanceof EntityBlock) {
                this.level.removeBlockEntity(blockposition);
            }

            if (!chunksection.getType(i, j & 15, k).is(block)) {
                return null;
            } else {
                BlockEntity tileentity;

                if (block1 instanceof EntityBlock) {
                    tileentity = this.getBlockEntity(blockposition, LevelChunk.EntityCreationType.CHECK);
                    if (tileentity != null) {
                        tileentity.clearCache();
                    }
                }

                // CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer. Prevents blocks such as TNT from activating when cancelled.
                if (!this.level.isClientSide && doPlace && (!this.level.captureBlockStates || block instanceof BaseEntityBlock)) {
                    iblockdata.onPlace(this.level, blockposition, iblockdata1, flag);
                }

                if (block instanceof EntityBlock) {
                    tileentity = this.getBlockEntity(blockposition, LevelChunk.EntityCreationType.CHECK);
                    if (tileentity == null) {
                        tileentity = ((EntityBlock) block).newBlockEntity(this.level);
                        this.level.setBlockEntity(blockposition, tileentity);
                    } else {
                        tileentity.clearCache();
                    }
                }

                this.unsaved = true;
                return iblockdata1;
            }
        }
    }

    @Nullable
    public LevelLightEngine getLightEngine() {
        return this.level.getChunkSourceOH().getLightEngine();
    }

    @Override
    public void addEntity(Entity entity) {
        this.lastSaveHadEntities = true;
        int i = Mth.floor(entity.getX() / 16.0D);
        int j = Mth.floor(entity.getZ() / 16.0D);

        if (i != this.chunkPos.x || j != this.chunkPos.z) {
            LevelChunk.LOGGER.warn("Wrong location! ({}, {}) should be ({}, {}), {}", i, j, this.chunkPos.x, this.chunkPos.z, entity);
            entity.removed = true;
        }

        int k = Mth.floor(entity.getY() / 16.0D);

        if (k < 0) {
            k = 0;
        }

        if (k >= this.entitySections.length) {
            k = this.entitySections.length - 1;
        }

        entity.inChunk = true;
        entity.xChunk = this.chunkPos.x;
        entity.yChunk = k;
        entity.zChunk = this.chunkPos.z;
        this.entitySections[k].add(entity);
    }

    @Override
    public void setHeightmap(Heightmap.Types heightmap_type, long[] along) {
        ((Heightmap) this.heightmaps.get(heightmap_type)).setRawData(along);
    }

    public void removeEntity(Entity entity) {
        this.removeEntity(entity, entity.yChunk);
    }

    public void removeEntity(Entity entity, int i) {
        if (i < 0) {
            i = 0;
        }

        if (i >= this.entitySections.length) {
            i = this.entitySections.length - 1;
        }

        this.entitySections[i].remove(entity);
    }

    @Override
    public int getHeight(Heightmap.Types heightmap_type, int i, int j) {
        return ((Heightmap) this.heightmaps.get(heightmap_type)).getFirstAvailable(i & 15, j & 15) - 1;
    }

    @Nullable
    private BlockEntity createBlockEntity(BlockPos blockposition) {
        BlockState iblockdata = this.getType(blockposition);
        Block block = iblockdata.getBlock();

        return !block.isEntityBlock() ? null : ((EntityBlock) block).newBlockEntity(this.level);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos blockposition) {
        return this.getBlockEntity(blockposition, LevelChunk.EntityCreationType.CHECK);
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos blockposition, LevelChunk.EntityCreationType chunk_enumtileentitystate) {
        // CraftBukkit start
        BlockEntity tileentity = level.capturedTileEntities.get(blockposition);
        if (tileentity == null) {
            tileentity = (BlockEntity) this.blockEntities.get(blockposition);
        }
        // CraftBukkit end

        if (tileentity == null) {
            CompoundTag nbttagcompound = (CompoundTag) this.pendingBlockEntities.remove(blockposition);

            if (nbttagcompound != null) {
                BlockEntity tileentity1 = this.promotePendingBlockEntity(blockposition, nbttagcompound);

                if (tileentity1 != null) {
                    return tileentity1;
                }
            }
        }

        if (tileentity == null) {
            if (chunk_enumtileentitystate == LevelChunk.EntityCreationType.IMMEDIATE) {
                tileentity = this.createBlockEntity(blockposition);
                this.level.setBlockEntity(blockposition, tileentity);
            }
        } else if (tileentity.isRemoved()) {
            this.blockEntities.remove(blockposition);
            return null;
        }

        return tileentity;
    }

    public void addBlockEntity(BlockEntity tileentity) {
        this.setBlockEntity(tileentity.getBlockPos(), tileentity);
        if (this.loaded || this.level.isClientSide()) {
            this.level.setBlockEntity(tileentity.getBlockPos(), tileentity);
        }

    }

    @Override
    public void setBlockEntity(BlockPos blockposition, BlockEntity tileentity) {
        if (this.getType(blockposition).getBlock() instanceof EntityBlock) {
            tileentity.setLevelAndPosition(this.level, blockposition);
            tileentity.clearRemoved();
            BlockEntity tileentity1 = (BlockEntity) this.blockEntities.put(blockposition.immutable(), tileentity);

            if (tileentity1 != null && tileentity1 != tileentity) {
                tileentity1.setRemoved();
            }

            // CraftBukkit start
        } else {
            System.out.println("Attempted to place a tile entity (" + tileentity + ") at " + tileentity.worldPosition.getX() + "," + tileentity.worldPosition.getY() + "," + tileentity.worldPosition.getZ()
                + " (" + getType(blockposition) + ") where there was no entity tile!");
            System.out.println("Chunk coordinates: " + (this.chunkPos.x * 16) + "," + (this.chunkPos.z * 16));
            new Exception().printStackTrace();
            // CraftBukkit end
        }
    }

    @Override
    public void setBlockEntityNbt(CompoundTag nbttagcompound) {
        this.pendingBlockEntities.put(new BlockPos(nbttagcompound.getInt("x"), nbttagcompound.getInt("y"), nbttagcompound.getInt("z")), nbttagcompound);
    }

    @Nullable
    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos blockposition) {
        BlockEntity tileentity = this.getBlockEntity(blockposition);
        CompoundTag nbttagcompound;

        if (tileentity != null && !tileentity.isRemoved()) {
            nbttagcompound = tileentity.save(new CompoundTag());
            nbttagcompound.putBoolean("keepPacked", false);
            return nbttagcompound;
        } else {
            nbttagcompound = (CompoundTag) this.pendingBlockEntities.get(blockposition);
            if (nbttagcompound != null) {
                nbttagcompound = nbttagcompound.copy();
                nbttagcompound.putBoolean("keepPacked", true);
            }

            return nbttagcompound;
        }
    }

    @Override
    public void removeBlockEntity(BlockPos blockposition) {
        if (this.loaded || this.level.isClientSide()) {
            BlockEntity tileentity = (BlockEntity) this.blockEntities.remove(blockposition);

            if (tileentity != null) {
                tileentity.setRemoved();
            }
        }

    }

    public void runPostLoad() {
        if (this.postLoad != null) {
            this.postLoad.accept(this);
            this.postLoad = null;
        }

    }

    // CraftBukkit start
    public void loadCallback() {
        org.bukkit.Server server = this.level.getServerOH();
        if (server != null) {
            /*
             * If it's a new world, the first few chunks are generated inside
             * the World constructor. We can't reliably alter that, so we have
             * no way of creating a CraftWorld/CraftServer at that point.
             */
            server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(this.bukkitChunk, this.needsDecoration));

            if (this.needsDecoration) {
                this.needsDecoration = false;
                java.util.Random random = new java.util.Random();
                random.setSeed(level.getSeed());
                long xRand = random.nextLong() / 2L * 2L + 1L;
                long zRand = random.nextLong() / 2L * 2L + 1L;
                random.setSeed((long) this.chunkPos.x * xRand + (long) this.chunkPos.z * zRand ^ level.getSeed());

                org.bukkit.World world = this.level.getWorld();
                if (world != null) {
                    this.level.populating = true;
                    try {
                        for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                            populator.populate(world, random, bukkitChunk);
                        }
                    } finally {
                        this.level.populating = false;
                    }
                }
                server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkPopulateEvent(bukkitChunk));
            }
        }
    }

    public void unloadCallback() {
        org.bukkit.Server server = this.level.getServerOH();
        org.bukkit.event.world.ChunkUnloadEvent unloadEvent = new org.bukkit.event.world.ChunkUnloadEvent(this.bukkitChunk, this.isUnsaved());
        server.getPluginManager().callEvent(unloadEvent);
        // note: saving can be prevented, but not forced if no saving is actually required
        this.mustNotSave = !unloadEvent.isSaveChunk();
    }
    // CraftBukkit end

    public void markUnsaved() {
        this.unsaved = true;
    }

    public void getEntities(@Nullable Entity entity, AABB axisalignedbb, List<Entity> list, @Nullable Predicate<? super Entity> predicate) {
        int i = Mth.floor((axisalignedbb.minY - 2.0D) / 16.0D);
        int j = Mth.floor((axisalignedbb.maxY + 2.0D) / 16.0D);

        i = Mth.clamp(i, 0, this.entitySections.length - 1);
        j = Mth.clamp(j, 0, this.entitySections.length - 1);

        for (int k = i; k <= j; ++k) {
            List<Entity> entityslice = this.entitySections[k]; // Spigot
            List<Entity> list1 = entityslice; // Spigot
            int l = list1.size();

            for (int i1 = 0; i1 < l; ++i1) {
                Entity entity1 = (Entity) list1.get(i1);

                if (entity1.getBoundingBox().intersects(axisalignedbb) && entity1 != entity) {
                    if (predicate == null || predicate.test(entity1)) {
                        list.add(entity1);
                    }

                    if (entity1 instanceof EnderDragon) {
                        EnderDragonPart[] aentitycomplexpart = ((EnderDragon) entity1).getSubEntities();
                        int j1 = aentitycomplexpart.length;

                        for (int k1 = 0; k1 < j1; ++k1) {
                            EnderDragonPart entitycomplexpart = aentitycomplexpart[k1];

                            if (entitycomplexpart != entity && entitycomplexpart.getBoundingBox().intersects(axisalignedbb) && (predicate == null || predicate.test(entitycomplexpart))) {
                                list.add(entitycomplexpart);
                            }
                        }
                    }
                }
            }
        }

    }

    public <T extends Entity> void getEntities(@Nullable EntityType<?> entitytypes, AABB axisalignedbb, List<? super T> list, Predicate<? super T> predicate) {
        int i = Mth.floor((axisalignedbb.minY - 2.0D) / 16.0D);
        int j = Mth.floor((axisalignedbb.maxY + 2.0D) / 16.0D);

        i = Mth.clamp(i, 0, this.entitySections.length - 1);
        j = Mth.clamp(j, 0, this.entitySections.length - 1);

        for (int k = i; k <= j; ++k) {
            Iterator iterator = this.entitySections[k].iterator(); // Spigot

            while (iterator.hasNext()) {
                T entity = (T) iterator.next(); // CraftBukkit - decompile error

                if ((entitytypes == null || entity.getType() == entitytypes) && entity.getBoundingBox().intersects(axisalignedbb) && predicate.test(entity)) {
                    list.add(entity);
                }
            }
        }

    }

    public <T extends Entity> void getEntitiesOfClass(Class<? extends T> oclass, AABB axisalignedbb, List<T> list, @Nullable Predicate<? super T> predicate) {
        int i = Mth.floor((axisalignedbb.minY - 2.0D) / 16.0D);
        int j = Mth.floor((axisalignedbb.maxY + 2.0D) / 16.0D);

        i = Mth.clamp(i, 0, this.entitySections.length - 1);
        j = Mth.clamp(j, 0, this.entitySections.length - 1);

        for (int k = i; k <= j; ++k) {
            Iterator iterator = this.entitySections[k].iterator(); // Spigot

            while (iterator.hasNext()) {
                T t0 = (T) iterator.next(); // CraftBukkit - decompile error

                if (oclass.isInstance(t0) && t0.getBoundingBox().intersects(axisalignedbb) && (predicate == null || predicate.test(t0))) { // Spigot - instance check
                    list.add(t0);
                }
            }
        }

    }

    public boolean isEmpty() {
        return false;
    }

    @Override
    public ChunkPos getPos() {
        return this.chunkPos;
    }

    @Override
    public ChunkBiomeContainer getBiomeIndex() {
        return this.biomes;
    }

    public void setLoaded(boolean flag) {
        this.loaded = flag;
    }

    public Level getLevel() {
        return this.level;
    }

    @Override
    public Collection<Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        return Collections.unmodifiableSet(this.heightmaps.entrySet());
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public List<Entity>[] getEntitySlices() { // Spigot
        return this.entitySections;
    }

    @Override
    public CompoundTag getBlockEntityNbt(BlockPos blockposition) {
        return (CompoundTag) this.pendingBlockEntities.get(blockposition);
    }

    @Override
    public Stream<BlockPos> getLights() {
        return StreamSupport.stream(BlockPos.betweenClosed(this.chunkPos.getMinBlockX(), 0, this.chunkPos.getMinBlockZ(), this.chunkPos.getMaxBlockX(), 255, this.chunkPos.getMaxBlockZ()).spliterator(), false).filter((blockposition) -> {
            return this.getType(blockposition).getLightEmission() != 0;
        });
    }

    @Override
    public TickList<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickList<Fluid> getLiquidTicks() {
        return this.liquidTicks;
    }

    @Override
    public void setUnsaved(boolean flag) {
        this.unsaved = flag;
    }

    @Override
    public boolean isUnsaved() {
        return (this.unsaved || this.lastSaveHadEntities && this.level.getGameTime() != this.lastSaveTime) && !this.mustNotSave; // CraftBukkit
    }

    public void setLastSaveHadEntities(boolean flag) {
        this.lastSaveHadEntities = flag;
    }

    @Override
    public void setLastSaveTime(long i) {
        this.lastSaveTime = i;
    }

    @Nullable
    @Override
    public StructureStart<?> getStartForFeature(StructureFeature<?> structuregenerator) {
        return (StructureStart) this.structureStarts.get(structuregenerator);
    }

    @Override
    public void setStartForFeature(StructureFeature<?> structuregenerator, StructureStart<?> structurestart) {
        this.structureStarts.put(structuregenerator, structurestart);
    }

    @Override
    public Map<StructureFeature<?>, StructureStart<?>> getAllStarts() {
        return this.structureStarts;
    }

    @Override
    public void setAllStarts(Map<StructureFeature<?>, StructureStart<?>> map) {
        this.structureStarts.clear();
        this.structureStarts.putAll(map);
    }

    @Override
    public LongSet getReferencesForFeature(StructureFeature<?> structuregenerator) {
        return (LongSet) this.structuresRefences.computeIfAbsent(structuregenerator, (structuregenerator1) -> {
            return new LongOpenHashSet();
        });
    }

    @Override
    public void addReferenceForFeature(StructureFeature<?> structuregenerator, long i) {
        ((LongSet) this.structuresRefences.computeIfAbsent(structuregenerator, (structuregenerator1) -> {
            return new LongOpenHashSet();
        })).add(i);
    }

    @Override
    public Map<StructureFeature<?>, LongSet> getAllReferences() {
        return this.structuresRefences;
    }

    @Override
    public void setAllReferences(Map<StructureFeature<?>, LongSet> map) {
        this.structuresRefences.clear();
        this.structuresRefences.putAll(map);
    }

    @Override
    public long getInhabitedTime() {
        return this.inhabitedTime;
    }

    @Override
    public void setInhabitedTime(long i) {
        this.inhabitedTime = i;
    }

    public void postProcessGeneration() {
        ChunkPos chunkcoordintpair = this.getPos();

        for (int i = 0; i < this.postProcessing.length; ++i) {
            if (this.postProcessing[i] != null) {
                ShortListIterator shortlistiterator = this.postProcessing[i].iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();
                    BlockPos blockposition = ProtoChunk.unpackOffsetCoordinates(oshort, i, chunkcoordintpair);
                    BlockState iblockdata = this.getType(blockposition);
                    BlockState iblockdata1 = Block.updateFromNeighbourShapes(iblockdata, (LevelAccessor) this.level, blockposition);

                    this.level.setTypeAndData(blockposition, iblockdata1, 20);
                }

                this.postProcessing[i].clear();
            }
        }

        this.unpackTicks();
        Iterator iterator = Sets.newHashSet(this.pendingBlockEntities.keySet()).iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();

            this.getBlockEntity(blockposition1);
        }

        this.pendingBlockEntities.clear();
        this.upgradeData.upgrade(this);
    }

    @Nullable
    private BlockEntity promotePendingBlockEntity(BlockPos blockposition, CompoundTag nbttagcompound) {
        BlockState iblockdata = this.getType(blockposition);
        BlockEntity tileentity;

        if ("DUMMY".equals(nbttagcompound.getString("id"))) {
            Block block = iblockdata.getBlock();

            if (block instanceof EntityBlock) {
                tileentity = ((EntityBlock) block).newBlockEntity(this.level);
            } else {
                tileentity = null;
                LevelChunk.LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", blockposition, iblockdata);
            }
        } else {
            tileentity = BlockEntity.create(iblockdata, nbttagcompound);
        }

        if (tileentity != null) {
            tileentity.setLevelAndPosition(this.level, blockposition);
            this.addBlockEntity(tileentity);
        } else {
            LevelChunk.LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", iblockdata, blockposition);
        }

        return tileentity;
    }

    @Override
    public UpgradeData getUpgradeData() {
        return this.upgradeData;
    }

    @Override
    public ShortList[] getPostProcessing() {
        return this.postProcessing;
    }

    public void unpackTicks() {
        if (this.blockTicks instanceof ProtoTickList) {
            ((ProtoTickList<Block>) this.blockTicks).copyOut(this.level.getBlockTickList(), (blockposition) -> { // CraftBukkit - decompile error
                return this.getType(blockposition).getBlock();
            });
            this.blockTicks = EmptyTickList.empty();
        } else if (this.blockTicks instanceof ChunkTickList) {
            ((ChunkTickList) this.blockTicks).copyOut(this.level.getBlockTickList());
            this.blockTicks = EmptyTickList.empty();
        }

        if (this.liquidTicks instanceof ProtoTickList) {
            ((ProtoTickList<Fluid>) this.liquidTicks).copyOut(this.level.getFluidTickList(), (blockposition) -> { // CraftBukkit - decompile error
                return this.getFluidState(blockposition).getType();
            });
            this.liquidTicks = EmptyTickList.empty();
        } else if (this.liquidTicks instanceof ChunkTickList) {
            ((ChunkTickList) this.liquidTicks).copyOut(this.level.getFluidTickList());
            this.liquidTicks = EmptyTickList.empty();
        }

    }

    public void packTicks(ServerLevel worldserver) {
        if (this.blockTicks == EmptyTickList.<Block>empty()) { // CraftBukkit - decompile error
            this.blockTicks = new ChunkTickList<>(Registry.BLOCK::getKey, worldserver.getBlockTickList().fetchTicksInChunk(this.chunkPos, true, false), worldserver.getGameTime());
            this.setUnsaved(true);
        }

        if (this.liquidTicks == EmptyTickList.<Fluid>empty()) { // CraftBukkit - decompile error
            this.liquidTicks = new ChunkTickList<>(Registry.FLUID::getKey, worldserver.getFluidTickList().fetchTicksInChunk(this.chunkPos, true, false), worldserver.getGameTime());
            this.setUnsaved(true);
        }

    }

    @Override
    public ChunkStatus getStatus() {
        return ChunkStatus.FULL;
    }

    public ChunkHolder.FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? ChunkHolder.FullChunkStatus.BORDER : (ChunkHolder.FullChunkStatus) this.fullStatus.get();
    }

    public void setFullStatus(Supplier<ChunkHolder.FullChunkStatus> supplier) {
        this.fullStatus = supplier;
    }

    @Override
    public boolean isLightCorrect() {
        return this.isLightCorrect;
    }

    @Override
    public void setLightCorrect(boolean flag) {
        this.isLightCorrect = flag;
        this.setUnsaved(true);
    }

    public static enum EntityCreationType {

        IMMEDIATE, QUEUED, CHECK;

        private EntityCreationType() {}
    }
}
