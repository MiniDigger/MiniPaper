package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
// CraftBukkit start
import java.util.List;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.bukkit.event.block.SpongeAbsorbEvent;
// CraftBukkit end

public class SpongeBlock extends Block {

    protected SpongeBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            this.tryAbsorbWater(world, blockposition);
        }
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        this.tryAbsorbWater(world, blockposition);
        super.doPhysics(iblockdata, world, blockposition, block, blockposition1, flag);
    }

    protected void tryAbsorbWater(Level world, BlockPos blockposition) {
        if (this.removeWaterBreadthFirstSearch(world, blockposition)) {
            world.setTypeAndData(blockposition, Blocks.WET_SPONGE.getBlockData(), 2);
            world.levelEvent(2001, blockposition, Block.getCombinedId(Blocks.WATER.getBlockData()));
        }

    }

    private boolean removeWaterBreadthFirstSearch(Level world, BlockPos blockposition) {
        Queue<Tuple<BlockPos, Integer>> queue = Lists.newLinkedList();

        queue.add(new Tuple<>(blockposition, 0));
        int i = 0;
        BlockStateListPopulator blockList = new BlockStateListPopulator(world); // CraftBukkit - Use BlockStateListPopulator

        while (!queue.isEmpty()) {
            Tuple<BlockPos, Integer> tuple = (Tuple) queue.poll();
            BlockPos blockposition1 = (BlockPos) tuple.getA();
            int j = (Integer) tuple.getB();
            Direction[] aenumdirection = Direction.values();
            int k = aenumdirection.length;

            for (int l = 0; l < k; ++l) {
                Direction enumdirection = aenumdirection[l];
                BlockPos blockposition2 = blockposition1.relative(enumdirection);
                // CraftBukkit start
                BlockState iblockdata = blockList.getType(blockposition2);
                FluidState fluid = blockList.getFluidState(blockposition2);
                // CraftBukkit end
                Material material = iblockdata.getMaterial();

                if (fluid.is((Tag) FluidTags.WATER)) {
                    if (iblockdata.getBlock() instanceof BucketPickup && ((BucketPickup) iblockdata.getBlock()).removeFluid(blockList, blockposition2, iblockdata) != Fluids.EMPTY) { // CraftBukkit
                        ++i;
                        if (j < 6) {
                            queue.add(new Tuple<>(blockposition2, j + 1));
                        }
                    } else if (iblockdata.getBlock() instanceof LiquidBlock) {
                        blockList.setTypeAndData(blockposition2, Blocks.AIR.getBlockData(), 3); // CraftBukkit
                        ++i;
                        if (j < 6) {
                            queue.add(new Tuple<>(blockposition2, j + 1));
                        }
                    } else if (material == Material.WATER_PLANT || material == Material.REPLACEABLE_WATER_PLANT) {
                        // CraftBukkit start
                        // TileEntity tileentity = iblockdata.getBlock().isTileEntity() ? world.getTileEntity(blockposition2) : null;

                        // a(iblockdata, world, blockposition2, tileentity);
                        blockList.setTypeAndData(blockposition2, Blocks.AIR.getBlockData(), 3);
                        // CraftBukkit end
                        ++i;
                        if (j < 6) {
                            queue.add(new Tuple<>(blockposition2, j + 1));
                        }
                    }
                }
            }

            if (i > 64) {
                break;
            }
        }
        // CraftBukkit start
        List<CraftBlockState> blocks = blockList.getList(); // Is a clone
        if (!blocks.isEmpty()) {
            final org.bukkit.block.Block bblock = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

            SpongeAbsorbEvent event = new SpongeAbsorbEvent(bblock, (List<org.bukkit.block.BlockState>) (List) blocks);
            world.getServerOH().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            for (CraftBlockState block : blocks) {
                BlockPos blockposition2 = block.getPosition();
                BlockState iblockdata = world.getType(blockposition2);
                FluidState fluid = world.getFluidState(blockposition2);
                Material material = iblockdata.getMaterial();

                if (fluid.is(FluidTags.WATER)) {
                    if (iblockdata.getBlock() instanceof BucketPickup && ((BucketPickup) iblockdata.getBlock()).removeFluid(blockList, blockposition2, iblockdata) != Fluids.EMPTY) {
                        // NOP
                    } else if (iblockdata.getBlock() instanceof LiquidBlock) {
                        // NOP
                    } else if (material == Material.WATER_PLANT || material == Material.REPLACEABLE_WATER_PLANT) {
                        BlockEntity tileentity = iblockdata.getBlock().isEntityBlock() ? world.getBlockEntity(blockposition2) : null;

                        dropResources(iblockdata, world, blockposition2, tileentity);
                    }
                }
                world.setTypeAndData(blockposition2, block.getHandle(), block.getFlag());
            }
        }
        // CraftBukkit end

        return i > 0;
    }
}
