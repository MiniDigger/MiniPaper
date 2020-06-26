package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CakeBlock extends Block {

    public static final IntegerProperty BITES = BlockStateProperties.BITES;
    protected static final VoxelShape[] SHAPE_BY_BITE = new VoxelShape[]{Block.box(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D), Block.box(3.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D), Block.box(5.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D), Block.box(7.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D), Block.box(9.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D), Block.box(11.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D), Block.box(13.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D)};

    protected CakeBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CakeBlock.BITES, 0));
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return CakeBlock.SHAPE_BY_BITE[(Integer) iblockdata.getValue(CakeBlock.BITES)];
    }

    @Override
    public InteractionResult interact(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman, InteractionHand enumhand, BlockHitResult movingobjectpositionblock) {
        if (world.isClientSide) {
            ItemStack itemstack = entityhuman.getItemInHand(enumhand);

            if (this.eat((LevelAccessor) world, blockposition, iblockdata, entityhuman).consumesAction()) {
                return InteractionResult.SUCCESS;
            }

            if (itemstack.isEmpty()) {
                return InteractionResult.CONSUME;
            }
        }

        return this.eat((LevelAccessor) world, blockposition, iblockdata, entityhuman);
    }

    private InteractionResult eat(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata, Player entityhuman) {
        if (!entityhuman.canEat(false)) {
            return InteractionResult.PASS;
        } else {
            entityhuman.awardStat(Stats.EAT_CAKE_SLICE);
            // CraftBukkit start
            // entityhuman.getFoodData().eat(2, 0.1F);
            int oldFoodLevel = entityhuman.getFoodData().foodLevel;

            org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(entityhuman, 2 + oldFoodLevel);

            if (!event.isCancelled()) {
                entityhuman.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 0.1F);
            }

            ((ServerPlayer) entityhuman).getBukkitEntity().sendHealthUpdate();
            // CraftBukkit end
            int i = (Integer) iblockdata.getValue(CakeBlock.BITES);

            if (i < 6) {
                generatoraccess.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(CakeBlock.BITES, i + 1), 3);
            } else {
                generatoraccess.removeBlock(blockposition, false);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection == Direction.DOWN && !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return iworldreader.getType(blockposition.below()).getMaterial().isSolid();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CakeBlock.BITES);
    }

    @Override
    public int getAnalogOutputSignal(BlockState iblockdata, Level world, BlockPos blockposition) {
        return (7 - (Integer) iblockdata.getValue(CakeBlock.BITES)) * 2;
    }

    @Override
    public boolean isComplexRedstone(BlockState iblockdata) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
