package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChestBlock extends AbstractChestBlock<ChestBlockEntity> implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape NORTH_AABB = Block.box(1.0D, 0.0D, 0.0D, 15.0D, 14.0D, 15.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(0.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    protected static final VoxelShape EAST_AABB = Block.box(1.0D, 0.0D, 1.0D, 16.0D, 14.0D, 15.0D);
    protected static final VoxelShape AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>> CHEST_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>>() {
        public Optional<Container> acceptDouble(ChestBlockEntity tileentitychest, ChestBlockEntity tileentitychest1) {
            return Optional.of(new CompoundContainer(tileentitychest, tileentitychest1));
        }

        public Optional<Container> acceptSingle(ChestBlockEntity tileentitychest) {
            return Optional.of(tileentitychest);
        }

        @Override
        public Optional<Container> acceptNone() {
            return Optional.empty();
        }
    };
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDER_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>>() {
        public Optional<MenuProvider> acceptDouble(final ChestBlockEntity tileentitychest, final ChestBlockEntity tileentitychest1) {
            final CompoundContainer inventorylargechest = new CompoundContainer(tileentitychest, tileentitychest1);

            return Optional.of(new net.minecraft.world.level.block.ChestBlock.DoubleInventory(tileentitychest, tileentitychest1, inventorylargechest)); // CraftBukkit
        }

        public Optional<MenuProvider> acceptSingle(ChestBlockEntity tileentitychest) {
            return Optional.of(tileentitychest);
        }

        @Override
        public Optional<MenuProvider> acceptNone() {
            return Optional.empty();
        }
    };

    // CraftBukkit start
    public static class DoubleInventory implements MenuProvider {

        private final ChestBlockEntity tileentitychest;
        private final ChestBlockEntity tileentitychest1;
        public final CompoundContainer inventorylargechest;

        public DoubleInventory(ChestBlockEntity tileentitychest, ChestBlockEntity tileentitychest1, CompoundContainer inventorylargechest) {
            this.tileentitychest = tileentitychest;
            this.tileentitychest1 = tileentitychest1;
            this.inventorylargechest = inventorylargechest;
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int i, Inventory playerinventory, Player entityhuman) {
            if (tileentitychest.canOpen(entityhuman) && tileentitychest1.canOpen(entityhuman)) {
                tileentitychest.unpackLootTable(playerinventory.player);
                tileentitychest1.unpackLootTable(playerinventory.player);
                return ChestMenu.sixRows(i, playerinventory, inventorylargechest);
            } else {
                return null;
            }
        }

        @Override
        public Component getDisplayName() {
            return (Component) (tileentitychest.hasCustomName() ? tileentitychest.getDisplayName() : (tileentitychest1.hasCustomName() ? tileentitychest1.getDisplayName() : new TranslatableComponent("container.chestDouble")));
        }
    };
    // CraftBukkit end

    protected ChestBlock(BlockBehaviour.Info blockbase_info, Supplier<BlockEntityType<? extends ChestBlockEntity>> supplier) {
        super(blockbase_info, supplier);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ChestBlock.FACING, Direction.NORTH)).setValue(ChestBlock.TYPE, ChestType.SINGLE)).setValue(ChestBlock.WATERLOGGED, false));
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState iblockdata) {
        ChestType blockpropertychesttype = (ChestType) iblockdata.getValue(ChestBlock.TYPE);

        return blockpropertychesttype == ChestType.SINGLE ? DoubleBlockCombiner.BlockType.SINGLE : (blockpropertychesttype == ChestType.RIGHT ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND);
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if ((Boolean) iblockdata.getValue(ChestBlock.WATERLOGGED)) {
            generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
        }

        if (iblockdata1.is((Block) this) && enumdirection.getAxis().isHorizontal()) {
            ChestType blockpropertychesttype = (ChestType) iblockdata1.getValue(ChestBlock.TYPE);

            if (iblockdata.getValue(ChestBlock.TYPE) == ChestType.SINGLE && blockpropertychesttype != ChestType.SINGLE && iblockdata.getValue(ChestBlock.FACING) == iblockdata1.getValue(ChestBlock.FACING) && getConnectedDirection(iblockdata1) == enumdirection.getOpposite()) {
                return (BlockState) iblockdata.setValue(ChestBlock.TYPE, blockpropertychesttype.getOpposite());
            }
        } else if (getConnectedDirection(iblockdata) == enumdirection) {
            return (BlockState) iblockdata.setValue(ChestBlock.TYPE, ChestType.SINGLE);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        if (iblockdata.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return ChestBlock.AABB;
        } else {
            switch (getConnectedDirection(iblockdata)) {
                case NORTH:
                default:
                    return ChestBlock.NORTH_AABB;
                case SOUTH:
                    return ChestBlock.SOUTH_AABB;
                case WEST:
                    return ChestBlock.WEST_AABB;
                case EAST:
                    return ChestBlock.EAST_AABB;
            }
        }
    }

    public static Direction getConnectedDirection(BlockState iblockdata) {
        Direction enumdirection = (Direction) iblockdata.getValue(ChestBlock.FACING);

        return iblockdata.getValue(ChestBlock.TYPE) == ChestType.LEFT ? enumdirection.getClockWise() : enumdirection.getCounterClockWise();
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        ChestType blockpropertychesttype = ChestType.SINGLE;
        Direction enumdirection = blockactioncontext.getHorizontalDirection().getOpposite();
        FluidState fluid = blockactioncontext.getLevel().getFluidState(blockactioncontext.getClickedPos());
        boolean flag = blockactioncontext.isSecondaryUseActive();
        Direction enumdirection1 = blockactioncontext.getClickedFace();

        if (enumdirection1.getAxis().isHorizontal() && flag) {
            Direction enumdirection2 = this.candidatePartnerFacing(blockactioncontext, enumdirection1.getOpposite());

            if (enumdirection2 != null && enumdirection2.getAxis() != enumdirection1.getAxis()) {
                enumdirection = enumdirection2;
                blockpropertychesttype = enumdirection2.getCounterClockWise() == enumdirection1.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }

        if (blockpropertychesttype == ChestType.SINGLE && !flag) {
            if (enumdirection == this.candidatePartnerFacing(blockactioncontext, enumdirection.getClockWise())) {
                blockpropertychesttype = ChestType.LEFT;
            } else if (enumdirection == this.candidatePartnerFacing(blockactioncontext, enumdirection.getCounterClockWise())) {
                blockpropertychesttype = ChestType.RIGHT;
            }
        }

        return (BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(ChestBlock.FACING, enumdirection)).setValue(ChestBlock.TYPE, blockpropertychesttype)).setValue(ChestBlock.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState iblockdata) {
        return (Boolean) iblockdata.getValue(ChestBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(iblockdata);
    }

    @Nullable
    private Direction candidatePartnerFacing(BlockPlaceContext blockactioncontext, Direction enumdirection) {
        BlockState iblockdata = blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos().relative(enumdirection));

        return iblockdata.is((Block) this) && iblockdata.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? (Direction) iblockdata.getValue(ChestBlock.FACING) : null;
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        if (itemstack.hasCustomHoverName()) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof ChestBlockEntity) {
                ((ChestBlockEntity) tileentity).setCustomName(itemstack.getHoverName());
            }
        }

    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof Container) {
                Containers.dropContents(world, blockposition, (Container) tileentity);
                world.updateNeighbourForOutputSignal(blockposition, this);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            MenuProvider itileinventory = this.getInventory(iblockdata, world, blockposition);

            if (itileinventory != null) {
                entityhuman.openMenu(itileinventory);
                entityhuman.awardStat(this.getOpenChestStat());
                PiglinAi.angerNearbyPiglins(entityhuman, true);
            }

            return InteractionResult.CONSUME;
        }
    }

    protected Stat<ResourceLocation> getOpenChestStat() {
        return Stats.CUSTOM.get(Stats.OPEN_CHEST);
    }

    @Nullable
    public static Container getInventory(ChestBlock blockchest, BlockState iblockdata, Level world, BlockPos blockposition, boolean flag) {
        return (Container) ((Optional) blockchest.a(iblockdata, world, blockposition, flag).apply(ChestBlock.CHEST_COMBINER)).orElse((Object) null);
    }

    public DoubleBlockCombiner.Result<? extends ChestBlockEntity> a(BlockState iblockdata, Level world, BlockPos blockposition, boolean flag) {
        BiPredicate<LevelAccessor, BlockPos> bipredicate; // CraftBukkit - decompile error

        if (flag) {
            bipredicate = (generatoraccess, blockposition1) -> {
                return false;
            };
        } else {
            bipredicate = ChestBlock::isChestBlockedAt;
        }

        return DoubleBlockCombiner.a((BlockEntityType) this.blockEntityType.get(), ChestBlock::getBlockType, ChestBlock::getConnectedDirection, ChestBlock.FACING, iblockdata, world, blockposition, bipredicate);
    }

    @Nullable
    @Override
    public MenuProvider getInventory(BlockState iblockdata, Level world, BlockPos blockposition) {
        return (MenuProvider) ((Optional) this.a(iblockdata, world, blockposition, false).apply(ChestBlock.MENU_PROVIDER_COMBINER)).orElse((Object) null);
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new ChestBlockEntity();
    }

    public static boolean isChestBlockedAt(LevelAccessor generatoraccess, BlockPos blockposition) {
        return isBlockedChestByBlock((BlockGetter) generatoraccess, blockposition) || isCatSittingOnChest(generatoraccess, blockposition);
    }

    private static boolean isBlockedChestByBlock(BlockGetter iblockaccess, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.above();

        return iblockaccess.getType(blockposition1).isRedstoneConductor(iblockaccess, blockposition1);
    }

    private static boolean isCatSittingOnChest(LevelAccessor generatoraccess, BlockPos blockposition) {
        List<Cat> list = generatoraccess.getEntitiesOfClass(Cat.class, new AABB((double) blockposition.getX(), (double) (blockposition.getY() + 1), (double) blockposition.getZ(), (double) (blockposition.getX() + 1), (double) (blockposition.getY() + 2), (double) (blockposition.getZ() + 1)));

        if (!list.isEmpty()) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Cat entitycat = (Cat) iterator.next();

                if (entitycat.isInSittingPose()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(getInventory(this, iblockdata, world, blockposition, false));
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(ChestBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(ChestBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(ChestBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ChestBlock.FACING, ChestBlock.TYPE, ChestBlock.WATERLOGGED);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
