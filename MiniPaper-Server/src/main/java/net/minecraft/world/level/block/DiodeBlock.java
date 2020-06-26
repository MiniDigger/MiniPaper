package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.TickPriority;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public abstract class DiodeBlock extends HorizontalDirectionalBlock {

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected DiodeBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return DiodeBlock.SHAPE;
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return canSupportRigidBlock((BlockGetter) iworldreader, blockposition.below());
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!this.isLocked((LevelReader) worldserver, blockposition, iblockdata)) {
            boolean flag = (Boolean) iblockdata.getValue(DiodeBlock.POWERED);
            boolean flag1 = this.shouldTurnOn((Level) worldserver, blockposition, iblockdata);

            if (flag && !flag1) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(worldserver, blockposition, 15, 0).getNewCurrent() != 0) {
                    return;
                }
                // CraftBukkit end
                worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(DiodeBlock.POWERED, false), 2);
            } else if (!flag) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(worldserver, blockposition, 0, 15).getNewCurrent() != 15) {
                    return;
                }
                // CraftBukkit end
                worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(DiodeBlock.POWERED, true), 2);
                if (!flag1) {
                    worldserver.getBlockTickList().scheduleTick(blockposition, this, this.getDelay(iblockdata), TickPriority.VERY_HIGH);
                }
            }

        }
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return iblockdata.getSignal(iblockaccess, blockposition, enumdirection);
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return !(Boolean) iblockdata.getValue(DiodeBlock.POWERED) ? 0 : (iblockdata.getValue(DiodeBlock.FACING) == enumdirection ? this.getOutputSignal(iblockaccess, blockposition, iblockdata) : 0);
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (iblockdata.canSurvive(world, blockposition)) {
            this.checkTickOnNeighbor(world, blockposition, iblockdata);
        } else {
            BlockEntity tileentity = this.isEntityBlock() ? world.getBlockEntity(blockposition) : null;

            dropResources(iblockdata, world, blockposition, tileentity);
            world.removeBlock(blockposition, false);
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                world.updateNeighborsAt(blockposition.relative(enumdirection), this);
            }

        }
    }

    protected void checkTickOnNeighbor(Level world, BlockPos blockposition, BlockState iblockdata) {
        if (!this.isLocked((LevelReader) world, blockposition, iblockdata)) {
            boolean flag = (Boolean) iblockdata.getValue(DiodeBlock.POWERED);
            boolean flag1 = this.shouldTurnOn(world, blockposition, iblockdata);

            if (flag != flag1 && !world.getBlockTickList().willTickThisTick(blockposition, this)) {
                TickPriority ticklistpriority = TickPriority.HIGH;

                if (this.shouldPrioritize((BlockGetter) world, blockposition, iblockdata)) {
                    ticklistpriority = TickPriority.EXTREMELY_HIGH;
                } else if (flag) {
                    ticklistpriority = TickPriority.VERY_HIGH;
                }

                world.getBlockTickList().scheduleTick(blockposition, this, this.getDelay(iblockdata), ticklistpriority);
            }

        }
    }

    public boolean isLocked(LevelReader iworldreader, BlockPos blockposition, BlockState iblockdata) {
        return false;
    }

    protected boolean shouldTurnOn(Level world, BlockPos blockposition, BlockState iblockdata) {
        return this.getInputSignal(world, blockposition, iblockdata) > 0;
    }

    protected int getInputSignal(Level world, BlockPos blockposition, BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(DiodeBlock.FACING);
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        int i = world.getSignal(blockposition1, enumdirection);

        if (i >= 15) {
            return i;
        } else {
            BlockState iblockdata1 = world.getType(blockposition1);

            return Math.max(i, iblockdata1.is(Blocks.REDSTONE_WIRE) ? (Integer) iblockdata1.getValue(RedStoneWireBlock.POWER) : 0);
        }
    }

    protected int getAlternateSignal(LevelReader iworldreader, BlockPos blockposition, BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(DiodeBlock.FACING);
        Direction enumdirection1 = enumdirection.getClockWise();
        Direction enumdirection2 = enumdirection.getCounterClockWise();

        return Math.max(this.getAlternateSignalAt(iworldreader, blockposition.relative(enumdirection1), enumdirection1), this.getAlternateSignalAt(iworldreader, blockposition.relative(enumdirection2), enumdirection2));
    }

    protected int getAlternateSignalAt(LevelReader iworldreader, BlockPos blockposition, Direction enumdirection) {
        BlockState iblockdata = iworldreader.getType(blockposition);

        return this.isAlternateInput(iblockdata) ? (iblockdata.is(Blocks.REDSTONE_BLOCK) ? 15 : (iblockdata.is(Blocks.REDSTONE_WIRE) ? (Integer) iblockdata.getValue(RedStoneWireBlock.POWER) : iworldreader.getDirectSignal(blockposition, enumdirection))) : 0;
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(DiodeBlock.FACING, blockactioncontext.getHorizontalDirection().getOpposite());
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        if (this.shouldTurnOn(world, blockposition, iblockdata)) {
            world.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        this.updateNeighborsInFront(world, blockposition, iblockdata);
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
            this.updateNeighborsInFront(world, blockposition, iblockdata);
        }
    }

    protected void updateNeighborsInFront(Level world, BlockPos blockposition, BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(DiodeBlock.FACING);
        BlockPos blockposition1 = blockposition.relative(enumdirection.getOpposite());

        world.neighborChanged(blockposition1, (Block) this, blockposition);
        world.updateNeighborsAtExceptFromFacing(blockposition1, (Block) this, enumdirection);
    }

    protected boolean isAlternateInput(BlockState iblockdata) {
        return iblockdata.isSignalSource();
    }

    protected int getOutputSignal(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata) {
        return 15;
    }

    public static boolean isDiode(BlockState iblockdata) {
        return iblockdata.getBlock() instanceof DiodeBlock;
    }

    public boolean shouldPrioritize(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata) {
        Direction enumdirection = ((Direction) iblockdata.getValue(DiodeBlock.FACING)).getOpposite();
        BlockState iblockdata1 = iblockaccess.getType(blockposition.relative(enumdirection));

        return isDiode(iblockdata1) && iblockdata1.getValue(DiodeBlock.FACING) != enumdirection;
    }

    protected abstract int getDelay(BlockState iblockdata);
}
