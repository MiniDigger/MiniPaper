package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.structures.JigsawJunction;
import net.minecraft.world.level.levelgen.feature.structures.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.levelgen.synth.SurfaceNoise;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {

    public static final Codec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter((chunkgeneratorabstract) -> {
            return chunkgeneratorabstract.biomeSource;
        }), Codec.LONG.fieldOf("seed").stable().forGetter((chunkgeneratorabstract) -> {
            return chunkgeneratorabstract.seed;
        }), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter((chunkgeneratorabstract) -> {
            return chunkgeneratorabstract.settings;
        })).apply(instance, instance.stable(NoiseBasedChunkGenerator::new));
    });
    private static final float[] BEARD_KERNEL = (float[]) Util.make((new float[13824]), (afloat) -> { // CraftBukkit - decompile error
        for (int i = 0; i < 24; ++i) {
            for (int j = 0; j < 24; ++j) {
                for (int k = 0; k < 24; ++k) {
                    afloat[i * 24 * 24 + j * 24 + k] = (float) computeContribution(j - 12, k - 12, i - 12);
                }
            }
        }

    });
    private static final float[] BIOME_WEIGHTS = (float[]) Util.make((new float[25]), (afloat) -> { // CraftBukkit - decompile error
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                float f = 10.0F / Mth.sqrt((float) (i * i + j * j) + 0.2F);

                afloat[i + 2 + (j + 2) * 5] = f;
            }
        }

    });
    private static final BlockState AIR = Blocks.AIR.getBlockData();
    private final int chunkHeight;
    private final int chunkWidth;
    private final int chunkCountX;
    private final int chunkCountY;
    private final int chunkCountZ;
    protected final WorldgenRandom random;
    private final PerlinNoise minLimitPerlinNoise;
    private final PerlinNoise maxLimitPerlinNoise;
    private final PerlinNoise mainPerlinNoise;
    private final SurfaceNoise surfaceNoise;
    private final PerlinNoise depthNoise;
    @Nullable
    private final SimplexNoise islandNoise;
    protected final BlockState defaultBlock;
    protected final BlockState defaultFluid;
    private final long seed;
    protected final NoiseGeneratorSettings settings;
    private final int height;

    public NoiseBasedChunkGenerator(BiomeSource worldchunkmanager, long i, NoiseGeneratorSettings generatorsettingbase) {
        this(worldchunkmanager, worldchunkmanager, i, generatorsettingbase);
    }

    private NoiseBasedChunkGenerator(BiomeSource worldchunkmanager, BiomeSource worldchunkmanager1, long i, NoiseGeneratorSettings generatorsettingbase) {
        super(worldchunkmanager, worldchunkmanager1, generatorsettingbase.structureSettings(), i);
        this.seed = i;
        this.settings = generatorsettingbase;
        NoiseSettings noisesettings = generatorsettingbase.noiseSettings();

        this.height = noisesettings.height();
        this.chunkHeight = noisesettings.noiseSizeVertical() * 4;
        this.chunkWidth = noisesettings.noiseSizeHorizontal() * 4;
        this.defaultBlock = generatorsettingbase.getDefaultBlock();
        this.defaultFluid = generatorsettingbase.getDefaultFluid();
        this.chunkCountX = 16 / this.chunkWidth;
        this.chunkCountY = noisesettings.height() / this.chunkHeight;
        this.chunkCountZ = 16 / this.chunkWidth;
        this.random = new WorldgenRandom(i);
        this.minLimitPerlinNoise = new PerlinNoise(this.random, IntStream.rangeClosed(-15, 0));
        this.maxLimitPerlinNoise = new PerlinNoise(this.random, IntStream.rangeClosed(-15, 0));
        this.mainPerlinNoise = new PerlinNoise(this.random, IntStream.rangeClosed(-7, 0));
        this.surfaceNoise = (SurfaceNoise) (noisesettings.useSimplexSurfaceNoise() ? new PerlinSimplexNoise(this.random, IntStream.rangeClosed(-3, 0)) : new PerlinNoise(this.random, IntStream.rangeClosed(-3, 0)));
        this.random.consumeCount(2620);
        this.depthNoise = new PerlinNoise(this.random, IntStream.rangeClosed(-15, 0));
        if (noisesettings.islandNoiseOverride()) {
            WorldgenRandom seededrandom = new WorldgenRandom(i);

            seededrandom.consumeCount(17292);
            this.islandNoise = new SimplexNoise(seededrandom);
        } else {
            this.islandNoise = null;
        }

    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return NoiseBasedChunkGenerator.CODEC;
    }

    public boolean stable(long i, NoiseGeneratorSettings.Preset generatorsettingbase_a) {
        return this.seed == i && this.settings.stable(generatorsettingbase_a);
    }

    private double sampleAndClampNoise(int i, int j, int k, double d0, double d1, double d2, double d3) {
        double d4 = 0.0D;
        double d5 = 0.0D;
        double d6 = 0.0D;
        boolean flag = true;
        double d7 = 1.0D;

        for (int l = 0; l < 16; ++l) {
            double d8 = PerlinNoise.wrap((double) i * d0 * d7);
            double d9 = PerlinNoise.wrap((double) j * d1 * d7);
            double d10 = PerlinNoise.wrap((double) k * d0 * d7);
            double d11 = d1 * d7;
            ImprovedNoise noisegeneratorperlin = this.minLimitPerlinNoise.getOctaveNoise(l);

            if (noisegeneratorperlin != null) {
                d4 += noisegeneratorperlin.noise(d8, d9, d10, d11, (double) j * d11) / d7;
            }

            ImprovedNoise noisegeneratorperlin1 = this.maxLimitPerlinNoise.getOctaveNoise(l);

            if (noisegeneratorperlin1 != null) {
                d5 += noisegeneratorperlin1.noise(d8, d9, d10, d11, (double) j * d11) / d7;
            }

            if (l < 8) {
                ImprovedNoise noisegeneratorperlin2 = this.mainPerlinNoise.getOctaveNoise(l);

                if (noisegeneratorperlin2 != null) {
                    d6 += noisegeneratorperlin2.noise(PerlinNoise.wrap((double) i * d2 * d7), PerlinNoise.wrap((double) j * d3 * d7), PerlinNoise.wrap((double) k * d2 * d7), d3 * d7, (double) j * d3 * d7) / d7;
                }
            }

            d7 /= 2.0D;
        }

        return Mth.clampedLerp(d4 / 512.0D, d5 / 512.0D, (d6 / 10.0D + 1.0D) / 2.0D);
    }

    private double[] makeAndFillNoiseColumn(int i, int j) {
        double[] adouble = new double[this.chunkCountY + 1];

        this.fillNoiseColumn(adouble, i, j);
        return adouble;
    }

    private void fillNoiseColumn(double[] adouble, int i, int j) {
        NoiseSettings noisesettings = this.settings.noiseSettings();
        double d0;
        double d1;
        double d2;
        double d3;

        if (this.islandNoise != null) {
            d0 = (double) (TheEndBiomeSource.getHeightValue(this.islandNoise, i, j) - 8.0F);
            if (d0 > 0.0D) {
                d1 = 0.25D;
            } else {
                d1 = 1.0D;
            }
        } else {
            float f = 0.0F;
            float f1 = 0.0F;
            float f2 = 0.0F;
            boolean flag = true;
            int k = this.getSeaLevel();
            float f3 = this.biomeSource.getNoiseBiome(i, k, j).getDepth();

            for (int l = -2; l <= 2; ++l) {
                for (int i1 = -2; i1 <= 2; ++i1) {
                    Biome biomebase = this.biomeSource.getNoiseBiome(i + l, k, j + i1);
                    float f4 = biomebase.getDepth();
                    float f5 = biomebase.getScale();
                    float f6;
                    float f7;

                    if (noisesettings.isAmplified() && f4 > 0.0F) {
                        f6 = 1.0F + f4 * 2.0F;
                        f7 = 1.0F + f5 * 4.0F;
                    } else {
                        f6 = f4;
                        f7 = f5;
                    }
                    // CraftBukkit start - fix MC-54738
                    if (f6 < -1.8F) {
                        f6 = -1.8F;
                    }
                    // CraftBukkit end

                    float f8 = f4 > f3 ? 0.5F : 1.0F;
                    float f9 = f8 * NoiseBasedChunkGenerator.BIOME_WEIGHTS[l + 2 + (i1 + 2) * 5] / (f6 + 2.0F);

                    f += f7 * f9;
                    f1 += f6 * f9;
                    f2 += f9;
                }
            }

            float f10 = f1 / f2;
            float f11 = f / f2;

            d2 = (double) (f10 * 0.5F - 0.125F);
            d3 = (double) (f11 * 0.9F + 0.1F);
            d0 = d2 * 0.265625D;
            d1 = 96.0D / d3;
        }

        double d4 = 684.412D * noisesettings.noiseSamplingSettings().xzScale();
        double d5 = 684.412D * noisesettings.noiseSamplingSettings().yScale();
        double d6 = d4 / noisesettings.noiseSamplingSettings().xzFactor();
        double d7 = d5 / noisesettings.noiseSamplingSettings().yFactor();

        d2 = (double) noisesettings.topSlideSettings().target();
        d3 = (double) noisesettings.topSlideSettings().size();
        double d8 = (double) noisesettings.topSlideSettings().offset();
        double d9 = (double) noisesettings.bottomSlideSettings().target();
        double d10 = (double) noisesettings.bottomSlideSettings().size();
        double d11 = (double) noisesettings.bottomSlideSettings().offset();
        double d12 = noisesettings.randomDensityOffset() ? this.getRandomDensity(i, j) : 0.0D;
        double d13 = noisesettings.densityFactor();
        double d14 = noisesettings.densityOffset();

        for (int j1 = 0; j1 <= this.chunkCountY; ++j1) {
            double d15 = this.sampleAndClampNoise(i, j1, j, d4, d5, d6, d7);
            double d16 = 1.0D - (double) j1 * 2.0D / (double) this.chunkCountY + d12;
            double d17 = d16 * d13 + d14;
            double d18 = (d17 + d0) * d1;

            if (d18 > 0.0D) {
                d15 += d18 * 4.0D;
            } else {
                d15 += d18;
            }

            double d19;

            if (d3 > 0.0D) {
                d19 = ((double) (this.chunkCountY - j1) - d8) / d3;
                d15 = Mth.clampedLerp(d2, d15, d19);
            }

            if (d10 > 0.0D) {
                d19 = ((double) j1 - d11) / d10;
                d15 = Mth.clampedLerp(d9, d15, d19);
            }

            adouble[j1] = d15;
        }

    }

    private double getRandomDensity(int i, int j) {
        double d0 = this.depthNoise.getValue((double) (i * 200), 10.0D, (double) (j * 200), 1.0D, 0.0D, true);
        double d1;

        if (d0 < 0.0D) {
            d1 = -d0 * 0.3D;
        } else {
            d1 = d0;
        }

        double d2 = d1 * 24.575625D - 2.0D;

        return d2 < 0.0D ? d2 * 0.009486607142857142D : Math.min(d2, 1.0D) * 0.006640625D;
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types heightmap_type) {
        return this.iterateNoiseColumn(i, j, (BlockState[]) null, heightmap_type.isOpaque());
    }

    @Override
    public BlockGetter getBaseColumn(int i, int j) {
        BlockState[] aiblockdata = new BlockState[this.chunkCountY * this.chunkHeight];

        this.iterateNoiseColumn(i, j, aiblockdata, (Predicate) null);
        return new NoiseColumn(aiblockdata);
    }

    private int iterateNoiseColumn(int i, int j, @Nullable BlockState[] aiblockdata, @Nullable Predicate<BlockState> predicate) {
        int k = Math.floorDiv(i, this.chunkWidth);
        int l = Math.floorDiv(j, this.chunkWidth);
        int i1 = Math.floorMod(i, this.chunkWidth);
        int j1 = Math.floorMod(j, this.chunkWidth);
        double d0 = (double) i1 / (double) this.chunkWidth;
        double d1 = (double) j1 / (double) this.chunkWidth;
        double[][] adouble = new double[][]{this.makeAndFillNoiseColumn(k, l), this.makeAndFillNoiseColumn(k, l + 1), this.makeAndFillNoiseColumn(k + 1, l), this.makeAndFillNoiseColumn(k + 1, l + 1)};

        for (int k1 = this.chunkCountY - 1; k1 >= 0; --k1) {
            double d2 = adouble[0][k1];
            double d3 = adouble[1][k1];
            double d4 = adouble[2][k1];
            double d5 = adouble[3][k1];
            double d6 = adouble[0][k1 + 1];
            double d7 = adouble[1][k1 + 1];
            double d8 = adouble[2][k1 + 1];
            double d9 = adouble[3][k1 + 1];

            for (int l1 = this.chunkHeight - 1; l1 >= 0; --l1) {
                double d10 = (double) l1 / (double) this.chunkHeight;
                double d11 = Mth.lerp3(d10, d0, d1, d2, d6, d4, d8, d3, d7, d5, d9);
                int i2 = k1 * this.chunkHeight + l1;
                BlockState iblockdata = this.generateBaseState(d11, i2);

                if (aiblockdata != null) {
                    aiblockdata[i2] = iblockdata;
                }

                if (predicate != null && predicate.test(iblockdata)) {
                    return i2 + 1;
                }
            }
        }

        return 0;
    }

    protected BlockState generateBaseState(double d0, int i) {
        BlockState iblockdata;

        if (d0 > 0.0D) {
            iblockdata = this.defaultBlock;
        } else if (i < this.getSeaLevel()) {
            iblockdata = this.defaultFluid;
        } else {
            iblockdata = NoiseBasedChunkGenerator.AIR;
        }

        return iblockdata;
    }

    @Override
    public void buildBase(WorldGenRegion regionlimitedworldaccess, ChunkAccess ichunkaccess) {
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();
        int i = chunkcoordintpair.x;
        int j = chunkcoordintpair.z;
        WorldgenRandom seededrandom = new WorldgenRandom();

        seededrandom.setBaseChunkSeed(i, j);
        ChunkPos chunkcoordintpair1 = ichunkaccess.getPos();
        int k = chunkcoordintpair1.getMinBlockX();
        int l = chunkcoordintpair1.getMinBlockZ();
        double d0 = 0.0625D;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        for (int i1 = 0; i1 < 16; ++i1) {
            for (int j1 = 0; j1 < 16; ++j1) {
                int k1 = k + i1;
                int l1 = l + j1;
                int i2 = ichunkaccess.getHeight(Heightmap.Types.WORLD_SURFACE_WG, i1, j1) + 1;
                double d1 = this.surfaceNoise.getSurfaceNoiseValue((double) k1 * 0.0625D, (double) l1 * 0.0625D, 0.0625D, (double) i1 * 0.0625D) * 15.0D;

                regionlimitedworldaccess.getBiome(blockposition_mutableblockposition.d(k + i1, i2, l + j1)).buildSurfaceAt(seededrandom, ichunkaccess, k1, l1, i2, d1, this.defaultBlock, this.defaultFluid, this.getSeaLevel(), regionlimitedworldaccess.getSeed());
            }
        }

        this.setBedrock(ichunkaccess, seededrandom);
    }

    private void setBedrock(ChunkAccess ichunkaccess, Random random) {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        int i = ichunkaccess.getPos().getMinBlockX();
        int j = ichunkaccess.getPos().getMinBlockZ();
        int k = this.settings.getBedrockFloorPosition();
        int l = this.height - 1 - this.settings.getBedrockRoofPosition();
        boolean flag = true;
        boolean flag1 = l + 4 >= 0 && l < this.height;
        boolean flag2 = k + 4 >= 0 && k < this.height;

        if (flag1 || flag2) {
            Iterator iterator = BlockPos.betweenClosed(i, 0, j, i + 15, 0, j + 15).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition = (BlockPos) iterator.next();
                int i1;

                if (flag1) {
                    for (i1 = 0; i1 < 5; ++i1) {
                        if (i1 <= random.nextInt(5)) {
                            ichunkaccess.setType(blockposition_mutableblockposition.d(blockposition.getX(), l - i1, blockposition.getZ()), Blocks.BEDROCK.getBlockData(), false);
                        }
                    }
                }

                if (flag2) {
                    for (i1 = 4; i1 >= 0; --i1) {
                        if (i1 <= random.nextInt(5)) {
                            ichunkaccess.setType(blockposition_mutableblockposition.d(blockposition.getX(), k + i1, blockposition.getZ()), Blocks.BEDROCK.getBlockData(), false);
                        }
                    }
                }
            }

        }
    }

    @Override
    public void buildNoise(LevelAccessor generatoraccess, StructureFeatureManager structuremanager, ChunkAccess ichunkaccess) {
        ObjectList<StructurePiece> objectlist = new ObjectArrayList(10);
        ObjectList<JigsawJunction> objectlist1 = new ObjectArrayList(32);
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();
        int i = chunkcoordintpair.x;
        int j = chunkcoordintpair.z;
        int k = i << 4;
        int l = j << 4;
        Iterator iterator = StructureFeature.NOISE_AFFECTING_FEATURES.iterator();

        while (iterator.hasNext()) {
            StructureFeature<?> structuregenerator = (StructureFeature) iterator.next();

            structuremanager.startsForFeature(SectionPos.of(chunkcoordintpair, 0), structuregenerator).forEach((structurestart) -> {
                Iterator iterator1 = structurestart.getPieces().iterator();

                while (iterator1.hasNext()) {
                    StructurePiece structurepiece = (StructurePiece) iterator1.next();

                    if (structurepiece.isCloseToChunk(chunkcoordintpair, 12)) {
                        if (structurepiece instanceof PoolElementStructurePiece) {
                            PoolElementStructurePiece worldgenfeaturepillageroutpostpoolpiece = (PoolElementStructurePiece) structurepiece;
                            StructureTemplatePool.Projection worldgenfeaturedefinedstructurepooltemplate_matching = worldgenfeaturepillageroutpostpoolpiece.getElement().getProjection();

                            if (worldgenfeaturedefinedstructurepooltemplate_matching == StructureTemplatePool.Projection.RIGID) {
                                objectlist.add(worldgenfeaturepillageroutpostpoolpiece);
                            }

                            Iterator iterator2 = worldgenfeaturepillageroutpostpoolpiece.getJunctions().iterator();

                            while (iterator2.hasNext()) {
                                JigsawJunction worldgenfeaturedefinedstructurejigsawjunction = (JigsawJunction) iterator2.next();
                                int i1 = worldgenfeaturedefinedstructurejigsawjunction.getSourceX();
                                int j1 = worldgenfeaturedefinedstructurejigsawjunction.getSourceZ();

                                if (i1 > k - 12 && j1 > l - 12 && i1 < k + 15 + 12 && j1 < l + 15 + 12) {
                                    objectlist1.add(worldgenfeaturedefinedstructurejigsawjunction);
                                }
                            }
                        } else {
                            objectlist.add(structurepiece);
                        }
                    }
                }

            });
        }

        double[][][] adouble = new double[2][this.chunkCountZ + 1][this.chunkCountY + 1];

        for (int i1 = 0; i1 < this.chunkCountZ + 1; ++i1) {
            adouble[0][i1] = new double[this.chunkCountY + 1];
            this.fillNoiseColumn(adouble[0][i1], i * this.chunkCountX, j * this.chunkCountZ + i1);
            adouble[1][i1] = new double[this.chunkCountY + 1];
        }

        ProtoChunk protochunk = (ProtoChunk) ichunkaccess;
        Heightmap heightmap = protochunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = protochunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        ObjectListIterator<StructurePiece> objectlistiterator = objectlist.iterator();
        ObjectListIterator<JigsawJunction> objectlistiterator1 = objectlist1.iterator();

        for (int j1 = 0; j1 < this.chunkCountX; ++j1) {
            int k1;

            for (k1 = 0; k1 < this.chunkCountZ + 1; ++k1) {
                this.fillNoiseColumn(adouble[1][k1], i * this.chunkCountX + j1 + 1, j * this.chunkCountZ + k1);
            }

            for (k1 = 0; k1 < this.chunkCountZ; ++k1) {
                LevelChunkSection chunksection = protochunk.getOrCreateSection(15);

                chunksection.acquire();

                for (int l1 = this.chunkCountY - 1; l1 >= 0; --l1) {
                    double d0 = adouble[0][k1][l1];
                    double d1 = adouble[0][k1 + 1][l1];
                    double d2 = adouble[1][k1][l1];
                    double d3 = adouble[1][k1 + 1][l1];
                    double d4 = adouble[0][k1][l1 + 1];
                    double d5 = adouble[0][k1 + 1][l1 + 1];
                    double d6 = adouble[1][k1][l1 + 1];
                    double d7 = adouble[1][k1 + 1][l1 + 1];

                    for (int i2 = this.chunkHeight - 1; i2 >= 0; --i2) {
                        int j2 = l1 * this.chunkHeight + i2;
                        int k2 = j2 & 15;
                        int l2 = j2 >> 4;

                        if (chunksection.bottomBlockY() >> 4 != l2) {
                            chunksection.release();
                            chunksection = protochunk.getOrCreateSection(l2);
                            chunksection.acquire();
                        }

                        double d8 = (double) i2 / (double) this.chunkHeight;
                        double d9 = Mth.lerp(d8, d0, d4);
                        double d10 = Mth.lerp(d8, d2, d6);
                        double d11 = Mth.lerp(d8, d1, d5);
                        double d12 = Mth.lerp(d8, d3, d7);

                        for (int i3 = 0; i3 < this.chunkWidth; ++i3) {
                            int j3 = k + j1 * this.chunkWidth + i3;
                            int k3 = j3 & 15;
                            double d13 = (double) i3 / (double) this.chunkWidth;
                            double d14 = Mth.lerp(d13, d9, d10);
                            double d15 = Mth.lerp(d13, d11, d12);

                            for (int l3 = 0; l3 < this.chunkWidth; ++l3) {
                                int i4 = l + k1 * this.chunkWidth + l3;
                                int j4 = i4 & 15;
                                double d16 = (double) l3 / (double) this.chunkWidth;
                                double d17 = Mth.lerp(d16, d14, d15);
                                double d18 = Mth.clamp(d17 / 200.0D, -1.0D, 1.0D);

                                int k4;
                                int l4;
                                int i5;

                                for (d18 = d18 / 2.0D - d18 * d18 * d18 / 24.0D; objectlistiterator.hasNext(); d18 += getContribution(k4, l4, i5) * 0.8D) {
                                    StructurePiece structurepiece = (StructurePiece) objectlistiterator.next();
                                    BoundingBox structureboundingbox = structurepiece.getBoundingBox();

                                    k4 = Math.max(0, Math.max(structureboundingbox.x0 - j3, j3 - structureboundingbox.x1));
                                    l4 = j2 - (structureboundingbox.y0 + (structurepiece instanceof PoolElementStructurePiece ? ((PoolElementStructurePiece) structurepiece).getGroundLevelDelta() : 0));
                                    i5 = Math.max(0, Math.max(structureboundingbox.z0 - i4, i4 - structureboundingbox.z1));
                                }

                                objectlistiterator.back(objectlist.size());

                                while (objectlistiterator1.hasNext()) {
                                    JigsawJunction worldgenfeaturedefinedstructurejigsawjunction = (JigsawJunction) objectlistiterator1.next();
                                    int j5 = j3 - worldgenfeaturedefinedstructurejigsawjunction.getSourceX();

                                    k4 = j2 - worldgenfeaturedefinedstructurejigsawjunction.getSourceGroundY();
                                    l4 = i4 - worldgenfeaturedefinedstructurejigsawjunction.getSourceZ();
                                    d18 += getContribution(j5, k4, l4) * 0.4D;
                                }

                                objectlistiterator1.back(objectlist1.size());
                                BlockState iblockdata = this.generateBaseState(d18, j2);

                                if (iblockdata != NoiseBasedChunkGenerator.AIR) {
                                    if (iblockdata.getLightEmission() != 0) {
                                        blockposition_mutableblockposition.d(j3, j2, i4);
                                        protochunk.addLight(blockposition_mutableblockposition);
                                    }

                                    chunksection.setType(k3, k2, j4, iblockdata, false);
                                    heightmap.update(k3, j2, j4, iblockdata);
                                    heightmap1.update(k3, j2, j4, iblockdata);
                                }
                            }
                        }
                    }
                }

                chunksection.release();
            }

            double[][] adouble1 = adouble[0];

            adouble[0] = adouble[1];
            adouble[1] = adouble1;
        }

    }

    private static double getContribution(int i, int j, int k) {
        int l = i + 12;
        int i1 = j + 12;
        int j1 = k + 12;

        return l >= 0 && l < 24 ? (i1 >= 0 && i1 < 24 ? (j1 >= 0 && j1 < 24 ? (double) NoiseBasedChunkGenerator.BEARD_KERNEL[j1 * 24 * 24 + l * 24 + i1] : 0.0D) : 0.0D) : 0.0D;
    }

    private static double computeContribution(int i, int j, int k) {
        double d0 = (double) (i * i + k * k);
        double d1 = (double) j + 0.5D;
        double d2 = d1 * d1;
        double d3 = Math.pow(2.718281828459045D, -(d2 / 16.0D + d0 / 16.0D));
        double d4 = -d1 * Mth.fastInvSqrt(d2 / 2.0D + d0 / 2.0D) / 2.0D;

        return d4 * d3;
    }

    @Override
    public int getGenDepth() {
        return this.height;
    }

    @Override
    public int getSeaLevel() {
        return this.settings.seaLevel();
    }

    @Override
    public List<Biome.BiomeMeta> getMobsFor(Biome biomebase, StructureFeatureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        if (structuremanager.getStructureAt(blockposition, true, StructureFeature.SWAMP_HUT).isValid()) {
            if (enumcreaturetype == MobCategory.MONSTER) {
                return StructureFeature.SWAMP_HUT.getSpecialEnemies();
            }

            if (enumcreaturetype == MobCategory.CREATURE) {
                return StructureFeature.SWAMP_HUT.getSpecialAnimals();
            }
        }

        if (enumcreaturetype == MobCategory.MONSTER) {
            if (structuremanager.getStructureAt(blockposition, false, StructureFeature.PILLAGER_OUTPOST).isValid()) {
                return StructureFeature.PILLAGER_OUTPOST.getSpecialEnemies();
            }

            if (structuremanager.getStructureAt(blockposition, false, StructureFeature.OCEAN_MONUMENT).isValid()) {
                return StructureFeature.OCEAN_MONUMENT.getSpecialEnemies();
            }

            if (structuremanager.getStructureAt(blockposition, true, StructureFeature.NETHER_BRIDGE).isValid()) {
                return StructureFeature.NETHER_BRIDGE.getSpecialEnemies();
            }
        }

        return super.getMobsFor(biomebase, structuremanager, enumcreaturetype, blockposition);
    }

    @Override
    public void addMobs(WorldGenRegion regionlimitedworldaccess) {
        if (!this.settings.disableMobGeneration()) {
            int i = regionlimitedworldaccess.getCenterX();
            int j = regionlimitedworldaccess.getCenterZ();
            Biome biomebase = regionlimitedworldaccess.getBiome((new ChunkPos(i, j)).getWorldPosition());
            WorldgenRandom seededrandom = new WorldgenRandom();

            seededrandom.setDecorationSeed(regionlimitedworldaccess.getSeed(), i << 4, j << 4);
            NaturalSpawner.spawnMobsForChunkGeneration(regionlimitedworldaccess, biomebase, i, j, seededrandom);
        }
    }
}
