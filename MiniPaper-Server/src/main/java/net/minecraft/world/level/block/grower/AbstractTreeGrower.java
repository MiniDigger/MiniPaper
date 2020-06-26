package net.minecraft.world.level.block.grower;

import java.util.Iterator;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.BiomeDefaultFeatures;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.bukkit.TreeType; // CraftBukkit

public abstract class AbstractTreeGrower {

    public AbstractTreeGrower() {}

    @Nullable
    protected abstract ConfiguredFeature<TreeConfiguration, ?> getConfiguredFeature(Random random, boolean flag);

    public boolean growTree(ServerLevel worldserver, ChunkGenerator chunkgenerator, BlockPos blockposition, BlockState iblockdata, Random random) {
        ConfiguredFeature<TreeConfiguration, ?> worldgenfeatureconfigured = this.getConfiguredFeature(random, this.hasFlowers(worldserver, blockposition));

        if (worldgenfeatureconfigured == null) {
            return false;
        } else {
            setTreeType(worldgenfeatureconfigured); // CraftBukkit
            worldserver.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 4);
            ((TreeConfiguration) worldgenfeatureconfigured.config).setFromSapling();
            if (worldgenfeatureconfigured.place(worldserver, worldserver.getStructureManager(), chunkgenerator, random, blockposition)) {
                return true;
            } else {
                worldserver.setTypeAndData(blockposition, iblockdata, 4);
                return false;
            }
        }
    }

    private boolean hasFlowers(LevelAccessor generatoraccess, BlockPos blockposition) {
        Iterator iterator = BlockPos.MutableBlockPosition.betweenClosed(blockposition.below().north(2).west(2), blockposition.above().south(2).east(2)).iterator();

        BlockPos blockposition1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            blockposition1 = (BlockPos) iterator.next();
        } while (!generatoraccess.getType(blockposition1).is((Tag) BlockTags.FLOWERS));

        return true;
    }

    // CraftBukkit start
    protected void setTreeType(ConfiguredFeature<?, ?> worldgentreeabstract) {
        if (worldgentreeabstract.config == BiomeDefaultFeatures.NORMAL_TREE_CONFIG || worldgentreeabstract.config == BiomeDefaultFeatures.NORMAL_TREE_WITH_BEES_005_CONFIG) {
            SaplingBlock.treeType = TreeType.TREE;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.HUGE_RED_MUSHROOM_CONFIG) {
            SaplingBlock.treeType = TreeType.RED_MUSHROOM;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.HUGE_BROWN_MUSHROOM_CONFIG) {
            SaplingBlock.treeType = TreeType.BROWN_MUSHROOM;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.JUNGLE_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.COCOA_TREE;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.JUNGLE_TREE_NOVINE_CONFIG) {
            SaplingBlock.treeType = TreeType.SMALL_JUNGLE;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.PINE_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.TALL_REDWOOD;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.SPRUCE_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.REDWOOD;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.ACACIA_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.ACACIA;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.BIRCH_TREE_CONFIG || worldgentreeabstract.config == BiomeDefaultFeatures.BIRCH_TREE_WITH_BEES_005_CONFIG) {
            SaplingBlock.treeType = TreeType.BIRCH;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.SUPER_BIRCH_TREE_WITH_BEES_0002_CONFIG) {
            SaplingBlock.treeType = TreeType.TALL_BIRCH;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.SWAMP_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.SWAMP;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.FANCY_TREE_CONFIG || worldgentreeabstract.config == BiomeDefaultFeatures.FANCY_TREE_WITH_BEES_005_CONFIG) {
            SaplingBlock.treeType = TreeType.BIG_TREE;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.JUNGLE_BUSH_CONFIG) {
            SaplingBlock.treeType = TreeType.JUNGLE_BUSH;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.DARK_OAK_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.DARK_OAK;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.MEGA_SPRUCE_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.MEGA_REDWOOD;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.MEGA_PINE_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.MEGA_REDWOOD;
        } else if (worldgentreeabstract.config == BiomeDefaultFeatures.MEGA_JUNGLE_TREE_CONFIG) {
            SaplingBlock.treeType = TreeType.JUNGLE;
        } else {
            throw new IllegalArgumentException("Unknown tree generator " + worldgentreeabstract);
        }
    }
    // CraftBukkit end
}
