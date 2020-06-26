package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class VineBlock extends Block {

    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != Direction.DOWN;
    }).collect(Util.toMap());
    protected static final VoxelShape UP_AABB = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);

    public VineBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(VineBlock.UP, false)).setValue(VineBlock.NORTH, false)).setValue(VineBlock.EAST, false)).setValue(VineBlock.SOUTH, false)).setValue(VineBlock.WEST, false));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        VoxelShape voxelshape = Shapes.empty();

        if ((Boolean) iblockdata.getValue(VineBlock.UP)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.UP_AABB);
        }

        if ((Boolean) iblockdata.getValue(VineBlock.NORTH)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.SOUTH_AABB);
        }

        if ((Boolean) iblockdata.getValue(VineBlock.EAST)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.WEST_AABB);
        }

        if ((Boolean) iblockdata.getValue(VineBlock.SOUTH)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.NORTH_AABB);
        }

        if ((Boolean) iblockdata.getValue(VineBlock.WEST)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.EAST_AABB);
        }

        return voxelshape;
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return this.hasFaces(this.getUpdatedState(iblockdata, iworldreader, blockposition));
    }

    private boolean hasFaces(BlockState iblockdata) {
        return this.countFaces(iblockdata) > 0;
    }

    private int countFaces(BlockState iblockdata) {
        int i = 0;
        Iterator iterator = VineBlock.PROPERTY_BY_DIRECTION.values().iterator();

        while (iterator.hasNext()) {
            BooleanProperty blockstateboolean = (BooleanProperty) iterator.next();

            if ((Boolean) iblockdata.getValue(blockstateboolean)) {
                ++i;
            }
        }

        return i;
    }

    private boolean canSupportAtFace(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        if (enumdirection == Direction.DOWN) {
            return false;
        } else {
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (isAcceptableNeighbour(iblockaccess, blockposition1, enumdirection)) {
                return true;
            } else if (enumdirection.getAxis() == Direction.Axis.Y) {
                return false;
            } else {
                BooleanProperty blockstateboolean = (BooleanProperty) VineBlock.PROPERTY_BY_DIRECTION.get(enumdirection);
                BlockState iblockdata = iblockaccess.getType(blockposition.above());

                return iblockdata.is((Block) this) && (Boolean) iblockdata.getValue(blockstateboolean);
            }
        }
    }

    public static boolean isAcceptableNeighbour(BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        BlockState iblockdata = iblockaccess.getType(blockposition);

        return Block.isFaceFull(iblockdata.getCollisionShape(iblockaccess, blockposition), enumdirection.getOpposite());
    }

    private BlockState getUpdatedState(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.above();

        if ((Boolean) iblockdata.getValue(VineBlock.UP)) {
            iblockdata = (BlockState) iblockdata.setValue(VineBlock.UP, isAcceptableNeighbour(iblockaccess, blockposition1, Direction.DOWN));
        }

        BlockState iblockdata1 = null;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BooleanProperty blockstateboolean = getPropertyForFace(enumdirection);

            if ((Boolean) iblockdata.getValue(blockstateboolean)) {
                boolean flag = this.canSupportAtFace(iblockaccess, blockposition, enumdirection);

                if (!flag) {
                    if (iblockdata1 == null) {
                        iblockdata1 = iblockaccess.getType(blockposition1);
                    }

                    flag = iblockdata1.is((Block) this) && (Boolean) iblockdata1.getValue(blockstateboolean);
                }

                iblockdata = (BlockState) iblockdata.setValue(blockstateboolean, flag);
            }
        }

        return iblockdata;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection == Direction.DOWN) {
            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        } else {
            BlockState iblockdata2 = this.getUpdatedState(iblockdata, generatoraccess, blockposition);

            return !this.hasFaces(iblockdata2) ? Blocks.AIR.getBlockData() : iblockdata2;
        }
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.random.nextInt(Math.max(1, (int) (100.0F / worldserver.spigotConfig.vineModifier) * 4)) == 0) { // Spigot
            Direction enumdirection = Direction.getRandom(random);
            BlockPos blockposition1 = blockposition.above();
            BlockPos blockposition2;
            BlockState iblockdata1;
            Direction enumdirection1;

            if (enumdirection.getAxis().isHorizontal() && !(Boolean) iblockdata.getValue(getPropertyForFace(enumdirection))) {
                if (this.canSpread((BlockGetter) worldserver, blockposition)) {
                    blockposition2 = blockposition.relative(enumdirection);
                    iblockdata1 = worldserver.getType(blockposition2);
                    if (iblockdata1.isAir()) {
                        enumdirection1 = enumdirection.getClockWise();
                        Direction enumdirection2 = enumdirection.getCounterClockWise();
                        boolean flag = (Boolean) iblockdata.getValue(getPropertyForFace(enumdirection1));
                        boolean flag1 = (Boolean) iblockdata.getValue(getPropertyForFace(enumdirection2));
                        BlockPos blockposition3 = blockposition2.relative(enumdirection1);
                        BlockPos blockposition4 = blockposition2.relative(enumdirection2);

                        // CraftBukkit start - Call BlockSpreadEvent
                        BlockPos source = blockposition;

                        if (flag && isAcceptableNeighbour((BlockGetter) worldserver, blockposition3, enumdirection1)) {
                            CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition2, (BlockState) this.getBlockData().setValue(getPropertyForFace(enumdirection1), true), 2);
                        } else if (flag1 && isAcceptableNeighbour((BlockGetter) worldserver, blockposition4, enumdirection2)) {
                            CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition2, (BlockState) this.getBlockData().setValue(getPropertyForFace(enumdirection2), true), 2);
                        } else {
                            Direction enumdirection3 = enumdirection.getOpposite();

                            if (flag && worldserver.isEmptyBlock(blockposition3) && isAcceptableNeighbour((BlockGetter) worldserver, blockposition.relative(enumdirection1), enumdirection3)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition3, (BlockState) this.getBlockData().setValue(getPropertyForFace(enumdirection3), true), 2);
                            } else if (flag1 && worldserver.isEmptyBlock(blockposition4) && isAcceptableNeighbour((BlockGetter) worldserver, blockposition.relative(enumdirection2), enumdirection3)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition4, (BlockState) this.getBlockData().setValue(getPropertyForFace(enumdirection3), true), 2);
                            } else if ((double) worldserver.random.nextFloat() < 0.05D && isAcceptableNeighbour((BlockGetter) worldserver, blockposition2.above(), Direction.UP)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition2, (BlockState) this.getBlockData().setValue(VineBlock.UP, true), 2);
                            }
                            // CraftBukkit end
                        }
                    } else if (isAcceptableNeighbour((BlockGetter) worldserver, blockposition2, enumdirection)) {
                        worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(getPropertyForFace(enumdirection), true), 2);
                    }

                }
            } else {
                if (enumdirection == Direction.UP && blockposition.getY() < 255) {
                    if (this.canSupportAtFace((BlockGetter) worldserver, blockposition, enumdirection)) {
                        worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(VineBlock.UP, true), 2);
                        return;
                    }

                    if (worldserver.isEmptyBlock(blockposition1)) {
                        if (!this.canSpread((BlockGetter) worldserver, blockposition)) {
                            return;
                        }

                        BlockState iblockdata2 = iblockdata;
                        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                        while (iterator.hasNext()) {
                            enumdirection1 = (Direction) iterator.next();
                            if (random.nextBoolean() || !isAcceptableNeighbour((BlockGetter) worldserver, blockposition1.relative(enumdirection1), Direction.UP)) {
                                iblockdata2 = (BlockState) iblockdata2.setValue(getPropertyForFace(enumdirection1), false);
                            }
                        }

                        if (this.canSpread(iblockdata2)) {
                            CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition1, iblockdata2, 2); // CraftBukkit
                        }

                        return;
                    }
                }

                if (blockposition.getY() > 0) {
                    blockposition2 = blockposition.below();
                    iblockdata1 = worldserver.getType(blockposition2);
                    if (iblockdata1.isAir() || iblockdata1.is((Block) this)) {
                        BlockState iblockdata3 = iblockdata1.isAir() ? this.getBlockData() : iblockdata1;
                        BlockState iblockdata4 = this.copyRandomFaces(iblockdata, iblockdata3, random);

                        if (iblockdata3 != iblockdata4 && this.canSpread(iblockdata4)) {
                            CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition2, iblockdata4, 2); // CraftBukkit
                        }
                    }
                }

            }
        }
    }

    private BlockState copyRandomFaces(BlockState iblockdata, BlockState iblockdata1, Random random) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();

            if (random.nextBoolean()) {
                BooleanProperty blockstateboolean = getPropertyForFace(enumdirection);

                if ((Boolean) iblockdata.getValue(blockstateboolean)) {
                    iblockdata1 = (BlockState) iblockdata1.setValue(blockstateboolean, true);
                }
            }
        }

        return iblockdata1;
    }

    private boolean canSpread(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(VineBlock.NORTH) || (Boolean) iblockdata.getValue(VineBlock.EAST) || (Boolean) iblockdata.getValue(VineBlock.SOUTH) || (Boolean) iblockdata.getValue(VineBlock.WEST);
    }

    private boolean canSpread(BlockGetter iblockaccess, BlockPos blockposition) {
        boolean flag = true;
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(blockposition.getX() - 4, blockposition.getY() - 1, blockposition.getZ() - 4, blockposition.getX() + 4, blockposition.getY() + 1, blockposition.getZ() + 4);
        int i = 5;
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();

            if (iblockaccess.getType(blockposition1).is((Block) this)) {
                --i;
                if (i <= 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean canBeReplaced(BlockState iblockdata, BlockPlaceContext blockactioncontext) {
        BlockState iblockdata1 = blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos());

        return iblockdata1.is((Block) this) ? this.countFaces(iblockdata1) < VineBlock.PROPERTY_BY_DIRECTION.size() : super.canBeReplaced(iblockdata, blockactioncontext);
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos());
        boolean flag = iblockdata.is((Block) this);
        BlockState iblockdata1 = flag ? iblockdata : this.getBlockData();
        Direction[] aenumdirection = blockactioncontext.getNearestLookingDirections();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (enumdirection != Direction.DOWN) {
                BooleanProperty blockstateboolean = getPropertyForFace(enumdirection);
                boolean flag1 = flag && (Boolean) iblockdata.getValue(blockstateboolean);

                if (!flag1 && this.canSupportAtFace((BlockGetter) blockactioncontext.getLevel(), blockactioncontext.getClickedPos(), enumdirection)) {
                    return (BlockState) iblockdata1.setValue(blockstateboolean, true);
                }
            }
        }

        return flag ? iblockdata1 : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(VineBlock.UP, VineBlock.NORTH, VineBlock.EAST, VineBlock.SOUTH, VineBlock.WEST);
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(VineBlock.NORTH, iblockdata.getValue(VineBlock.SOUTH))).setValue(VineBlock.EAST, iblockdata.getValue(VineBlock.WEST))).setValue(VineBlock.SOUTH, iblockdata.getValue(VineBlock.NORTH))).setValue(VineBlock.WEST, iblockdata.getValue(VineBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(VineBlock.NORTH, iblockdata.getValue(VineBlock.EAST))).setValue(VineBlock.EAST, iblockdata.getValue(VineBlock.SOUTH))).setValue(VineBlock.SOUTH, iblockdata.getValue(VineBlock.WEST))).setValue(VineBlock.WEST, iblockdata.getValue(VineBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(VineBlock.NORTH, iblockdata.getValue(VineBlock.WEST))).setValue(VineBlock.EAST, iblockdata.getValue(VineBlock.NORTH))).setValue(VineBlock.SOUTH, iblockdata.getValue(VineBlock.EAST))).setValue(VineBlock.WEST, iblockdata.getValue(VineBlock.SOUTH));
            default:
                return iblockdata;
        }
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        switch (enumblockmirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) iblockdata.setValue(VineBlock.NORTH, iblockdata.getValue(VineBlock.SOUTH))).setValue(VineBlock.SOUTH, iblockdata.getValue(VineBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) iblockdata.setValue(VineBlock.EAST, iblockdata.getValue(VineBlock.WEST))).setValue(VineBlock.WEST, iblockdata.getValue(VineBlock.EAST));
            default:
                return super.mirror(iblockdata, enumblockmirror);
        }
    }

    public static BooleanProperty getPropertyForFace(Direction enumdirection) {
        return (BooleanProperty) VineBlock.PROPERTY_BY_DIRECTION.get(enumdirection);
    }
}
