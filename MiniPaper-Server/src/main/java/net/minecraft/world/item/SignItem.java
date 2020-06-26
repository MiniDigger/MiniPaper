package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SignItem extends StandingAndWallBlockItem {

    public static BlockPos openSign; // CraftBukkit

    public SignItem(Item.Info item_info, Block block, Block block1) {
        super(block, block1, item_info);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos blockposition, Level world, @Nullable Player entityhuman, ItemStack itemstack, BlockState iblockdata) {
        boolean flag = super.updateCustomBlockEntityTag(blockposition, world, entityhuman, itemstack, iblockdata);

        if (!world.isClientSide && !flag && entityhuman != null) {
            // CraftBukkit start - SPIGOT-4678
            // entityhuman.openSign((TileEntitySign) world.getTileEntity(blockposition));
            SignItem.openSign = blockposition;
            // CraftBukkit end
        }

        return flag;
    }
}
