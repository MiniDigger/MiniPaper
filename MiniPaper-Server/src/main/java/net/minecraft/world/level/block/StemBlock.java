package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class StemBlock extends BushBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    protected static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{Block.box(7.0D, 0.0D, 7.0D, 9.0D, 2.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 4.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 6.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 8.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 10.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 12.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 14.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D)};
    private final StemGrownBlock fruit;

    protected StemBlock(StemGrownBlock blockstemmed, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.fruit = blockstemmed;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(StemBlock.AGE, 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return StemBlock.SHAPE_BY_AGE[(Integer) iblockdata.getValue(StemBlock.AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.is(Blocks.FARMLAND);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.getRawBrightness(blockposition, 0) >= 9) {
            float f = CropBlock.getGrowthSpeed((Block) this, (BlockGetter) worldserver, blockposition);

            if (random.nextInt((int) ((100.0F / (this == Blocks.PUMPKIN_STEM ? worldserver.spigotConfig.pumpkinModifier : worldserver.spigotConfig.melonModifier)) * (25.0F / f)) + 1) == 0) { // Spigot
                int i = (Integer) iblockdata.getValue(StemBlock.AGE);

                if (i < 7) {
                    iblockdata = (BlockState) iblockdata.setValue(StemBlock.AGE, i + 1);
                    CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, iblockdata, 2); // CraftBukkit
                } else {
                    Direction enumdirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    BlockPos blockposition1 = blockposition.relative(enumdirection);
                    BlockState iblockdata1 = worldserver.getType(blockposition1.below());

                    if (worldserver.getType(blockposition1).isAir() && (iblockdata1.is(Blocks.FARMLAND) || iblockdata1.is(Blocks.DIRT) || iblockdata1.is(Blocks.COARSE_DIRT) || iblockdata1.is(Blocks.PODZOL) || iblockdata1.is(Blocks.GRASS_BLOCK))) {
                        // CraftBukkit start
                        if (!CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition1, this.fruit.getBlockData())) {
                            return;
                        }
                        // CraftBukkit end
                        worldserver.setTypeUpdate(blockposition, (BlockState) this.fruit.getAttachedStem().getBlockData().setValue(HorizontalDirectionalBlock.FACING, enumdirection));
                    }
                }
            }

        }
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return (Integer) iblockdata.getValue(StemBlock.AGE) != 7;
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        int i = Math.min(7, (Integer) iblockdata.getValue(StemBlock.AGE) + Mth.nextInt(worldserver.random, 2, 5));
        BlockState iblockdata1 = (BlockState) iblockdata.setValue(StemBlock.AGE, i);

        CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, iblockdata1, 2); // CraftBukkit
        if (i == 7) {
            iblockdata1.randomTick(worldserver, blockposition, worldserver.random);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(StemBlock.AGE);
    }

    public StemGrownBlock getFruit() {
        return this.fruit;
    }
}
