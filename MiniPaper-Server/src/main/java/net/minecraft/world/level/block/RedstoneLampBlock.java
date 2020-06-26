package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class RedstoneLampBlock extends Block {

    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    public RedstoneLampBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) this.getBlockData().setValue(RedstoneLampBlock.LIT, false));
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return (BlockState) this.getBlockData().setValue(RedstoneLampBlock.LIT, blockactioncontext.getLevel().hasNeighborSignal(blockactioncontext.getClickedPos()));
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (!world.isClientSide) {
            boolean flag1 = (Boolean) iblockdata.getValue(RedstoneLampBlock.LIT);

            if (flag1 != world.hasNeighborSignal(blockposition)) {
                if (flag1) {
                    world.getBlockTickList().scheduleTick(blockposition, this, 4);
                } else {
                    // CraftBukkit start
                    if (CraftEventFactory.callRedstoneChange(world, blockposition, 0, 15).getNewCurrent() != 15) {
                        return;
                    }
                    // CraftBukkit end
                    world.setTypeAndData(blockposition, (BlockState) iblockdata.cycle((Property) RedstoneLampBlock.LIT), 2);
                }
            }

        }
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Boolean) iblockdata.getValue(RedstoneLampBlock.LIT) && !worldserver.hasNeighborSignal(blockposition)) {
            // CraftBukkit start
            if (CraftEventFactory.callRedstoneChange(worldserver, blockposition, 15, 0).getNewCurrent() != 0) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.cycle((Property) RedstoneLampBlock.LIT), 2);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(RedstoneLampBlock.LIT);
    }
}
