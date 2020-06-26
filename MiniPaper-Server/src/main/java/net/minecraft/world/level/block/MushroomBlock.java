package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.BiomeDefaultFeatures;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.TreeType;
// CraftBukkit end

public class MushroomBlock extends BushBlock implements BonemealableBlock {

    protected static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);

    public MushroomBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return MushroomBlock.SHAPE;
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (random.nextInt(Math.max(1, (int) (100.0F / worldserver.spigotConfig.mushroomModifier) * 25)) == 0) { // Spigot
            int i = 5;
            boolean flag = true;
            Iterator iterator = BlockPos.betweenClosed(blockposition.offset(-4, -1, -4), blockposition.offset(4, 1, 4)).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition1 = (BlockPos) iterator.next();

                if (worldserver.getType(blockposition1).is((Block) this)) {
                    --i;
                    if (i <= 0) {
                        return;
                    }
                }
            }

            BlockPos blockposition2 = blockposition.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);

            for (int j = 0; j < 4; ++j) {
                if (worldserver.isEmptyBlock(blockposition2) && iblockdata.canSurvive(worldserver, blockposition2)) {
                    blockposition = blockposition2;
                }

                blockposition2 = blockposition.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
            }

            if (worldserver.isEmptyBlock(blockposition2) && iblockdata.canSurvive(worldserver, blockposition2)) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition2, iblockdata, 2); // CraftBukkit
            }
        }

    }

    @Override
    protected boolean mayPlaceOn(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return iblockdata.isSolidRender(iblockaccess, blockposition);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();
        BlockState iblockdata1 = iworldreader.getType(blockposition1);

        return !iblockdata1.is(Blocks.MYCELIUM) && !iblockdata1.is(Blocks.PODZOL) ? iworldreader.getRawBrightness(blockposition, 0) < 13 && this.mayPlaceOn(iblockdata1, (BlockGetter) iworldreader, blockposition1) : true;
    }

    public boolean growMushroom(ServerLevel worldserver, BlockPos blockposition, BlockState iblockdata, Random random) {
        worldserver.removeBlock(blockposition, false);
        ConfiguredFeature worldgenfeatureconfigured;

        if (this == Blocks.BROWN_MUSHROOM) {
            SaplingBlock.treeType = TreeType.BROWN_MUSHROOM; // CraftBukkit
            worldgenfeatureconfigured = Feature.HUGE_BROWN_MUSHROOM.configured(BiomeDefaultFeatures.HUGE_BROWN_MUSHROOM_CONFIG); // CraftBukkit - decompile error
        } else {
            if (this != Blocks.RED_MUSHROOM) {
                worldserver.setTypeAndData(blockposition, iblockdata, 3);
                return false;
            }

            SaplingBlock.treeType = TreeType.RED_MUSHROOM; // CraftBukkit
            worldgenfeatureconfigured = Feature.HUGE_RED_MUSHROOM.configured(BiomeDefaultFeatures.HUGE_RED_MUSHROOM_CONFIG); // CraftBukkit - decompile error
        }

        if (worldgenfeatureconfigured.place(worldserver, worldserver.getStructureManager(), worldserver.getChunkSourceOH().getGenerator(), random, blockposition)) {
            return true;
        } else {
            worldserver.setTypeAndData(blockposition, iblockdata, 3);
            return false;
        }
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return (double) random.nextFloat() < 0.4D;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        this.growMushroom(worldserver, blockposition, iblockdata, random);
    }
}
