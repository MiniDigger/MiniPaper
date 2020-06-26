package net.minecraft.world.level.levelgen.structure;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;

public class SwamplandHutPiece extends ScatteredFeaturePiece {

    private boolean spawnedWitch;
    private boolean spawnedCat;

    public SwamplandHutPiece(Random random, int i, int j) {
        super(StructurePieceType.SWAMPLAND_HUT, random, i, 64, j, 7, 7, 9);
    }

    public SwamplandHutPiece(StructureManager definedstructuremanager, CompoundTag nbttagcompound) {
        super(StructurePieceType.SWAMPLAND_HUT, nbttagcompound);
        this.spawnedWitch = nbttagcompound.getBoolean("Witch");
        this.spawnedCat = nbttagcompound.getBoolean("Cat");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("Witch", this.spawnedWitch);
        nbttagcompound.putBoolean("Cat", this.spawnedCat);
    }

    @Override
    public boolean postProcess(WorldGenLevel generatoraccessseed, StructureFeatureManager structuremanager, ChunkGenerator chunkgenerator, Random random, BoundingBox structureboundingbox, ChunkPos chunkcoordintpair, BlockPos blockposition) {
        if (!this.updateAverageGroundHeight(generatoraccessseed, structureboundingbox, 0)) {
            return false;
        } else {
            this.generateBox(generatoraccessseed, structureboundingbox, 1, 1, 1, 5, 1, 7, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 1, 4, 2, 5, 4, 7, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 2, 1, 0, 4, 1, 0, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 2, 2, 2, 3, 3, 2, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 1, 2, 3, 1, 3, 6, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 5, 2, 3, 5, 3, 6, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 2, 2, 7, 4, 3, 7, Blocks.SPRUCE_PLANKS.getBlockData(), Blocks.SPRUCE_PLANKS.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 1, 0, 2, 1, 3, 2, Blocks.OAK_LOG.getBlockData(), Blocks.OAK_LOG.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 5, 0, 2, 5, 3, 2, Blocks.OAK_LOG.getBlockData(), Blocks.OAK_LOG.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 1, 0, 7, 1, 3, 7, Blocks.OAK_LOG.getBlockData(), Blocks.OAK_LOG.getBlockData(), false);
            this.generateBox(generatoraccessseed, structureboundingbox, 5, 0, 7, 5, 3, 7, Blocks.OAK_LOG.getBlockData(), Blocks.OAK_LOG.getBlockData(), false);
            this.placeBlock(generatoraccessseed, Blocks.OAK_FENCE.getBlockData(), 2, 3, 2, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.OAK_FENCE.getBlockData(), 3, 3, 7, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.AIR.getBlockData(), 1, 3, 4, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.AIR.getBlockData(), 5, 3, 4, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.AIR.getBlockData(), 5, 3, 5, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.POTTED_RED_MUSHROOM.getBlockData(), 1, 3, 5, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.CRAFTING_TABLE.getBlockData(), 3, 2, 6, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.CAULDRON.getBlockData(), 4, 2, 6, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.OAK_FENCE.getBlockData(), 1, 2, 1, structureboundingbox);
            this.placeBlock(generatoraccessseed, Blocks.OAK_FENCE.getBlockData(), 5, 2, 1, structureboundingbox);
            BlockState iblockdata = (BlockState) Blocks.SPRUCE_STAIRS.getBlockData().setValue(StairBlock.FACING, Direction.NORTH);
            BlockState iblockdata1 = (BlockState) Blocks.SPRUCE_STAIRS.getBlockData().setValue(StairBlock.FACING, Direction.EAST);
            BlockState iblockdata2 = (BlockState) Blocks.SPRUCE_STAIRS.getBlockData().setValue(StairBlock.FACING, Direction.WEST);
            BlockState iblockdata3 = (BlockState) Blocks.SPRUCE_STAIRS.getBlockData().setValue(StairBlock.FACING, Direction.SOUTH);

            this.generateBox(generatoraccessseed, structureboundingbox, 0, 4, 1, 6, 4, 1, iblockdata, iblockdata, false);
            this.generateBox(generatoraccessseed, structureboundingbox, 0, 4, 2, 0, 4, 7, iblockdata1, iblockdata1, false);
            this.generateBox(generatoraccessseed, structureboundingbox, 6, 4, 2, 6, 4, 7, iblockdata2, iblockdata2, false);
            this.generateBox(generatoraccessseed, structureboundingbox, 0, 4, 8, 6, 4, 8, iblockdata3, iblockdata3, false);
            this.placeBlock(generatoraccessseed, (BlockState) iblockdata.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT), 0, 4, 1, structureboundingbox);
            this.placeBlock(generatoraccessseed, (BlockState) iblockdata.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), 6, 4, 1, structureboundingbox);
            this.placeBlock(generatoraccessseed, (BlockState) iblockdata3.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), 0, 4, 8, structureboundingbox);
            this.placeBlock(generatoraccessseed, (BlockState) iblockdata3.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT), 6, 4, 8, structureboundingbox);

            int i;
            int j;

            for (j = 2; j <= 7; j += 5) {
                for (i = 1; i <= 5; i += 4) {
                    this.fillColumnDown(generatoraccessseed, Blocks.OAK_LOG.getBlockData(), i, -1, j, structureboundingbox);
                }
            }

            if (!this.spawnedWitch) {
                j = this.getWorldX(2, 5);
                i = this.getWorldY(2);
                int k = this.getWorldZ(2, 5);

                if (structureboundingbox.isInside((Vec3i) (new BlockPos(j, i, k)))) {
                    this.spawnedWitch = true;
                    Witch entitywitch = (Witch) EntityType.WITCH.create(generatoraccessseed.getLevel());

                    entitywitch.setPersistenceRequired();
                    entitywitch.moveTo((double) j + 0.5D, (double) i, (double) k + 0.5D, 0.0F, 0.0F);
                    entitywitch.prepare(generatoraccessseed, generatoraccessseed.getDamageScaler(new BlockPos(j, i, k)), MobSpawnType.STRUCTURE, (SpawnGroupData) null, (CompoundTag) null);
                    generatoraccessseed.addEntity(entitywitch, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN); // CraftBukkit - add SpawnReason
                }
            }

            this.spawnCat((LevelAccessor) generatoraccessseed, structureboundingbox);
            return true;
        }
    }

    private void spawnCat(LevelAccessor generatoraccess, BoundingBox structureboundingbox) {
        if (!this.spawnedCat) {
            int i = this.getWorldX(2, 5);
            int j = this.getWorldY(2);
            int k = this.getWorldZ(2, 5);

            if (structureboundingbox.isInside((Vec3i) (new BlockPos(i, j, k)))) {
                this.spawnedCat = true;
                Cat entitycat = (Cat) EntityType.CAT.create(generatoraccess.getLevel());

                entitycat.setPersistenceRequired();
                entitycat.moveTo((double) i + 0.5D, (double) j, (double) k + 0.5D, 0.0F, 0.0F);
                entitycat.prepare(generatoraccess, generatoraccess.getDamageScaler(new BlockPos(i, j, k)), MobSpawnType.STRUCTURE, (SpawnGroupData) null, (CompoundTag) null);
                generatoraccess.addFreshEntity(entitycat);
            }
        }

    }
}
