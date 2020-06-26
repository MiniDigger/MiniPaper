package net.minecraft.world.level;

import com.mojang.datafixers.DataFixUtils;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.FeatureAccess;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class StructureFeatureManager {

    private final LevelAccessor level;
    private final WorldGenSettings worldGenSettings;

    public StructureFeatureManager(LevelAccessor generatoraccess, WorldGenSettings generatorsettings) {
        this.level = generatoraccess;
        this.worldGenSettings = generatorsettings;
    }

    public StructureFeatureManager forWorldGenRegion(WorldGenRegion regionlimitedworldaccess) {
        if (regionlimitedworldaccess.getLevel() != this.level) {
            throw new IllegalStateException("Using invalid feature manager (source level: " + regionlimitedworldaccess.getLevel() + ", region: " + regionlimitedworldaccess);
        } else {
            return new StructureFeatureManager(regionlimitedworldaccess, this.worldGenSettings);
        }
    }

    public Stream<? extends StructureStart<?>> startsForFeature(SectionPos sectionposition, StructureFeature<?> structuregenerator) {
        return this.level.getChunk(sectionposition.x(), sectionposition.z(), ChunkStatus.STRUCTURE_REFERENCES).getReferencesForFeature(structuregenerator).stream().map((olong) -> {
            return SectionPos.of(new ChunkPos(olong), 0);
        }).map((sectionposition1) -> {
            return this.getStartForFeature(sectionposition1, structuregenerator, this.level.getChunk(sectionposition1.x(), sectionposition1.z(), ChunkStatus.STRUCTURE_STARTS));
        }).filter((structurestart) -> {
            return structurestart != null && structurestart.isValid();
        });
    }

    @Nullable
    public StructureStart<?> getStartForFeature(SectionPos sectionposition, StructureFeature<?> structuregenerator, FeatureAccess istructureaccess) {
        return istructureaccess.getStartForFeature(structuregenerator);
    }

    public void setStartForFeature(SectionPos sectionposition, StructureFeature<?> structuregenerator, StructureStart<?> structurestart, FeatureAccess istructureaccess) {
        istructureaccess.setStartForFeature(structuregenerator, structurestart);
    }

    public void addReferenceForFeature(SectionPos sectionposition, StructureFeature<?> structuregenerator, long i, FeatureAccess istructureaccess) {
        istructureaccess.addReferenceForFeature(structuregenerator, i);
    }

    public boolean shouldGenerateFeatures() {
        return this.worldGenSettings.generateFeatures();
    }

    public StructureStart<?> getStructureAt(BlockPos blockposition, boolean flag, StructureFeature<?> structuregenerator) {
        return (StructureStart) DataFixUtils.orElse(this.startsForFeature(SectionPos.of(blockposition), structuregenerator).filter((structurestart) -> {
            return structurestart.getBoundingBox().isInside((Vec3i) blockposition);
        }).filter((structurestart) -> {
            return !flag || structurestart.getPieces().stream().anyMatch((structurepiece) -> {
                return structurepiece.getBoundingBox().isInside((Vec3i) blockposition);
            });
        }).findFirst(), StructureStart.INVALID_START);
    }

    // Spigot start
    public Level getWorld() {
        return this.level.getLevel();
    }
    // Spigot end
}
