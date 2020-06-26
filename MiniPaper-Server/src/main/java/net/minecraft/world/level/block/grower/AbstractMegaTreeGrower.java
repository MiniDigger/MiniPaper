package net.minecraft.world.level.block.grower;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public abstract class AbstractMegaTreeGrower extends AbstractTreeGrower {

    public AbstractMegaTreeGrower() {}

    @Override
    public boolean growTree(ServerLevel worldserver, ChunkGenerator chunkgenerator, BlockPos blockposition, BlockState iblockdata, Random random) {
        for (int i = 0; i >= -1; --i) {
            for (int j = 0; j >= -1; --j) {
                if (isTwoByTwoSapling(iblockdata, worldserver, blockposition, i, j)) {
                    return this.placeMega(worldserver, chunkgenerator, blockposition, iblockdata, random, i, j);
                }
            }
        }

        return super.growTree(worldserver, chunkgenerator, blockposition, iblockdata, random);
    }

    @Nullable
    protected abstract ConfiguredFeature<TreeConfiguration, ?> getConfiguredMegaFeature(Random random);

    public boolean placeMega(ServerLevel worldserver, ChunkGenerator chunkgenerator, BlockPos blockposition, BlockState iblockdata, Random random, int i, int j) {
        ConfiguredFeature<TreeConfiguration, ?> worldgenfeatureconfigured = this.getConfiguredMegaFeature(random);

        if (worldgenfeatureconfigured == null) {
            return false;
        } else {
            ((TreeConfiguration) worldgenfeatureconfigured.config).setFromSapling();
            setTreeType(worldgenfeatureconfigured); // CraftBukkit
            BlockState iblockdata1 = Blocks.AIR.getBlockData();

            worldserver.setTypeAndData(blockposition.offset(i, 0, j), iblockdata1, 4);
            worldserver.setTypeAndData(blockposition.offset(i + 1, 0, j), iblockdata1, 4);
            worldserver.setTypeAndData(blockposition.offset(i, 0, j + 1), iblockdata1, 4);
            worldserver.setTypeAndData(blockposition.offset(i + 1, 0, j + 1), iblockdata1, 4);
            if (worldgenfeatureconfigured.place(worldserver, worldserver.getStructureManager(), chunkgenerator, random, blockposition.offset(i, 0, j))) {
                return true;
            } else {
                worldserver.setTypeAndData(blockposition.offset(i, 0, j), iblockdata, 4);
                worldserver.setTypeAndData(blockposition.offset(i + 1, 0, j), iblockdata, 4);
                worldserver.setTypeAndData(blockposition.offset(i, 0, j + 1), iblockdata, 4);
                worldserver.setTypeAndData(blockposition.offset(i + 1, 0, j + 1), iblockdata, 4);
                return false;
            }
        }
    }

    public static boolean isTwoByTwoSapling(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, int i, int j) {
        Block block = iblockdata.getBlock();

        return block == iblockaccess.getType(blockposition.offset(i, 0, j)).getBlock() && block == iblockaccess.getType(blockposition.offset(i + 1, 0, j)).getBlock() && block == iblockaccess.getType(blockposition.offset(i, 0, j + 1)).getBlock() && block == iblockaccess.getType(blockposition.offset(i + 1, 0, j + 1)).getBlock();
    }
}
