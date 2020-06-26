package net.minecraft.server.level;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldGenRegion implements WorldGenLevel {

    private static final Logger LOGGER = LogManager.getLogger();
    private final List<ChunkAccess> cache;
    private final int x;
    private final int z;
    private final int size;
    private final ServerLevel level;
    private final long seed;
    private final LevelData levelData;
    private final Random random;
    private final DimensionType dimensionType;
    private final TickList<Block> blockTicks = new WorldGenTickList<>((blockposition) -> {
        return this.getChunk(blockposition).getBlockTicks();
    });
    private final TickList<Fluid> liquidTicks = new WorldGenTickList<>((blockposition) -> {
        return this.getChunk(blockposition).getLiquidTicks();
    });
    private final BiomeManager biomeManager;
    private final ChunkPos firstPos;
    private final ChunkPos lastPos;

    public WorldGenRegion(ServerLevel worldserver, List<ChunkAccess> list) {
        int i = Mth.floor(Math.sqrt((double) list.size()));

        if (i * i != list.size()) {
            throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Cache size is not a square."));
        } else {
            ChunkPos chunkcoordintpair = ((ChunkAccess) list.get(list.size() / 2)).getPos();

            this.cache = list;
            this.x = chunkcoordintpair.x;
            this.z = chunkcoordintpair.z;
            this.size = i;
            this.level = worldserver;
            this.seed = worldserver.getSeed();
            this.levelData = worldserver.getLevelData();
            this.random = worldserver.getRandom();
            this.dimensionType = worldserver.dimensionType();
            this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed), worldserver.dimensionType().getGenLayerZoomer());
            this.firstPos = ((ChunkAccess) list.get(0)).getPos();
            this.lastPos = ((ChunkAccess) list.get(list.size() - 1)).getPos();
        }
    }

    public int getCenterX() {
        return this.x;
    }

    public int getCenterZ() {
        return this.z;
    }

    @Override
    public ChunkAccess getChunk(int i, int j) {
        return this.getChunk(i, j, ChunkStatus.EMPTY);
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        ChunkAccess ichunkaccess;

        if (this.hasChunk(i, j)) {
            int k = i - this.firstPos.x;
            int l = j - this.firstPos.z;

            ichunkaccess = (ChunkAccess) this.cache.get(k + l * this.size);
            if (ichunkaccess.getStatus().isOrAfter(chunkstatus)) {
                return ichunkaccess;
            }
        } else {
            ichunkaccess = null;
        }

        if (!flag) {
            return null;
        } else {
            WorldGenRegion.LOGGER.error("Requested chunk : {} {}", i, j);
            WorldGenRegion.LOGGER.error("Region bounds : {} {} | {} {}", this.firstPos.x, this.firstPos.z, this.lastPos.x, this.lastPos.z);
            if (ichunkaccess != null) {
                throw (RuntimeException) Util.pauseInIde(new RuntimeException(String.format("Chunk is not of correct status. Expecting %s, got %s | %s %s", chunkstatus, ichunkaccess.getStatus(), i, j)));
            } else {
                throw (RuntimeException) Util.pauseInIde(new RuntimeException(String.format("We are asking a region for a chunk out of bound | %s %s", i, j)));
            }
        }
    }

    @Override
    public boolean hasChunk(int i, int j) {
        return i >= this.firstPos.x && i <= this.lastPos.x && j >= this.firstPos.z && j <= this.lastPos.z;
    }

    @Override
    public BlockState getType(BlockPos blockposition) {
        return this.getChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4).getType(blockposition);
    }

    @Override
    public FluidState getFluidState(BlockPos blockposition) {
        return this.getChunk(blockposition).getFluidState(blockposition);
    }

    @Nullable
    @Override
    public Player getNearestPlayer(double d0, double d1, double d2, double d3, Predicate<Entity> predicate) {
        return null;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Biome getUncachedNoiseBiome(int i, int j, int k) {
        return this.level.getUncachedNoiseBiome(i, j, k);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public boolean destroyBlock(BlockPos blockposition, boolean flag, @Nullable Entity entity, int i) {
        BlockState iblockdata = this.getType(blockposition);

        if (iblockdata.isAir()) {
            return false;
        } else {
            if (flag) {
                BlockEntity tileentity = iblockdata.getBlock().isEntityBlock() ? this.getBlockEntity(blockposition) : null;

                Block.dropItems(iblockdata, this.level, blockposition, tileentity, entity, ItemStack.EMPTY);
            }

            return this.setBlock(blockposition, Blocks.AIR.getBlockData(), 3, i);
        }
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos blockposition) {
        ChunkAccess ichunkaccess = this.getChunk(blockposition);
        BlockEntity tileentity = ichunkaccess.getBlockEntity(blockposition);

        if (tileentity != null) {
            return tileentity;
        } else {
            CompoundTag nbttagcompound = ichunkaccess.getBlockEntityNbt(blockposition);
            BlockState iblockdata = ichunkaccess.getType(blockposition);

            if (nbttagcompound != null) {
                if ("DUMMY".equals(nbttagcompound.getString("id"))) {
                    Block block = iblockdata.getBlock();

                    if (!(block instanceof EntityBlock)) {
                        return null;
                    }

                    tileentity = ((EntityBlock) block).newBlockEntity(this.level);
                } else {
                    tileentity = BlockEntity.create(iblockdata, nbttagcompound);
                }

                if (tileentity != null) {
                    ichunkaccess.setBlockEntity(blockposition, tileentity);
                    return tileentity;
                }
            }

            if (iblockdata.getBlock() instanceof EntityBlock) {
                WorldGenRegion.LOGGER.warn("Tried to access a block entity before it was created. {}", blockposition);
            }

            return null;
        }
    }

    @Override
    public boolean setBlock(BlockPos blockposition, BlockState iblockdata, int i, int j) {
        ChunkAccess ichunkaccess = this.getChunk(blockposition);
        BlockState iblockdata1 = ichunkaccess.setType(blockposition, iblockdata, false);

        if (iblockdata1 != null) {
            this.level.onBlockStateChange(blockposition, iblockdata1, iblockdata);
        }

        Block block = iblockdata.getBlock();

        if (block.isEntityBlock()) {
            if (ichunkaccess.getStatus().getChunkType() == ChunkStatus.ChunkType.LEVELCHUNK) {
                ichunkaccess.setBlockEntity(blockposition, ((EntityBlock) block).newBlockEntity(this));
            } else {
                CompoundTag nbttagcompound = new CompoundTag();

                nbttagcompound.putInt("x", blockposition.getX());
                nbttagcompound.putInt("y", blockposition.getY());
                nbttagcompound.putInt("z", blockposition.getZ());
                nbttagcompound.putString("id", "DUMMY");
                ichunkaccess.setBlockEntityNbt(nbttagcompound);
            }
        } else if (iblockdata1 != null && iblockdata1.getBlock().isEntityBlock()) {
            ichunkaccess.removeBlockEntity(blockposition);
        }

        if (iblockdata.hasPostProcess(this, blockposition)) {
            this.markPosForPostprocessing(blockposition);
        }

        return true;
    }

    private void markPosForPostprocessing(BlockPos blockposition) {
        this.getChunk(blockposition).markPosForPostprocessing(blockposition);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return addEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        int i = Mth.floor(entity.getX() / 16.0D);
        int j = Mth.floor(entity.getZ() / 16.0D);

        this.getChunk(i, j).addEntity(entity);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos blockposition, boolean flag) {
        return this.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 3);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Deprecated
    @Override
    public ServerLevel getLevel() {
        return this.level;
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    @Override
    public DifficultyInstance getDamageScaler(BlockPos blockposition) {
        if (!this.hasChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4)) {
            throw new RuntimeException("We are asking a region for a chunk out of bound");
        } else {
            return new DifficultyInstance(this.level.getDifficulty(), this.level.getDayTime(), 0L, this.level.getMoonBrightness());
        }
    }

    @Override
    public ChunkSource getChunkSource() {
        return this.level.getChunkSourceOH();
    }

    @Override
    public long getSeed() {
        return this.seed;
    }

    @Override
    public TickList<Block> getBlockTickList() {
        return this.blockTicks;
    }

    @Override
    public TickList<Fluid> getFluidTickList() {
        return this.liquidTicks;
    }

    @Override
    public int getSeaLevel() {
        return this.level.getSeaLevel();
    }

    @Override
    public Random getRandom() {
        return this.random;
    }

    @Override
    public int getHeight(Heightmap.Types heightmap_type, int i, int j) {
        return this.getChunk(i >> 4, j >> 4).getHeight(heightmap_type, i & 15, j & 15) + 1;
    }

    @Override
    public void playSound(@Nullable Player entityhuman, BlockPos blockposition, SoundEvent soundeffect, SoundSource soundcategory, float f, float f1) {}

    @Override
    public void addParticle(ParticleOptions particleparam, double d0, double d1, double d2, double d3, double d4, double d5) {}

    @Override
    public void levelEvent(@Nullable Player entityhuman, int i, BlockPos blockposition, int j) {}

    @Override
    public DimensionType dimensionType() {
        return this.dimensionType;
    }

    @Override
    public boolean isStateAtPosition(BlockPos blockposition, Predicate<BlockState> predicate) {
        return predicate.test(this.getType(blockposition));
    }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<? extends T> oclass, AABB axisalignedbb, @Nullable Predicate<? super T> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB axisalignedbb, @Nullable Predicate<? super Entity> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Player> players() {
        return Collections.emptyList();
    }
}
