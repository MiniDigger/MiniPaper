package net.minecraft.world.level.block;

import com.google.common.base.MoreObjects;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class TripWireHookBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    protected static final VoxelShape NORTH_AABB = Block.box(5.0D, 0.0D, 10.0D, 11.0D, 10.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0D, 0.0D, 0.0D, 11.0D, 10.0D, 6.0D);
    protected static final VoxelShape WEST_AABB = Block.box(10.0D, 0.0D, 5.0D, 16.0D, 10.0D, 11.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 5.0D, 6.0D, 10.0D, 11.0D);

    public TripWireHookBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TripWireHookBlock.FACING, Direction.NORTH)).setValue(TripWireHookBlock.POWERED, false)).setValue(TripWireHookBlock.ATTACHED, false));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        switch ((Direction) iblockdata.getValue(TripWireHookBlock.FACING)) {
            case EAST:
            default:
                return TripWireHookBlock.EAST_AABB;
            case WEST:
                return TripWireHookBlock.WEST_AABB;
            case SOUTH:
                return TripWireHookBlock.SOUTH_AABB;
            case NORTH:
                return TripWireHookBlock.NORTH_AABB;
        }
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        Direction enumdirection = (Direction) iblockdata.getValue(TripWireHookBlock.FACING);
        BlockPos blockposition1 = blockposition.relative(enumdirection.getOpposite());
        BlockState iblockdata1 = iworldreader.getType(blockposition1);

        return enumdirection.getAxis().isHorizontal() && iblockdata1.isFaceSturdy(iworldreader, blockposition1, enumdirection);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection.getOpposite() == iblockdata.getValue(TripWireHookBlock.FACING) && !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = (BlockState) ((BlockState) this.getBlockData().setValue(TripWireHookBlock.POWERED, false)).setValue(TripWireHookBlock.ATTACHED, false);
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        Direction[] aenumdirection = blockactioncontext.getNearestLookingDirections();
        Direction[] aenumdirection1 = aenumdirection;
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection1[j];

            if (enumdirection.getAxis().isHorizontal()) {
                Direction enumdirection1 = enumdirection.getOpposite();

                iblockdata = (BlockState) iblockdata.setValue(TripWireHookBlock.FACING, enumdirection1);
                if (iblockdata.canSurvive(world, blockposition)) {
                    return iblockdata;
                }
            }
        }

        return null;
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        this.calculateState(world, blockposition, iblockdata, false, false, -1, (BlockState) null);
    }

    public void calculateState(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag, boolean flag1, int i, @Nullable BlockState iblockdata1) {
        Direction enumdirection = (Direction) iblockdata.getValue(TripWireHookBlock.FACING);
        boolean flag2 = (Boolean) iblockdata.getValue(TripWireHookBlock.ATTACHED);
        boolean flag3 = (Boolean) iblockdata.getValue(TripWireHookBlock.POWERED);
        boolean flag4 = !flag;
        boolean flag5 = false;
        int j = 0;
        BlockState[] aiblockdata = new BlockState[42];

        BlockPos blockposition1;

        for (int k = 1; k < 42; ++k) {
            blockposition1 = blockposition.relative(enumdirection, k);
            BlockState iblockdata2 = world.getType(blockposition1);

            if (iblockdata2.is(Blocks.TRIPWIRE_HOOK)) {
                if (iblockdata2.getValue(TripWireHookBlock.FACING) == enumdirection.getOpposite()) {
                    j = k;
                }
                break;
            }

            if (!iblockdata2.is(Blocks.TRIPWIRE) && k != i) {
                aiblockdata[k] = null;
                flag4 = false;
            } else {
                if (k == i) {
                    iblockdata2 = (BlockState) MoreObjects.firstNonNull(iblockdata1, iblockdata2);
                }

                boolean flag6 = !(Boolean) iblockdata2.getValue(TripWireBlock.DISARMED);
                boolean flag7 = (Boolean) iblockdata2.getValue(TripWireBlock.POWERED);

                flag5 |= flag6 && flag7;
                aiblockdata[k] = iblockdata2;
                if (k == i) {
                    world.getBlockTickList().scheduleTick(blockposition, this, 10);
                    flag4 &= flag6;
                }
            }
        }

        flag4 &= j > 1;
        flag5 &= flag4;
        BlockState iblockdata3 = (BlockState) ((BlockState) this.getBlockData().setValue(TripWireHookBlock.ATTACHED, flag4)).setValue(TripWireHookBlock.POWERED, flag5);

        if (j > 0) {
            blockposition1 = blockposition.relative(enumdirection, j);
            Direction enumdirection1 = enumdirection.getOpposite();

            world.setTypeAndData(blockposition1, (BlockState) iblockdata3.setValue(TripWireHookBlock.FACING, enumdirection1), 3);
            this.notifyNeighbors(world, blockposition1, enumdirection1);
            this.playSound(world, blockposition1, flag4, flag5, flag2, flag3);
        }

        // CraftBukkit start
        org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

        BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, 15, 0);
        world.getServerOH().getPluginManager().callEvent(eventRedstone);

        if (eventRedstone.getNewCurrent() > 0) {
            return;
        }
        // CraftBukkit end

        this.playSound(world, blockposition, flag4, flag5, flag2, flag3);
        if (!flag) {
            world.setTypeAndData(blockposition, (BlockState) iblockdata3.setValue(TripWireHookBlock.FACING, enumdirection), 3);
            if (flag1) {
                this.notifyNeighbors(world, blockposition, enumdirection);
            }
        }

        if (flag2 != flag4) {
            for (int l = 1; l < j; ++l) {
                BlockPos blockposition2 = blockposition.relative(enumdirection, l);
                BlockState iblockdata4 = aiblockdata[l];

                if (iblockdata4 != null) {
                    world.setTypeAndData(blockposition2, (BlockState) iblockdata4.setValue(TripWireHookBlock.ATTACHED, flag4), 3);
                    if (!world.getType(blockposition2).isAir()) {
                        ;
                    }
                }
            }
        }

    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        this.calculateState(worldserver, blockposition, iblockdata, false, true, -1, (BlockState) null);
    }

    private void playSound(Level world, BlockPos blockposition, boolean flag, boolean flag1, boolean flag2, boolean flag3) {
        if (flag1 && !flag3) {
            world.playSound((Player) null, blockposition, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 0.6F);
        } else if (!flag1 && flag3) {
            world.playSound((Player) null, blockposition, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.5F);
        } else if (flag && !flag2) {
            world.playSound((Player) null, blockposition, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4F, 0.7F);
        } else if (!flag && flag2) {
            world.playSound((Player) null, blockposition, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 0.4F, 1.2F / (world.random.nextFloat() * 0.2F + 0.9F));
        }

    }

    private void notifyNeighbors(Level world, BlockPos blockposition, Direction enumdirection) {
        world.updateNeighborsAt(blockposition, this);
        world.updateNeighborsAt(blockposition.relative(enumdirection.getOpposite()), this);
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            boolean flag1 = (Boolean) iblockdata.getValue(TripWireHookBlock.ATTACHED);
            boolean flag2 = (Boolean) iblockdata.getValue(TripWireHookBlock.POWERED);

            if (flag1 || flag2) {
                this.calculateState(world, blockposition, iblockdata, true, false, -1, (BlockState) null);
            }

            if (flag2) {
                world.updateNeighborsAt(blockposition, this);
                world.updateNeighborsAt(blockposition.relative(((Direction) iblockdata.getValue(TripWireHookBlock.FACING)).getOpposite()), this);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(TripWireHookBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return !(Boolean) iblockdata.getValue(TripWireHookBlock.POWERED) ? 0 : (iblockdata.getValue(TripWireHookBlock.FACING) == enumdirection ? 15 : 0);
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(TripWireHookBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(TripWireHookBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(TripWireHookBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(TripWireHookBlock.FACING, TripWireHookBlock.POWERED, TripWireHookBlock.ATTACHED);
    }
}
