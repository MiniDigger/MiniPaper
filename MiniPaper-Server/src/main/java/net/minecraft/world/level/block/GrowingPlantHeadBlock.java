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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantHeadBlock extends GrowingPlantBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    private final double growPerTickProbability;

    protected GrowingPlantHeadBlock(BlockBehaviour.Info blockbase_info, Direction enumdirection, VoxelShape voxelshape, boolean flag, double d0) {
        super(blockbase_info, enumdirection, voxelshape, flag);
        this.growPerTickProbability = d0;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(GrowingPlantHeadBlock.AGE, 0));
    }

    public BlockState getStateForPlacement(LevelAccessor generatoraccess) {
        return (BlockState) this.getBlockData().setValue(GrowingPlantHeadBlock.AGE, generatoraccess.getRandom().nextInt(25));
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        }

    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(GrowingPlantHeadBlock.AGE) < 25;
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Integer) iblockdata.getValue(GrowingPlantHeadBlock.AGE) < 25 && random.nextDouble() < (100.0D / worldserver.spigotConfig.kelpModifier) * this.growPerTickProbability) { // Spigot
            BlockPos blockposition1 = blockposition.relative(this.growthDirection);

            if (this.canGrowInto(worldserver.getType(blockposition1))) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition1, (BlockState) iblockdata.cycle((Property) GrowingPlantHeadBlock.AGE)); // CraftBukkit
            }
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection == this.growthDirection.getOpposite() && !iblockdata.canSurvive(generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        if (enumdirection == this.growthDirection && iblockdata1.is((Block) this)) {
            return this.getBodyBlock().getBlockData();
        } else {
            if (this.scheduleFluidTicks) {
                generatoraccess.getFluidTickList().scheduleTick(blockposition, Fluids.WATER, Fluids.WATER.getTickDelay((LevelReader) generatoraccess));
            }

            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(GrowingPlantHeadBlock.AGE);
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return this.canGrowInto(iblockaccess.getType(blockposition.relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        BlockPos blockposition1 = blockposition.relative(this.growthDirection);
        int i = Math.min((Integer) iblockdata.getValue(GrowingPlantHeadBlock.AGE) + 1, 25);
        int j = this.getBlocksToGrowWhenBonemealed(random);

        for (int k = 0; k < j && this.canGrowInto(worldserver.getType(blockposition1)); ++k) {
            worldserver.setTypeUpdate(blockposition1, (BlockState) iblockdata.setValue(GrowingPlantHeadBlock.AGE, i));
            blockposition1 = blockposition1.relative(this.growthDirection);
            i = Math.min(i + 1, 25);
        }

    }

    protected abstract int getBlocksToGrowWhenBonemealed(Random random);

    protected abstract boolean canGrowInto(BlockState iblockdata);

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return this;
    }
}
