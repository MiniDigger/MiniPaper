package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ScaffoldingBlock extends Block implements SimpleWaterloggedBlock {

    private static final VoxelShape STABLE_SHAPE;
    private static final VoxelShape UNSTABLE_SHAPE;
    private static final VoxelShape UNSTABLE_SHAPE_BOTTOM = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape BELOW_BLOCK = Shapes.block().move(0.0D, -1.0D, 0.0D);
    public static final IntegerProperty DISTANCE = BlockStateProperties.STABILITY_DISTANCE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;

    protected ScaffoldingBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ScaffoldingBlock.DISTANCE, 7)).setValue(ScaffoldingBlock.WATERLOGGED, false)).setValue(ScaffoldingBlock.BOTTOM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ScaffoldingBlock.DISTANCE, ScaffoldingBlock.WATERLOGGED, ScaffoldingBlock.BOTTOM);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return !voxelshapecollision.isHoldingItem(iblockdata.getBlock().asItem()) ? ((Boolean) iblockdata.getValue(ScaffoldingBlock.BOTTOM) ? ScaffoldingBlock.UNSTABLE_SHAPE : ScaffoldingBlock.STABLE_SHAPE) : Shapes.block();
    }

    @Override
    public VoxelShape getInteractionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return Shapes.block();
    }

    @Override
    public boolean canBeReplaced(BlockState iblockdata, BlockPlaceContext blockactioncontext) {
        return blockactioncontext.getItemInHand().getItem() == this.asItem();
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockPos blockposition = blockactioncontext.getClickedPos();
        Level world = blockactioncontext.getLevel();
        int i = getDistance((BlockGetter) world, blockposition);

        return (BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(ScaffoldingBlock.WATERLOGGED, world.getFluidState(blockposition).getType() == Fluids.WATER)).setValue(ScaffoldingBlock.DISTANCE, i)).setValue(ScaffoldingBlock.BOTTOM, this.isBottom(world, blockposition, i));
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!world.isClientSide) {
            world.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if ((Boolean) iblockdata.getValue(ScaffoldingBlock.WATERLOGGED)) {
            generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
        }

        if (!generatoraccess.isClientSide()) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        return iblockdata;
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        int i = getDistance((BlockGetter) worldserver, blockposition);
        BlockState iblockdata1 = (BlockState) ((BlockState) iblockdata.setValue(ScaffoldingBlock.DISTANCE, i)).setValue(ScaffoldingBlock.BOTTOM, this.isBottom(worldserver, blockposition, i));

        if ((Integer) iblockdata1.getValue(ScaffoldingBlock.DISTANCE) == 7 && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, Blocks.AIR.getBlockData()).isCancelled()) { // CraftBukkit - BlockFadeEvent
            if ((Integer) iblockdata.getValue(ScaffoldingBlock.DISTANCE) == 7) {
                worldserver.addFreshEntity(new FallingBlockEntity(worldserver, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, (BlockState) iblockdata1.setValue(ScaffoldingBlock.WATERLOGGED, false)));
            } else {
                worldserver.destroyBlock(blockposition, true);
            }
        } else if (iblockdata != iblockdata1) {
            worldserver.setTypeAndData(blockposition, iblockdata1, 3);
        }

    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return getDistance((BlockGetter) iworldreader, blockposition) < 7;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return voxelshapecollision.isAbove(Shapes.block(), blockposition, true) && !voxelshapecollision.isDescending() ? ScaffoldingBlock.STABLE_SHAPE : ((Integer) iblockdata.getValue(ScaffoldingBlock.DISTANCE) != 0 && (Boolean) iblockdata.getValue(ScaffoldingBlock.BOTTOM) && voxelshapecollision.isAbove(ScaffoldingBlock.BELOW_BLOCK, blockposition, true) ? ScaffoldingBlock.UNSTABLE_SHAPE_BOTTOM : Shapes.empty());
    }

    @Override
    public FluidState getFluidState(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(ScaffoldingBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(iblockdata);
    }

    private boolean isBottom(BlockGetter iblockaccess, BlockPos blockposition, int i) {
        return i > 0 && !iblockaccess.getType(blockposition.below()).is((Block) this);
    }

    public static int getDistance(BlockGetter iblockaccess, BlockPos blockposition) {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = blockposition.i().c(Direction.DOWN);
        BlockState iblockdata = iblockaccess.getType(blockposition_mutableblockposition);
        int i = 7;

        if (iblockdata.is(Blocks.SCAFFOLDING)) {
            i = (Integer) iblockdata.getValue(ScaffoldingBlock.DISTANCE);
        } else if (iblockdata.isFaceSturdy(iblockaccess, blockposition_mutableblockposition, Direction.UP)) {
            return 0;
        }

        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockState iblockdata1 = iblockaccess.getType(blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection));

            if (iblockdata1.is(Blocks.SCAFFOLDING)) {
                i = Math.min(i, (Integer) iblockdata1.getValue(ScaffoldingBlock.DISTANCE) + 1);
                if (i == 1) {
                    break;
                }
            }
        }

        return i;
    }

    static {
        VoxelShape voxelshape = Block.box(0.0D, 14.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        VoxelShape voxelshape1 = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 2.0D);
        VoxelShape voxelshape2 = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D);
        VoxelShape voxelshape3 = Block.box(0.0D, 0.0D, 14.0D, 2.0D, 16.0D, 16.0D);
        VoxelShape voxelshape4 = Block.box(14.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D);

        STABLE_SHAPE = Shapes.or(voxelshape, voxelshape1, voxelshape2, voxelshape3, voxelshape4);
        VoxelShape voxelshape5 = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 16.0D);
        VoxelShape voxelshape6 = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
        VoxelShape voxelshape7 = Block.box(0.0D, 0.0D, 14.0D, 16.0D, 2.0D, 16.0D);
        VoxelShape voxelshape8 = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 2.0D);

        UNSTABLE_SHAPE = Shapes.or(ScaffoldingBlock.UNSTABLE_SHAPE_BOTTOM, ScaffoldingBlock.STABLE_SHAPE, voxelshape6, voxelshape5, voxelshape8, voxelshape7);
    }
}
