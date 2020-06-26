package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LayerLightEngine;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {

    protected SpreadingSnowyDirtBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    private static boolean canBeGrass(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.above();
        BlockState iblockdata1 = iworldreader.getType(blockposition1);

        if (iblockdata1.is(Blocks.SNOW) && (Integer) iblockdata1.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        } else if (iblockdata1.getFluidState().getAmount() == 8) {
            return false;
        } else {
            int i = LayerLightEngine.getLightBlockInto(iworldreader, iblockdata, blockposition, iblockdata1, blockposition1, Direction.UP, iblockdata1.getLightBlock((BlockGetter) iworldreader, blockposition1));

            return i < iworldreader.getMaxLightLevel();
        }
    }

    private static boolean canPropagate(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.above();

        return canBeGrass(iblockdata, iworldreader, blockposition) && !iworldreader.getFluidState(blockposition1).is((Tag) FluidTags.WATER);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!canBeGrass(iblockdata, (LevelReader) worldserver, blockposition)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, Blocks.DIRT.getBlockData()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeUpdate(blockposition, Blocks.DIRT.getBlockData());
        } else {
            if (worldserver.getMaxLocalRawBrightness(blockposition.above()) >= 9) {
                BlockState iblockdata1 = this.getBlockData();

                for (int i = 0; i < 4; ++i) {
                    BlockPos blockposition1 = blockposition.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);

                    if (worldserver.getType(blockposition1).is(Blocks.DIRT) && canPropagate(iblockdata1, (LevelReader) worldserver, blockposition1)) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition1, (BlockState) iblockdata1.setValue(SpreadingSnowyDirtBlock.SNOWY, worldserver.getType(blockposition1.above()).is(Blocks.SNOW))); // CraftBukkit
                    }
                }
            }

        }
    }
}
