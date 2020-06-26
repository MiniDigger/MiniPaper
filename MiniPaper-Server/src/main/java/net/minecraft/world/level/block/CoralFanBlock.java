package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class CoralFanBlock extends BaseCoralFanBlock {

    private final Block deadBlock;

    protected CoralFanBlock(Block block, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.deadBlock = block;
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        this.tryScheduleDieTick(iblockdata, (LevelAccessor) world, blockposition);
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!scanForWater(iblockdata, (BlockGetter) worldserver, blockposition)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, this.deadBlock.getBlockData().setValue(CoralFanBlock.WATERLOGGED, false)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) this.deadBlock.getBlockData().setValue(CoralFanBlock.WATERLOGGED, false), 2);
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection == Direction.DOWN && !iblockdata.canSurvive(generatoraccess, blockposition)) {
            return Blocks.AIR.getBlockData();
        } else {
            this.tryScheduleDieTick(iblockdata, generatoraccess, blockposition);
            if ((Boolean) iblockdata.getValue(CoralFanBlock.WATERLOGGED)) {
                generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
            }

            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        }
    }
}
