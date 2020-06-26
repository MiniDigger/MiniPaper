package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class CactusBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    protected static final VoxelShape COLLISION_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);
    protected static final VoxelShape OUTLINE_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    protected CactusBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CactusBlock.AGE, 0));
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        }

    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        BlockPos blockposition1 = blockposition.above();

        if (worldserver.isEmptyBlock(blockposition1)) {
            int i;

            for (i = 1; worldserver.getType(blockposition.below(i)).is((Block) this); ++i) {
                ;
            }

            if (i < 3) {
                int j = (Integer) iblockdata.getValue(CactusBlock.AGE);

                if (j >= (byte) range(3, ((100.0F / worldserver.spigotConfig.cactusModifier) * 15) + 0.5F, 15)) { // Spigot
                    CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition1, this.getBlockData()); // CraftBukkit
                    BlockState iblockdata1 = (BlockState) iblockdata.setValue(CactusBlock.AGE, 0);

                    worldserver.setTypeAndData(blockposition, iblockdata1, 4);
                    iblockdata1.neighborChanged(worldserver, blockposition1, this, blockposition, false);
                } else {
                    worldserver.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(CactusBlock.AGE, j + 1), 4);
                }

            }
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return CactusBlock.COLLISION_SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return CactusBlock.OUTLINE_SHAPE;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (!iblockdata.canSurvive(generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        Direction enumdirection;
        Material material;

        do {
            if (!iterator.hasNext()) {
                BlockState iblockdata1 = iworldreader.getType(blockposition.below());

                return (iblockdata1.is(Blocks.CACTUS) || iblockdata1.is(Blocks.SAND) || iblockdata1.is(Blocks.RED_SAND)) && !iworldreader.getType(blockposition.above()).getMaterial().isLiquid();
            }

            enumdirection = (Direction) iterator.next();
            BlockState iblockdata2 = iworldreader.getType(blockposition.relative(enumdirection));

            material = iblockdata2.getMaterial();
        } while (!material.isSolid() && !iworldreader.getFluidState(blockposition.relative(enumdirection)).is((Tag) FluidTags.LAVA));

        return false;
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        CraftEventFactory.blockDamage = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()); // CraftBukkit
        entity.hurt(DamageSource.CACTUS, 1.0F);
        CraftEventFactory.blockDamage = null; // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(CactusBlock.AGE);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
