package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class DoublePlantBlock extends BushBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public DoublePlantBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        DoubleBlockHalf blockpropertydoubleblockhalf = (DoubleBlockHalf) iblockdata.getValue(DoublePlantBlock.HALF);

        return enumdirection.getAxis() == Direction.Axis.Y && blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER == (enumdirection == Direction.UP) && (!iblockdata1.is((Block) this) || iblockdata1.getValue(DoublePlantBlock.HALF) == blockpropertydoubleblockhalf) ? Blocks.AIR.getBlockData() : (blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER && enumdirection == Direction.DOWN && !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1));
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        BlockPos blockposition = blockactioncontext.getClickedPos();

        return blockposition.getY() < 255 && blockactioncontext.getLevel().getType(blockposition.above()).canBeReplaced(blockactioncontext) ? super.getPlacedState(blockactioncontext) : null;
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, LivingEntity entityliving, ItemStack itemstack) {
        world.setTypeAndData(blockposition.above(), (BlockState) this.getBlockData().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        if (iblockdata.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.UPPER) {
            return super.canPlace(iblockdata, iworldreader, blockposition);
        } else {
            BlockState iblockdata1 = iworldreader.getType(blockposition.below());

            return iblockdata1.is((Block) this) && iblockdata1.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
        }
    }

    public void placeAt(LevelAccessor generatoraccess, BlockPos blockposition, int i) {
        generatoraccess.setTypeAndData(blockposition, (BlockState) this.getBlockData().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), i);
        generatoraccess.setTypeAndData(blockposition.above(), (BlockState) this.getBlockData().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), i);
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!world.isClientSide) {
            if (entityhuman.isCreative()) {
                preventCreativeDropFromBottomPart(world, blockposition, iblockdata, entityhuman);
            } else {
                dropItems(iblockdata, world, blockposition, (BlockEntity) null, entityhuman, entityhuman.getMainHandItem());
            }
        }

        super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    @Override
    public void playerDestroy(Level world, Player entityhuman, BlockPos blockposition, BlockState iblockdata, @Nullable BlockEntity tileentity, ItemStack itemstack) {
        super.playerDestroy(world, entityhuman, blockposition, Blocks.AIR.getBlockData(), tileentity, itemstack);
    }

    protected static void preventCreativeDropFromBottomPart(Level world, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPhysicsEvent(world, blockposition).isCancelled()) {
            return;
        }
        // CraftBukkit end
        DoubleBlockHalf blockpropertydoubleblockhalf = (DoubleBlockHalf) iblockdata.getValue(DoublePlantBlock.HALF);

        if (blockpropertydoubleblockhalf == DoubleBlockHalf.UPPER) {
            BlockPos blockposition1 = blockposition.below();
            BlockState iblockdata1 = world.getType(blockposition1);

            if (iblockdata1.getBlock() == iblockdata.getBlock() && iblockdata1.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                world.setTypeAndData(blockposition1, Blocks.AIR.getBlockData(), 35);
                world.levelEvent(entityhuman, 2001, blockposition1, Block.getCombinedId(iblockdata1));
            }
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(DoublePlantBlock.HALF);
    }

    @Override
    public BlockBehaviour.OffsetType getOffsetType() {
        return BlockBehaviour.OffsetType.XZ;
    }
}
