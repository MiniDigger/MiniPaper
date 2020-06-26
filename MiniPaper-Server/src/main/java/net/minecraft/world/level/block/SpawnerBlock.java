package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerBlock extends BaseEntityBlock {

    protected SpawnerBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new SpawnerBlockEntity();
    }

    @Override
    public void dropNaturally(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        super.dropNaturally(iblockdata, world, blockposition, itemstack);
        /* CraftBukkit start - Delegate to getExpDrop
        int i = 15 + world.random.nextInt(15) + world.random.nextInt(15);

        this.dropExperience(world, blockposition, i);
        */
    }

    @Override
    public int getExpDrop(BlockState iblockdata, Level world, BlockPos blockposition, ItemStack itemstack) {
        int i = 15 + world.random.nextInt(15) + world.random.nextInt(15);

        return i;
        // CraftBukkit end
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }
}
