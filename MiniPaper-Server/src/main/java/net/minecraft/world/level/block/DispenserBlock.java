package net.minecraft.world.level.block;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.BlockSourceImpl;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.PositionImpl;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class DispenserBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    public static final Map<Item, DispenseItemBehavior> DISPENSER_REGISTRY = (Map) Util.make((new Object2ObjectOpenHashMap()), (object2objectopenhashmap) -> { // CraftBukkit - decompile error
        object2objectopenhashmap.defaultReturnValue(new DefaultDispenseItemBehavior());
    });
    public static boolean eventFired = false; // CraftBukkit

    public static void registerBehavior(ItemLike imaterial, DispenseItemBehavior idispensebehavior) {
        DispenserBlock.DISPENSER_REGISTRY.put(imaterial.asItem(), idispensebehavior);
    }

    protected DispenserBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DispenserBlock.FACING, Direction.NORTH)).setValue(DispenserBlock.TRIGGERED, false));
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof DispenserBlockEntity) {
                entityhuman.openMenu((DispenserBlockEntity) tileentity);
                if (tileentity instanceof DropperBlockEntity) {
                    entityhuman.awardStat(Stats.INSPECT_DROPPER);
                } else {
                    entityhuman.awardStat(Stats.INSPECT_DISPENSER);
                }
            }

            return InteractionResult.CONSUME;
        }
    }

    public void dispenseFrom(Level world, BlockPos blockposition) {
        BlockSourceImpl sourceblock = new BlockSourceImpl(world, blockposition);
        DispenserBlockEntity tileentitydispenser = (DispenserBlockEntity) sourceblock.getEntity();
        int i = tileentitydispenser.getRandomSlot();

        if (i < 0) {
            world.levelEvent(1001, blockposition, 0);
        } else {
            ItemStack itemstack = tileentitydispenser.getItem(i);
            DispenseItemBehavior idispensebehavior = this.getDispenseMethod(itemstack);

            if (idispensebehavior != DispenseItemBehavior.NOOP) {
                eventFired = false; // CraftBukkit - reset event status
                tileentitydispenser.setItem(i, idispensebehavior.dispense(sourceblock, itemstack));
            }

        }
    }

    protected DispenseItemBehavior getDispenseMethod(ItemStack itemstack) {
        return (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(itemstack.getItem());
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        boolean flag1 = world.hasNeighborSignal(blockposition) || world.hasNeighborSignal(blockposition.above());
        boolean flag2 = (Boolean) iblockdata.getValue(DispenserBlock.TRIGGERED);

        if (flag1 && !flag2) {
            world.getBlockTickList().scheduleTick(blockposition, this, 4);
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(DispenserBlock.TRIGGERED, true), 4);
        } else if (!flag1 && flag2) {
            world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(DispenserBlock.TRIGGERED, false), 4);
        }

    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        this.dispenseFrom(worldserver, blockposition);
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new DispenserBlockEntity();
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(DispenserBlock.FACING, blockactioncontext.getNearestLookingDirection().getOpposite());
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        if (itemstack.hasCustomHoverName()) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof DispenserBlockEntity) {
                ((DispenserBlockEntity) tileentity).setCustomName(itemstack.getHoverName());
            }
        }

    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof DispenserBlockEntity) {
                Containers.dropContents(world, blockposition, (DispenserBlockEntity) tileentity);
                world.updateNeighbourForOutputSignal(blockposition, this);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    public static Position getDispensePosition(BlockSource isourceblock) {
        Direction enumdirection = (Direction) isourceblock.getBlockData().getValue(DispenserBlock.FACING);
        double d0 = isourceblock.x() + 0.7D * (double) enumdirection.getStepX();
        double d1 = isourceblock.y() + 0.7D * (double) enumdirection.getStepY();
        double d2 = isourceblock.z() + 0.7D * (double) enumdirection.getStepZ();

        return new PositionImpl(d0, d1, d2);
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(world.getBlockEntity(blockposition));
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState iblockdata, Rotation enumblockrotation) {
        return (BlockState) iblockdata.setValue(DispenserBlock.FACING, enumblockrotation.rotate((Direction) iblockdata.getValue(DispenserBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState iblockdata, Mirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((Direction) iblockdata.getValue(DispenserBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(DispenserBlock.FACING, DispenserBlock.TRIGGERED);
    }
}
