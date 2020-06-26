package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FenceGateBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
    protected static final VoxelShape Z_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape X_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);
    protected static final VoxelShape Z_SHAPE_LOW = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 13.0D, 10.0D);
    protected static final VoxelShape X_SHAPE_LOW = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 13.0D, 16.0D);
    protected static final VoxelShape Z_COLLISION_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 24.0D, 10.0D);
    protected static final VoxelShape X_COLLISION_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 24.0D, 16.0D);
    protected static final VoxelShape Z_OCCLUSION_SHAPE = Shapes.or(Block.box(0.0D, 5.0D, 7.0D, 2.0D, 16.0D, 9.0D), Block.box(14.0D, 5.0D, 7.0D, 16.0D, 16.0D, 9.0D));
    protected static final VoxelShape X_OCCLUSION_SHAPE = Shapes.or(Block.box(7.0D, 5.0D, 0.0D, 9.0D, 16.0D, 2.0D), Block.box(7.0D, 5.0D, 14.0D, 9.0D, 16.0D, 16.0D));
    protected static final VoxelShape Z_OCCLUSION_SHAPE_LOW = Shapes.or(Block.box(0.0D, 2.0D, 7.0D, 2.0D, 13.0D, 9.0D), Block.box(14.0D, 2.0D, 7.0D, 16.0D, 13.0D, 9.0D));
    protected static final VoxelShape X_OCCLUSION_SHAPE_LOW = Shapes.or(Block.box(7.0D, 2.0D, 0.0D, 9.0D, 13.0D, 2.0D), Block.box(7.0D, 2.0D, 14.0D, 9.0D, 13.0D, 16.0D));

    public FenceGateBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(FenceGateBlock.OPEN, false)).setValue(FenceGateBlock.POWERED, false)).setValue(FenceGateBlock.IN_WALL, false));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return (Boolean) iblockdata.getValue(FenceGateBlock.IN_WALL) ? (((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_SHAPE_LOW : FenceGateBlock.Z_SHAPE_LOW) : (((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_SHAPE : FenceGateBlock.Z_SHAPE);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();

        if (((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getClockWise().getAxis() != enumdirection_enumaxis) {
            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        } else {
            boolean flag = this.isWall(iblockdata1) || this.isWall(generatoraccess.getType(blockposition.relative(enumdirection.getOpposite())));

            return (BlockState) iblockdata.setValue(FenceGateBlock.IN_WALL, flag);
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return (Boolean) iblockdata.getValue(FenceGateBlock.OPEN) ? Shapes.empty() : (((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.Z ? FenceGateBlock.Z_COLLISION_SHAPE : FenceGateBlock.X_COLLISION_SHAPE);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return (Boolean) iblockdata.getValue(FenceGateBlock.IN_WALL) ? (((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_OCCLUSION_SHAPE_LOW : FenceGateBlock.Z_OCCLUSION_SHAPE_LOW) : (((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_OCCLUSION_SHAPE : FenceGateBlock.Z_OCCLUSION_SHAPE);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        switch (pathmode) {
            case LAND:
                return (Boolean) iblockdata.getValue(FenceGateBlock.OPEN);
            case WATER:
                return false;
            case AIR:
                return (Boolean) iblockdata.getValue(FenceGateBlock.OPEN);
            default:
                return false;
        }
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        boolean flag = world.hasNeighborSignal(blockposition);
        Direction enumdirection = blockactioncontext.getHorizontalDirection();
        Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
        boolean flag1 = enumdirection_enumaxis == Direction.Axis.Z && (this.isWall(world.getType(blockposition.west())) || this.isWall(world.getType(blockposition.east()))) || enumdirection_enumaxis == Direction.Axis.X && (this.isWall(world.getType(blockposition.north())) || this.isWall(world.getType(blockposition.south())));

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(FenceGateBlock.FACING, enumdirection)).setValue(FenceGateBlock.OPEN, flag)).setValue(FenceGateBlock.POWERED, flag)).setValue(FenceGateBlock.IN_WALL, flag1);
    }

    private boolean isWall(BlockState iblockdata) {
        return iblockdata.getBlock().is((Tag) BlockTags.WALLS);
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if ((Boolean) iblockdata.getValue(FenceGateBlock.OPEN)) {
            iblockdata = (BlockState) iblockdata.setValue(FenceGateBlock.OPEN, false);
            world.setTypeAndData(blockposition, iblockdata, 10);
        } else {
            Direction enumdirection = entityhuman.getDirection();

            if (iblockdata.getValue(FenceGateBlock.FACING) == enumdirection.getOpposite()) {
                iblockdata = (BlockState) iblockdata.setValue(FenceGateBlock.FACING, enumdirection);
            }

            iblockdata = (BlockState) iblockdata.setValue(FenceGateBlock.OPEN, true);
            world.setTypeAndData(blockposition, iblockdata, 10);
        }

        world.levelEvent(entityhuman, (Boolean) iblockdata.getValue(FenceGateBlock.OPEN) ? 1008 : 1014, blockposition, 0);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (!world.isClientSide) {
            boolean flag1 = world.hasNeighborSignal(blockposition);
            // CraftBukkit start
            boolean oldPowered = iblockdata.getValue(FenceGateBlock.POWERED);
            if (oldPowered != flag1) {
                int newPower = flag1 ? 15 : 0;
                int oldPower = oldPowered ? 15 : 0;
                org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(world, blockposition);
                org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(bukkitBlock, oldPower, newPower);
                world.getServerOH().getPluginManager().callEvent(eventRedstone);
                flag1 = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end

            if ((Boolean) iblockdata.getValue(FenceGateBlock.POWERED) != flag1) {
                world.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(FenceGateBlock.POWERED, flag1)).setValue(FenceGateBlock.OPEN, flag1), 2);
                if ((Boolean) iblockdata.getValue(FenceGateBlock.OPEN) != flag1) {
                    world.levelEvent((Player) null, flag1 ? 1008 : 1014, blockposition, 0);
                }
            }

        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(FenceGateBlock.FACING, FenceGateBlock.OPEN, FenceGateBlock.POWERED, FenceGateBlock.IN_WALL);
    }

    public static boolean connectsToDirection(BlockState iblockdata, Direction enumdirection) {
        return ((Direction) iblockdata.getValue(FenceGateBlock.FACING)).getAxis() == enumdirection.getClockWise().getAxis();
    }
}
