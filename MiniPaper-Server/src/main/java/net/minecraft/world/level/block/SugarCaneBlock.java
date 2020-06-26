package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SugarCaneBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    protected SugarCaneBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(SugarCaneBlock.AGE, 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return SugarCaneBlock.SHAPE;
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        }

    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.isEmptyBlock(blockposition.above())) {
            int i;

            for (i = 1; worldserver.getType(blockposition.below(i)).is((Block) this); ++i) {
                ;
            }

            if (i < 3) {
                int j = (Integer) iblockdata.getValue(SugarCaneBlock.AGE);

                if (j >= (byte) range(3, ((100.0F / worldserver.spigotConfig.caneModifier) * 15) + 0.5F, 15)) { // Spigot
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition.above(), this.getBlockData()); // CraftBukkit
                    worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(SugarCaneBlock.AGE, 0), 4);
                } else {
                    worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(SugarCaneBlock.AGE, j + 1), 4);
                }
            }
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (!iblockdata.canSurvive(generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockState iblockdata1 = iworldreader.getType(blockposition.below());

        if (iblockdata1.getBlock() == this) {
            return true;
        } else {
            if (iblockdata1.is(Blocks.GRASS_BLOCK) || iblockdata1.is(Blocks.DIRT) || iblockdata1.is(Blocks.COARSE_DIRT) || iblockdata1.is(Blocks.PODZOL) || iblockdata1.is(Blocks.SAND) || iblockdata1.is(Blocks.RED_SAND)) {
                BlockPos blockposition1 = blockposition.below();
                Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext()) {
                    Direction enumdirection = (Direction) iterator.next();
                    BlockState iblockdata2 = iworldreader.getType(blockposition1.relative(enumdirection));
                    FluidState fluid = iworldreader.getFluidState(blockposition1.relative(enumdirection));

                    if (fluid.is((Tag) FluidTags.WATER) || iblockdata2.is(Blocks.FROSTED_ICE)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(SugarCaneBlock.AGE);
    }
}
