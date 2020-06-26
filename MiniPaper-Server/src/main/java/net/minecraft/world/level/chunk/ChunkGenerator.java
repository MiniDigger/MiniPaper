package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeDefaultFeatures;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.StrongholdConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;

public abstract class ChunkGenerator {

    public static final Codec<ChunkGenerator> CODEC;
    protected final BiomeSource biomeSource;
    protected final BiomeSource runtimeBiomeSource;
    private final StructureSettings settings;
    private final long strongholdSeed;
    private final List<ChunkPos> strongholdPositions;

    public ChunkGenerator(BiomeSource worldchunkmanager, StructureSettings structuresettings) {
        this(worldchunkmanager, worldchunkmanager, structuresettings, 0L);
    }

    public ChunkGenerator(BiomeSource worldchunkmanager, BiomeSource worldchunkmanager1, StructureSettings structuresettings, long i) {
        this.strongholdPositions = Lists.newArrayList();
        this.biomeSource = worldchunkmanager;
        this.runtimeBiomeSource = worldchunkmanager1;
        this.settings = structuresettings;
        this.strongholdSeed = i;
    }

    private void generateStrongholds() {
        if (this.strongholdPositions.isEmpty()) {
            StrongholdConfiguration structuresettingsstronghold = this.settings.stronghold();

            if (structuresettingsstronghold != null && structuresettingsstronghold.count() != 0) {
                List<Biome> list = Lists.newArrayList();
                Iterator iterator = this.biomeSource.possibleBiomes().iterator();

                while (iterator.hasNext()) {
                    Biome biomebase = (Biome) iterator.next();

                    if (biomebase.isValidStart(StructureFeature.STRONGHOLD)) {
                        list.add(biomebase);
                    }
                }

                int i = structuresettingsstronghold.distance();
                int j = structuresettingsstronghold.count();
                int k = structuresettingsstronghold.spread();
                Random random = new Random();

                random.setSeed(this.strongholdSeed);
                double d0 = random.nextDouble() * 3.141592653589793D * 2.0D;
                int l = 0;
                int i1 = 0;

                for (int j1 = 0; j1 < j; ++j1) {
                    double d1 = (double) (4 * i + i * i1 * 6) + (random.nextDouble() - 0.5D) * (double) i * 2.5D;
                    int k1 = (int) Math.round(Math.cos(d0) * d1);
                    int l1 = (int) Math.round(Math.sin(d0) * d1);
                    BlockPos blockposition = this.biomeSource.findBiomeHorizontal((k1 << 4) + 8, 0, (l1 << 4) + 8, 112, list, random);

                    if (blockposition != null) {
                        k1 = blockposition.getX() >> 4;
                        l1 = blockposition.getZ() >> 4;
                    }

                    this.strongholdPositions.add(new ChunkPos(k1, l1));
                    d0 += 6.283185307179586D / (double) k;
                    ++l;
                    if (l == k) {
                        ++i1;
                        l = 0;
                        k += 2 * k / (i1 + 1);
                        k = Math.min(k, j - j1);
                        d0 += random.nextDouble() * 3.141592653589793D * 2.0D;
                    }
                }

            }
        }
    }

    protected abstract Codec<? extends ChunkGenerator> codec();

    public void createBiomes(ChunkAccess ichunkaccess) {
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();

        ((ProtoChunk) ichunkaccess).setBiomes(new ChunkBiomeContainer(chunkcoordintpair, this.runtimeBiomeSource));
    }

    public void applyCarvers(long i, BiomeManager biomemanager, ChunkAccess ichunkaccess, GenerationStep.Carving worldgenstage_features) {
        BiomeManager biomemanager1 = biomemanager.withDifferentSource(this.biomeSource);
        WorldgenRandom seededrandom = new WorldgenRandom();
        boolean flag = true;
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();
        int j = chunkcoordintpair.x;
        int k = chunkcoordintpair.z;
        Biome biomebase = this.biomeSource.getNoiseBiome(chunkcoordintpair.x << 2, 0, chunkcoordintpair.z << 2);
        BitSet bitset = ((ProtoChunk) ichunkaccess).getOrCreateCarvingMask(worldgenstage_features);

        for (int l = j - 8; l <= j + 8; ++l) {
            for (int i1 = k - 8; i1 <= k + 8; ++i1) {
                List<ConfiguredWorldCarver<?>> list = biomebase.getCarvers(worldgenstage_features);
                ListIterator listiterator = list.listIterator();

                while (listiterator.hasNext()) {
                    int j1 = listiterator.nextIndex();
                    ConfiguredWorldCarver<?> worldgencarverwrapper = (ConfiguredWorldCarver) listiterator.next();

                    seededrandom.setLargeFeatureSeed(i + (long) j1, l, i1);
                    if (worldgencarverwrapper.isStartChunk(seededrandom, l, i1)) {
                        worldgencarverwrapper.carve(ichunkaccess, biomemanager1::getBiome, seededrandom, this.getSeaLevel(), l, i1, j, k, bitset);
                    }
                }
            }
        }

    }

    @Nullable
    public BlockPos findNearestMapFeature(ServerLevel worldserver, StructureFeature<?> structuregenerator, BlockPos blockposition, int i, boolean flag) {
        if (!this.biomeSource.canGenerateStructure(structuregenerator)) {
            return null;
        } else if (structuregenerator == StructureFeature.STRONGHOLD) {
            this.generateStrongholds();
            BlockPos blockposition1 = null;
            double d0 = Double.MAX_VALUE;
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
            Iterator iterator = this.strongholdPositions.iterator();

            while (iterator.hasNext()) {
                ChunkPos chunkcoordintpair = (ChunkPos) iterator.next();

                blockposition_mutableblockposition.d((chunkcoordintpair.x << 4) + 8, 32, (chunkcoordintpair.z << 4) + 8);
                double d1 = blockposition_mutableblockposition.distSqr(blockposition);

                if (blockposition1 == null) {
                    blockposition1 = new BlockPos(blockposition_mutableblockposition);
                    d0 = d1;
                } else if (d1 < d0) {
                    blockposition1 = new BlockPos(blockposition_mutableblockposition);
                    d0 = d1;
                }
            }

            return blockposition1;
        } else {
            updateStructureSettings(worldserver, settings); // Spigot
            return structuregenerator.getNearestGeneratedFeature(worldserver, worldserver.getStructureManager(), blockposition, i, flag, worldserver.getSeed(), this.settings.getConfig(structuregenerator));
        }
    }

    public void addDecorations(WorldGenRegion regionlimitedworldaccess, StructureFeatureManager structuremanager) {
        int i = regionlimitedworldaccess.getCenterX();
        int j = regionlimitedworldaccess.getCenterZ();
        int k = i * 16;
        int l = j * 16;
        BlockPos blockposition = new BlockPos(k, 0, l);
        Biome biomebase = this.biomeSource.getNoiseBiome((i << 2) + 2, 2, (j << 2) + 2);
        WorldgenRandom seededrandom = new WorldgenRandom();
        long i1 = seededrandom.setDecorationSeed(regionlimitedworldaccess.getSeed(), k, l);
        GenerationStep.Decoration[] aworldgenstage_decoration = GenerationStep.Decoration.values();
        int j1 = aworldgenstage_decoration.length;

        for (int k1 = 0; k1 < j1; ++k1) {
            GenerationStep.Decoration worldgenstage_decoration = aworldgenstage_decoration[k1];

            try {
                biomebase.generate(worldgenstage_decoration, structuremanager, this, regionlimitedworldaccess, i1, seededrandom, blockposition);
            } catch (Exception exception) {
                CrashReport crashreport = CrashReport.forThrowable(exception, "Biome decoration");

                crashreport.addCategory("Generation").setDetail("CenterX", (Object) i).setDetail("CenterZ", (Object) j).setDetail("Step", (Object) worldgenstage_decoration).setDetail("Seed", (Object) i1).setDetail("Biome", (Object) Registry.BIOME.getKey(biomebase));
                throw new ReportedException(crashreport);
            }
        }

    }

    public abstract void buildBase(WorldGenRegion regionlimitedworldaccess, ChunkAccess ichunkaccess);

    public void addMobs(WorldGenRegion regionlimitedworldaccess) {}

    public StructureSettings getSettings() {
        return this.settings;
    }

    public int getSpawnHeight() {
        return 64;
    }

    public BiomeSource getWorldChunkManager() {
        return this.runtimeBiomeSource;
    }

    public int getGenDepth() {
        return 256;
    }

    public List<Biome.BiomeMeta> getMobsFor(Biome biomebase, StructureFeatureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        return biomebase.getMobs(enumcreaturetype);
    }

    public void createStructures(StructureFeatureManager structuremanager, ChunkAccess ichunkaccess, StructureManager definedstructuremanager, long i) {
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();
        Biome biomebase = this.biomeSource.getNoiseBiome((chunkcoordintpair.x << 2) + 2, 0, (chunkcoordintpair.z << 2) + 2);

        this.createStructure(BiomeDefaultFeatures.STRONGHOLD, structuremanager, ichunkaccess, definedstructuremanager, i, chunkcoordintpair, biomebase);
        Iterator iterator = biomebase.structures().iterator();

        while (iterator.hasNext()) {
            ConfiguredStructureFeature<?, ?> structurefeature = (ConfiguredStructureFeature) iterator.next();

            // CraftBukkit start
            if (structurefeature.feature == StructureFeature.STRONGHOLD) {
                synchronized (structurefeature) {
                    this.createStructure(structurefeature, structuremanager, ichunkaccess, definedstructuremanager, i, chunkcoordintpair, biomebase);
                }
            } else
            // CraftBukkit end
            this.createStructure(structurefeature, structuremanager, ichunkaccess, definedstructuremanager, i, chunkcoordintpair, biomebase);
        }

    }

    private void createStructure(ConfiguredStructureFeature<?, ?> structurefeature, StructureFeatureManager structuremanager, ChunkAccess ichunkaccess, StructureManager definedstructuremanager, long i, ChunkPos chunkcoordintpair, Biome biomebase) {
        StructureStart<?> structurestart = structuremanager.getStartForFeature(SectionPos.of(ichunkaccess.getPos(), 0), structurefeature.feature, ichunkaccess);
        int j = structurestart != null ? structurestart.getReferences() : 0;
        updateStructureSettings(structuremanager.getWorld(), settings); // Spigot
        StructureStart<?> structurestart1 = structurefeature.generate(this, this.biomeSource, definedstructuremanager, i, chunkcoordintpair, biomebase, j, this.settings.getConfig(structurefeature.feature));

        structuremanager.setStartForFeature(SectionPos.of(ichunkaccess.getPos(), 0), structurefeature.feature, structurestart1, ichunkaccess);
    }

    // Spigot start
    private volatile boolean injected;
    private void updateStructureSettings(Level world, StructureSettings settings) {
        if (injected) {
            return;
        }
        synchronized (settings) {
            if (injected) {
                return;
            }
            java.util.Map<StructureFeature<?>, StructureFeatureConfiguration> original = settings.structureConfig();
            java.util.Map<StructureFeature<?>, StructureFeatureConfiguration> updated = new java.util.HashMap<>();
            org.spigotmc.SpigotWorldConfig conf = world.spigotConfig;

            for (java.util.Map.Entry<StructureFeature<?>, StructureFeatureConfiguration> entry : original.entrySet()) {
                String name = Registry.STRUCTURE_FEATURE.getKey(entry.getKey()).getPath();
                StructureFeatureConfiguration feature = entry.getValue();
                int seed = feature.salt();

                switch (name) {
                    case "bastion_remnant":
                        seed = conf.bastionSeed;
                        break;
                    case "desert_pyramid":
                        seed = conf.desertSeed;
                        break;
                    case "endcity":
                        seed = conf.endCitySeed;
                        break;
                    case "fortress":
                        seed = conf.fortressSeed;
                        break;
                    case "igloo":
                        seed = conf.iglooSeed;
                        break;
                    case "jungle_pyramid":
                        seed = conf.jungleSeed;
                        break;
                    case "mansion":
                        seed = conf.mansionSeed;
                        break;
                    case "monument":
                        seed = conf.monumentSeed;
                        break;
                    case "nether_fossil":
                        seed = conf.fossilSeed;
                        break;
                    case "ocean_ruin":
                        seed = conf.oceanSeed;
                        break;
                    case "pillager_outpost":
                        seed = conf.outpostSeed;
                        break;
                    case "ruined_portal":
                        seed = conf.portalSeed;
                        break;
                    case "shipwreck":
                        seed = conf.shipwreckSeed;
                        break;
                    case "swamp_hut":
                        seed = conf.swampSeed;
                        break;
                    case "village":
                        seed = conf.villageSeed;
                        break;
                }

                updated.put(entry.getKey(), new StructureFeatureConfiguration(feature.spacing(), feature.separation(), seed));
            }

            original.clear();
            original.putAll(updated);
            injected = true;
        }
    }
    // Spigot end

    public void storeStructures(LevelAccessor generatoraccess, StructureFeatureManager structuremanager, ChunkAccess ichunkaccess) {
        boolean flag = true;
        int i = ichunkaccess.getPos().x;
        int j = ichunkaccess.getPos().z;
        int k = i << 4;
        int l = j << 4;
        SectionPos sectionposition = SectionPos.of(ichunkaccess.getPos(), 0);

        for (int i1 = i - 8; i1 <= i + 8; ++i1) {
            for (int j1 = j - 8; j1 <= j + 8; ++j1) {
                long k1 = ChunkPos.asLong(i1, j1);
                Iterator iterator = generatoraccess.getChunk(i1, j1).getAllStarts().values().iterator();

                while (iterator.hasNext()) {
                    StructureStart structurestart = (StructureStart) iterator.next();

                    try {
                        if (structurestart != StructureStart.INVALID_START && structurestart.getBoundingBox().intersects(k, l, k + 15, l + 15)) {
                            structuremanager.addReferenceForFeature(sectionposition, structurestart.getFeature(), k1, ichunkaccess);
                            DebugPackets.sendStructurePacket(generatoraccess, structurestart);
                        }
                    } catch (Exception exception) {
                        CrashReport crashreport = CrashReport.forThrowable(exception, "Generating structure reference");
                        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Structure");

                        crashreportsystemdetails.setDetail("Id", () -> {
                            return Registry.STRUCTURE_FEATURE.getKey(structurestart.getFeature()).toString();
                        });
                        crashreportsystemdetails.setDetail("Name", () -> {
                            return structurestart.getFeature().getFeatureName();
                        });
                        crashreportsystemdetails.setDetail("Class", () -> {
                            return structurestart.getFeature().getClass().getCanonicalName();
                        });
                        throw new ReportedException(crashreport);
                    }
                }
            }
        }

    }

    public abstract void buildNoise(LevelAccessor generatoraccess, StructureFeatureManager structuremanager, ChunkAccess ichunkaccess);

    public int getSeaLevel() {
        return 63;
    }

    public abstract int getBaseHeight(int i, int j, Heightmap.Types heightmap_type);

    public abstract BlockGetter getBaseColumn(int i, int j);

    public int getFirstFreeHeight(int i, int j, Heightmap.Types heightmap_type) {
        return this.getBaseHeight(i, j, heightmap_type);
    }

    public int getFirstOccupiedHeight(int i, int j, Heightmap.Types heightmap_type) {
        return this.getBaseHeight(i, j, heightmap_type) - 1;
    }

    public boolean hasStronghold(ChunkPos chunkcoordintpair) {
        this.generateStrongholds();
        return this.strongholdPositions.contains(chunkcoordintpair);
    }

    static {
        // CraftBukkit start - decompile errors
        Registry.register(Registry.CHUNK_GENERATOR, "noise", NoiseBasedChunkGenerator.CODEC);
        Registry.register(Registry.CHUNK_GENERATOR, "flat", FlatLevelSource.CODEC);
        Registry.register(Registry.CHUNK_GENERATOR, "debug", DebugLevelSource.CODEC);
        // CraftBukkit end
        CODEC = Registry.CHUNK_GENERATOR.dispatchStable(ChunkGenerator::codec, Function.identity());
    }
}
