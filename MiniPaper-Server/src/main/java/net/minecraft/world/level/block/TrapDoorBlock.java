package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class TrapDoorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape EAST_OPEN_AABB = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_OPEN_AABB = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape SOUTH_OPEN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D);
    protected static final VoxelShape NORTH_OPEN_AABB = Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape BOTTOM_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);
    protected static final VoxelShape TOP_AABB = Block.box(0.0D, 13.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    protected TrapDoorBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TrapDoorBlock.FACING, Direction.NORTH)).setValue(TrapDoorBlock.OPEN, false)).setValue(TrapDoorBlock.HALF, Half.BOTTOM)).setValue(TrapDoorBlock.POWERED, false)).setValue(TrapDoorBlock.WATERLOGGED, false));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        if (!(Boolean) iblockdata.getValue(TrapDoorBlock.OPEN)) {
            return iblockdata.getValue(TrapDoorBlock.HALF) == Half.TOP ? TrapDoorBlock.TOP_AABB : TrapDoorBlock.BOTTOM_AABB;
        } else {
            switch ((Direction) iblockdata.getValue(TrapDoorBlock.FACING)) {
                case NORTH:
                default:
                    return TrapDoorBlock.NORTH_OPEN_AABB;
                case SOUTH:
                    return TrapDoorBlock.SOUTH_OPEN_AABB;
                case WEST:
                    return TrapDoorBlock.WEST_OPEN_AABB;
                case EAST:
                    return TrapDoorBlock.EAST_OPEN_AABB;
            }
        }
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        switch (pathmode) {
            case LAND:
                return (Boolean) iblockdata.getValue(TrapDoorBlock.OPEN);
            case WATER:
                return (Boolean) iblockdata.getValue(TrapDoorBlock.WATERLOGGED);
            case AIR:
                return (Boolean) iblockdata.getValue(TrapDoorBlock.OPEN);
            default:
                return false;
        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (this.material == Material.METAL) {
            return InteractionResult.PASS;
        } else {
            iblockdata = (BlockState) iblockdata.cycle((Property) TrapDoorBlock.OPEN);
            world.setTypeAndData(blockposition, iblockdata, 2);
            if ((Boolean) iblockdata.getValue(TrapDoorBlock.WATERLOGGED)) {
                world.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) world));
            }

            this.playSound(entityhuman, world, blockposition, (Boolean) iblockdata.getValue(TrapDoorBlock.OPEN));
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    protected void playSound(@Nullable Player entityhuman, Level world, BlockPos blockposition, boolean flag) {
        int i;

        if (flag) {
            i = this.material == Material.METAL ? 1037 : 1007;
            world.levelEvent(entityhuman, i, blockposition, 0);
        } else {
            i = this.material == Material.METAL ? 1036 : 1013;
            world.levelEvent(entityhuman, i, blockposition, 0);
        }

    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (!world.isClientSide) {
            boolean flag1 = world.hasNeighborSignal(blockposition);

            if (flag1 != (Boolean) iblockdata.getValue(TrapDoorBlock.POWERED)) {
                // CraftBukkit start
                org.bukkit.World bworld = world.getWorld();
                org.bukkit.block.Block bblock = bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

                int power = bblock.getBlockPower();
                int oldPower = (Boolean) iblockdata.getValue(OPEN) ? 15 : 0;

                if (oldPower == 0 ^ power == 0 || block.getBlockData().isSignalSource()) {
                    BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bblock, oldPower, power);
                    world.getServerOH().getPluginManager().callEvent(eventRedstone);
                    flag1 = eventRedstone.getNewCurrent() > 0;
                }
                // CraftBukkit end
                if ((Boolean) iblockdata.getValue(TrapDoorBlock.OPEN) != flag1) {
                    iblockdata = (BlockState) iblockdata.setValue(TrapDoorBlock.OPEN, flag1);
                    this.playSound((Player) null, world, blockposition, flag1);
                }

                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(TrapDoorBlock.POWERED, flag1), 2);
                if ((Boolean) iblockdata.getValue(TrapDoorBlock.WATERLOGGED)) {
                    world.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) world));
                }
            }

        }
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = this.getBlockData();
        FluidState fluid = blockactioncontext.getLevel().getFluidState(blockactioncontext.getClickedPos());
        Direction enumdirection = blockactioncontext.getClickedFace();

        if (!blockactioncontext.replacingClickedOnBlock() && enumdirection.getAxis().isHorizontal()) {
            iblockdata = (BlockState) ((BlockState) iblockdata.setValue(TrapDoorBlock.FACING, enumdirection)).setValue(TrapDoorBlock.HALF, blockactioncontext.getClickLocation().y - (double) blockactioncontext.getClickedPos().getY() > 0.5D ? Half.TOP : Half.BOTTOM);
        } else {
            iblockdata = (BlockState) ((BlockState) iblockdata.setValue(TrapDoorBlock.FACING, blockactioncontext.getHorizontalDirection().getOpposite())).setValue(TrapDoorBlock.HALF, enumdirection == Direction.UP ? Half.BOTTOM : Half.TOP);
        }

        if (blockactioncontext.getLevel().hasNeighborSignal(blockactioncontext.getClickedPos())) {
            iblockdata = (BlockState) ((BlockState) iblockdata.setValue(TrapDoorBlock.OPEN, true)).setValue(TrapDoorBlock.POWERED, true);
        }

        return (BlockState) iblockdata.setValue(TrapDoorBlock.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(TrapDoorBlock.FACING, TrapDoorBlock.OPEN, TrapDoorBlock.HALF, TrapDoorBlock.POWERED, TrapDoorBlock.WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(TrapDoorBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(iblockdata);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if ((Boolean) iblockdata.getValue(TrapDoorBlock.WATERLOGGED)) {
            generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }
}
