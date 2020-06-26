package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class FarmBlock extends Block {

    public static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    protected FarmBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(FarmBlock.MOISTURE, 0));
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (enumdirection == Direction.UP && !iblockdata.canSurvive(generatoraccess, blockposition)) {
            generatoraccess.getBlockTickList().scheduleTick(blockposition, this, 1);
        }

        return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockState iblockdata1 = iworldreader.getType(blockposition.above());

        return !iblockdata1.getMaterial().isSolid() || iblockdata1.getBlock() instanceof FenceGateBlock || iblockdata1.getBlock() instanceof MovingPistonBlock;
    }

    @Override
    public BlockState getPlacedState(BlockPlaceContext blockactioncontext) {
        return !this.getBlockData().canSurvive(blockactioncontext.getLevel(), blockactioncontext.getClickedPos()) ? Blocks.DIRT.getBlockData() : super.getPlacedState(blockactioncontext);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState iblockdata) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return FarmBlock.SHAPE;
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            fade(iblockdata, worldserver, blockposition);
        }

    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        int i = (Integer) iblockdata.getValue(FarmBlock.MOISTURE);

        if (!isNearWater((LevelReader) worldserver, blockposition) && !worldserver.isRainingAt(blockposition.above())) {
            if (i > 0) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(worldserver, blockposition, (BlockState) iblockdata.setValue(FarmBlock.MOISTURE, i - 1), 2); // CraftBukkit
            } else if (!isUnderCrops((BlockGetter) worldserver, blockposition)) {
                fade(iblockdata, worldserver, blockposition);
            }
        } else if (i < 7) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(worldserver, blockposition, (BlockState) iblockdata.setValue(FarmBlock.MOISTURE, 7), 2); // CraftBukkit
        }

    }

    @Override
    public void fallOn(Level world, BlockPos blockposition, Entity entity, float f) {
        super.fallOn(world, blockposition, entity, f); // CraftBukkit - moved here as game rules / events shouldn't affect fall damage.
        if (!world.isClientSide && world.random.nextFloat() < f - 0.5F && entity instanceof LivingEntity && (entity instanceof Player || world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) && entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() > 0.512F) {
            // CraftBukkit start - Interact soil
            org.bukkit.event.Cancellable cancellable;
            if (entity instanceof Player) {
                cancellable = CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                world.getServerOH().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            if (cancellable.isCancelled()) {
                return;
            }

            if (CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, Blocks.DIRT.getBlockData()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            fade(world.getType(blockposition), world, blockposition);
        }

        // super.fallOn(world, blockposition, entity, f); // CraftBukkit - moved up
    }

    public static void fade(BlockState iblockdata, Level world, BlockPos blockposition) {
        // CraftBukkit start
        if (CraftEventFactory.callBlockFadeEvent(world, blockposition, Blocks.DIRT.getBlockData()).isCancelled()) {
            return;
        }
        // CraftBukkit end
        world.setTypeUpdate(blockposition, pushEntitiesUp(iblockdata, Blocks.DIRT.getBlockData(), world, blockposition));
    }

    private static boolean isUnderCrops(BlockGetter iblockaccess, BlockPos blockposition) {
        Block block = iblockaccess.getType(blockposition.above()).getBlock();

        return block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock;
    }

    private static boolean isNearWater(LevelReader iworldreader, BlockPos blockposition) {
        Iterator iterator = BlockPos.betweenClosed(blockposition.offset(-4, 0, -4), blockposition.offset(4, 1, 4)).iterator();

        BlockPos blockposition1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            blockposition1 = (BlockPos) iterator.next();
        } while (!iworldreader.getFluidState(blockposition1).is((Tag) FluidTags.WATER));

        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockstatelist_a) {
        blockstatelist_a.add(FarmBlock.MOISTURE);
    }

    @Override
    public boolean isPathfindable(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, PathComputationType pathmode) {
        return false;
    }
}
