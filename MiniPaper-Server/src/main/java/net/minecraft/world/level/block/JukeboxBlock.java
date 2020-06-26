package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class JukeboxBlock extends BaseEntityBlock {

    public static final BooleanProperty HAS_RECORD = BlockStateProperties.HAS_RECORD;

    protected JukeboxBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(JukeboxBlock.HAS_RECORD, false));
    }

    @Override
    public void postPlace(Level world, BlockPos blockposition, BlockState iblockdata, @Nullable LivingEntity entityliving, ItemStack itemstack) {
        super.postPlace(world, blockposition, iblockdata, entityliving, itemstack);
        CompoundTag nbttagcompound = itemstack.getOrCreateTag();

        if (nbttagcompound.contains("BlockEntityTag")) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("BlockEntityTag");

            if (nbttagcompound1.contains("RecordItem")) {
                world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(JukeboxBlock.HAS_RECORD, true), 2);
            }
        }

    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if ((Boolean) iblockdata.getValue(JukeboxBlock.HAS_RECORD)) {
            this.dropRecording(world, blockposition);
            iblockdata = (BlockState) iblockdata.setValue(JukeboxBlock.HAS_RECORD, false);
            world.setTypeAndData(blockposition, iblockdata, 2);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public void setRecord(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata, ItemStack itemstack) {
        BlockEntity tileentity = generatoraccess.getBlockEntity(blockposition);

        if (tileentity instanceof JukeboxBlockEntity) {
            // CraftBukkit start - There can only be one
            itemstack = itemstack.copy();
            if (!itemstack.isEmpty()) {
                itemstack.setCount(1);
            }
            ((JukeboxBlockEntity) tileentity).setRecord(itemstack);
            // CraftBukkit end
            generatoraccess.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(JukeboxBlock.HAS_RECORD, true), 2);
        }
    }

    public void dropRecording(Level world, BlockPos blockposition) {
        if (!world.isClientSide) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof JukeboxBlockEntity) {
                JukeboxBlockEntity tileentityjukebox = (JukeboxBlockEntity) tileentity;
                ItemStack itemstack = tileentityjukebox.getRecord();

                if (!itemstack.isEmpty()) {
                    world.levelEvent(1010, blockposition, 0);
                    tileentityjukebox.clearContent();
                    float f = 0.7F;
                    double d0 = (double) (world.random.nextFloat() * 0.7F) + 0.15000000596046448D;
                    double d1 = (double) (world.random.nextFloat() * 0.7F) + 0.06000000238418579D + 0.6D;
                    double d2 = (double) (world.random.nextFloat() * 0.7F) + 0.15000000596046448D;
                    ItemStack itemstack1 = itemstack.copy();
                    ItemEntity entityitem = new ItemEntity(world, (double) blockposition.getX() + d0, (double) blockposition.getY() + d1, (double) blockposition.getZ() + d2, itemstack1);

                    entityitem.setDefaultPickUpDelay();
                    world.addFreshEntity(entityitem);
                }
            }
        }
    }

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!iblockdata.is(iblockdata1.getBlock())) {
            this.dropRecording(world, blockposition);
            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockGetter iblockaccess) {
        return new JukeboxBlockEntity();
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        BlockEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof JukeboxBlockEntity) {
            Item item = ((JukeboxBlockEntity) tileentity).getRecord().getItem();

            if (item instanceof RecordItem) {
                return ((RecordItem) item).getAnalogOutput();
            }
        }

        return 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(JukeboxBlock.HAS_RECORD);
    }
}
