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

public class CoralWallFanBlock extends BaseCoralWallFanBlock {

    private final Block deadBlock;

    protected CoralWallFanBlock(Block block, BlockBehaviour.Info blockbase_info) {
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
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, this.deadBlock.getBlockData().setValue(CoralWallFanBlock.WATERLOGGED, false).setValue(CoralWallFanBlock.FACING, iblockdata.getValue(CoralWallFanBlock.FACING))).isCancelled()) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) ((BlockState) this.deadBlock.getBlockData().setValue(CoralWallFanBlock.WATERLOGGED, false)).setValue(CoralWallFanBlock.FACING, iblockdata.getValue(CoralWallFanBlock.FACING)), 2);
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection.getOpposite() == iblockdata.getValue(CoralWallFanBlock.FACING) && !iblockdata.canSurvive(generatoraccess, blockposition)) {
            return Blocks.AIR.getBlockData();
        } else {
            if ((Boolean) iblockdata.getValue(CoralWallFanBlock.WATERLOGGED)) {
                generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
            }

            this.tryScheduleDieTick(iblockdata, generatoraccess, blockposition);
            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        }
    }
}
