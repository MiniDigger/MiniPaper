package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagManager;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Scoreboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.SpigotTimings; // Spigot
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockPhysicsEvent;
// CraftBukkit end

public abstract class Level implements LevelAccessor, AutoCloseable {

    protected static final Logger LOGGER = LogManager.getLogger();
    public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceLocation.CODEC.xmap(ResourceKey.elementKey(Registry.DIMENSION_REGISTRY), ResourceKey::location);
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("the_end"));
    private static final Direction[] DIRECTIONS = Direction.values();
    public final List<BlockEntity> blockEntityList = Lists.newArrayList();
    public final List<BlockEntity> tickableBlockEntities = Lists.newArrayList();
    protected final List<BlockEntity> pendingBlockEntities = Lists.newArrayList();
    protected final List<BlockEntity> blockEntitiesToUnload = Lists.newArrayList();
    public final Thread thread;
    private final boolean isDebug;
    private int skyDarken;
    protected int randValue = (new Random()).nextInt();
    protected final int addend = 1013904223;
    protected float oRainLevel;
    public float rainLevel;
    protected float oThunderLevel;
    public float thunderLevel;
    public final Random random = new Random();
    private final DimensionType dimensionType;
    public final WritableLevelData levelData;
    private final Supplier<ProfilerFiller> profiler;
    public final boolean isClientSide;
    protected boolean updatingBlockEntities;
    private final WorldBorder worldBorder;
    private final BiomeManager biomeManager;
    private final ResourceKey<Level> dimension;
    private final ResourceKey<DimensionType> dimensionTypeKey;

    // CraftBukkit start Added the following
    private final CraftWorld world;
    public boolean pvpMode;
    public boolean keepSpawnInMemory = true;
    public org.bukkit.generator.ChunkGenerator generator;

    public boolean captureBlockStates = false;
    public boolean captureTreeGeneration = false;
    public Map<BlockPos, CapturedBlockState> capturedBlockStates = new HashMap<>();
    public Map<BlockPos, BlockEntity> capturedTileEntities = new HashMap<>();
    public List<ItemEntity> captureDrops;
    public long ticksPerAnimalSpawns;
    public long ticksPerMonsterSpawns;
    public long ticksPerWaterSpawns;
    public long ticksPerAmbientSpawns;
    public boolean populating;
    public final org.spigotmc.SpigotWorldConfig spigotConfig; // Spigot

    public final com.destroystokyo.paper.PaperWorldConfig paperConfig; // Paper

    public final SpigotTimings.WorldTimingsHandler timings; // Spigot
    public static BlockPos lastPhysicsProblem; // Spigot
    private org.spigotmc.TickLimiter entityLimiter;
    private org.spigotmc.TickLimiter tileLimiter;
    private int tileTickPosition;

    public CraftWorld getWorld() {
        return this.world;
    }

    public CraftServer getServerOH() {
        return (CraftServer) Bukkit.getServer();
    }

    protected Level(WritableLevelData worlddatamutable, ResourceKey<Level> resourcekey, ResourceKey<DimensionType> resourcekey1, DimensionType dimensionmanager, Supplier<ProfilerFiller> supplier, boolean flag, boolean flag1, long i, org.bukkit.generator.ChunkGenerator gen, org.bukkit.World.Environment env) {
        this.spigotConfig = new org.spigotmc.SpigotWorldConfig(((PrimaryLevelData) worlddatamutable).getLevelName()); // Spigot
        this.paperConfig = new com.destroystokyo.paper.PaperWorldConfig(worlddata.getName(), this.spigotConfig); // Paper
        this.generator = gen;
        this.world = new CraftWorld((ServerLevel) this, gen, env);
        this.ticksPerAnimalSpawns = this.getServerOH().getTicksPerAnimalSpawns(); // CraftBukkit
        this.ticksPerMonsterSpawns = this.getServerOH().getTicksPerMonsterSpawns(); // CraftBukkit
        this.ticksPerWaterSpawns = this.getServerOH().getTicksPerWaterSpawns(); // CraftBukkit
        this.ticksPerAmbientSpawns = this.getServerOH().getTicksPerAmbientSpawns(); // CraftBukkit
        // CraftBukkit end
        this.profiler = supplier;
        this.levelData = worlddatamutable;
        this.dimensionType = dimensionmanager;
        this.dimension = resourcekey;
        this.dimensionTypeKey = resourcekey1;
        this.isClientSide = flag;
        if (dimensionmanager.shrunk()) {
            this.worldBorder = new WorldBorder() {
                @Override
                public double getCenterX() {
                    return super.getCenterX(); // CraftBukkit
                }

                @Override
                public double getCenterZ() {
                    return super.getCenterZ(); // CraftBukkit
                }
            };
        } else {
            this.worldBorder = new WorldBorder();
        }

        this.thread = Thread.currentThread();
        this.biomeManager = new BiomeManager(this, i, dimensionmanager.getGenLayerZoomer());
        this.isDebug = flag1;
        // CraftBukkit start
        getWorldBorder().world = (ServerLevel) this;
        // From PlayerList.setPlayerFileData
        getWorldBorder().addListener(new BorderChangeListener() {
            public void onBorderSizeSet(WorldBorder worldborder, double d0) {
                getServerOH().getHandle().sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_SIZE), worldborder.world);
            }

            public void onBorderSizeLerping(WorldBorder worldborder, double d0, double d1, long i) {
                getServerOH().getHandle().sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.LERP_SIZE), worldborder.world);
            }

            public void onBorderCenterSet(WorldBorder worldborder, double d0, double d1) {
                getServerOH().getHandle().sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_CENTER), worldborder.world);
            }

            public void onBorderSetWarningTime(WorldBorder worldborder, int i) {
                getServerOH().getHandle().sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_WARNING_TIME), worldborder.world);
            }

            public void onBorderSetWarningBlocks(WorldBorder worldborder, int i) {
                getServerOH().getHandle().sendAll(new ClientboundSetBorderPacket(worldborder, ClientboundSetBorderPacket.Type.SET_WARNING_BLOCKS), worldborder.world);
            }

            public void onBorderSetDamagePerBlock(WorldBorder worldborder, double d0) {}

            public void onBorderSetDamageSafeZOne(WorldBorder worldborder, double d0) {}
        });
        // CraftBukkit end
        timings = new SpigotTimings.WorldTimingsHandler(this); // Spigot - code below can generate new world and access timings
        this.entityLimiter = new org.spigotmc.TickLimiter(spigotConfig.entityMaxTickTime);
        this.tileLimiter = new org.spigotmc.TickLimiter(spigotConfig.tileMaxTickTime);
    }

    @Override
    public boolean isClientSide() {
        return this.isClientSide;
    }

    @Nullable
    public MinecraftServer getServer() {
        return null;
    }

    public static boolean isInWorldBounds(BlockPos blockposition) {
        return !isOutsideBuildHeight(blockposition) && isInWorldBoundsHorizontal(blockposition);
    }

    public static boolean isInSpawnableBounds(BlockPos blockposition) {
        return !isOutsideSpawnableHeight(blockposition.getY()) && isInWorldBoundsHorizontal(blockposition);
    }

    private static boolean isInWorldBoundsHorizontal(BlockPos blockposition) {
        return blockposition.getX() >= -30000000 && blockposition.getZ() >= -30000000 && blockposition.getX() < 30000000 && blockposition.getZ() < 30000000;
    }

    private static boolean isOutsideSpawnableHeight(int i) {
        return i < -20000000 || i >= 20000000;
    }

    public static boolean isOutsideBuildHeight(BlockPos blockposition) {
        return isOutsideBuildHeight(blockposition.getY());
    }

    public static boolean isOutsideBuildHeight(int i) {
        return i < 0 || i >= 256;
    }

    public double getRelativeFloorHeight(BlockPos blockposition) {
        return this.getRelativeFloorHeight(blockposition, (iblockdata) -> {
            return false;
        });
    }

    public double getRelativeFloorHeight(BlockPos blockposition, Predicate<BlockState> predicate) {
        BlockState iblockdata = this.getType(blockposition);
        VoxelShape voxelshape = predicate.test(iblockdata) ? Shapes.empty() : iblockdata.getCollisionShape(this, blockposition);

        if (voxelshape.isEmpty()) {
            BlockPos blockposition1 = blockposition.below();
            BlockState iblockdata1 = this.getType(blockposition1);
            VoxelShape voxelshape1 = predicate.test(iblockdata1) ? Shapes.empty() : iblockdata1.getCollisionShape(this, blockposition1);
            double d0 = voxelshape1.max(Direction.Axis.Y);

            return d0 >= 1.0D ? d0 - 1.0D : Double.NEGATIVE_INFINITY;
        } else {
            return voxelshape.max(Direction.Axis.Y);
        }
    }

    public double getRelativeCeilingHeight(BlockPos blockposition, double d0) {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = blockposition.i();
        int i = Mth.ceil(d0);
        int j = 0;

        while (j < i) {
            VoxelShape voxelshape = this.getType(blockposition_mutableblockposition).getCollisionShape(this, blockposition_mutableblockposition);

            if (!voxelshape.isEmpty()) {
                return (double) j + voxelshape.min(Direction.Axis.Y);
            }

            ++j;
            blockposition_mutableblockposition.c(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    public LevelChunk getChunkAt(BlockPos blockposition) {
        return this.getChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4);
    }

    @Override
    public LevelChunk getChunk(int i, int j) {
        return (LevelChunk) this.getChunk(i, j, ChunkStatus.FULL);
    }

    @Override
    public ChunkAccess getChunk(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        ChunkAccess ichunkaccess = this.getChunkSource().getChunk(i, j, chunkstatus, flag);

        if (ichunkaccess == null && flag) {
            throw new IllegalStateException("Should always be able to create a chunk!");
        } else {
            return ichunkaccess;
        }
    }

    @Override
    public boolean setTypeAndData(BlockPos blockposition, BlockState iblockdata, int i) {
        return this.setBlock(blockposition, iblockdata, i, 512);
    }

    @Override
    public boolean setBlock(BlockPos blockposition, BlockState iblockdata, int i, int j) {
        // CraftBukkit start - tree generation
        if (this.captureTreeGeneration) {
            CapturedBlockState blockstate = capturedBlockStates.get(blockposition);
            if (blockstate == null) {
                blockstate = CapturedBlockState.getTreeBlockState(this, blockposition, i);
                this.capturedBlockStates.put(blockposition.immutable(), blockstate);
            }
            blockstate.setData(iblockdata);
            return true;
        }
        // CraftBukkit end
        if (isOutsideBuildHeight(blockposition)) {
            return false;
        } else if (!this.isClientSide && this.isDebug()) {
            return false;
        } else {
            LevelChunk chunk = this.getChunkAt(blockposition);
            Block block = iblockdata.getBlock();

            // CraftBukkit start - capture blockstates
            boolean captured = false;
            if (this.captureBlockStates && !this.capturedBlockStates.containsKey(blockposition)) {
                CapturedBlockState blockstate = CapturedBlockState.getBlockState(this, blockposition, i);
                this.capturedBlockStates.put(blockposition.immutable(), blockstate);
                captured = true;
            }
            // CraftBukkit end

            BlockState iblockdata1 = chunk.setType(blockposition, iblockdata, (i & 64) != 0, (i & 1024) == 0); // CraftBukkit custom NO_PLACE flag

            if (iblockdata1 == null) {
                // CraftBukkit start - remove blockstate if failed (or the same)
                if (this.captureBlockStates && captured) {
                    this.capturedBlockStates.remove(blockposition);
                }
                // CraftBukkit end
                return false;
            } else {
                BlockState iblockdata2 = this.getType(blockposition);

                if (iblockdata2 != iblockdata1 && (iblockdata2.getLightBlock((BlockGetter) this, blockposition) != iblockdata1.getLightBlock((BlockGetter) this, blockposition) || iblockdata2.getLightEmission() != iblockdata1.getLightEmission() || iblockdata2.useShapeForLightOcclusion() || iblockdata1.useShapeForLightOcclusion())) {
                    this.getProfiler().push("queueCheckLight");
                    this.getChunkSource().getLightEngine().checkBlock(blockposition);
                    this.getProfiler().pop();
                }

                /*
                if (iblockdata2 == iblockdata) {
                    if (iblockdata1 != iblockdata2) {
                        this.b(blockposition, iblockdata1, iblockdata2);
                    }

                    if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk.getState() != null && chunk.getState().isAtLeast(PlayerChunk.State.TICKING))) {
                        this.notify(blockposition, iblockdata1, iblockdata, i);
                    }

                    if ((i & 1) != 0) {
                        this.update(blockposition, iblockdata1.getBlock());
                        if (!this.isClientSide && iblockdata.isComplexRedstone()) {
                            this.updateAdjacentComparators(blockposition, block);
                        }
                    }

                    if ((i & 16) == 0 && j > 0) {
                        int k = i & -34;

                        iblockdata1.b(this, blockposition, k, j - 1);
                        iblockdata.a((GeneratorAccess) this, blockposition, k, j - 1);
                        iblockdata.b(this, blockposition, k, j - 1);
                    }

                    this.a(blockposition, iblockdata1, iblockdata2);
                }
                */

                // CraftBukkit start
                if (!this.captureBlockStates) { // Don't notify clients or update physics while capturing blockstates
                    // Modularize client and physic updates
                    // Spigot start
                    try {
                        notifyAndUpdatePhysics(blockposition, chunk, iblockdata1, iblockdata, iblockdata2, i);
                    } catch (StackOverflowError ex) {
                        lastPhysicsProblem = new BlockPos(blockposition);
                    }
                    // Spigot end
                }
                // CraftBukkit end

                return true;
            }
        }
    }

    // CraftBukkit start - Split off from above in order to directly send client and physic updates
    public void notifyAndUpdatePhysics(BlockPos blockposition, LevelChunk chunk, BlockState oldBlock, BlockState newBlock, BlockState actualBlock, int i) {
        BlockState iblockdata = newBlock;
        BlockState iblockdata1 = oldBlock;
        BlockState iblockdata2 = actualBlock;
        if (iblockdata2 == iblockdata) {
            if (iblockdata1 != iblockdata2) {
                this.setBlocksDirty(blockposition, iblockdata1, iblockdata2);
            }

            if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk == null || (chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(ChunkHolder.FullChunkStatus.TICKING)))) { // allow chunk to be null here as chunk.isReady() is false when we send our notification during block placement
                this.notify(blockposition, iblockdata1, iblockdata, i);
            }

            if (!this.isClientSide && (i & 1) != 0) {
                this.blockUpdated(blockposition, iblockdata1.getBlock());
                if (iblockdata.hasAnalogOutputSignal()) {
                    this.updateNeighbourForOutputSignal(blockposition, newBlock.getBlock());
                }
            }

            if ((i & 16) == 0) {
                int j = i & -2;

                // CraftBukkit start
                iblockdata1.updateIndirectNeighbourShapes(this, blockposition, j); // Don't call an event for the old block to limit event spam
                CraftWorld world = ((ServerLevel) this).getWorld();
                if (world != null) {
                    BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), CraftBlockData.fromData(iblockdata));
                    this.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                }
                // CraftBukkit end
                iblockdata.updateNeighbourShapes(this, blockposition, j);
                iblockdata.updateIndirectNeighbourShapes(this, blockposition, j);
            }

            this.onBlockStateChange(blockposition, iblockdata1, iblockdata2);
        }
    }
    // CraftBukkit end

    public void onBlockStateChange(BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1) {}

    @Override
    public boolean removeBlock(BlockPos blockposition, boolean flag) {
        FluidState fluid = this.getFluidState(blockposition);

        return this.setTypeAndData(blockposition, fluid.getBlockData(), 3 | (flag ? 64 : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos blockposition, boolean flag, @Nullable Entity entity, int i) {
        BlockState iblockdata = this.getType(blockposition);

        if (iblockdata.isAir()) {
            return false;
        } else {
            FluidState fluid = this.getFluidState(blockposition);

            if (!(iblockdata.getBlock() instanceof BaseFireBlock)) {
                this.levelEvent(2001, blockposition, Block.getCombinedId(iblockdata));
            }

            if (flag) {
                BlockEntity tileentity = iblockdata.getBlock().isEntityBlock() ? this.getBlockEntity(blockposition) : null;

                Block.dropItems(iblockdata, this, blockposition, tileentity, entity, ItemStack.EMPTY);
            }

            return this.setBlock(blockposition, fluid.getBlockData(), 3, i);
        }
    }

    public boolean setTypeUpdate(BlockPos blockposition, BlockState iblockdata) {
        return this.setTypeAndData(blockposition, iblockdata, 3);
    }

    public abstract void notify(BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1, int i);

    public void setBlocksDirty(BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1) {}

    public void updateNeighborsAt(BlockPos blockposition, Block block) {
        this.neighborChanged(blockposition.west(), block, blockposition);
        this.neighborChanged(blockposition.east(), block, blockposition);
        this.neighborChanged(blockposition.below(), block, blockposition);
        this.neighborChanged(blockposition.above(), block, blockposition);
        this.neighborChanged(blockposition.north(), block, blockposition);
        this.neighborChanged(blockposition.south(), block, blockposition);
    }

    public void updateNeighborsAtExceptFromFacing(BlockPos blockposition, Block block, Direction enumdirection) {
        if (enumdirection != Direction.WEST) {
            this.neighborChanged(blockposition.west(), block, blockposition);
        }

        if (enumdirection != Direction.EAST) {
            this.neighborChanged(blockposition.east(), block, blockposition);
        }

        if (enumdirection != Direction.DOWN) {
            this.neighborChanged(blockposition.below(), block, blockposition);
        }

        if (enumdirection != Direction.UP) {
            this.neighborChanged(blockposition.above(), block, blockposition);
        }

        if (enumdirection != Direction.NORTH) {
            this.neighborChanged(blockposition.north(), block, blockposition);
        }

        if (enumdirection != Direction.SOUTH) {
            this.neighborChanged(blockposition.south(), block, blockposition);
        }

    }

    public void neighborChanged(BlockPos blockposition, Block block, BlockPos blockposition1) {
        if (!this.isClientSide) {
            BlockState iblockdata = this.getType(blockposition);

            try {
                // CraftBukkit start
                CraftWorld world = ((ServerLevel) this).getWorld();
                if (world != null) {
                    BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), CraftBlockData.fromData(iblockdata), world.getBlockAt(blockposition1.getX(), blockposition1.getY(), blockposition1.getZ()));
                    this.getServerOH().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                }
                // CraftBukkit end
                iblockdata.neighborChanged(this, blockposition, block, blockposition1, false);
            // Spigot Start
            } catch (StackOverflowError ex) {
                lastPhysicsProblem = new BlockPos(blockposition);
                // Spigot End
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while updating neighbours");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being updated");

                crashreportsystemdetails.setDetail("Source block type", () -> {
                    try {
                        return String.format("ID #%s (%s // %s)", Registry.BLOCK.getKey(block), block.getDescriptionId(), block.getClass().getCanonicalName());
                    } catch (Throwable throwable1) {
                        return "ID #" + Registry.BLOCK.getKey(block);
                    }
                });
                CrashReportCategory.populateBlockDetails(crashreportsystemdetails, blockposition, iblockdata);
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public int getHeight(Heightmap.Types heightmap_type, int i, int j) {
        int k;

        if (i >= -30000000 && j >= -30000000 && i < 30000000 && j < 30000000) {
            if (this.hasChunk(i >> 4, j >> 4)) {
                k = this.getChunk(i >> 4, j >> 4).getHeight(heightmap_type, i & 15, j & 15) + 1;
            } else {
                k = 0;
            }
        } else {
            k = this.getSeaLevel() + 1;
        }

        return k;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.getChunkSource().getLightEngine();
    }

    @Override
    public BlockState getType(BlockPos blockposition) {
        // CraftBukkit start - tree generation
        if (captureTreeGeneration) {
            CapturedBlockState previous = capturedBlockStates.get(blockposition);
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (isOutsideBuildHeight(blockposition)) {
            return Blocks.VOID_AIR.getBlockData();
        } else {
            LevelChunk chunk = this.getChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4);

            return chunk.getType(blockposition);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos blockposition) {
        if (isOutsideBuildHeight(blockposition)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunk chunk = this.getChunkAt(blockposition);

            return chunk.getFluidState(blockposition);
        }
    }

    public boolean isDay() {
        return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
    }

    public boolean isNight() {
        return !this.dimensionType().hasFixedTime() && !this.isDay();
    }

    @Override
    public void playSound(@Nullable Player entityhuman, BlockPos blockposition, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1) {
        this.playSound(entityhuman, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, soundeffect, soundcategory, f, f1);
    }

    public abstract void playSound(@Nullable Player entityhuman, double d0, double d1, double d2, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1);

    public abstract void playSound(@Nullable Player entityhuman, Entity entity, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1);

    public void playLocalSound(double d0, double d1, double d2, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1, boolean flag) {}

    @Override
    public void addParticle(ParticleOptions particleparam, double d0, double d1, double d2, double d3, double d4, double d5) {}

    public void addAlwaysVisibleParticle(ParticleOptions particleparam, double d0, double d1, double d2, double d3, double d4, double d5) {}

    public void addAlwaysVisibleParticle(ParticleOptions particleparam, boolean flag, double d0, double d1, double d2, double d3, double d4, double d5) {}

    public float getSunAngle(float f) {
        float f1 = this.getTimeOfDay(f);

        return f1 * 6.2831855F;
    }

    public boolean addBlockEntity(BlockEntity tileentity) {
        if (this.updatingBlockEntities) {
            Level.LOGGER.error("Adding block entity while ticking: {} @ {}", new org.apache.logging.log4j.util.Supplier[]{() -> {
                        return Registry.BLOCK_ENTITY_TYPE.getKey(tileentity.getType());
                    }, tileentity::getBlockPos});
        }

        boolean flag = this.blockEntityList.add(tileentity);

        if (flag && tileentity instanceof TickableBlockEntity) {
            this.tickableBlockEntities.add(tileentity);
        }

        if (this.isClientSide) {
            BlockPos blockposition = tileentity.getBlockPos();
            BlockState iblockdata = this.getType(blockposition);

            this.notify(blockposition, iblockdata, iblockdata, 2);
        }

        return flag;
    }

    public void addAllPendingBlockEntities(Collection<BlockEntity> collection) {
        if (this.updatingBlockEntities) {
            this.pendingBlockEntities.addAll(collection);
        } else {
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                BlockEntity tileentity = (BlockEntity) iterator.next();

                this.addBlockEntity(tileentity);
            }
        }

    }

    public void tickBlockEntities() {
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        gameprofilerfiller.push("blockEntities");
        timings.tileEntityTick.startTiming(); // Spigot
        if (!this.blockEntitiesToUnload.isEmpty()) {
            this.tickableBlockEntities.removeAll(this.blockEntitiesToUnload);
            this.blockEntityList.removeAll(this.blockEntitiesToUnload);
            this.blockEntitiesToUnload.clear();
        }

        this.updatingBlockEntities = true;
        // Spigot start
        // Iterator iterator = this.tileEntityListTick.iterator();
        int tilesThisCycle = 0;
        for (tileLimiter.initTick();
                tilesThisCycle < tickableBlockEntities.size() && (tilesThisCycle % 10 != 0 || tileLimiter.shouldContinue());
                tileTickPosition++, tilesThisCycle++) {
            tileTickPosition = (tileTickPosition < tickableBlockEntities.size()) ? tileTickPosition : 0;
            BlockEntity tileentity = (BlockEntity) this.tickableBlockEntities.get(tileTickPosition);
            // Spigot start
            if (tileentity == null) {
                getServerOH().getLogger().severe("Spigot has detected a null entity and has removed it, preventing a crash");
                tilesThisCycle--;
                this.tickableBlockEntities.remove(tileTickPosition--);
                continue;
            }
            // Spigot end

            if (!tileentity.isRemoved() && tileentity.hasLevel()) {
                BlockPos blockposition = tileentity.getBlockPos();

                if (this.getChunkSource().isTickingChunk(blockposition) && this.getWorldBorder().isWithinBounds(blockposition)) {
                    try {
                        gameprofilerfiller.push(() -> {
                            return String.valueOf(BlockEntityType.getKey(tileentity.getType()));
                        });
                        tileentity.tickTimer.startTiming(); // Spigot
                        if (tileentity.getType().isValid(this.getType(blockposition).getBlock())) {
                            ((TickableBlockEntity) tileentity).tick();
                        } else {
                            tileentity.logInvalidState();
                        }

                        gameprofilerfiller.pop();
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking block entity");
                        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block entity being ticked");

                        tileentity.fillCrashReportCategory(crashreportsystemdetails);
                        throw new ReportedException(crashreport);
                    }
                    // Spigot start
                    finally {
                        tileentity.tickTimer.stopTiming();
                    }
                    // Spigot end
                }
            }

            if (tileentity.isRemoved()) {
                // Spigot start
                tilesThisCycle--;
                this.tickableBlockEntities.remove(tileTickPosition--);
                // Spigot end
                this.blockEntityList.remove(tileentity);
                if (this.hasChunkAt(tileentity.getBlockPos())) {
                    this.getChunkAt(tileentity.getBlockPos()).removeBlockEntity(tileentity.getBlockPos());
                }
            }
        }

        timings.tileEntityTick.stopTiming(); // Spigot
        timings.tileEntityPending.startTiming(); // Spigot
        this.updatingBlockEntities = false;
        gameprofilerfiller.popPush("pendingBlockEntities");
        if (!this.pendingBlockEntities.isEmpty()) {
            for (int i = 0; i < this.pendingBlockEntities.size(); ++i) {
                BlockEntity tileentity1 = (BlockEntity) this.pendingBlockEntities.get(i);

                if (!tileentity1.isRemoved()) {
                    /* CraftBukkit start - Order matters, moved down
                    if (!this.tileEntityList.contains(tileentity1)) {
                        this.a(tileentity1);
                    }
                    // CraftBukkit end */

                    if (this.hasChunkAt(tileentity1.getBlockPos())) {
                        LevelChunk chunk = this.getChunkAt(tileentity1.getBlockPos());
                        BlockState iblockdata = chunk.getType(tileentity1.getBlockPos());

                        chunk.setBlockEntity(tileentity1.getBlockPos(), tileentity1);
                        this.notify(tileentity1.getBlockPos(), iblockdata, iblockdata, 3);
                        // CraftBukkit start
                        // From above, don't screw this up - SPIGOT-1746
                        if (!this.blockEntityList.contains(tileentity1)) {
                            this.addBlockEntity(tileentity1);
                        }
                        // CraftBukkit end
                    }
                }
            }

            this.pendingBlockEntities.clear();
        }

        timings.tileEntityPending.stopTiming(); // Spigot
        gameprofilerfiller.pop();
        spigotConfig.currentPrimedTnt = 0; // Spigot
    }

    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        try {
            SpigotTimings.tickEntityTimer.startTiming(); // Spigot
            consumer.accept(entity);
            SpigotTimings.tickEntityTimer.stopTiming(); // Spigot
        } catch (Throwable throwable) {
<<<<<<< HEAD
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking entity");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being ticked");
=======
            if (throwable instanceof ThreadDeath) throw throwable; // Paper
            // Paper start - Prevent tile entity and entity crashes
            String msg = "Entity threw exception at " + entity.level.getWorld().getName() + ":" + entity.getX() + "," + entity.getY() + "," + entity.getZ();
            System.err.println(msg);
            throwable.printStackTrace();
            getServerOH().getPluginManager().callEvent(new ServerExceptionEvent(new ServerInternalException(msg, throwable)));
            entity.removed = true;
            return;
            // Paper end
        }
    }

    // Paper start - Prevent armor stands from doing entity lookups
    @Override
    public boolean noCollision(@Nullable Entity entity, AABB axisAlignedBB) {
        if (entity instanceof ArmorStand && !entity.level.paperConfig.armorStandEntityLookups) return false;
        return noCollision(entity, axisAlignedBB, Collections.emptySet());
    }
    // Paper end

    public boolean containsAnyBlocks(AABB axisalignedbb) {
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.minY);
        int l = Mth.ceil(axisalignedbb.maxY);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        BlockPos.PooledBlockPosition blockposition_pooledblockposition = BlockPos.PooledBlockPosition.r();
        Throwable throwable = null;

        try {
            for (int k1 = i; k1 < j; ++k1) {
                for (int l1 = k; l1 < l; ++l1) {
                    for (int i2 = i1; i2 < j1; ++i2) {
                        net.minecraft.world.level.block.state.BlockState iblockdata = this.getBlockState(blockposition_pooledblockposition.d(k1, l1, i2));

                        if (!iblockdata.isAir()) {
                            boolean flag = true;
>>>>>>> Toothpick

            entity.appendEntityCrashDetails(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    public Explosion explode(@Nullable Entity entity, double d0, double d1, double d2, float f, Explosion.BlockInteraction explosion_effect) {
        return this.createExplosion(entity, (DamageSource) null, (ExplosionDamageCalculator) null, d0, d1, d2, f, false, explosion_effect);
    }

    public Explosion explode(@Nullable Entity entity, double d0, double d1, double d2, float f, boolean flag, Explosion.BlockInteraction explosion_effect) {
        return this.createExplosion(entity, (DamageSource) null, (ExplosionDamageCalculator) null, d0, d1, d2, f, flag, explosion_effect);
    }

    public Explosion createExplosion(@Nullable Entity entity, @Nullable DamageSource damagesource, @Nullable ExplosionDamageCalculator explosiondamagecalculator, double d0, double d1, double d2, float f, boolean flag, Explosion.BlockInteraction explosion_effect) {
        Explosion explosion = new Explosion(this, entity, damagesource, explosiondamagecalculator, d0, d1, d2, f, flag, explosion_effect);

        explosion.explode();
        explosion.finalizeExplosion(true);
        return explosion;
    }

    @Nullable
    @Override
    // CraftBukkit start
    public BlockEntity getBlockEntity(BlockPos blockposition) {
        return getTileEntity(blockposition, true);
    }

    @Nullable
    public BlockEntity getTileEntity(BlockPos blockposition, boolean validate) {
        // CraftBukkit end
        if (isOutsideBuildHeight(blockposition)) {
            return null;
        } else if (!this.isClientSide && Thread.currentThread() != this.thread) {
            return null;
        } else {
            // CraftBukkit start
            if (capturedTileEntities.containsKey(blockposition)) {
                return capturedTileEntities.get(blockposition);
            }
            // CraftBukkit end

            BlockEntity tileentity = null;

            if (this.updatingBlockEntities) {
                tileentity = this.getPendingBlockEntityAt(blockposition);
            }

            if (tileentity == null) {
                tileentity = this.getChunkAt(blockposition).getBlockEntity(blockposition, LevelChunk.EntityCreationType.IMMEDIATE);
            }

            if (tileentity == null) {
                tileentity = this.getPendingBlockEntityAt(blockposition);
            }

            return tileentity;
        }
    }

    @Nullable
    private BlockEntity getPendingBlockEntityAt(BlockPos blockposition) {
        for (int i = 0; i < this.pendingBlockEntities.size(); ++i) {
            BlockEntity tileentity = (BlockEntity) this.pendingBlockEntities.get(i);

            if (!tileentity.isRemoved() && tileentity.getBlockPos().equals(blockposition)) {
                return tileentity;
            }
        }

        return null;
    }

    public void setBlockEntity(BlockPos blockposition, @Nullable BlockEntity tileentity) {
        if (!isOutsideBuildHeight(blockposition)) {
            if (tileentity != null && !tileentity.isRemoved()) {
                // CraftBukkit start
                if (captureBlockStates) {
                    tileentity.setLevelAndPosition(this, blockposition);
                    capturedTileEntities.put(blockposition.immutable(), tileentity);
                    return;
                }
                // CraftBukkit end
                if (this.updatingBlockEntities) {
                    tileentity.setLevelAndPosition(this, blockposition);
                    Iterator iterator = this.pendingBlockEntities.iterator();

                    while (iterator.hasNext()) {
                        BlockEntity tileentity1 = (BlockEntity) iterator.next();

                        if (tileentity1.getBlockPos().equals(blockposition)) {
                            tileentity1.setRemoved();
                            iterator.remove();
                        }
                    }

                    this.pendingBlockEntities.add(tileentity);
                } else {
                    this.getChunkAt(blockposition).setBlockEntity(blockposition, tileentity);
                    this.addBlockEntity(tileentity);
                }
            }

        }
    }

    public void removeBlockEntity(BlockPos blockposition) {
        BlockEntity tileentity = this.getTileEntity(blockposition, false); // CraftBukkit

        if (tileentity != null && this.updatingBlockEntities) {
            tileentity.setRemoved();
            this.pendingBlockEntities.remove(tileentity);
        } else {
            if (tileentity != null) {
                this.pendingBlockEntities.remove(tileentity);
                this.blockEntityList.remove(tileentity);
                this.tickableBlockEntities.remove(tileentity);
            }

            this.getChunkAt(blockposition).removeBlockEntity(blockposition);
        }

    }

    public boolean isLoaded(BlockPos blockposition) {
        return isOutsideBuildHeight(blockposition) ? false : this.getChunkSource().hasChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4);
    }

    public boolean loadedAndEntityCanStandOnFace(BlockPos blockposition, Entity entity, Direction enumdirection) {
        if (isOutsideBuildHeight(blockposition)) {
            return false;
        } else {
            ChunkAccess ichunkaccess = this.getChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4, ChunkStatus.FULL, false);

            return ichunkaccess == null ? false : ichunkaccess.getType(blockposition).entityCanStandOnFace((BlockGetter) this, blockposition, entity, enumdirection);
        }
    }

    public boolean loadedAndEntityCanStandOn(BlockPos blockposition, Entity entity) {
        return this.loadedAndEntityCanStandOnFace(blockposition, entity, Direction.UP);
    }

    public void updateSkyBrightness() {
        double d0 = 1.0D - (double) (this.getRainLevel(1.0F) * 5.0F) / 16.0D;
        double d1 = 1.0D - (double) (this.getThunderLevel(1.0F) * 5.0F) / 16.0D;
        double d2 = 0.5D + 2.0D * Mth.clamp((double) Mth.cos(this.getTimeOfDay(1.0F) * 6.2831855F), -0.25D, 0.25D);

        this.skyDarken = (int) ((1.0D - d2 * d0 * d1) * 11.0D);
    }

    public void setSpawnSettings(boolean flag, boolean flag1) {
        this.getChunkSource().setSpawnSettings(flag, flag1);
    }

    protected void prepareWeather() {
        if (this.levelData.isRaining()) {
            this.rainLevel = 1.0F;
            if (this.levelData.isThundering()) {
                this.thunderLevel = 1.0F;
            }
        }

    }

    public void close() throws IOException {
        this.getChunkSource().close();
    }

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int i, int j) {
        return this.getChunk(i, j, ChunkStatus.FULL, false);
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB axisalignedbb, @Nullable Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        List<Entity> list = Lists.newArrayList();
        int i = Mth.floor((axisalignedbb.minX - 2.0D) / 16.0D);
        int j = Mth.floor((axisalignedbb.maxX + 2.0D) / 16.0D);
        int k = Mth.floor((axisalignedbb.minZ - 2.0D) / 16.0D);
        int l = Mth.floor((axisalignedbb.maxZ + 2.0D) / 16.0D);
        ChunkSource ichunkprovider = this.getChunkSource();

        for (int i1 = i; i1 <= j; ++i1) {
            for (int j1 = k; j1 <= l; ++j1) {
                LevelChunk chunk = ichunkprovider.getChunk(i1, j1, false);

                if (chunk != null) {
                    chunk.getEntities(entity, axisalignedbb, list, predicate);
                }
            }
        }

        return list;
    }

    public <T extends Entity> List<T> getEntities(@Nullable EntityType<T> entitytypes, AABB axisalignedbb, Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        int i = Mth.floor((axisalignedbb.minX - 2.0D) / 16.0D);
        int j = Mth.ceil((axisalignedbb.maxX + 2.0D) / 16.0D);
        int k = Mth.floor((axisalignedbb.minZ - 2.0D) / 16.0D);
        int l = Mth.ceil((axisalignedbb.maxZ + 2.0D) / 16.0D);
        List<T> list = Lists.newArrayList();

        for (int i1 = i; i1 < j; ++i1) {
            for (int j1 = k; j1 < l; ++j1) {
                LevelChunk chunk = this.getChunkSource().getChunk(i1, j1, false);

                if (chunk != null) {
                    chunk.getEntities(entitytypes, axisalignedbb, list, predicate);
                }
            }
        }

        return list;
    }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<? extends T> oclass, AABB axisalignedbb, @Nullable Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        int i = Mth.floor((axisalignedbb.minX - 2.0D) / 16.0D);
        int j = Mth.ceil((axisalignedbb.maxX + 2.0D) / 16.0D);
        int k = Mth.floor((axisalignedbb.minZ - 2.0D) / 16.0D);
        int l = Mth.ceil((axisalignedbb.maxZ + 2.0D) / 16.0D);
        List<T> list = Lists.newArrayList();
        ChunkSource ichunkprovider = this.getChunkSource();

        for (int i1 = i; i1 < j; ++i1) {
            for (int j1 = k; j1 < l; ++j1) {
                LevelChunk chunk = ichunkprovider.getChunk(i1, j1, false);

                if (chunk != null) {
                    chunk.getEntitiesOfClass(oclass, axisalignedbb, list, predicate);
                }
            }
        }

        return list;
    }

    @Override
    public <T extends Entity> List<T> getLoadedEntitiesOfClass(Class<? extends T> oclass, AABB axisalignedbb, @Nullable Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getLoadedEntities");
        int i = Mth.floor((axisalignedbb.minX - 2.0D) / 16.0D);
        int j = Mth.ceil((axisalignedbb.maxX + 2.0D) / 16.0D);
        int k = Mth.floor((axisalignedbb.minZ - 2.0D) / 16.0D);
        int l = Mth.ceil((axisalignedbb.maxZ + 2.0D) / 16.0D);
        List<T> list = Lists.newArrayList();
        ChunkSource ichunkprovider = this.getChunkSource();

        for (int i1 = i; i1 < j; ++i1) {
            for (int j1 = k; j1 < l; ++j1) {
                LevelChunk chunk = ichunkprovider.getChunkNow(i1, j1);

                if (chunk != null) {
                    chunk.getEntitiesOfClass(oclass, axisalignedbb, list, predicate);
                }
            }
        }

        return list;
    }

    @Nullable
    public abstract Entity getEntity(int i);

    public void blockEntityChanged(BlockPos blockposition, BlockEntity tileentity) {
        if (this.hasChunkAt(blockposition)) {
            this.getChunkAt(blockposition).markUnsaved();
        }

    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public Level getLevel() {
        return this;
    }

    public int getDirectSignalTo(BlockPos blockposition) {
        byte b0 = 0;
        int i = Math.max(b0, this.getDirectSignal(blockposition.below(), Direction.DOWN));

        if (i >= 15) {
            return i;
        } else {
            i = Math.max(i, this.getDirectSignal(blockposition.above(), Direction.UP));
            if (i >= 15) {
                return i;
            } else {
                i = Math.max(i, this.getDirectSignal(blockposition.north(), Direction.NORTH));
                if (i >= 15) {
                    return i;
                } else {
                    i = Math.max(i, this.getDirectSignal(blockposition.south(), Direction.SOUTH));
                    if (i >= 15) {
                        return i;
                    } else {
                        i = Math.max(i, this.getDirectSignal(blockposition.west(), Direction.WEST));
                        if (i >= 15) {
                            return i;
                        } else {
                            i = Math.max(i, this.getDirectSignal(blockposition.east(), Direction.EAST));
                            return i >= 15 ? i : i;
                        }
                    }
                }
            }
        }
    }

    public boolean hasSignal(BlockPos blockposition, Direction enumdirection) {
        return this.getSignal(blockposition, enumdirection) > 0;
    }

    public int getSignal(BlockPos blockposition, Direction enumdirection) {
        BlockState iblockdata = this.getType(blockposition);
        int i = iblockdata.getSignal((BlockGetter) this, blockposition, enumdirection);

        return iblockdata.isRedstoneConductor(this, blockposition) ? Math.max(i, this.getDirectSignalTo(blockposition)) : i;
    }

    public boolean hasNeighborSignal(BlockPos blockposition) {
        return this.getSignal(blockposition.below(), Direction.DOWN) > 0 ? true : (this.getSignal(blockposition.above(), Direction.UP) > 0 ? true : (this.getSignal(blockposition.north(), Direction.NORTH) > 0 ? true : (this.getSignal(blockposition.south(), Direction.SOUTH) > 0 ? true : (this.getSignal(blockposition.west(), Direction.WEST) > 0 ? true : this.getSignal(blockposition.east(), Direction.EAST) > 0))));
    }

    public int getBestNeighborSignal(BlockPos blockposition) {
        int i = 0;
        Direction[] aenumdirection = Level.DIRECTIONS;
        int j = aenumdirection.length;

        for (int k = 0; k < j; ++k) {
            Direction enumdirection = aenumdirection[k];
            int l = this.getSignal(blockposition.relative(enumdirection), enumdirection);

            if (l >= 15) {
                return 15;
            }

            if (l > i) {
                i = l;
            }
        }

        return i;
    }

    public long getGameTime() {
        return this.levelData.getGameTime();
    }

    public long getDayTime() {
        return this.levelData.getDayTime();
    }

    public boolean mayInteract(Player entityhuman, BlockPos blockposition) {
        return true;
    }

    public void broadcastEntityEvent(Entity entity, byte b0) {}

    public void blockEvent(BlockPos blockposition, Block block, int i, int j) {
        this.getType(blockposition).triggerEvent(this, blockposition, i, j);
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    public GameRules getGameRules() {
        return this.levelData.getGameRules();
    }

    public float getThunderLevel(float f) {
        return Mth.lerp(f, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(f);
    }

    public float getRainLevel(float f) {
        return Mth.lerp(f, this.oRainLevel, this.rainLevel);
    }

    public boolean isThundering() {
        return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling() ? (double) this.getThunderLevel(1.0F) > 0.9D : false;
    }

    public boolean isRaining() {
        return (double) this.getRainLevel(1.0F) > 0.2D;
    }

    public boolean isRainingAt(BlockPos blockposition) {
        if (!this.isRaining()) {
            return false;
        } else if (!this.canSeeSky(blockposition)) {
            return false;
        } else if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockposition).getY() > blockposition.getY()) {
            return false;
        } else {
            Biome biomebase = this.getBiome(blockposition);

            return biomebase.getPrecipitation() == Biome.Precipitation.RAIN && biomebase.getTemperature(blockposition) >= 0.15F;
        }
    }

    public boolean isHumidAt(BlockPos blockposition) {
        Biome biomebase = this.getBiome(blockposition);

        return biomebase.isHumid();
    }

    @Nullable
    public abstract MapItemSavedData getMapData(String s);

    public abstract void setMapData(MapItemSavedData worldmap);

    public abstract int getFreeMapId();

    public void globalLevelEvent(int i, BlockPos blockposition, int j) {}

    public CrashReportCategory fillReportDetails(CrashReport crashreport) {
        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Affected level", 1);

        crashreportsystemdetails.setDetail("All players", () -> {
            return this.players().size() + " total; " + this.players();
        });
        ChunkSource ichunkprovider = this.getChunkSource();

        crashreportsystemdetails.setDetail("Chunk stats", ichunkprovider::gatherStats);
        crashreportsystemdetails.setDetail("Level dimension", () -> {
            return this.getDimensionKey().location().toString();
        });

        try {
            this.levelData.fillCrashReportCategory(crashreportsystemdetails);
        } catch (Throwable throwable) {
            crashreportsystemdetails.setDetailError("Level Data Unobtainable", throwable);
        }

        return crashreportsystemdetails;
    }

    public abstract void destroyBlockProgress(int i, BlockPos blockposition, int j);

    public abstract Scoreboard getScoreboard();

    public void updateNeighbourForOutputSignal(BlockPos blockposition, Block block) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (this.hasChunkAt(blockposition1)) {
                BlockState iblockdata = this.getType(blockposition1);

                if (iblockdata.is(Blocks.COMPARATOR)) {
                    iblockdata.neighborChanged(this, blockposition1, block, blockposition, false);
                } else if (iblockdata.isRedstoneConductor(this, blockposition1)) {
                    blockposition1 = blockposition1.relative(enumdirection);
                    iblockdata = this.getType(blockposition1);
                    if (iblockdata.is(Blocks.COMPARATOR)) {
                        iblockdata.neighborChanged(this, blockposition1, block, blockposition, false);
                    }
                }
            }
        }

    }

    @Override
    public DifficultyInstance getDamageScaler(BlockPos blockposition) {
        long i = 0L;
        float f = 0.0F;

        if (this.hasChunkAt(blockposition)) {
            f = this.getMoonBrightness();
            i = this.getChunkAt(blockposition).getInhabitedTime();
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), i, f);
    }

    @Override
    public int getSkyDarken() {
        return this.skyDarken;
    }

    public void setSkyFlashTime(int i) {}

    @Override
    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    public void sendPacketToServer(Packet<?> packet) {
        throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
    }

    @Override
    public DimensionType dimensionType() {
        return this.dimensionType;
    }

    public ResourceKey<DimensionType> getTypeKey() {
        return this.dimensionTypeKey;
    }

    public ResourceKey<Level> getDimensionKey() {
        return this.dimension;
    }

    @Override
    public Random getRandom() {
        return this.random;
    }

    @Override
    public boolean isStateAtPosition(BlockPos blockposition, Predicate<BlockState> predicate) {
        return predicate.test(this.getType(blockposition));
    }

    public abstract RecipeManager getRecipeManager();

    public abstract TagManager getTagManager();

    public BlockPos getBlockRandomPos(int i, int j, int k, int l) {
        this.randValue = this.randValue * 3 + 1013904223;
        int i1 = this.randValue >> 2;

        return new BlockPos(i + (i1 & 15), j + (i1 >> 16 & l), k + (i1 >> 8 & 15));
    }

    public boolean noSave() {
        return false;
    }

    public ProfilerFiller getProfiler() {
        return (ProfilerFiller) this.profiler.get();
    }

    public Supplier<ProfilerFiller> getProfilerSupplier() {
        return this.profiler;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    public final boolean isDebug() {
        return this.isDebug;
    }
}
