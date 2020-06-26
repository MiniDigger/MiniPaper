package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class CoralBlock extends Block {

    private final Block deadBlock;

    public CoralBlock(Block block, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.deadBlock = block;
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!this.scanForWater((BlockGetter) worldserver, blockposition)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, this.deadBlock.getBlockData()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, this.deadBlock.getBlockData(), 2);
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (!this.scanForWater((BlockGetter) generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 60 + generatoraccess.getRandom().nextInt(40));
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    protected boolean scanForWater(BlockGetter iblockaccess, BlockPos blockposition) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];
            FluidState fluid = iblockaccess.getFluidState(blockposition.relative(enumdirection));

            if (fluid.is((Tag) FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        if (!this.scanForWater((BlockGetter) blockactioncontext.getLevel(), blockactioncontext.getClickedPos())) {
            blockactioncontext.getLevel().getBlockTickList().scheduleTick(blockactioncontext.getClickedPos(), this, 60 + blockactioncontext.getLevel().getRandom().nextInt(40));
        }

        return this.getBlockData();
    }
}
