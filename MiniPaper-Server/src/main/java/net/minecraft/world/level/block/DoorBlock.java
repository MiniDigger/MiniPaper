package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class DoorBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D);

    protected DoorBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DoorBlock.FACING, Direction.NORTH)).setValue(DoorBlock.OPEN, false)).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)).setValue(DoorBlock.POWERED, false)).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        Direction enumdirection = (Direction) iblockdata.getValue(DoorBlock.FACING);
        boolean flag = !(Boolean) iblockdata.getValue(DoorBlock.OPEN);
        boolean flag1 = iblockdata.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;

        switch (enumdirection) {
            case EAST:
            default:
                return flag ? DoorBlock.EAST_AABB : (flag1 ? DoorBlock.NORTH_AABB : DoorBlock.SOUTH_AABB);
            case SOUTH:
                return flag ? DoorBlock.SOUTH_AABB : (flag1 ? DoorBlock.EAST_AABB : DoorBlock.WEST_AABB);
            case WEST:
                return flag ? DoorBlock.WEST_AABB : (flag1 ? DoorBlock.SOUTH_AABB : DoorBlock.NORTH_AABB);
            case NORTH:
                return flag ? DoorBlock.NORTH_AABB : (flag1 ? DoorBlock.WEST_AABB : DoorBlock.EAST_AABB);
        }
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        DoubleBlockHalf blockpropertydoubleblockhalf = (DoubleBlockHalf) iblockdata.getValue(DoorBlock.HALF);

        return enumdirection.getAxis() == Direction.Axis.Y && blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER == (enumdirection == Direction.UP) ? (iblockdata1.is((Block) this) && iblockdata1.getValue(DoorBlock.HALF) != blockpropertydoubleblockhalf ? (BlockState) ((BlockState) ((BlockState) ((BlockState) iblockdata.setValue(DoorBlock.FACING, iblockdata1.getValue(DoorBlock.FACING))).setValue(DoorBlock.OPEN, iblockdata1.getValue(DoorBlock.OPEN))).setValue(DoorBlock.HINGE, iblockdata1.getValue(DoorBlock.HINGE))).setValue(DoorBlock.POWERED, iblockdata1.getValue(DoorBlock.POWERED)) : Blocks.AIR.getBlockData()) : (blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER && enumdirection == Direction.DOWN && !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1));
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!world.isClientSide && entityhuman.isCreative()) {
            DoublePlantBlock.preventCreativeDropFromBottomPart(world, blockposition, iblockdata, entityhuman);
        }

        super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        switch (pathmode) {
            case LAND:
                return (Boolean) iblockdata.getValue(DoorBlock.OPEN);
            case WATER:
                return false;
            case AIR:
                return (Boolean) iblockdata.getValue(DoorBlock.OPEN);
            default:
                return false;
        }
    }

    private int getCloseSound() {
        return this.material == Material.METAL ? 1011 : 1012;
    }

    private int getOpenSound() {
        return this.material == Material.METAL ? 1005 : 1006;
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockPos blockposition = blockactioncontext.getClickedPos();

        if (blockposition.getY() < 255 && blockactioncontext.getLevel().getType(blockposition.above()).canBeReplaced(blockactioncontext)) {
            Level world = blockactioncontext.getLevel();
            boolean flag = world.hasNeighborSignal(blockposition) || world.hasNeighborSignal(blockposition.above());

            return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(DoorBlock.FACING, blockactioncontext.getHorizontalDirection())).setValue(DoorBlock.HINGE, this.getHinge(blockactioncontext))).setValue(DoorBlock.POWERED, flag)).setValue(DoorBlock.OPEN, flag)).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        } else {
            return null;
        }
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        world.setTypeAndData(blockposition.above(), (BlockState) iblockdata.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private DoorHingeSide getHinge(BlockPlaceContext blockactioncontext) {
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        Direction enumdirection = blockactioncontext.getHorizontalDirection();
        BlockPos blockposition1 = blockposition.above();
        Direction enumdirection1 = enumdirection.getCounterClockWise();
        BlockPos blockposition2 = blockposition.relative(enumdirection1);
        BlockState iblockdata = world.getType(blockposition2);
        BlockPos blockposition3 = blockposition1.relative(enumdirection1);
        BlockState iblockdata1 = world.getType(blockposition3);
        Direction enumdirection2 = enumdirection.getClockWise();
        BlockPos blockposition4 = blockposition.relative(enumdirection2);
        BlockState iblockdata2 = world.getType(blockposition4);
        BlockPos blockposition5 = blockposition1.relative(enumdirection2);
        BlockState iblockdata3 = world.getType(blockposition5);
        int i = (iblockdata.isCollisionShapeFullBlock(world, blockposition2) ? -1 : 0) + (iblockdata1.isCollisionShapeFullBlock(world, blockposition3) ? -1 : 0) + (iblockdata2.isCollisionShapeFullBlock(world, blockposition4) ? 1 : 0) + (iblockdata3.isCollisionShapeFullBlock(world, blockposition5) ? 1 : 0);
        boolean flag = iblockdata.is((Block) this) && iblockdata.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        boolean flag1 = iblockdata2.is((Block) this) && iblockdata2.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;

        if ((!flag || flag1) && i <= 0) {
            if ((!flag1 || flag) && i >= 0) {
                int j = enumdirection.getStepX();
                int k = enumdirection.getStepZ();
                Vec3 vec3d = blockactioncontext.getClickLocation();
                double d0 = vec3d.x - (double) blockposition.getX();
                double d1 = vec3d.z - (double) blockposition.getZ();

                return (j >= 0 || d1 >= 0.5D) && (j <= 0 || d1 <= 0.5D) && (k >= 0 || d0 <= 0.5D) && (k <= 0 || d0 >= 0.5D) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
            } else {
                return DoorHingeSide.LEFT;
            }
        } else {
            return DoorHingeSide.RIGHT;
        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (this.material == Material.METAL) {
            return InteractionResult.PASS;
        } else {
            iblockdata = (BlockState) iblockdata.cycle((Property) DoorBlock.OPEN);
            world.setTypeAndData(blockposition, iblockdata, 10);
            world.levelEvent(entityhuman, (Boolean) iblockdata.getValue(DoorBlock.OPEN) ? this.getOpenSound() : this.getCloseSound(), blockposition, 0);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    public void setOpen(Level world, BlockPos blockposition, boolean flag) {
        BlockState iblockdata = world.getType(blockposition);

        if (iblockdata.is((Block) this) && (Boolean) iblockdata.getValue(DoorBlock.OPEN) != flag) {
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(DoorBlock.OPEN, flag), 10);
            this.playSound(world, blockposition, flag);
        }
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        // CraftBukkit start
        BlockPos otherHalf = blockposition.relative(iblockdata.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);

        org.bukkit.World bworld = world.getWorld();
        org.bukkit.block.Block bukkitBlock = bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
        org.bukkit.block.Block blockTop = bworld.getBlockAt(otherHalf.getX(), otherHalf.getY(), otherHalf.getZ());

        int power = bukkitBlock.getBlockPower();
        int powerTop = blockTop.getBlockPower();
        if (powerTop > power) power = powerTop;
        int oldPower = (Boolean) iblockdata.getValue(DoorBlock.POWERED) ? 15 : 0;

        if (oldPower == 0 ^ power == 0) {
            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bukkitBlock, oldPower, power);
            world.getServerOH().getPluginManager().callEvent(eventRedstone);

            boolean flag1 = eventRedstone.getNewCurrent() > 0;
            // CraftBukkit end
            if (flag1 != (Boolean) iblockdata.getValue(DoorBlock.OPEN)) {
                this.playSound(world, blockposition, flag1);
            }

            world.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(DoorBlock.POWERED, flag1)).setValue(DoorBlock.OPEN, flag1), 2);
        }

    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();
        BlockState iblockdata1 = iworldreader.getType(blockposition1);

        return iblockdata.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? iblockdata1.isFaceSturdy(iworldreader, blockposition1, Direction.UP) : iblockdata1.is((Block) this);
    }

    private void playSound(Level world, BlockPos blockposition, boolean flag) {
        world.levelEvent((Player) null, flag ? this.getOpenSound() : this.getCloseSound(), blockposition, 0);
    }

    @Override
    public PushReaction getPushReaction(BlockState iblockdata) {
        return PushReaction.DESTROY;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(DoorBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(DoorBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return enumblockmirror == Mirror.NONE ? iblockdata : (BlockState) iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(DoorBlock.FACING))).cycle((Property) DoorBlock.HINGE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(DoorBlock.HALF, DoorBlock.FACING, DoorBlock.OPEN, DoorBlock.HINGE, DoorBlock.POWERED);
    }

    public static boolean isWoodenDoor(Level world, BlockPos blockposition) {
        return isWoodenDoor(world.getType(blockposition));
    }

    public static boolean isWoodenDoor(BlockState iblockdata) {
        return iblockdata.getBlock() instanceof DoorBlock && (iblockdata.getMaterial() == Material.WOOD || iblockdata.getMaterial() == Material.NETHER_WOOD);
    }
}
