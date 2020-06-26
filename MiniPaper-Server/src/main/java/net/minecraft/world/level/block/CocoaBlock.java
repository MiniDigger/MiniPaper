package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class CocoaBlock extends HorizontalDirectionalBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
    protected static final VoxelShape[] EAST_AABB = new VoxelShape[]{Block.box(11.0D, 7.0D, 6.0D, 15.0D, 12.0D, 10.0D), Block.box(9.0D, 5.0D, 5.0D, 15.0D, 12.0D, 11.0D), Block.box(7.0D, 3.0D, 4.0D, 15.0D, 12.0D, 12.0D)};
    protected static final VoxelShape[] WEST_AABB = new VoxelShape[]{Block.box(1.0D, 7.0D, 6.0D, 5.0D, 12.0D, 10.0D), Block.box(1.0D, 5.0D, 5.0D, 7.0D, 12.0D, 11.0D), Block.box(1.0D, 3.0D, 4.0D, 9.0D, 12.0D, 12.0D)};
    protected static final VoxelShape[] NORTH_AABB = new VoxelShape[]{Block.box(6.0D, 7.0D, 1.0D, 10.0D, 12.0D, 5.0D), Block.box(5.0D, 5.0D, 1.0D, 11.0D, 12.0D, 7.0D), Block.box(4.0D, 3.0D, 1.0D, 12.0D, 12.0D, 9.0D)};
    protected static final VoxelShape[] SOUTH_AABB = new VoxelShape[]{Block.box(6.0D, 7.0D, 11.0D, 10.0D, 12.0D, 15.0D), Block.box(5.0D, 5.0D, 9.0D, 11.0D, 12.0D, 15.0D), Block.box(4.0D, 3.0D, 7.0D, 12.0D, 12.0D, 15.0D)};

    public CocoaBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CocoaBlock.FACING, Direction.NORTH)).setValue(CocoaBlock.AGE, 0));
    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(CocoaBlock.AGE) < 2;
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.random.nextInt(Math.max(1, (int) (100.0F / worldserver.spigotConfig.cocoaModifier) * 5)) == 0) { // Spigot
            int i = (Integer) iblockdata.getValue(CocoaBlock.AGE);

            if (i < 2) {
                CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (BlockState) iblockdata.setValue(CocoaBlock.AGE, i + 1), 2); // CraftBukkkit
            }
        }

    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        Block block = iworldreader.getType(blockposition.relative((Direction) iblockdata.getValue(CocoaBlock.FACING))).getBlock();

        return block.is((Tag) BlockTags.JUNGLE_LOGS);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        int i = (Integer) iblockdata.getValue(CocoaBlock.AGE);

        switch ((Direction) iblockdata.getValue(CocoaBlock.FACING)) {
            case SOUTH:
                return CocoaBlock.SOUTH_AABB[i];
            case NORTH:
            default:
                return CocoaBlock.NORTH_AABB[i];
            case WEST:
                return CocoaBlock.WEST_AABB[i];
            case EAST:
                return CocoaBlock.EAST_AABB[i];
        }
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockState iblockdata = this.getBlockData();
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        Direction[] aenumdirection = blockactioncontext.getNearestLookingDirections();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (enumdirection.getAxis().isHorizontal()) {
                iblockdata = (BlockState) iblockdata.setValue(CocoaBlock.FACING, enumdirection);
                if (iblockdata.canSurvive(world, blockposition)) {
                    return iblockdata;
                }
            }
        }

        return null;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection == iblockdata.getValue(CocoaBlock.FACING) && !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return (Integer) iblockdata.getValue(CocoaBlock.AGE) < 2;
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (BlockState) iblockdata.setValue(CocoaBlock.AGE, (Integer) iblockdata.getValue(CocoaBlock.AGE) + 1), 2); // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CocoaBlock.FACING, CocoaBlock.AGE);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
