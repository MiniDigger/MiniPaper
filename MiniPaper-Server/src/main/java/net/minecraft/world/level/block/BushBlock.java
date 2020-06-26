package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class BushBlock extends Block {

    protected BushBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    protected boolean mayPlaceOn(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.is(Blocks.GRASS_BLOCK) || iblockdata.is(Blocks.DIRT) || iblockdata.is(Blocks.COARSE_DIRT) || iblockdata.is(Blocks.PODZOL) || iblockdata.is(Blocks.FARMLAND);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        // CraftBukkit start
        if (!iblockdata.canSurvive(generatoraccess, blockposition)) {
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPhysicsEvent(generatoraccess, blockposition).isCancelled()) {
                return Blocks.AIR.getBlockData();
            }
        }
        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        // CraftBukkit end
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();

        return this.mayPlaceOn(iworldreader.getType(blockposition1), (BlockGetter) iworldreader, blockposition1);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.getFluidState().isEmpty();
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return pathmode == PathComputationType.AIR && !this.hasCollision ? true : super.isPathfindable(iblockdata, iblockaccess, blockposition, pathmode);
    }
}
