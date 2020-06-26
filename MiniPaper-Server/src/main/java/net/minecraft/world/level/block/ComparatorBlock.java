package net.minecraft.world.level.block;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.TickPriority;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ComparatorBlock extends DiodeBlock implements EntityBlock {

    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    public ComparatorBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ComparatorBlock.FACING, Direction.NORTH)).setValue(ComparatorBlock.POWERED, false)).setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE));
    }

    @Override
    protected int getDelay(BlockState iblockdata) {
        return 2;
    }

    @Override
    protected int getOutputSignal(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata) {
        BlockEntity tileentity = iblockaccess.getBlockEntity(blockposition);

        return tileentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity) tileentity).getOutputSignal() : 0;
    }

    private int calculateOutputSignal(Level world, BlockPos blockposition, BlockState iblockdata) {
        return iblockdata.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? Math.max(this.getInputSignal(world, blockposition, iblockdata) - this.getAlternateSignal((LevelReader) world, blockposition, iblockdata), 0) : this.getInputSignal(world, blockposition, iblockdata);
    }

    @Override
    protected boolean shouldTurnOn(Level world, BlockPos blockposition, BlockState iblockdata) {
        int i = this.getInputSignal(world, blockposition, iblockdata);

        if (i == 0) {
            return false;
        } else {
            int j = this.getAlternateSignal((LevelReader) world, blockposition, iblockdata);

            return i > j ? true : i == j && iblockdata.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE;
        }
    }

    @Override
    protected int getInputSignal(Level world, BlockPos blockposition, BlockState iblockdata) {
        int i = super.getInputSignal(world, blockposition, iblockdata);
        Direction enumdirection = (Direction) iblockdata.getValue(ComparatorBlock.FACING);
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        BlockState iblockdata1 = world.getType(blockposition1);

        if (iblockdata1.hasAnalogOutputSignal()) {
            i = iblockdata1.getAnalogOutputSignal(world, blockposition1);
        } else if (i < 15 && iblockdata1.isRedstoneConductor(world, blockposition1)) {
            blockposition1 = blockposition1.relative(enumdirection);
            iblockdata1 = world.getType(blockposition1);
            ItemFrame entityitemframe = this.getItemFrame(world, enumdirection, blockposition1);
            int j = Math.max(entityitemframe == null ? Integer.MIN_VALUE : entityitemframe.getAnalogOutput(), iblockdata1.hasAnalogOutputSignal() ? iblockdata1.getAnalogOutputSignal(world, blockposition1) : Integer.MIN_VALUE);

            if (j != Integer.MIN_VALUE) {
                i = j;
            }
        }

        return i;
    }

    @Nullable
    private ItemFrame getItemFrame(Level world, Direction enumdirection, BlockPos blockposition) {
        // CraftBukkit - decompile error
        List<ItemFrame> list = world.getEntitiesOfClass(ItemFrame.class, new AABB((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), (double) (blockposition.getX() + 1), (double) (blockposition.getY() + 1), (double) (blockposition.getZ() + 1)), (java.util.function.Predicate<ItemFrame>) (entityitemframe) -> {
            return entityitemframe != null && entityitemframe.getDirection() == enumdirection;
        });

        return list.size() == 1 ? (ItemFrame) list.get(0) : null;
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (!entityhuman.abilities.mayBuild) {
            return InteractionResult.PASS;
        } else {
            iblockdata = (BlockState) iblockdata.cycle((Property) ComparatorBlock.MODE);
            float f = iblockdata.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;

            world.playSound(entityhuman, blockposition, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, f);
            world.setTypeAndData(blockposition, iblockdata, 2);
            this.refreshOutputState(world, blockposition, iblockdata);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    @Override
    protected void checkTickOnNeighbor(Level world, BlockPos blockposition, BlockState iblockdata) {
        if (!world.getBlockTickList().willTickThisTick(blockposition, this)) {
            int i = this.calculateOutputSignal(world, blockposition, iblockdata);
            BlockEntity tileentity = world.getBlockEntity(blockposition);
            int j = tileentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity) tileentity).getOutputSignal() : 0;

            if (i != j || (Boolean) iblockdata.getValue(ComparatorBlock.POWERED) != this.shouldTurnOn(world, blockposition, iblockdata)) {
                TickPriority ticklistpriority = this.shouldPrioritize((BlockGetter) world, blockposition, iblockdata) ? TickPriority.HIGH : TickPriority.NORMAL;

                world.getBlockTickList().scheduleTick(blockposition, this, 2, ticklistpriority);
            }

        }
    }

    private void refreshOutputState(Level world, BlockPos blockposition, BlockState iblockdata) {
        int i = this.calculateOutputSignal(world, blockposition, iblockdata);
        BlockEntity tileentity = world.getBlockEntity(blockposition);
        int j = 0;

        if (tileentity instanceof ComparatorBlockEntity) {
            ComparatorBlockEntity tileentitycomparator = (ComparatorBlockEntity) tileentity;

            j = tileentitycomparator.getOutputSignal();
            tileentitycomparator.setOutputSignal(i);
        }

        if (j != i || iblockdata.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE) {
            boolean flag = this.shouldTurnOn(world, blockposition, iblockdata);
            boolean flag1 = (Boolean) iblockdata.getValue(ComparatorBlock.POWERED);

            if (flag1 && !flag) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(world, blockposition, 15, 0).getNewCurrent() != 0) {
                    return;
                }
                // CraftBukkit end
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ComparatorBlock.POWERED, false), 2);
            } else if (!flag1 && flag) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(world, blockposition, 0, 15).getNewCurrent() != 15) {
                    return;
                }
                // CraftBukkit end
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(ComparatorBlock.POWERED, true), 2);
            }

            this.updateNeighborsInFront(world, blockposition, iblockdata);
        }

    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        this.refreshOutputState((Level) worldserver, blockposition, iblockdata);
    }

    @Override
    public boolean triggerEvent(BlockState iblockdata, Level world, BlockPos blockposition, int i, int j) {
        super.triggerEvent(iblockdata, world, blockposition, i, j);
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        return tileentity != null && tileentity.triggerEvent(i, j);
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new ComparatorBlockEntity();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(ComparatorBlock.FACING, ComparatorBlock.MODE, ComparatorBlock.POWERED);
    }
}
