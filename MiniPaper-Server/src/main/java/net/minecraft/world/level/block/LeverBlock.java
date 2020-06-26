package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    protected static final VoxelShape NORTH_AABB = Block.box(5.0D, 4.0D, 10.0D, 11.0D, 12.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0D, 4.0D, 0.0D, 11.0D, 12.0D, 6.0D);
    protected static final VoxelShape WEST_AABB = Block.box(10.0D, 4.0D, 5.0D, 16.0D, 12.0D, 11.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 4.0D, 5.0D, 6.0D, 12.0D, 11.0D);
    protected static final VoxelShape UP_AABB_Z = Block.box(5.0D, 0.0D, 4.0D, 11.0D, 6.0D, 12.0D);
    protected static final VoxelShape UP_AABB_X = Block.box(4.0D, 0.0D, 5.0D, 12.0D, 6.0D, 11.0D);
    protected static final VoxelShape DOWN_AABB_Z = Block.box(5.0D, 10.0D, 4.0D, 11.0D, 16.0D, 12.0D);
    protected static final VoxelShape DOWN_AABB_X = Block.box(4.0D, 10.0D, 5.0D, 12.0D, 16.0D, 11.0D);

    protected LeverBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LeverBlock.FACING, Direction.NORTH)).setValue(LeverBlock.POWERED, false)).setValue(LeverBlock.FACE, AttachFace.WALL));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        switch ((AttachFace) iblockdata.getValue(LeverBlock.FACE)) {
            case FLOOR:
                switch (((Direction) iblockdata.getValue(LeverBlock.FACING)).getAxis()) {
                    case X:
                        return LeverBlock.UP_AABB_X;
                    case Z:
                    default:
                        return LeverBlock.UP_AABB_Z;
                }
            case WALL:
                switch ((Direction) iblockdata.getValue(LeverBlock.FACING)) {
                    case EAST:
                        return LeverBlock.EAST_AABB;
                    case WEST:
                        return LeverBlock.WEST_AABB;
                    case SOUTH:
                        return LeverBlock.SOUTH_AABB;
                    case NORTH:
                    default:
                        return LeverBlock.NORTH_AABB;
                }
            case CEILING:
            default:
                switch (((Direction) iblockdata.getValue(LeverBlock.FACING)).getAxis()) {
                    case X:
                        return LeverBlock.DOWN_AABB_X;
                    case Z:
                    default:
                        return LeverBlock.DOWN_AABB_Z;
                }
        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        BlockState iblockdata1;

        if (world.isClientSide) {
            iblockdata1 = (BlockState) iblockdata.cycle((Property) LeverBlock.POWERED);
            if ((Boolean) iblockdata1.getValue(LeverBlock.POWERED)) {
                makeParticle(iblockdata1, world, blockposition, 1.0F);
            }

            return InteractionResult.SUCCESS;
        } else {
            // CraftBukkit start - Interact Lever
            boolean powered = iblockdata.getValue(LeverBlock.POWERED); // Old powered state
            org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getServerOH().getPluginManager().callEvent(eventRedstone);

            if ((eventRedstone.getNewCurrent() > 0) != (!powered)) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end

            iblockdata1 = this.pull(iblockdata, world, blockposition);
            float f = (Boolean) iblockdata1.getValue(LeverBlock.POWERED) ? 0.6F : 0.5F;

            world.playSound((Player) null, blockposition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
            return InteractionResult.CONSUME;
        }
    }

    public BlockState pull(BlockState iblockdata, Level world, BlockPos blockposition) {
        iblockdata = (BlockState) iblockdata.cycle((Property) LeverBlock.POWERED);
        world.setTypeAndData(blockposition, iblockdata, 3);
        this.updateNeighbours(iblockdata, world, blockposition);
        return iblockdata;
    }

    private static void makeParticle(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, float f) {
        Direction enumdirection = ((Direction) iblockdata.getValue(LeverBlock.FACING)).getOpposite();
        Direction enumdirection1 = getConnectedDirection(iblockdata).getOpposite();
        double d0 = (double) blockposition.getX() + 0.5D + 0.1D * (double) enumdirection.getStepX() + 0.2D * (double) enumdirection1.getStepX();
        double d1 = (double) blockposition.getY() + 0.5D + 0.1D * (double) enumdirection.getStepY() + 0.2D * (double) enumdirection1.getStepY();
        double d2 = (double) blockposition.getZ() + 0.5D + 0.1D * (double) enumdirection.getStepZ() + 0.2D * (double) enumdirection1.getStepZ();

        generatoraccess.addParticle(new DustParticleOptions(1.0F, 0.0F, 0.0F, f), d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            if ((Boolean) iblockdata.getValue(LeverBlock.POWERED)) {
                this.updateNeighbours(iblockdata, world, blockposition);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(LeverBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(LeverBlock.POWERED) && getConnectedDirection(iblockdata) == enumdirection ? 15 : 0;
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    private void updateNeighbours(BlockState iblockdata, Level world, BlockPos blockposition) {
        world.updateNeighborsAt(blockposition, this);
        world.updateNeighborsAt(blockposition.relative(getConnectedDirection(iblockdata).getOpposite()), this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(LeverBlock.FACE, LeverBlock.FACING, LeverBlock.POWERED);
    }
}
