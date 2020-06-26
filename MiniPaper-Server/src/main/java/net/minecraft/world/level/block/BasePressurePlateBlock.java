package net.minecraft.world.level.block;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public abstract class BasePressurePlateBlock extends Block {

    protected static final VoxelShape PRESSED_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 0.5D, 15.0D);
    protected static final VoxelShape AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 1.0D, 15.0D);
    protected static final AABB TOUCH_AABB = new AABB(0.125D, 0.0D, 0.125D, 0.875D, 0.25D, 0.875D);

    protected BasePressurePlateBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return this.getPower(iblockdata) > 0 ? BasePressurePlateBlock.PRESSED_AABB : BasePressurePlateBlock.AABB;
    }

    protected int getPressedTime() {
        return 20;
    }

    @Override
    public boolean isPossibleToRespawnInThis() {
        return true;
    }

    @Override
    public BlockState updateState(BlockState iblockdata, Direction enumdirection, BlockState iblockdata1, LevelAccessor generatoraccess, BlockPos blockposition, BlockPos blockposition1) {
        return enumdirection == Direction.DOWN && !iblockdata.canSurvive(generatoraccess, blockposition) ? Blocks.AIR.getBlockData() : super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
    }

    @Override
    public boolean canPlace(BlockState iblockdata, LevelReader iworldreader, BlockPos blockposition) {
        BlockPos blockposition1 = blockposition.below();

        return canSupportRigidBlock((BlockGetter) iworldreader, blockposition1) || canSupportCenter(iworldreader, blockposition1, Direction.UP);
    }

    @Override
    public void tickAlways(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, Random random) {
        int i = this.getPower(iblockdata);

        if (i > 0) {
            this.checkPressed(worldserver, blockposition, iblockdata, i);
        }

    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!world.isClientSide) {
            int i = this.getPower(iblockdata);

            if (i == 0) {
                this.checkPressed(world, blockposition, iblockdata, i);
            }

        }
    }

    protected void checkPressed(Level world, BlockPos blockposition, BlockState iblockdata, int i) {
        int j = this.getSignalStrength(world, blockposition);
        boolean flag = i > 0;
        boolean flag1 = j > 0;

        // CraftBukkit start - Interact Pressure Plate
        org.bukkit.World bworld = world.getWorld();
        org.bukkit.plugin.PluginManager manager = world.getServerOH().getPluginManager();

        if (flag != flag1) {
            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), i, j);
            manager.callEvent(eventRedstone);

            flag1 = eventRedstone.getNewCurrent() > 0;
            j = eventRedstone.getNewCurrent();
        }
        // CraftBukkit end

        if (i != j) {
            BlockState iblockdata1 = this.setSignalForState(iblockdata, j);

            world.setTypeAndData(blockposition, iblockdata1, 2);
            this.updateNeighbours(world, blockposition);
            world.setBlocksDirty(blockposition, iblockdata, iblockdata1);
        }

        if (!flag1 && flag) {
            this.playOffSound((LevelAccessor) world, blockposition);
        } else if (flag1 && !flag) {
            this.playOnSound((LevelAccessor) world, blockposition);
        }

        if (flag1) {
            world.getBlockTickList().scheduleTick(new BlockPos(blockposition), this, this.getPressedTime());
        }

    }

    protected abstract void playOnSound(LevelAccessor generatoraccess, BlockPos blockposition);

    protected abstract void playOffSound(LevelAccessor generatoraccess, BlockPos blockposition);

    @Override
    public void remove(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag) {
        if (!flag && !iblockdata.is(iblockdata1.getBlock())) {
            if (this.getPower(iblockdata) > 0) {
                this.updateNeighbours(world, blockposition);
            }

            super.remove(iblockdata, world, blockposition, iblockdata1, flag);
        }
    }

    protected void updateNeighbours(Level world, BlockPos blockposition) {
        world.updateNeighborsAt(blockposition, this);
        world.updateNeighborsAt(blockposition.below(), this);
    }

    @Override
    public int getSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return this.getPower(iblockdata);
    }

    @Override
    public int getDirectSignal(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, Direction enumdirection) {
        return enumdirection == Direction.UP ? this.getPower(iblockdata) : 0;
    }

    @Override
    public boolean isPowerSource(BlockState iblockdata) {
        return true;
    }

    @Override
    public PushReaction getPushReaction(BlockState iblockdata) {
        return PushReaction.DESTROY;
    }

    protected abstract int getSignalStrength(Level world, BlockPos blockposition);

    protected abstract int getPower(BlockState iblockdata);

    protected abstract BlockState setSignalForState(BlockState iblockdata, int i);
}
