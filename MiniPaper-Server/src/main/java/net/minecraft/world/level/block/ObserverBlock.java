package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ObserverBlock extends DirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ObserverBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ObserverBlock.FACING, Direction.SOUTH)).setValue(ObserverBlock.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ObserverBlock.FACING, ObserverBlock.POWERED);
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(ObserverBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(ObserverBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(ObserverBlock.FACING)));
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Boolean) iblockdata.getValue(ObserverBlock.POWERED)) {
            // CraftBukkit start
            if (CraftEventFactory.callRedstoneChange(worldserver, blockposition, 15, 0).getNewCurrent() != 0) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ObserverBlock.POWERED, false), 2);
        } else {
            // CraftBukkit start
            if (CraftEventFactory.callRedstoneChange(worldserver, blockposition, 0, 15).getNewCurrent() != 15) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ObserverBlock.POWERED, true), 2);
            worldserver.getBlockTickList().scheduleTick(blockposition, this, 2);
        }

        this.updateNeighborsInFront((Level) worldserver, blockposition, iblockdata);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (iblockdata.getValue(ObserverBlock.FACING) == enumdirection && !(Boolean) iblockdata.getValue(ObserverBlock.POWERED)) {
            this.startSignal(generatoraccess, blockposition);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    private void startSignal(LevelAccessor generatoraccess, BlockPos blockposition) {
        if (!generatoraccess.isClientSide() && !generatoraccess.getBlockTickList().hasScheduledTick(blockposition, this)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 2);
        }

    }

    protected void updateNeighborsInFront(Level world, BlockPos blockposition, BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(ObserverBlock.FACING);
        BlockPos blockposition1 = blockposition.relative(enumdirection.getOpposite());

        world.neighborChanged(blockposition1, (Block) this, blockposition);
        world.updateNeighborsAtExceptFromFacing(blockposition1, (Block) this, enumdirection);
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return iblockdata.getSignal(iblockaccess, blockposition, enumdirection);
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(ObserverBlock.POWERED) && iblockdata.getValue(ObserverBlock.FACING) == enumdirection ? 15 : 0;
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            if (!world.isClientSide() && (Boolean) iblockdata.getValue(ObserverBlock.POWERED) && !world.getBlockTickList().hasScheduledTick(blockposition, this)) {
                BlockState iblockdata2 = (BlockState) iblockdata.setValue(ObserverBlock.POWERED, false);

                world.setTypeAndData(blockposition, iblockdata2, 18);
                this.updateNeighborsInFront(world, blockposition, iblockdata2);
            }

        }
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            if (!world.isClientSide && (Boolean) iblockdata.getValue(ObserverBlock.POWERED) && world.getBlockTickList().hasScheduledTick(blockposition, this)) {
                this.updateNeighborsInFront(world, blockposition, (BlockState) iblockdata.setValue(ObserverBlock.POWERED, false));
            }

        }
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(ObserverBlock.FACING, blockactioncontext.getNearestLookingDirection().getOpposite().getOpposite());
    }
}
