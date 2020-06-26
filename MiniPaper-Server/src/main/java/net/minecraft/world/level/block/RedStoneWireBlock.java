package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.math.Vector3f;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class RedStoneWireBlock extends Block {

    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
    public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final Map<Direction, EnumProperty<RedstoneSide>> PROPERTY_BY_DIRECTION = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, RedStoneWireBlock.NORTH, Direction.EAST, RedStoneWireBlock.EAST, Direction.SOUTH, RedStoneWireBlock.SOUTH, Direction.WEST, RedStoneWireBlock.WEST));
    private static final VoxelShape SHAPE_DOT = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 1.0D, 13.0D);
    private static final Map<Direction, VoxelShape> SHAPES_FLOOR = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, Block.box(3.0D, 0.0D, 0.0D, 13.0D, 1.0D, 13.0D), Direction.SOUTH, Block.box(3.0D, 0.0D, 3.0D, 13.0D, 1.0D, 16.0D), Direction.EAST, Block.box(3.0D, 0.0D, 3.0D, 16.0D, 1.0D, 13.0D), Direction.WEST, Block.box(0.0D, 0.0D, 3.0D, 13.0D, 1.0D, 13.0D)));
    private static final Map<Direction, VoxelShape> SHAPES_UP = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, Shapes.or((VoxelShape) RedStoneWireBlock.SHAPES_FLOOR.get(Direction.NORTH), Block.box(3.0D, 0.0D, 0.0D, 13.0D, 16.0D, 1.0D)), Direction.SOUTH, Shapes.or((VoxelShape) RedStoneWireBlock.SHAPES_FLOOR.get(Direction.SOUTH), Block.box(3.0D, 0.0D, 15.0D, 13.0D, 16.0D, 16.0D)), Direction.EAST, Shapes.or((VoxelShape) RedStoneWireBlock.SHAPES_FLOOR.get(Direction.EAST), Block.box(15.0D, 0.0D, 3.0D, 16.0D, 16.0D, 13.0D)), Direction.WEST, Shapes.or((VoxelShape) RedStoneWireBlock.SHAPES_FLOOR.get(Direction.WEST), Block.box(0.0D, 0.0D, 3.0D, 1.0D, 16.0D, 13.0D))));
    private final Map<BlockState, VoxelShape> SHAPES_CACHE = Maps.newHashMap();
    private static final Vector3f[] COLORS = new Vector3f[16];
    private final BlockState crossState;
    private boolean shouldSignal = true;

    public RedStoneWireBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(RedStoneWireBlock.NORTH, RedstoneSide.NONE)).setValue(RedStoneWireBlock.EAST, RedstoneSide.NONE)).setValue(RedStoneWireBlock.SOUTH, RedstoneSide.NONE)).setValue(RedStoneWireBlock.WEST, RedstoneSide.NONE)).setValue(RedStoneWireBlock.POWER, 0));
        this.crossState = (BlockState) ((BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)).setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)).setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE)).setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE);
        UnmodifiableIterator unmodifiableiterator = this.getStateDefinition().getPossibleStates().iterator();

        while (unmodifiableiterator.hasNext()) {
            BlockState iblockdata = (BlockState) unmodifiableiterator.next();

            if ((Integer) iblockdata.getValue(RedStoneWireBlock.POWER) == 0) {
                this.SHAPES_CACHE.put(iblockdata, this.calculateShape(iblockdata));
            }
        }

    }

    private VoxelShape calculateShape(BlockState iblockdata) {
        VoxelShape voxelshape = RedStoneWireBlock.SHAPE_DOT;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            RedstoneSide blockpropertyredstoneside = (RedstoneSide) iblockdata.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection));

            if (blockpropertyredstoneside == RedstoneSide.SIDE) {
                voxelshape = Shapes.or(voxelshape, (VoxelShape) RedStoneWireBlock.SHAPES_FLOOR.get(enumdirection));
            } else if (blockpropertyredstoneside == RedstoneSide.UP) {
                voxelshape = Shapes.or(voxelshape, (VoxelShape) RedStoneWireBlock.SHAPES_UP.get(enumdirection));
            }
        }

        return voxelshape;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return (VoxelShape) this.SHAPES_CACHE.get(iblockdata.setValue(RedStoneWireBlock.POWER, 0));
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return this.getConnectionState((BlockGetter) blockactioncontext.getLevel(), this.crossState, blockactioncontext.getClickedPos());
    }

    private BlockState getConnectionState(BlockGetter iblockaccess, BlockState iblockdata, BlockPos blockposition) {
        boolean flag = isDot(iblockdata);

        iblockdata = this.getMissingConnections(iblockaccess, (BlockState) this.getBlockData().setValue(RedStoneWireBlock.POWER, iblockdata.getValue(RedStoneWireBlock.POWER)), blockposition);
        if (flag && isDot(iblockdata)) {
            return iblockdata;
        } else {
            boolean flag1 = ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.NORTH)).isConnected();
            boolean flag2 = ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.SOUTH)).isConnected();
            boolean flag3 = ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.EAST)).isConnected();
            boolean flag4 = ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.WEST)).isConnected();
            boolean flag5 = !flag1 && !flag2;
            boolean flag6 = !flag3 && !flag4;

            if (!flag4 && flag5) {
                iblockdata = (BlockState) iblockdata.setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE);
            }

            if (!flag3 && flag5) {
                iblockdata = (BlockState) iblockdata.setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE);
            }

            if (!flag1 && flag6) {
                iblockdata = (BlockState) iblockdata.setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE);
            }

            if (!flag2 && flag6) {
                iblockdata = (BlockState) iblockdata.setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
            }

            return iblockdata;
        }
    }

    private BlockState getMissingConnections(BlockGetter iblockaccess, BlockState iblockdata, BlockPos blockposition) {
        boolean flag = !iblockaccess.getType(blockposition.above()).isRedstoneConductor(iblockaccess, blockposition);
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();

            if (!((RedstoneSide) iblockdata.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection))).isConnected()) {
                RedstoneSide blockpropertyredstoneside = this.getConnectingSide(iblockaccess, blockposition, enumdirection, flag);

                iblockdata = (BlockState) iblockdata.setValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection), blockpropertyredstoneside);
            }
        }

        return iblockdata;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection == Direction.DOWN) {
            return iblockdata;
        } else if (enumdirection == Direction.UP) {
            return this.getConnectionState((BlockGetter) generatoraccess, iblockdata, blockposition);
        } else {
            RedstoneSide blockpropertyredstoneside = this.getConnectingSide((BlockGetter) generatoraccess, blockposition, enumdirection);

            return blockpropertyredstoneside.isConnected() == ((RedstoneSide) iblockdata.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection))).isConnected() && !isCross(iblockdata) ? (BlockState) iblockdata.setValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection), blockpropertyredstoneside) : this.getConnectionState((BlockGetter) generatoraccess, (BlockState) ((BlockState) this.crossState.setValue(RedStoneWireBlock.POWER, iblockdata.getValue(RedStoneWireBlock.POWER))).setValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection), blockpropertyredstoneside), blockposition);
        }
    }

    private static boolean isCross(BlockState iblockdata) {
        return ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.NORTH)).isConnected() && ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.SOUTH)).isConnected() && ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.EAST)).isConnected() && ((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.WEST)).isConnected();
    }

    private static boolean isDot(BlockState iblockdata) {
        return !((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.NORTH)).isConnected() && !((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.SOUTH)).isConnected() && !((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.EAST)).isConnected() && !((RedstoneSide) iblockdata.getValue(RedStoneWireBlock.WEST)).isConnected();
    }

    @Override
    public void updateIndirectNeighbourShapes(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, int i, int j) {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            RedstoneSide blockpropertyredstoneside = (RedstoneSide) iblockdata.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection));

            if (blockpropertyredstoneside != RedstoneSide.NONE && !generatoraccess.getType(blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection)).is((Block) this)) {
                blockposition_mutableblockposition.c(Direction.DOWN);
                BlockState iblockdata1 = generatoraccess.getType(blockposition_mutableblockposition);

                if (!iblockdata1.is(Blocks.OBSERVER)) {
                    BlockPos blockposition1 = blockposition_mutableblockposition.relative(enumdirection.getOpposite());
                    BlockState iblockdata2 = iblockdata1.updateState(enumdirection.getOpposite(), generatoraccess.getType(blockposition1), generatoraccess, blockposition_mutableblockposition, blockposition1);

                    updateOrDestroy(iblockdata1, iblockdata2, generatoraccess, blockposition_mutableblockposition, i, j);
                }

                blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection).c(Direction.UP);
                BlockState iblockdata3 = generatoraccess.getType(blockposition_mutableblockposition);

                if (!iblockdata3.is(Blocks.OBSERVER)) {
                    BlockPos blockposition2 = blockposition_mutableblockposition.relative(enumdirection.getOpposite());
                    BlockState iblockdata4 = iblockdata3.updateState(enumdirection.getOpposite(), generatoraccess.getType(blockposition2), generatoraccess, blockposition_mutableblockposition, blockposition2);

                    updateOrDestroy(iblockdata3, iblockdata4, generatoraccess, blockposition_mutableblockposition, i, j);
                }
            }
        }

    }

    private RedstoneSide getConnectingSide(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return this.getConnectingSide(iblockaccess, blockposition, enumdirection, !iblockaccess.getType(blockposition.above()).isRedstoneConductor(iblockaccess, blockposition));
    }

    private RedstoneSide getConnectingSide(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection, boolean flag) {
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        BlockState iblockdata = iblockaccess.getType(blockposition1);

        if (flag) {
            boolean flag1 = this.canSurviveOn(iblockaccess, blockposition1, iblockdata);

            if (flag1 && shouldConnectTo(iblockaccess.getType(blockposition1.above()))) {
                if (iblockdata.isFaceSturdy(iblockaccess, blockposition1, enumdirection.getOpposite())) {
                    return RedstoneSide.UP;
                }

                return RedstoneSide.SIDE;
            }
        }

        return !shouldConnectTo(iblockdata, enumdirection) && (iblockdata.isRedstoneConductor(iblockaccess, blockposition1) || !shouldConnectTo(iblockaccess.getType(blockposition1.below()))) ? RedstoneSide.NONE : RedstoneSide.SIDE;
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();
        BlockState iblockdata1 = iworldreader.getType(blockposition1);

        return this.canSurviveOn((BlockGetter) iworldreader, blockposition1, iblockdata1);
    }

    private boolean canSurviveOn(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata) {
        return iblockdata.isFaceSturdy(iblockaccess, blockposition, Direction.UP) || iblockdata.is(Blocks.HOPPER);
    }

    private void updatePowerStrength(Level world, BlockPos blockposition, BlockState iblockdata) {
        int i = this.calculateTargetStrength(world, blockposition);

        // CraftBukkit start
        int oldPower = (Integer) iblockdata.getValue(RedStoneWireBlock.POWER);
        if (oldPower != i) {
            BlockRedstoneEvent event = new BlockRedstoneEvent(world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), oldPower, i);
            world.getServerOH().getPluginManager().callEvent(event);

            i = event.getNewCurrent();
            // CraftBukkit end
            if (world.getType(blockposition) == iblockdata) {
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(RedStoneWireBlock.POWER, i), 2);
            }

            Set<BlockPos> set = Sets.newHashSet();

            set.add(blockposition);
            Direction[] aenumdirection = Direction.values();
            int j = aenumdirection.length;

            for (int k = 0; k < j; ++k) {
                Direction enumdirection = aenumdirection[k];

                set.add(blockposition.relative(enumdirection));
            }

            Iterator iterator = set.iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition1 = (BlockPos) iterator.next();

                world.updateNeighborsAt(blockposition1, this);
            }
        }

    }

    private int calculateTargetStrength(Level world, BlockPos blockposition) {
        this.shouldSignal = false;
        int i = world.getBestNeighborSignal(blockposition);

        this.shouldSignal = true;
        int j = 0;

        if (i < 15) {
            Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

            while (iterator.hasNext()) {
                Direction enumdirection = (Direction) iterator.next();
                BlockPos blockposition1 = blockposition.relative(enumdirection);
                BlockState iblockdata = world.getType(blockposition1);

                j = Math.max(j, this.getWireSignal(iblockdata));
                BlockPos blockposition2 = blockposition.above();

                if (iblockdata.isRedstoneConductor(world, blockposition1) && !world.getType(blockposition2).isRedstoneConductor(world, blockposition2)) {
                    j = Math.max(j, this.getWireSignal(world.getType(blockposition1.above())));
                } else if (!iblockdata.isRedstoneConductor(world, blockposition1)) {
                    j = Math.max(j, this.getWireSignal(world.getType(blockposition1.below())));
                }
            }
        }

        return Math.max(i, j - 1);
    }

    private int getWireSignal(BlockState iblockdata) {
        return iblockdata.is((Block) this) ? (Integer) iblockdata.getValue(RedStoneWireBlock.POWER) : 0;
    }

    private void checkCornerChangeAt(Level world, BlockPos blockposition) {
        if (world.getType(blockposition).is((Block) this)) {
            world.updateNeighborsAt(blockposition, this);
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                world.updateNeighborsAt(blockposition.relative(enumdirection), this);
            }

        }
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock()) && !world.isClientSide) {
            this.updatePowerStrength(world, blockposition, iblockdata);
            Iterator iterator = Direction.Plane.VERTICAL.iterator();

            while (iterator.hasNext()) {
                Direction enumdirection = (Direction) iterator.next();

                world.updateNeighborsAt(blockposition.relative(enumdirection), this);
            }

            this.updateNeighborsOfNeighboringWires(world, blockposition);
        }
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
            if (!world.isClientSide) {
                Direction[] aenumdirection = Direction.values();
                int i = aenumdirection.length;

                for (int j = 0; j < i; ++j) {
                    Direction enumdirection = aenumdirection[j];

                    world.updateNeighborsAt(blockposition.relative(enumdirection), this);
                }

                this.updatePowerStrength(world, blockposition, iblockdata);
                this.updateNeighborsOfNeighboringWires(world, blockposition);
            }
        }
    }

    private void updateNeighborsOfNeighboringWires(Level world, BlockPos blockposition) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        Direction enumdirection;

        while (iterator.hasNext()) {
            enumdirection = (Direction) iterator.next();
            this.checkCornerChangeAt(world, blockposition.relative(enumdirection));
        }

        iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (world.getType(blockposition1).isRedstoneConductor(world, blockposition1)) {
                this.checkCornerChangeAt(world, blockposition1.above());
            } else {
                this.checkCornerChangeAt(world, blockposition1.below());
            }
        }

    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (!world.isClientSide) {
            if (iblockdata.canSurvive(world, blockposition)) {
                this.updatePowerStrength(world, blockposition, iblockdata);
            } else {
                dropResources(iblockdata, world, blockposition);
                world.removeBlock(blockposition, false);
            }

        }
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return !this.shouldSignal ? 0 : iblockdata.getSignal(iblockaccess, blockposition, enumdirection);
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        if (this.shouldSignal && enumdirection != Direction.DOWN) {
            int i = (Integer) iblockdata.getValue(RedStoneWireBlock.POWER);

            return i == 0 ? 0 : (enumdirection != Direction.UP && !((RedstoneSide) this.getConnectionState(iblockaccess, iblockdata, blockposition).getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection.getOpposite()))).isConnected() ? 0 : i);
        } else {
            return 0;
        }
    }

    protected static boolean shouldConnectTo(BlockState iblockdata) {
        return shouldConnectTo(iblockdata, (Direction) null);
    }

    protected static boolean shouldConnectTo(BlockState iblockdata, @Nullable Direction enumdirection) {
        if (iblockdata.is(Blocks.REDSTONE_WIRE)) {
            return true;
        } else if (iblockdata.is(Blocks.REPEATER)) {
            Direction enumdirection1 = (Direction) iblockdata.getValue(RepeaterBlock.FACING);

            return enumdirection1 == enumdirection || enumdirection1.getOpposite() == enumdirection;
        } else {
            return iblockdata.is(Blocks.OBSERVER) ? enumdirection == iblockdata.getValue(ObserverBlock.FACING) : iblockdata.isSignalSource() && enumdirection != null;
        }
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return this.shouldSignal;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(RedStoneWireBlock.NORTH, iblockdata.getValue(RedStoneWireBlock.SOUTH))).setValue(RedStoneWireBlock.EAST, iblockdata.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.SOUTH, iblockdata.getValue(RedStoneWireBlock.NORTH))).setValue(RedStoneWireBlock.WEST, iblockdata.getValue(RedStoneWireBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(RedStoneWireBlock.NORTH, iblockdata.getValue(RedStoneWireBlock.EAST))).setValue(RedStoneWireBlock.EAST, iblockdata.getValue(RedStoneWireBlock.SOUTH))).setValue(RedStoneWireBlock.SOUTH, iblockdata.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.WEST, iblockdata.getValue(RedStoneWireBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(RedStoneWireBlock.NORTH, iblockdata.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.EAST, iblockdata.getValue(RedStoneWireBlock.NORTH))).setValue(RedStoneWireBlock.SOUTH, iblockdata.getValue(RedStoneWireBlock.EAST))).setValue(RedStoneWireBlock.WEST, iblockdata.getValue(RedStoneWireBlock.SOUTH));
            default:
                return iblockdata;
        }
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        switch (enumblockmirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) iblockdata.setValue(RedStoneWireBlock.NORTH, iblockdata.getValue(RedStoneWireBlock.SOUTH))).setValue(RedStoneWireBlock.SOUTH, iblockdata.getValue(RedStoneWireBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) iblockdata.setValue(RedStoneWireBlock.EAST, iblockdata.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.WEST, iblockdata.getValue(RedStoneWireBlock.EAST));
            default:
                return super.mirror(iblockdata, enumblockmirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(RedStoneWireBlock.NORTH, RedStoneWireBlock.EAST, RedStoneWireBlock.SOUTH, RedStoneWireBlock.WEST, RedStoneWireBlock.POWER);
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (!entityhuman.abilities.mayBuild) {
            return InteractionResult.PASS;
        } else {
            if (isCross(iblockdata) || isDot(iblockdata)) {
                BlockState iblockdata1 = isCross(iblockdata) ? this.getBlockData() : this.crossState;

                iblockdata1 = (BlockState) iblockdata1.setValue(RedStoneWireBlock.POWER, iblockdata.getValue(RedStoneWireBlock.POWER));
                iblockdata1 = this.getConnectionState((BlockGetter) world, iblockdata1, blockposition);
                if (iblockdata1 != iblockdata) {
                    world.setTypeAndData(blockposition, iblockdata1, 3);
                    this.updatesOnShapeChange(world, blockposition, iblockdata, iblockdata1);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }
    }

    private void updatesOnShapeChange(Level world, BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (((RedstoneSide) iblockdata.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection))).isConnected() != ((RedstoneSide) iblockdata1.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection))).isConnected() && world.getType(blockposition1).isRedstoneConductor(world, blockposition1)) {
                world.updateNeighborsAtExceptFromFacing(blockposition1, iblockdata1.getBlock(), enumdirection.getOpposite());
            }
        }

    }

    static {
        for (int i = 0; i <= 15; ++i) {
            float f = (float) i / 15.0F;
            float f1 = f * 0.6F + (f > 0.0F ? 0.4F : 0.3F);
            float f2 = Mth.clamp(f * f * 0.7F - 0.5F, 0.0F, 1.0F);
            float f3 = Mth.clamp(f * f * 0.6F - 0.7F, 0.0F, 1.0F);

            RedStoneWireBlock.COLORS[i] = new Vector3f(f1, f2, f3);
        }

    }
}
