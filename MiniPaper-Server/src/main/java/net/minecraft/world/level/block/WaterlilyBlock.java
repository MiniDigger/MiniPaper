package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WaterlilyBlock extends BushBlock {

    protected static final VoxelShape AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 1.5D, 15.0D);

    protected WaterlilyBlock(BlockBehaviour.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        super.entityInside(iblockdata, world, blockposition, entity);
        if (world instanceof ServerLevel && entity instanceof Boat && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, Blocks.AIR.getBlockData()).isCancelled()) { // CraftBukkit
            world.destroyBlock(new BlockPos(blockposition), true, entity);
        }

    }

    @Override
    public VoxelShape getShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
        return WaterlilyBlock.AABB;
    }

    @Override
    protected boolean mayPlaceOn(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        FluidState fluid = iblockaccess.getFluidState(blockposition);
        FluidState fluid1 = iblockaccess.getFluidState(blockposition.above());

        return (fluid.getType() == Fluids.WATER || iblockdata.getMaterial() == Material.ICE) && fluid1.getType() == Fluids.EMPTY;
    }
}
