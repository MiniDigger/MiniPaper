package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BambooSaplingBlock extends Block implements BonemealableBlock {

    protected static final VoxelShape SAPLING_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    public BambooSaplingBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public BlockBehaviour.OffsetType getOffsetType() {
        return BlockBehaviour.OffsetType.XZ;
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        Vec3 vec3d = iblockdata.getOffset(iblockaccess, blockposition);

        return BambooSaplingBlock.SAPLING_SHAPE.move(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public void tick(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        if (random.nextInt(3) == 0 && worldserver.isEmptyBlock(blockposition.above()) && worldserver.getRawBrightness(blockposition.above(), 0) >= 9) {
            this.growBamboo((Level) worldserver, blockposition);
        }

    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        return iworldreader.getType(blockposition.below()).is((Tag) BlockTags.BAMBOO_PLANTABLE_ON);
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        if (!iblockdata.canSurvive(generatoraccess, blockposition)) {
            return Blocks.AIR.getBlockData();
        } else {
            if (enumdirection == Direction.UP && iblockdata1.is(Blocks.BAMBOO)) {
                generatoraccess.setTypeAndData(blockposition, Blocks.BAMBOO.getBlockData(), 2);
            }

            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        }
    }

    @Override
    public boolean isValidBonemealTarget(BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, boolean flag) {
        return iblockaccess.getType(blockposition.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level world, Random random, BlockPos blockposition, BlockState iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel worldserver, Random random, BlockPos blockposition, BlockState iblockdata) {
        this.growBamboo((Level) worldserver, blockposition);
    }

    @Override
    public float getDamage(BlockState iblockdata, Player entityhuman, BlockGetter iblockaccess, BlockPos blockposition) {
        return entityhuman.getMainHandItem().getItem() instanceof SwordItem ? 1.0F : super.getDamage(iblockdata, entityhuman, iblockaccess, blockposition);
    }

    protected void growBamboo(Level world, BlockPos blockposition) {
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, blockposition, blockposition.above(), (BlockState) Blocks.BAMBOO.getBlockData().setValue(BambooBlock.LEAVES, BambooLeaves.SMALL), 3); // CraftBukkit - BlockSpreadEvent
    }
}
