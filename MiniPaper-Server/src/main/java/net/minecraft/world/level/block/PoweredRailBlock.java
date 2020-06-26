package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class PoweredRailBlock extends BaseRailBlock {

    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected PoweredRailBlock(BlockBehaviour.Info blockbase_info) {
        super(true, blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH)).setValue(PoweredRailBlock.POWERED, false));
    }

    protected boolean findPoweredRailSignal(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag, int i) {
        if (i >= 8) {
            return false;
        } else {
            int j = blockposition.getX();
            int k = blockposition.getY();
            int l = blockposition.getZ();
            boolean flag1 = true;
            RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE);

            switch (blockpropertytrackposition) {
                case NORTH_SOUTH:
                    if (flag) {
                        ++l;
                    } else {
                        --l;
                    }
                    break;
                case EAST_WEST:
                    if (flag) {
                        --j;
                    } else {
                        ++j;
                    }
                    break;
                case ASCENDING_EAST:
                    if (flag) {
                        --j;
                    } else {
                        ++j;
                        ++k;
                        flag1 = false;
                    }

                    blockpropertytrackposition = RailShape.EAST_WEST;
                    break;
                case ASCENDING_WEST:
                    if (flag) {
                        --j;
                        ++k;
                        flag1 = false;
                    } else {
                        ++j;
                    }

                    blockpropertytrackposition = RailShape.EAST_WEST;
                    break;
                case ASCENDING_NORTH:
                    if (flag) {
                        ++l;
                    } else {
                        --l;
                        ++k;
                        flag1 = false;
                    }

                    blockpropertytrackposition = RailShape.NORTH_SOUTH;
                    break;
                case ASCENDING_SOUTH:
                    if (flag) {
                        ++l;
                        ++k;
                        flag1 = false;
                    } else {
                        --l;
                    }

                    blockpropertytrackposition = RailShape.NORTH_SOUTH;
            }

            return this.isSameRailWithPower(world, new BlockPos(j, k, l), flag, i, blockpropertytrackposition) ? true : flag1 && this.isSameRailWithPower(world, new BlockPos(j, k - 1, l), flag, i, blockpropertytrackposition);
        }
    }

    protected boolean isSameRailWithPower(Level world, BlockPos blockposition, boolean flag, int i, RailShape blockpropertytrackposition) {
        BlockState iblockdata = world.getType(blockposition);

        if (!iblockdata.is((Block) this)) {
            return false;
        } else {
            RailShape blockpropertytrackposition1 = (RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE);

            return blockpropertytrackposition == RailShape.EAST_WEST && (blockpropertytrackposition1 == RailShape.NORTH_SOUTH || blockpropertytrackposition1 == RailShape.ASCENDING_NORTH || blockpropertytrackposition1 == RailShape.ASCENDING_SOUTH) ? false : (blockpropertytrackposition == RailShape.NORTH_SOUTH && (blockpropertytrackposition1 == RailShape.EAST_WEST || blockpropertytrackposition1 == RailShape.ASCENDING_EAST || blockpropertytrackposition1 == RailShape.ASCENDING_WEST) ? false : ((Boolean) iblockdata.getValue(PoweredRailBlock.POWERED) ? (world.hasNeighborSignal(blockposition) ? true : this.findPoweredRailSignal(world, blockposition, iblockdata, flag, i + 1)) : false));
        }
    }

    @Override
    protected void updateState(BlockState iblockdata, Level world, BlockPos blockposition, Block block) {
        boolean flag = (Boolean) iblockdata.getValue(PoweredRailBlock.POWERED);
        boolean flag1 = world.hasNeighborSignal(blockposition) || this.findPoweredRailSignal(world, blockposition, iblockdata, true, 0) || this.findPoweredRailSignal(world, blockposition, iblockdata, false, 0);

        if (flag1 != flag) {
            // CraftBukkit start
            int power = flag ? 15 : 0;
            int newPower = CraftEventFactory.callRedstoneChange(world, blockposition, power, 15 - power).getNewCurrent();
            if (newPower == power) {
                return;
            }
            // CraftBukkit end
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(PoweredRailBlock.POWERED, flag1), 3);
            world.updateNeighborsAt(blockposition.below(), this);
            if (((RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE)).isAscending()) {
                world.updateNeighborsAt(blockposition.above(), this);
            }
        }

    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return PoweredRailBlock.SHAPE;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                switch ((RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                }
            case COUNTERCLOCKWISE_90:
                switch ((RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE)) {
                    case NORTH_SOUTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                }
            case CLOCKWISE_90:
                switch ((RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE)) {
                    case NORTH_SOUTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                }
            default:
                return iblockdata;
        }
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE);

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                switch (blockpropertytrackposition) {
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    default:
                        return super.mirror(iblockdata, enumblockmirror);
                }
            case FRONT_BACK:
                switch (blockpropertytrackposition) {
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    default:
                        break;
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                }
        }

        return super.mirror(iblockdata, enumblockmirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(PoweredRailBlock.SHAPE, PoweredRailBlock.POWERED);
    }
}
