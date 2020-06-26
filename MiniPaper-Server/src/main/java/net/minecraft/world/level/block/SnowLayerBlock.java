package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SnowLayerBlock extends Block {

    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    protected static final VoxelShape[] SHAPE_BY_LAYER = new VoxelShape[]{Shapes.empty(), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)};

    protected SnowLayerBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(SnowLayerBlock.LAYERS, 1));
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        switch (pathmode) {
            case LAND:
                return (Integer) iblockdata.getValue(SnowLayerBlock.LAYERS) < 5;
            case WATER:
                return false;
            case AIR:
                return false;
            default:
                return false;
        }
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return SnowLayerBlock.SHAPE_BY_LAYER[(Integer) iblockdata.getValue(SnowLayerBlock.LAYERS)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return SnowLayerBlock.SHAPE_BY_LAYER[(Integer) iblockdata.getValue(SnowLayerBlock.LAYERS) - 1];
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return SnowLayerBlock.SHAPE_BY_LAYER[(Integer) iblockdata.getValue(SnowLayerBlock.LAYERS)];
    }

    @Override
    public VoxelShape getVisualShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return SnowLayerBlock.SHAPE_BY_LAYER[(Integer) iblockdata.getValue(SnowLayerBlock.LAYERS)];
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState iblockdata) {
        return true;
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockState iblockdata1 = iworldreader.getType(blockposition.below());

        return !iblockdata1.is(Blocks.ICE) && !iblockdata1.is(Blocks.PACKED_ICE) && !iblockdata1.is(Blocks.BARRIER) ? (!iblockdata1.is(Blocks.HONEY_BLOCK) && !iblockdata1.is(Blocks.SOUL_SAND) ? Block.isFaceFull(iblockdata1.getCollisionShape(iworldreader, blockposition.below()), Direction.UP) || iblockdata1.getBlock() == this && (Integer) iblockdata1.getValue(SnowLayerBlock.LAYERS) == 8 : true) : false;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.getBrightness(LightLayer.BLOCK, blockposition) > 11) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            dropResources(iblockdata, (Level) worldserver, blockposition);
            worldserver.removeBlock(blockposition, false);
        }

    }

    @Override
    public boolean canBeReplaced(BlockState iblockdata, BlockPlaceContext blockactioncontext) {
        int i = (Integer) iblockdata.getValue(SnowLayerBlock.LAYERS);

        return blockactioncontext.getItemInHand().getItem() == this.asItem() && i < 8 ? (blockactioncontext.replacingClickedOnBlock() ? blockactioncontext.getClickedFace() == Direction.UP : true) : i == 1;
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos());

        if (iblockdata.is((Block) this)) {
            int i = (Integer) iblockdata.getValue(SnowLayerBlock.LAYERS);

            return (BlockState) iblockdata.setValue(SnowLayerBlock.LAYERS, Math.min(8, i + 1));
        } else {
            return super.getPlacedState(blockactioncontext);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(SnowLayerBlock.LAYERS);
    }
}
