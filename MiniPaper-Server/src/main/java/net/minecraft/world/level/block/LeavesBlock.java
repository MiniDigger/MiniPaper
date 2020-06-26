package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.LeavesDecayEvent; // CraftBukkit

public class LeavesBlock extends Block {

    public static final IntegerProperty DISTANCE = BlockStateProperties.DISTANCE;
    public static final BooleanProperty PERSISTENT = BlockStateProperties.PERSISTENT;

    public LeavesBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LeavesBlock.DISTANCE, 7)).setValue(LeavesBlock.PERSISTENT, false));
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return Shapes.empty();
    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(LeavesBlock.DISTANCE) == 7 && !(Boolean) iblockdata.getValue(LeavesBlock.PERSISTENT);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!(Boolean) iblockdata.getValue(LeavesBlock.PERSISTENT) && (Integer) iblockdata.getValue(LeavesBlock.DISTANCE) == 7) {
            // CraftBukkit start
            LeavesDecayEvent event = new LeavesDecayEvent(worldserver.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
            worldserver.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled() || worldserver.getType(blockposition).getBlock() != this) {
                return;
            }
            // CraftBukkit end
            dropResources(iblockdata, (Level) worldserver, blockposition);
            worldserver.removeBlock(blockposition, false);
        }

    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        worldserver.setTypeAndData(blockposition, updateDistance(iblockdata, (LevelAccessor) worldserver, blockposition), 3);
    }

    @Override
    public int getLightBlock(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return 1;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        int i = getDistanceAt(iblockdata1) + 1;

        if (i != 1 || (Integer) iblockdata.getValue(LeavesBlock.DISTANCE) != i) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        return iblockdata;
    }

    private static BlockState updateDistance(BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition) {
        int i = 7;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
        Direction[] aenumdirection = Direction.values();
        int j = aenumdirection.length;

        for (int k = 0; k < j; ++k) {
            Direction enumdirection = aenumdirection[k];

            blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection);
            i = Math.min(i, getDistanceAt(generatoraccess.getType(blockposition_mutableblockposition)) + 1);
            if (i == 1) {
                break;
            }
        }

        return (BlockState) iblockdata.setValue(LeavesBlock.DISTANCE, i);
    }

    private static int getDistanceAt(BlockState iblockdata) {
        return BlockTags.LOGS.contains(iblockdata.getBlock()) ? 0 : (iblockdata.getBlock() instanceof LeavesBlock ? (Integer) iblockdata.getValue(LeavesBlock.DISTANCE) : 7);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(LeavesBlock.DISTANCE, LeavesBlock.PERSISTENT);
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return updateDistance((BlockState) this.getBlockData().setValue(LeavesBlock.PERSISTENT, true), (LevelAccessor) blockactioncontext.getLevel(), blockactioncontext.getClickedPos());
    }
}
