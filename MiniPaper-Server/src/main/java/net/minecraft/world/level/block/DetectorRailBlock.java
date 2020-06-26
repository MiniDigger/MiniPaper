package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class DetectorRailBlock extends BaseRailBlock {

    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public DetectorRailBlock(BlockBehaviour.Info blockbase_info) {
        super(true, blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DetectorRailBlock.POWERED, false)).setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH));
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!world.isClientSide) {
            if (!(Boolean) iblockdata.getValue(DetectorRailBlock.POWERED)) {
                this.checkPressed(world, blockposition, iblockdata);
            }
        }
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Boolean) iblockdata.getValue(DetectorRailBlock.POWERED)) {
            this.checkPressed((Level) worldserver, blockposition, iblockdata);
        }
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(DetectorRailBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return !(Boolean) iblockdata.getValue(DetectorRailBlock.POWERED) ? 0 : (enumdirection == Direction.UP ? 15 : 0);
    }

    private void checkPressed(Level world, BlockPos blockposition, BlockState iblockdata) {
        boolean flag = (Boolean) iblockdata.getValue(DetectorRailBlock.POWERED);
        boolean flag1 = false;
        List<AbstractMinecart> list = this.getInteractingMinecartOfType(world, blockposition, AbstractMinecart.class, (Predicate) null);

        if (!list.isEmpty()) {
            flag1 = true;
        }

        BlockState iblockdata1;
        // CraftBukkit start
        if (flag != flag1) {
            org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, flag ? 15 : 0, flag1 ? 15 : 0);
            world.getServerOH().getPluginManager().callEvent(eventRedstone);

            flag1 = eventRedstone.getNewCurrent() > 0;
        }
        // CraftBukkit end

        if (flag1 && !flag) {
            iblockdata1 = (BlockState) iblockdata.setValue(DetectorRailBlock.POWERED, true);
            world.setTypeAndData(blockposition, iblockdata1, 3);
            this.updatePowerToConnected(world, blockposition, iblockdata1, true);
            world.updateNeighborsAt(blockposition, this);
            world.updateNeighborsAt(blockposition.below(), this);
            world.setBlocksDirty(blockposition, iblockdata, iblockdata1);
        }

        if (!flag1 && flag) {
            iblockdata1 = (BlockState) iblockdata.setValue(DetectorRailBlock.POWERED, false);
            world.setTypeAndData(blockposition, iblockdata1, 3);
            this.updatePowerToConnected(world, blockposition, iblockdata1, false);
            world.updateNeighborsAt(blockposition, this);
            world.updateNeighborsAt(blockposition.below(), this);
            world.setBlocksDirty(blockposition, iblockdata, iblockdata1);
        }

        if (flag1) {
            world.getBlockTickList().scheduleTick(blockposition, this, 20);
        }

        world.updateNeighbourForOutputSignal(blockposition, this);
    }

    protected void updatePowerToConnected(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        RailState minecarttracklogic = new RailState(world, blockposition, iblockdata);
        List<BlockPos> list = minecarttracklogic.getConnections();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();
            BlockState iblockdata1 = world.getType(blockposition1);

            iblockdata1.neighborChanged(world, blockposition1, iblockdata1.getBlock(), blockposition, false);
        }

    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            this.checkPressed(world, blockposition, this.updateState(iblockdata, world, blockposition, flag));
        }
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return DetectorRailBlock.SHAPE;
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        if ((Boolean) iblockdata.getValue(DetectorRailBlock.POWERED)) {
            List<MinecartCommandBlock> list = this.getInteractingMinecartOfType(world, blockposition, MinecartCommandBlock.class, (Predicate) null);

            if (!list.isEmpty()) {
                return ((MinecartCommandBlock) list.get(0)).getCommandBlock().getSuccessCount();
            }

            List<AbstractMinecart> list1 = this.getInteractingMinecartOfType(world, blockposition, AbstractMinecart.class, EntitySelector.CONTAINER_ENTITY_SELECTOR);

            if (!list1.isEmpty()) {
                return AbstractContainerMenu.getRedstoneSignalFromContainer((Container) list1.get(0));
            }
        }

        return 0;
    }

    protected <T extends AbstractMinecart> List<T> getInteractingMinecartOfType(Level world, BlockPos blockposition, Class<T> oclass, @Nullable Predicate<Entity> predicate) {
        return world.getEntitiesOfClass(oclass, this.getSearchBB(blockposition), predicate);
    }

    private AABB getSearchBB(BlockPos blockposition) {
        double d0 = 0.2D;

        return new AABB((double) blockposition.getX() + 0.2D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.2D, (double) (blockposition.getX() + 1) - 0.2D, (double) (blockposition.getY() + 1) - 0.2D, (double) (blockposition.getZ() + 1) - 0.2D);
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                switch ((RailShape) iblockdata.getValue(DetectorRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                }
            case COUNTERCLOCKWISE_90:
                switch ((RailShape) iblockdata.getValue(DetectorRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_SOUTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                }
            case CLOCKWISE_90:
                switch ((RailShape) iblockdata.getValue(DetectorRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_SOUTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                }
            default:
                return iblockdata;
        }
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(DetectorRailBlock.SHAPE);

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                switch (blockpropertytrackposition) {
                    case ASCENDING_NORTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    default:
                        return super.mirror(iblockdata, enumblockmirror);
                }
            case FRONT_BACK:
                switch (blockpropertytrackposition) {
                    case ASCENDING_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    default:
                        break;
                    case SOUTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) iblockdata.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                }
        }

        return super.mirror(iblockdata, enumblockmirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(DetectorRailBlock.SHAPE, DetectorRailBlock.POWERED);
    }
}
