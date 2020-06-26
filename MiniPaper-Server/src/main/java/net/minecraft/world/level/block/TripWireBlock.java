package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit

public class TripWireBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    public static final BooleanProperty DISARMED = BlockStateProperties.DISARMED;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = CrossCollisionBlock.PROPERTY_BY_DIRECTION;
    protected static final VoxelShape AABB = Block.box(0.0D, 1.0D, 0.0D, 16.0D, 2.5D, 16.0D);
    protected static final VoxelShape NOT_ATTACHED_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private final TripWireHookBlock hook;

    public TripWireBlock(TripWireHookBlock blocktripwirehook, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TripWireBlock.POWERED, false)).setValue(TripWireBlock.ATTACHED, false)).setValue(TripWireBlock.DISARMED, false)).setValue(TripWireBlock.NORTH, false)).setValue(TripWireBlock.EAST, false)).setValue(TripWireBlock.SOUTH, false)).setValue(TripWireBlock.WEST, false));
        this.hook = blocktripwirehook;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return (Boolean) iblockdata.getValue(TripWireBlock.ATTACHED) ? TripWireBlock.AABB : TripWireBlock.NOT_ATTACHED_AABB;
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(TripWireBlock.NORTH, this.shouldConnectTo(world.getType(blockposition.north()), Direction.NORTH))).setValue(TripWireBlock.EAST, this.shouldConnectTo(world.getType(blockposition.east()), Direction.EAST))).setValue(TripWireBlock.SOUTH, this.shouldConnectTo(world.getType(blockposition.south()), Direction.SOUTH))).setValue(TripWireBlock.WEST, this.shouldConnectTo(world.getType(blockposition.west()), Direction.WEST));
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection.getAxis().isHorizontal() ? (BlockState) iblockdata.setValue((Property) TripWireBlock.PROPERTY_BY_DIRECTION.get(enumdirection), this.shouldConnectTo(iblockdata1, enumdirection)) : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            this.updateSource(world, blockposition, iblockdata);
        }
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            this.updateSource(world, blockposition, (BlockState) iblockdata.setValue(TripWireBlock.POWERED, true));
        }
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!world.isClientSide && !entityhuman.getMainHandItem().isEmpty() && entityhuman.getMainHandItem().getItem() == Items.SHEARS) {
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(TripWireBlock.DISARMED, true), 4);
        }

        super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    private void updateSource(Level world, BlockPos blockposition, BlockState iblockdata) {
        Direction[] aenumdirection = new Direction[]{Direction.SOUTH, Direction.WEST};
        int i = aenumdirection.length;
        int j = 0;

        while (j < i) {
            Direction enumdirection = aenumdirection[j];
            int k = 1;

            while (true) {
                if (k < 42) {
                    BlockPos blockposition1 = blockposition.relative(enumdirection, k);
                    BlockState iblockdata1 = world.getType(blockposition1);

                    if (iblockdata1.is((Block) this.hook)) {
                        if (iblockdata1.getValue(TripWireHookBlock.FACING) == enumdirection.getOpposite()) {
                            this.hook.calculateState(world, blockposition1, iblockdata1, false, true, k, iblockdata);
                        }
                    } else if (iblockdata1.is((Block) this)) {
                        ++k;
                        continue;
                    }
                }

                ++j;
                break;
            }
        }

    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!world.isClientSide) {
            if (!(Boolean) iblockdata.getValue(TripWireBlock.POWERED)) {
                this.checkPressed(world, blockposition);
            }
        }
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Boolean) worldserver.getType(blockposition).getValue(TripWireBlock.POWERED)) {
            this.checkPressed((Level) worldserver, blockposition);
        }
    }

    private void checkPressed(Level world, BlockPos blockposition) {
        BlockState iblockdata = world.getType(blockposition);
        boolean flag = (Boolean) iblockdata.getValue(TripWireBlock.POWERED);
        boolean flag1 = false;
        List<? extends Entity> list = world.getEntities((Entity) null, iblockdata.getShape(world, blockposition).bounds().move(blockposition));

        if (!list.isEmpty()) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (!entity.isIgnoringBlockTriggers()) {
                    flag1 = true;
                    break;
                }
            }
        }

        // CraftBukkit start - Call interact even when triggering connected tripwire
        if (flag != flag1 && flag1 && (Boolean)iblockdata.getValue(ATTACHED)) {
            org.bukkit.World bworld = world.getWorld();
            org.bukkit.plugin.PluginManager manager = world.getServerOH().getPluginManager();
            org.bukkit.block.Block block = bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            boolean allowed = false;

            // If all of the events are cancelled block the tripwire trigger, else allow
            for (Object object : list) {
                if (object != null) {
                    org.bukkit.event.Cancellable cancellable;

                    if (object instanceof Player) {
                        cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) object, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
                    } else if (object instanceof Entity) {
                        cancellable = new EntityInteractEvent(((Entity) object).getBukkitEntity(), block);
                        manager.callEvent((EntityInteractEvent) cancellable);
                    } else {
                        continue;
                    }

                    if (!cancellable.isCancelled()) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) {
                return;
            }
        }
        // CraftBukkit end

        if (flag1 != flag) {
            iblockdata = (BlockState) iblockdata.setValue(TripWireBlock.POWERED, flag1);
            world.setTypeAndData(blockposition, iblockdata, 3);
            this.updateSource(world, blockposition, iblockdata);
        }

        if (flag1) {
            world.getBlockTickList().scheduleTick(new BlockPos(blockposition), this, 10);
        }

    }

    public boolean shouldConnectTo(BlockState iblockdata, Direction enumdirection) {
        Block block = iblockdata.getBlock();

        return block == this.hook ? iblockdata.getValue(TripWireHookBlock.FACING) == enumdirection.getOpposite() : block == this;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(TripWireBlock.NORTH, iblockdata.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.EAST, iblockdata.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.SOUTH, iblockdata.getValue(TripWireBlock.NORTH))).setValue(TripWireBlock.WEST, iblockdata.getValue(TripWireBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(TripWireBlock.NORTH, iblockdata.getValue(TripWireBlock.EAST))).setValue(TripWireBlock.EAST, iblockdata.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.SOUTH, iblockdata.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.WEST, iblockdata.getValue(TripWireBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(TripWireBlock.NORTH, iblockdata.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.EAST, iblockdata.getValue(TripWireBlock.NORTH))).setValue(TripWireBlock.SOUTH, iblockdata.getValue(TripWireBlock.EAST))).setValue(TripWireBlock.WEST, iblockdata.getValue(TripWireBlock.SOUTH));
            default:
                return iblockdata;
        }
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        switch (enumblockmirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) iblockdata.setValue(TripWireBlock.NORTH, iblockdata.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.SOUTH, iblockdata.getValue(TripWireBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) iblockdata.setValue(TripWireBlock.EAST, iblockdata.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.WEST, iblockdata.getValue(TripWireBlock.EAST));
            default:
                return super.mirror(iblockdata, enumblockmirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(TripWireBlock.POWERED, TripWireBlock.ATTACHED, TripWireBlock.DISARMED, TripWireBlock.NORTH, TripWireBlock.EAST, TripWireBlock.WEST, TripWireBlock.SOUTH);
    }
}
