package org.bukkit.craftbukkit.generator;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public class CustomChunkGenerator extends InternalChunkGenerator {
    private final net.minecraft.world.level.chunk.ChunkGenerator delegate;
    private final ChunkGenerator generator;
    private final ServerLevel world;
    private final Random random = new Random();

    private class CustomBiomeGrid implements BiomeGrid {

        private final ChunkBiomeContainer biome; // SPIGOT-5529: stored in 4x4 grid

        public CustomBiomeGrid(ChunkBiomeContainer biome) {
            this.biome = biome;
        }

        @Override
        public Biome getBiome(int x, int z) {
            return getBiome(x, 0, z);
        }

        @Override
        public void setBiome(int x, int z, Biome bio) {
            for (int y = 0; y < world.getWorld().getMaxHeight(); y += 4) {
                setBiome(x, y, z, bio);
            }
        }

        @Override
        public Biome getBiome(int x, int y, int z) {
            return CraftBlock.biomeBaseToBiome(biome.getNoiseBiome(x >> 2, y >> 2, z >> 2));
        }

        @Override
        public void setBiome(int x, int y, int z, Biome bio) {
            biome.setBiome(x >> 2, y >> 2, z >> 2, CraftBlock.biomeToBiomeBase(bio));
        }
    }

    public CustomChunkGenerator(ServerLevel world, net.minecraft.world.level.chunk.ChunkGenerator delegate, ChunkGenerator generator) {
        super(delegate.getWorldChunkManager(), delegate.getSettings());

        this.world = world;
        this.delegate = delegate;
        this.generator = generator;
    }

    @Override
    public void createBiomes(ChunkAccess ichunkaccess) {
        // Don't allow the server to override any custom biomes that have been set
    }

    @Override
    public BiomeSource getWorldChunkManager() {
        return delegate.getWorldChunkManager();
    }

    @Override
    public void storeStructures(LevelAccessor generatoraccess, StructureFeatureManager structuremanager, ChunkAccess ichunkaccess) {
        delegate.storeStructures(generatoraccess, structuremanager, ichunkaccess);
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public void buildBase(WorldGenRegion regionlimitedworldaccess, ChunkAccess ichunkaccess) {
        // Call the bukkit ChunkGenerator before structure generation so correct biome information is available.
        int x = ichunkaccess.getPos().x;
        int z = ichunkaccess.getPos().z;
        random.setSeed((long) x * 341873128712L + (long) z * 132897987541L);

        // Get default biome data for chunk
        CustomBiomeGrid biomegrid = new CustomBiomeGrid(new ChunkBiomeContainer(ichunkaccess.getPos(), this.getWorldChunkManager()));

        ChunkData data;
        if (generator.isParallelCapable()) {
            data = generator.generateChunkData(this.world.getWorld(), random, x, z, biomegrid);
        } else {
            synchronized (this) {
                data = generator.generateChunkData(this.world.getWorld(), random, x, z, biomegrid);
            }
        }

        Preconditions.checkArgument(data instanceof CraftChunkData, "Plugins must use createChunkData(World) rather than implementing ChunkData: %s", data);
        CraftChunkData craftData = (CraftChunkData) data;
        LevelChunkSection[] sections = craftData.getRawChunkData();

        LevelChunkSection[] csect = ichunkaccess.getSections();
        int scnt = Math.min(csect.length, sections.length);

        // Loop through returned sections
        for (int sec = 0; sec < scnt; sec++) {
            if (sections[sec] == null) {
                continue;
            }
            LevelChunkSection section = sections[sec];

            csect[sec] = section;
        }

        // Set biome grid
        ((ProtoChunk) ichunkaccess).setBiomes(biomegrid.biome);

        if (craftData.getTiles() != null) {
            for (BlockPos pos : craftData.getTiles()) {
                int tx = pos.getX();
                int ty = pos.getY();
                int tz = pos.getZ();
                Block block = craftData.getTypeId(tx, ty, tz).getBlock();

                if (block.isEntityBlock()) {
                    BlockEntity tile = ((EntityBlock) block).newBlockEntity(world);
                    ichunkaccess.setBlockEntity(new BlockPos((x << 4) + tx, ty, (z << 4) + tz), tile);
                }
            }
        }
    }

    @Override
    public void createStructures(StructureFeatureManager structuremanager, ChunkAccess ichunkaccess, StructureManager definedstructuremanager, long i) {
        if (generator.shouldGenerateStructures()) {
            // Still need a way of getting the biome of this chunk to pass to createStructures
            // Using default biomes for now.
            delegate.createStructures(structuremanager, ichunkaccess, definedstructuremanager, i);
        }
    }

    @Override
    public void applyCarvers(long i, BiomeManager biomemanager, ChunkAccess ichunkaccess, GenerationStep.Carving worldgenstage_features) {
        if (generator.shouldGenerateCaves()) {
            delegate.applyCarvers(i, biomemanager, ichunkaccess, worldgenstage_features);
        }
    }

    @Override
    public void buildNoise(LevelAccessor generatoraccess, StructureFeatureManager structuremanager, ChunkAccess ichunkaccess) {
        // Disable vanilla generation
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types heightmap_type) {
        return delegate.getBaseHeight(i, j, heightmap_type);
    }

    @Override
    public List<net.minecraft.world.level.biome.Biome.BiomeMeta> getMobsFor(net.minecraft.world.level.biome.Biome biomebase, StructureFeatureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        return delegate.getMobsFor(biomebase, structuremanager, enumcreaturetype, blockposition);
    }

    @Override
    public void addDecorations(WorldGenRegion regionlimitedworldaccess, StructureFeatureManager structuremanager) {
        if (generator.shouldGenerateDecorations()) {
            delegate.addDecorations(regionlimitedworldaccess, structuremanager);
        }
    }

    @Override
    public void addMobs(WorldGenRegion regionlimitedworldaccess) {
        if (generator.shouldGenerateMobs()) {
            delegate.addMobs(regionlimitedworldaccess);
        }
    }

    @Override
    public int getSpawnHeight() {
        return delegate.getSpawnHeight();
    }

    @Override
    public int getGenDepth() {
        return delegate.getGenDepth();
    }

    @Override
    public BlockGetter getBaseColumn(int i, int j) {
        return delegate.getBaseColumn(i, j);
    }

    @Override
    protected Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator> codec() {
        throw new UnsupportedOperationException("Cannot serialize CustomChunkGenerator");
    }
}
