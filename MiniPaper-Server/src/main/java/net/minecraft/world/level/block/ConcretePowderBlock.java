package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.event.block.BlockFormEvent;
// CraftBukkit end

public class ConcretePowderBlock extends FallingBlock {

    private final BlockState concrete;

    public ConcretePowderBlock(Block block, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.concrete = block.getBlockData();
    }

    @Override
    public void onLand(Level world, BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1, FallingBlockEntity entityfallingblock) {
        if (canHarden(world, blockposition, iblockdata1)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition, this.concrete, 3); // CraftBukkit
        }

    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        Level world = blockactioncontext.getLevel();
        BlockPos blockposition = blockactioncontext.getClickedPos();
        BlockState iblockdata = world.getType(blockposition);

        // CraftBukkit start
        if (!canHarden(world, blockposition, iblockdata)) {
            return super.getPlacedState(blockactioncontext);
        }

        // TODO: An event factory call for methods like this
        CraftBlockState blockState = CraftBlockState.getBlockState(world, blockposition);
        blockState.setData(this.concrete);

        BlockFormEvent event = new BlockFormEvent(blockState.getBlock(), blockState);
        world.getServer().server.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            return blockState.getHandle();
        }

        return super.getPlacedState(blockactioncontext);
        // CraftBukkit end
    }

    private static boolean canHarden(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata) {
        return canSolidify(iblockdata) || touchesLiquid(iblockaccess, blockposition);
    }

    private static boolean touchesLiquid(BlockGetter iblockaccess, BlockPos blockposition) {
        boolean flag = false;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = blockposition.i();
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];
            BlockState iblockdata = iblockaccess.getType(blockposition_mutableblockposition);

            if (enumdirection != Direction.DOWN || canSolidify(iblockdata)) {
                blockposition_mutableblockposition.a((Vec3i) blockposition, enumdirection);
                iblockdata = iblockaccess.getType(blockposition_mutableblockposition);
                if (canSolidify(iblockdata) && !iblockdata.isFaceSturdy(iblockaccess, blockposition, enumdirection.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static boolean canSolidify(BlockState iblockdata) {
        return iblockdata.getFluidState().is((Tag) FluidTags.WATER);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        // CraftBukkit start
        if (touchesLiquid((BlockGetter) generatoraccess, blockposition)) {
            CraftBlockState blockState = CraftBlockState.getBlockState(generatoraccess, blockposition);
            blockState.setData(this.concrete);

            BlockFormEvent event = new BlockFormEvent(blockState.getBlock(), blockState);
            generatoraccess.getLevel().getServer().server.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        // CraftBukkit end
    }
}
