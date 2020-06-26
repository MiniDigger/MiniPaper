package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LecternBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty HAS_BOOK = BlockStateProperties.HAS_BOOK;
    public static final VoxelShape SHAPE_BASE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    public static final VoxelShape SHAPE_POST = Block.box(4.0D, 2.0D, 4.0D, 12.0D, 14.0D, 12.0D);
    public static final VoxelShape SHAPE_COMMON = Shapes.or(LecternBlock.SHAPE_BASE, LecternBlock.SHAPE_POST);
    public static final VoxelShape SHAPE_TOP_PLATE = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 15.0D, 16.0D);
    public static final VoxelShape SHAPE_COLLISION = Shapes.or(LecternBlock.SHAPE_COMMON, LecternBlock.SHAPE_TOP_PLATE);
    public static final VoxelShape SHAPE_WEST = Shapes.or(Block.box(1.0D, 10.0D, 0.0D, 5.333333D, 14.0D, 16.0D), Block.box(5.333333D, 12.0D, 0.0D, 9.666667D, 16.0D, 16.0D), Block.box(9.666667D, 14.0D, 0.0D, 14.0D, 18.0D, 16.0D), LecternBlock.SHAPE_COMMON);
    public static final VoxelShape SHAPE_NORTH = Shapes.or(Block.box(0.0D, 10.0D, 1.0D, 16.0D, 14.0D, 5.333333D), Block.box(0.0D, 12.0D, 5.333333D, 16.0D, 16.0D, 9.666667D), Block.box(0.0D, 14.0D, 9.666667D, 16.0D, 18.0D, 14.0D), LecternBlock.SHAPE_COMMON);
    public static final VoxelShape SHAPE_EAST = Shapes.or(Block.box(15.0D, 10.0D, 0.0D, 10.666667D, 14.0D, 16.0D), Block.box(10.666667D, 12.0D, 0.0D, 6.333333D, 16.0D, 16.0D), Block.box(6.333333D, 14.0D, 0.0D, 2.0D, 18.0D, 16.0D), LecternBlock.SHAPE_COMMON);
    public static final VoxelShape SHAPE_SOUTH = Shapes.or(Block.box(0.0D, 10.0D, 15.0D, 16.0D, 14.0D, 10.666667D), Block.box(0.0D, 12.0D, 10.666667D, 16.0D, 16.0D, 6.333333D), Block.box(0.0D, 14.0D, 6.333333D, 16.0D, 18.0D, 2.0D), LecternBlock.SHAPE_COMMON);

    protected LecternBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LecternBlock.FACING, Direction.NORTH)).setValue(LecternBlock.POWERED, false)).setValue(LecternBlock.HAS_BOOK, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return LecternBlock.SHAPE_COMMON;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState iblockdata) {
        return true;
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        Level world = blockactioncontext.getLevel();
        ItemStack itemstack = blockactioncontext.getItemInHand();
        CompoundTag nbttagcompound = itemstack.getTag();
        Player entityhuman = blockactioncontext.getPlayer();
        boolean flag = false;

        if (!world.isClientSide && entityhuman != null && nbttagcompound != null && entityhuman.canUseGameMasterBlocks() && nbttagcompound.contains("BlockEntityTag")) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("BlockEntityTag");

            if (nbttagcompound1.contains("Book")) {
                flag = true;
            }
        }

        return (BlockState) ((BlockState) this.getBlockData().setValue(LecternBlock.FACING, blockactioncontext.getHorizontalDirection().getOpposite())).setValue(LecternBlock.HAS_BOOK, flag);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return LecternBlock.SHAPE_COLLISION;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        switch ((Direction) iblockdata.getValue(LecternBlock.FACING)) {
            case NORTH:
                return LecternBlock.SHAPE_NORTH;
            case SOUTH:
                return LecternBlock.SHAPE_SOUTH;
            case EAST:
                return LecternBlock.SHAPE_EAST;
            case WEST:
                return LecternBlock.SHAPE_WEST;
            default:
                return LecternBlock.SHAPE_COMMON;
        }
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(LecternBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(LecternBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(LecternBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(LecternBlock.FACING, LecternBlock.POWERED, LecternBlock.HAS_BOOK);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new LecternBlockEntity();
    }

    public static boolean tryPlaceBook(Level world, BlockPos blockposition, BlockState iblockdata, ItemStack itemstack) {
        if (!(Boolean) iblockdata.getValue(LecternBlock.HAS_BOOK)) {
            if (!world.isClientSide) {
                placeBook(world, blockposition, iblockdata, itemstack);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void placeBook(Level world, BlockPos blockposition, BlockState iblockdata, ItemStack itemstack) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof LecternBlockEntity) {
            LecternBlockEntity tileentitylectern = (LecternBlockEntity) tileentity;

            tileentitylectern.setBook(itemstack.split(1));
            setHasBook(world, blockposition, iblockdata, true);
            world.playSound((Player) null, blockposition, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    public static void setHasBook(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        world.setTypeAndData(blockposition, (BlockState) ((BlockState) iblockdata.setValue(LecternBlock.POWERED, false)).setValue(LecternBlock.HAS_BOOK, flag), 3);
        updateBelow(world, blockposition, iblockdata);
    }

    public static void signalPageChange(Level world, BlockPos blockposition, BlockState iblockdata) {
        changePowered(world, blockposition, iblockdata, true);
        world.getBlockTickList().scheduleTick(blockposition, iblockdata.getBlock(), 2);
        world.levelEvent(1043, blockposition, 0);
    }

    private static void changePowered(Level world, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(LecternBlock.POWERED, flag), 3);
        updateBelow(world, blockposition, iblockdata);
    }

    private static void updateBelow(Level world, BlockPos blockposition, BlockState iblockdata) {
        world.updateNeighborsAt(blockposition.below(), iblockdata.getBlock());
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        changePowered(worldserver, blockposition, iblockdata, false);
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            if ((Boolean) iblockdata.getValue(LecternBlock.HAS_BOOK)) {
                this.popBook(iblockdata, world, blockposition);
            }

            if ((Boolean) iblockdata.getValue(LecternBlock.POWERED)) {
                world.updateNeighborsAt(blockposition.below(), this);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    private void popBook(BlockState iblockdata, Level world, BlockPos blockposition) {
        BlockEntity tileentity = world.getTileEntity(blockposition, false); // CraftBukkit - don't validate, type may be changed already

        if (tileentity instanceof LecternBlockEntity) {
            LecternBlockEntity tileentitylectern = (LecternBlockEntity) tileentity;
            Direction enumdirection = (Direction) iblockdata.getValue(LecternBlock.FACING);
            ItemStack itemstack = tileentitylectern.getBook().copy();
            if (itemstack.isEmpty()) return; // CraftBukkit - SPIGOT-5500
            float f = 0.25F * (float) enumdirection.getStepX();
            float f1 = 0.25F * (float) enumdirection.getStepZ();
            ItemEntity entityitem = new ItemEntity(world, (double) blockposition.getX() + 0.5D + (double) f, (double) (blockposition.getY() + 1), (double) blockposition.getZ() + 0.5D + (double) f1, itemstack);

            entityitem.setDefaultPickUpDelay();
            world.addFreshEntity(entityitem);
            tileentitylectern.clearContent();
        }

    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(LecternBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return enumdirection == Direction.UP && (Boolean) iblockdata.getValue(LecternBlock.POWERED) ? 15 : 0;
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        if ((Boolean) iblockdata.getValue(LecternBlock.HAS_BOOK)) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof LecternBlockEntity) {
                return ((LecternBlockEntity) tileentity).getRedstoneSignal();
            }
        }

        return 0;
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if ((Boolean) iblockdata.getValue(LecternBlock.HAS_BOOK)) {
            if (!world.isClientSide) {
                this.openScreen(world, blockposition, entityhuman);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            ItemStack itemstack = entityhuman.getItemInHand(enumhand);

            return !itemstack.isEmpty() && !itemstack.getItem().is((Tag) ItemTags.LECTERN_BOOKS) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
    }

    @Nullable
    @Override
    public MenuProvider getInventory(BlockState iblockdata, Level world, BlockPos blockposition) {
        return !(Boolean) iblockdata.getValue(LecternBlock.HAS_BOOK) ? null : super.getInventory(iblockdata, world, blockposition);
    }

    private void openScreen(Level world, BlockPos blockposition, Player entityhuman) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof LecternBlockEntity) {
            entityhuman.openMenu((LecternBlockEntity) tileentity);
            entityhuman.awardStat(Stats.INTERACT_WITH_LECTERN);
        }

    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
