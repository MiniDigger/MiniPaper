package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class RedstoneTorchBlock extends TorchBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<BlockGetter, List<RedstoneTorchBlock.RedstoneUpdateInfo>> RECENT_TOGGLES = new WeakHashMap();

    protected RedstoneTorchBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info, DustParticleOptions.REDSTONE);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(RedstoneTorchBlock.LIT, true));
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            world.updateNeighborsAt(blockposition.relative(enumdirection), this);
        }

    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag) {
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                world.updateNeighborsAt(blockposition.relative(enumdirection), this);
            }

        }
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return (Boolean) iblockdata.getValue(RedstoneTorchBlock.LIT) && Direction.UP != enumdirection ? 15 : 0;
    }

    protected boolean hasNeighborSignal(Level world, BlockPos blockposition, BlockState iblockdata) {
        return world.hasSignal(blockposition.below(), Direction.DOWN);
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        boolean flag = this.hasNeighborSignal((Level) worldserver, blockposition, iblockdata);
        List list = (List) RedstoneTorchBlock.RECENT_TOGGLES.get(worldserver);

        while (list != null && !list.isEmpty() && worldserver.getGameTime() - ((RedstoneTorchBlock.RedstoneUpdateInfo) list.get(0)).b > 60L) {
            list.remove(0);
        }

        // CraftBukkit start
        org.bukkit.plugin.PluginManager manager = worldserver.getServerOH().getPluginManager();
        org.bukkit.block.Block block = worldserver.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
        int oldCurrent = ((Boolean) iblockdata.getValue(RedstoneTorchBlock.LIT)).booleanValue() ? 15 : 0;

        BlockRedstoneEvent event = new BlockRedstoneEvent(block, oldCurrent, oldCurrent);
        // CraftBukkit end
        if ((Boolean) iblockdata.getValue(RedstoneTorchBlock.LIT)) {
            if (flag) {
                // CraftBukkit start
                if (oldCurrent != 0) {
                    event.setNewCurrent(0);
                    manager.callEvent(event);
                    if (event.getNewCurrent() != 0) {
                        return;
                    }
                }
                // CraftBukkit end
                worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(RedstoneTorchBlock.LIT, false), 3);
                if (isToggledTooFrequently(worldserver, blockposition, true)) {
                    worldserver.levelEvent(1502, blockposition, 0);
                    worldserver.getBlockTickList().scheduleTick(blockposition, worldserver.getType(blockposition).getBlock(), 160);
                }
            }
        } else if (!flag && !isToggledTooFrequently(worldserver, blockposition, false)) {
            // CraftBukkit start
            if (oldCurrent != 15) {
                event.setNewCurrent(15);
                manager.callEvent(event);
                if (event.getNewCurrent() != 15) {
                    return;
                }
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(RedstoneTorchBlock.LIT, true), 3);
        }

    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if ((Boolean) iblockdata.getValue(RedstoneTorchBlock.LIT) == this.hasNeighborSignal(world, blockposition, iblockdata) && !world.getBlockTickList().willTickThisTick(blockposition, this)) {
            world.getBlockTickList().scheduleTick(blockposition, this, 2);
        }

    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return enumdirection == Direction.DOWN ? iblockdata.getSignal(iblockaccess, blockposition, enumdirection) : 0;
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(RedstoneTorchBlock.LIT);
    }

    private static boolean isToggledTooFrequently(Level world, BlockPos blockposition, boolean flag) {
        List<RedstoneTorchBlock.RedstoneUpdateInfo> list = (List) RedstoneTorchBlock.RECENT_TOGGLES.computeIfAbsent(world, (iblockaccess) -> {
            return Lists.newArrayList();
        });

        if (flag) {
            list.add(new RedstoneTorchBlock.RedstoneUpdateInfo(blockposition.immutable(), world.getGameTime()));
        }

        int i = 0;

        for (int j = 0; j < list.size(); ++j) {
            RedstoneTorchBlock.RedstoneUpdateInfo blockredstonetorch_redstoneupdateinfo = (RedstoneTorchBlock.RedstoneUpdateInfo) list.get(j);

            if (blockredstonetorch_redstoneupdateinfo.a.equals(blockposition)) {
                ++i;
                if (i >= 8) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class RedstoneUpdateInfo {

        private final BlockPos a;
        private final long b;

        public RedstoneUpdateInfo(BlockPos blockposition, long i) {
            this.a = blockposition;
            this.b = i;
        }
    }
}
