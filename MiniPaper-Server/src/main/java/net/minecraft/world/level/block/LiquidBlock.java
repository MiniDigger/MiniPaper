package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LiquidBlock extends Block implements BucketPickup {

    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
    protected final FlowingFluid fluid;
    private final List<FluidState> stateCache;
    public static final VoxelShape STABLE_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

    protected LiquidBlock(FlowingFluid fluidtypeflowing, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.fluid = fluidtypeflowing;
        this.stateCache = Lists.newArrayList();
        this.stateCache.add(fluidtypeflowing.getSource(false));

        for (int i = 1; i < 8; ++i) {
            this.stateCache.add(fluidtypeflowing.getFlowing(8 - i, false));
        }

        this.stateCache.add(fluidtypeflowing.getFlowing(8, true));
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LiquidBlock.LEVEL, 0));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return voxelshapecollision.isAbove(LiquidBlock.STABLE_SHAPE, blockposition, true) && (Integer) iblockdata.getValue(LiquidBlock.LEVEL) == 0 && voxelshapecollision.canStandOnFluid(iblockaccess.getFluidState(blockposition.above()), this.fluid) ? LiquidBlock.STABLE_SHAPE : Shapes.empty();
    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return iblockdata.getFluidState().isRandomlyTicking();
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        iblockdata.getFluidState().randomTick(worldserver, blockposition, random);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return false;
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return !this.fluid.is((Tag) FluidTags.LAVA);
    }

    @Override
    public FluidState getFluidState(BlockState iblockdata) {
        int i = (Integer) iblockdata.getValue(LiquidBlock.LEVEL);

        return (FluidState) this.stateCache.get(Math.min(i, 8));
    }

    @Override
    public RenderShape getRenderShape(BlockState iblockdata) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState iblockdata, LootContext.Builder loottableinfo_builder) {
        return Collections.emptyList();
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (this.shouldSpreadLiquid(world, blockposition, iblockdata)) {
            world.getFluidTickList().scheduleTick(blockposition, iblockdata.getFluidState().getType(), this.fluid.getTickDelay((LevelReader) world));
        }

    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (iblockdata.getFluidState().isSource() || iblockdata1.getFluidState().isSource()) {
            generatoraccess.getFluidTickList().scheduleTick(blockposition, iblockdata.getFluidState().getType(), this.fluid.getTickDelay((LevelReader) generatoraccess));
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public void doPhysics(BlockState iblockdata, Level world, BlockPos blockposition, Block block, BlockPos blockposition1, boolean flag) {
        if (this.shouldSpreadLiquid(world, blockposition, iblockdata)) {
            world.getFluidTickList().scheduleTick(blockposition, iblockdata.getFluidState().getType(), this.fluid.getTickDelay((LevelReader) world));
        }

    }

    private boolean shouldSpreadLiquid(Level world, BlockPos blockposition, BlockState iblockdata) {
        if (this.fluid.is((Tag) FluidTags.LAVA)) {
            boolean flag = world.getType(blockposition.below()).is(Blocks.SOUL_SOIL);
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                if (enumdirection != Direction.DOWN) {
                    BlockPos blockposition1 = blockposition.relative(enumdirection);

                    if (world.getFluidState(blockposition1).is((Tag) FluidTags.WATER)) {
                        Block block = world.getFluidState(blockposition).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;

                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition, block.getBlockData())) {
                            this.fizz(world, blockposition);
                        }
                        // CraftBukkit end
                        return false;
                    }

                    if (flag && world.getType(blockposition1).is(Blocks.BLUE_ICE)) {
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition, Blocks.BASALT.getBlockData())) {
                            this.fizz(world, blockposition);
                        }
                        // CraftBukkit end
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void fizz(LevelAccessor generatoraccess, BlockPos blockposition) {
        generatoraccess.levelEvent(1501, blockposition, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(LiquidBlock.LEVEL);
    }

    @Override
    public Fluid removeFluid(LevelAccessor generatoraccess, BlockPos blockposition, BlockState iblockdata) {
        if ((Integer) iblockdata.getValue(LiquidBlock.LEVEL) == 0) {
            generatoraccess.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 11);
            return this.fluid;
        } else {
            return Fluids.EMPTY;
        }
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (this.fluid.is((Tag) FluidTags.LAVA)) {
            float f = (float) blockposition.getY() + iblockdata.getFluidState().getHeight(world, blockposition);
            AABB axisalignedbb = entity.getBoundingBox();

            if (axisalignedbb.minY < (double) f || (double) f > axisalignedbb.maxY) {
                entity.setInLava();
            }
        }

    }
}
