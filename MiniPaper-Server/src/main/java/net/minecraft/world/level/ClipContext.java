package net.minecraft.world.level;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClipContext {

    private final Vec3 from;
    private final Vec3 to;
    private final ClipContext.Block block;
    private final ClipContext.Fluid fluid;
    private final CollisionContext collisionContext;

    public ClipContext(Vec3 vec3d, Vec3 vec3d1, ClipContext.Block raytrace_blockcollisionoption, ClipContext.Fluid raytrace_fluidcollisionoption, Entity entity) {
        this.from = vec3d;
        this.to = vec3d1;
        this.block = raytrace_blockcollisionoption;
        this.fluid = raytrace_fluidcollisionoption;
        this.collisionContext = (entity == null) ? CollisionContext.empty() : CollisionContext.of(entity); // CraftBukkit
    }

    public Vec3 getTo() {
        return this.to;
    }

    public Vec3 getFrom() {
        return this.from;
    }

    public VoxelShape getBlockShape(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return this.block.get(iblockdata, iblockaccess, blockposition, this.collisionContext);
    }

    public VoxelShape getFluidShape(FluidState fluid, BlockGetter iblockaccess, BlockPos blockposition) {
        return this.fluid.canPick(fluid) ? fluid.getShape(iblockaccess, blockposition) : Shapes.empty();
    }

    public static enum Fluid {

        NONE((fluid) -> {
            return false;
        }), SOURCE_ONLY(FluidState::isSource), ANY((fluid) -> {
            return !fluid.isEmpty();
        });

        private final Predicate<FluidState> canPick;

        private Fluid(Predicate<FluidState> predicate) { // CraftBukkit - decompile error
            this.canPick = predicate;
        }

        public boolean canPick(FluidState fluid) {
            return this.canPick.test(fluid);
        }
    }

    public interface ShapeGetter {

        VoxelShape get(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision);
    }

    public static enum Block implements ClipContext.ShapeGetter {

        COLLIDER(BlockBehaviour.BlockStateBase::getCollisionShape), OUTLINE(BlockBehaviour.BlockStateBase::getShape), VISUAL(BlockBehaviour.BlockStateBase::getVisualShape);

        private final ClipContext.ShapeGetter shapeGetter;

        private Block(ClipContext.ShapeGetter raytrace_c) {
            this.shapeGetter = raytrace_c;
        }

        @Override
        public VoxelShape get(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition, CollisionContext voxelshapecollision) {
            return this.shapeGetter.get(iblockdata, iblockaccess, blockposition, voxelshapecollision);
        }
    }
}
