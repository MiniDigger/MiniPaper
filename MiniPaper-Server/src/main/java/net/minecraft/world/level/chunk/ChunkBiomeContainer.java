package net.minecraft.world.level.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkBiomeContainer implements BiomeManager.NoiseBiomeSource {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int WIDTH_BITS = (int) Math.round(Math.log(16.0D) / Math.log(2.0D)) - 2;
    private static final int HEIGHT_BITS = (int) Math.round(Math.log(256.0D) / Math.log(2.0D)) - 2;
    public static final int BIOMES_SIZE = 1 << ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.HEIGHT_BITS;
    public static final int HORIZONTAL_MASK = (1 << ChunkBiomeContainer.WIDTH_BITS) - 1;
    public static final int VERTICAL_MASK = (1 << ChunkBiomeContainer.HEIGHT_BITS) - 1;
    private final Biome[] biomes;

    public ChunkBiomeContainer(Biome[] abiomebase) {
        this.biomes = abiomebase;
    }

    private ChunkBiomeContainer() {
        this(new Biome[ChunkBiomeContainer.BIOMES_SIZE]);
    }

    public ChunkBiomeContainer(FriendlyByteBuf packetdataserializer) {
        this();

        for (int i = 0; i < this.biomes.length; ++i) {
            int j = packetdataserializer.readInt();
            Biome biomebase = (Biome) Registry.BIOME.byId(j);

            if (biomebase == null) {
                ChunkBiomeContainer.LOGGER.warn("Received invalid biome id: " + j);
                this.biomes[i] = Biomes.PLAINS;
            } else {
                this.biomes[i] = biomebase;
            }
        }

    }

    public ChunkBiomeContainer(ChunkPos chunkcoordintpair, BiomeSource worldchunkmanager) {
        this();
        int i = chunkcoordintpair.getMinBlockX() >> 2;
        int j = chunkcoordintpair.getMinBlockZ() >> 2;

        for (int k = 0; k < this.biomes.length; ++k) {
            int l = k & ChunkBiomeContainer.HORIZONTAL_MASK;
            int i1 = k >> ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.WIDTH_BITS & ChunkBiomeContainer.VERTICAL_MASK;
            int j1 = k >> ChunkBiomeContainer.WIDTH_BITS & ChunkBiomeContainer.HORIZONTAL_MASK;

            this.biomes[k] = worldchunkmanager.getNoiseBiome(i + l, i1, j + j1);
        }

    }

    public ChunkBiomeContainer(ChunkPos chunkcoordintpair, BiomeSource worldchunkmanager, @Nullable int[] aint) {
        this();
        int i = chunkcoordintpair.getMinBlockX() >> 2;
        int j = chunkcoordintpair.getMinBlockZ() >> 2;
        int k;
        int l;
        int i1;
        int j1;

        if (aint != null) {
            for (k = 0; k < aint.length; ++k) {
                this.biomes[k] = (Biome) Registry.BIOME.byId(aint[k]);
                if (this.biomes[k] == null) {
                    l = k & ChunkBiomeContainer.HORIZONTAL_MASK;
                    i1 = k >> ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.WIDTH_BITS & ChunkBiomeContainer.VERTICAL_MASK;
                    j1 = k >> ChunkBiomeContainer.WIDTH_BITS & ChunkBiomeContainer.HORIZONTAL_MASK;
                    this.biomes[k] = worldchunkmanager.getNoiseBiome(i + l, i1, j + j1);
                }
            }
        } else {
            for (k = 0; k < this.biomes.length; ++k) {
                l = k & ChunkBiomeContainer.HORIZONTAL_MASK;
                i1 = k >> ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.WIDTH_BITS & ChunkBiomeContainer.VERTICAL_MASK;
                j1 = k >> ChunkBiomeContainer.WIDTH_BITS & ChunkBiomeContainer.HORIZONTAL_MASK;
                this.biomes[k] = worldchunkmanager.getNoiseBiome(i + l, i1, j + j1);
            }
        }

    }

    public int[] writeBiomes() {
        int[] aint = new int[this.biomes.length];

        for (int i = 0; i < this.biomes.length; ++i) {
            aint[i] = Registry.BIOME.getId(this.biomes[i]); // CraftBukkit - decompile error
        }

        return aint;
    }

    public void write(FriendlyByteBuf packetdataserializer) {
        Biome[] abiomebase = this.biomes;
        int i = abiomebase.length;

        for (int j = 0; j < i; ++j) {
            Biome biomebase = abiomebase[j];

            packetdataserializer.writeInt(Registry.BIOME.getId(biomebase)); // CraftBukkit - decompile error
        }

    }

    public ChunkBiomeContainer copy() {
        return new ChunkBiomeContainer((Biome[]) this.biomes.clone());
    }

    @Override
    public Biome getNoiseBiome(int i, int j, int k) {
        int l = i & ChunkBiomeContainer.HORIZONTAL_MASK;
        int i1 = Mth.clamp(j, 0, ChunkBiomeContainer.VERTICAL_MASK);
        int j1 = k & ChunkBiomeContainer.HORIZONTAL_MASK;

        return this.biomes[i1 << ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.WIDTH_BITS | j1 << ChunkBiomeContainer.WIDTH_BITS | l];
    }

    // CraftBukkit start
    public void setBiome(int i, int j, int k, Biome biome) {
        int l = i & ChunkBiomeContainer.HORIZONTAL_MASK;
        int i1 = Mth.clamp(j, 0, ChunkBiomeContainer.VERTICAL_MASK);
        int j1 = k & ChunkBiomeContainer.HORIZONTAL_MASK;

        this.biomes[i1 << ChunkBiomeContainer.WIDTH_BITS + ChunkBiomeContainer.WIDTH_BITS | j1 << ChunkBiomeContainer.WIDTH_BITS | l] = biome;
    }
    // CraftBukkit end
}
