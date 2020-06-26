package net.minecraft.world.level.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BambooBlock extends Block implements BonemealableBlock {

    protected static final VoxelShape SMALL_SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    protected static final VoxelShape LARGE_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    protected static final VoxelShape COLLISION_SHAPE = Block.box(6.5D, 0.0D, 6.5D, 9.5D, 16.0D, 9.5D);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_1;
    public static final EnumProperty<BambooLeaves> LEAVES = BlockStateProperties.BAMBOO_LEAVES;
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;

    public BambooBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BambooBlock.AGE, 0)).setValue(BambooBlock.LEAVES, BambooLeaves.NONE)).setValue(BambooBlock.STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(BambooBlock.AGE, BambooBlock.LEAVES, BambooBlock.STAGE);
    }

    @Override
    public BlockBehaviour.OffsetType getOffsetType() {
        return BlockBehaviour.OffsetType.XZ;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        VoxelShape voxelshape = iblockdata.getValue(BambooBlock.LEAVES) == BambooLeaves.LARGE ? BambooBlock.LARGE_SHAPE : BambooBlock.SMALL_SHAPE;
        Vec3 vec3d = iblockdata.getOffset(iblockaccess, blockposition);

        return voxelshape.move(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        Vec3 vec3d = iblockdata.getOffset(iblockaccess, blockposition);

        return BambooBlock.COLLISION_SHAPE.move(vec3d.x, vec3d.y, vec3d.z);
    }

    @Nullable
    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        FluidState fluid = blockactioncontext.getLevel().getFluidState(blockactioncontext.getClickedPos());

        if (!fluid.isEmpty()) {
            return null;
        } else {
            BlockState iblockdata = blockactioncontext.getLevel().getType(blockactioncontext.getClickedPos().below());

            if (iblockdata.is((Tag) BlockTags.BAMBOO_PLANTABLE_ON)) {
                if (iblockdata.is(Blocks.BAMBOO_SAPLING)) {
                    return (BlockState) this.getBlockData().setValue(BambooBlock.AGE, 0);
                } else if (iblockdata.is(Blocks.BAMBOO)) {
                    int i = (Integer) iblockdata.getValue(BambooBlock.AGE) > 0 ? 1 : 0;

                    return (BlockState) this.getBlockData().setValue(BambooBlock.AGE, i);
                } else {
                    return Blocks.BAMBOO_SAPLING.getBlockData();
                }
            } else {
                return null;
            }
        }
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        }

    }

    @Override
    public boolean isTicking(BlockState iblockdata) {
        return (Integer) iblockdata.getValue(BambooBlock.STAGE) == 0;
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if ((Integer) iblockdata.getValue(BambooBlock.STAGE) == 0) {
            if (random.nextInt(Math.max(1, (int) (100.0F / worldserver.spigotConfig.bambooModifier) * 3)) == 0 && worldserver.isEmptyBlock(blockposition.above()) && worldserver.getRawBrightness(blockposition.above(), 0) >= 9) { // Spigot
                int i = this.getHeightBelowUpToMax(worldserver, blockposition) + 1;

                if (i < 16) {
                    this.growBamboo(iblockdata, (Level) worldserver, blockposition, random, i);
                }
            }

        }
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return iworldreader.getType(blockposition.below()).is((Tag) BlockTags.BAMBOO_PLANTABLE_ON);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (!iblockdata.canSurvive(generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        if (enumdirection == Direction.UP && iblockdata1.is(Blocks.BAMBOO) && (Integer) iblockdata1.getValue(BambooBlock.AGE) > (Integer) iblockdata.getValue(BambooBlock.AGE)) {
            generatoraccess.setTypeAndData(blockposition, (BlockState) iblockdata.cycle((Property) BambooBlock.AGE), 2);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        int i = this.getHeightAboveUpToMax(iblockaccess, blockposition);
        int j = this.getHeightBelowUpToMax(iblockaccess, blockposition);

        return i + j + 1 < 16 && (Integer) iblockaccess.getType(blockposition.above(i)).getValue(BambooBlock.STAGE) != 1;
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        int i = this.getHeightAboveUpToMax((BlockGetter) worldserver, blockposition);
        int j = this.getHeightBelowUpToMax(worldserver, blockposition);
        int k = i + j + 1;
        int l = 1 + random.nextInt(2);

        for (int i1 = 0; i1 < l; ++i1) {
            BlockPos blockposition1 = blockposition.above(i);
            BlockState iblockdata1 = worldserver.getType(blockposition1);

            if (k >= 16 || (Integer) iblockdata1.getValue(BambooBlock.STAGE) == 1 || !worldserver.isEmptyBlock(blockposition1.above())) {
                return;
            }

            this.growBamboo(iblockdata1, (Level) worldserver, blockposition1, random, k);
            ++i;
            ++k;
        }

    }

    @Override
    public float getDamage(BlockState iblockdata, Player entityhuman, BlockGetter iblockaccess, BlockPos blockposition) {
        return entityhuman.getMainHandItem().getItem() instanceof SwordItem ? 1.0F : super.getDamage(iblockdata, entityhuman, iblockaccess, blockposition);
    }

    protected void growBamboo(BlockState iblockdata, Level world, BlockPos blockposition, Random random, int i) {
        BlockState iblockdata1 = world.getType(blockposition.below());
        BlockPos blockposition1 = blockposition.below(2);
        BlockState iblockdata2 = world.getType(blockposition1);
        BambooLeaves blockpropertybamboosize = BambooLeaves.NONE;
        boolean shouldUpdateOthers = false; // CraftBukkit

        if (i >= 1) {
            if (iblockdata1.is(Blocks.BAMBOO) && iblockdata1.getValue(BambooBlock.LEAVES) != BambooLeaves.NONE) {
                if (iblockdata1.is(Blocks.BAMBOO) && iblockdata1.getValue(BambooBlock.LEAVES) != BambooLeaves.NONE) {
                    blockpropertybamboosize = BambooLeaves.LARGE;
                    if (iblockdata2.is(Blocks.BAMBOO)) {
                        // CraftBukkit start - moved down
                        // world.setTypeAndData(blockposition.down(), (IBlockData) iblockdata1.set(BlockBamboo.e, BlockPropertyBambooSize.SMALL), 3);
                        // world.setTypeAndData(blockposition1, (IBlockData) iblockdata2.set(BlockBamboo.e, BlockPropertyBambooSize.NONE), 3);
                        shouldUpdateOthers = true;
                        // CraftBukkit end
                    }
                }
            } else {
                blockpropertybamboosize = BambooLeaves.SMALL;
            }
        }

        int j = (Integer) iblockdata.getValue(BambooBlock.AGE) != 1 && !iblockdata2.is(Blocks.BAMBOO) ? 0 : 1;
        int k = (i < 11 || random.nextFloat() >= 0.25F) && i != 15 ? 0 : 1;

        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, blockposition, blockposition.above(), (BlockState) ((BlockState) ((BlockState) this.getBlockData().setValue(BambooBlock.AGE, j)).setValue(BambooBlock.LEAVES, blockpropertybamboosize)).setValue(BambooBlock.STAGE, k), 3)) {
            if (shouldUpdateOthers) {
                world.setTypeAndData(blockposition.below(), (BlockState) iblockdata1.setValue(BambooBlock.LEAVES, BambooLeaves.SMALL), 3);
                world.setTypeAndData(blockposition1, (BlockState) iblockdata2.setValue(BambooBlock.LEAVES, BambooLeaves.NONE), 3);
            }
        }
        // CraftBukkit end
    }

    protected int getHeightAboveUpToMax(BlockGetter iblockaccess, BlockPos blockposition) {
        int i;

        for (i = 0; i < 16 && iblockaccess.getType(blockposition.above(i + 1)).is(Blocks.BAMBOO); ++i) {
            ;
        }

        return i;
    }

    protected int getHeightBelowUpToMax(BlockGetter iblockaccess, BlockPos blockposition) {
        int i;

        for (i = 0; i < 16 && iblockaccess.getType(blockposition.below(i + 1)).is(Blocks.BAMBOO); ++i) {
            ;
        }

        return i;
    }
}
