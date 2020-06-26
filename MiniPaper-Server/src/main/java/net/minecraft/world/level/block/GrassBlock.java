package net.minecraft.world.level.block;

import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.AbstractFlowerFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.DecoratedFeatureConfiguration;

public class GrassBlock extends SpreadingSnowyDirtBlock implements BonemealableBlock {

    public GrassBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return iblockaccess.getType(blockposition.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        BlockPos blockposition1 = blockposition.above();
        BlockState iblockdata1 = Blocks.GRASS.getBlockData();
        int i = 0;

        while (i < 128) {
            BlockPos blockposition2 = blockposition1;
            int j = 0;

            while (true) {
                if (j < i / 16) {
                    blockposition2 = blockposition2.offset(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                    if (worldserver.getType(blockposition2.below()).is((Block) this) && !worldserver.getType(blockposition2).isCollisionShapeFullBlock(worldserver, blockposition2)) {
                        ++j;
                        continue;
                    }
                } else {
                    BlockState iblockdata2 = worldserver.getType(blockposition2);

                    if (iblockdata2.is(iblockdata1.getBlock()) && random.nextInt(10) == 0) {
                        ((BonemealableBlock) iblockdata1.getBlock()).performBonemeal(worldserver, random, blockposition2, iblockdata2);
                    }

                    if (iblockdata2.isAir()) {
                        label38:
                        {
                            BlockState iblockdata3;

                            if (random.nextInt(8) == 0) {
                                List<ConfiguredFeature<?, ?>> list = worldserver.getBiome(blockposition2).getFlowerFeatures();

                                if (list.isEmpty()) {
                                    break label38;
                                }

                                ConfiguredFeature<?, ?> worldgenfeatureconfigured = ((DecoratedFeatureConfiguration) ((ConfiguredFeature) list.get(0)).config).feature;

                                iblockdata3 = ((AbstractFlowerFeature) worldgenfeatureconfigured.feature).getRandomFlower(random, blockposition2, worldgenfeatureconfigured.config);
                            } else {
                                iblockdata3 = iblockdata1;
                            }

                            if (iblockdata3.canSurvive(worldserver, blockposition2)) {
                                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition2, iblockdata3, 3); // CraftBukkit
                            }
                        }
                    }
                }

                ++i;
                break;
            }
        }

    }
}
