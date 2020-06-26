package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public class SaplingBlock extends BushBlock implements BonemealableBlock {

    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);
    private final AbstractTreeGrower treeGrower;
    public static TreeType treeType; // CraftBukkit

    protected SaplingBlock(AbstractTreeGrower worldgentreeprovider, BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
        this.treeGrower = worldgentreeprovider;
        this.registerDefaultState((net.minecraft.world.level.block.state.BlockState) ((net.minecraft.world.level.block.state.BlockState) this.stateDefinition.any()).setValue(SaplingBlock.STAGE, 0));
    }

    @Override
    public VoxelShape getShape(net.minecraft.world.level.block.state.BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return SaplingBlock.SHAPE;
    }

    @Override
    public void tick(net.minecraft.world.level.block.state.BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (worldserver.getMaxLocalRawBrightness(blockposition.above()) >= 9 && random.nextInt(Math.max(2, (int) (((100.0F / worldserver.spigotConfig.saplingModifier) * 7) + 0.5F))) == 0) { // Spigot
            // CraftBukkit start
            worldserver.captureTreeGeneration = true;
            // CraftBukkit end
            this.grow(worldserver, blockposition, iblockdata, random);
            // CraftBukkit start
            worldserver.captureTreeGeneration = false;
            if (worldserver.capturedBlockStates.size() > 0) {
                TreeType treeType = SaplingBlock.treeType;
                SaplingBlock.treeType = null;
                Location location = new Location(worldserver.getWorld(), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                java.util.List<BlockState> blocks = new java.util.ArrayList<>(worldserver.capturedBlockStates.values());
                worldserver.capturedBlockStates.clear();
                StructureGrowEvent event = null;
                if (treeType != null) {
                    event = new StructureGrowEvent(location, treeType, false, null, blocks);
                    org.bukkit.Bukkit.getPluginManager().callEvent(event);
                }
                if (event == null || !event.isCancelled()) {
                    for (BlockState blockstate : blocks) {
                        blockstate.update(true);
                    }
                }
            }
            // CraftBukkit end
        }

    }

    public void grow(ServerLevel worldserver, BlockPos blockposition, net.minecraft.world.level.block.state.BlockState iblockdata, Random random) {
        if ((Integer) iblockdata.getValue(SaplingBlock.STAGE) == 0) {
            worldserver.setTypeAndData(blockposition, (net.minecraft.world.level.block.state.BlockState) iblockdata.cycle((Property) SaplingBlock.STAGE), 4);
        } else {
            this.treeGrower.growTree(worldserver, worldserver.getChunkSourceOH().getGenerator(), blockposition, iblockdata, random);
        }

    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, net.minecraft.world.level.block.state.BlockState iblockdata, boolean flag) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, net.minecraft.world.level.block.state.BlockState iblockdata) {
        return (double) world.random.nextFloat() < 0.45D;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, net.minecraft.world.level.block.state.BlockState iblockdata) {
        this.grow(worldserver, blockposition, iblockdata, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> blockstatelist_a) {
        blockstatelist_a.add(SaplingBlock.STAGE);
    }
}
