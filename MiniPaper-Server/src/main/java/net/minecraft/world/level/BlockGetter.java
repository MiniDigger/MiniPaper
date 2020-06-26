package net.minecraft.world.level;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface BlockGetter {

    public @Nullable
    BlockEntity getBlockEntity(BlockPos blockposition);

    BlockState getType(BlockPos blockposition);

    FluidState getFluidState(BlockPos blockposition);

    default int getLightEmission(BlockPos blockposition) {
        return this.getType(blockposition).getLightEmission();
    }

    default int getMaxLightLevel() {
        return 15;
    }

    default int getMaxBuildHeight() {
        return 256;
    }

    default Stream<BlockState> getBlockStates(AABB axisalignedbb) {
        return BlockPos.betweenClosedStream(axisalignedbb).map(this::getType);
    }

    // CraftBukkit start - moved block handling into separate method for use by Block#rayTrace
    default BlockHitResult rayTraceBlock(ClipContext raytrace1, BlockPos blockposition) {
            BlockState iblockdata = this.getType(blockposition);
            FluidState fluid = this.getFluidState(blockposition);
            Vec3 vec3d = raytrace1.getFrom();
            Vec3 vec3d1 = raytrace1.getTo();
            VoxelShape voxelshape = raytrace1.getBlockShape(iblockdata, this, blockposition);
            BlockHitResult movingobjectpositionblock = this.rayTrace(vec3d, vec3d1, blockposition, voxelshape, iblockdata);
            VoxelShape voxelshape1 = raytrace1.getFluidShape(fluid, this, blockposition);
            BlockHitResult movingobjectpositionblock1 = voxelshape1.clip(vec3d, vec3d1, blockposition);
            double d0 = movingobjectpositionblock == null ? Double.MAX_VALUE : raytrace1.getFrom().distanceToSqr(movingobjectpositionblock.getLocation());
            double d1 = movingobjectpositionblock1 == null ? Double.MAX_VALUE : raytrace1.getFrom().distanceToSqr(movingobjectpositionblock1.getLocation());

            return d0 <= d1 ? movingobjectpositionblock : movingobjectpositionblock1;
    }
    // CraftBukkit end

    default BlockHitResult clip(ClipContext raytrace) {
        return (BlockHitResult) traverseBlocks(raytrace, (raytrace1, blockposition) -> {
            return this.rayTraceBlock(raytrace1, blockposition); // CraftBukkit - moved into separate method
        }, (raytrace1) -> {
            Vec3 vec3d = raytrace1.getFrom().subtract(raytrace1.getTo());

            return BlockHitResult.miss(raytrace1.getTo(), Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), new BlockPos(raytrace1.getTo()));
        });
    }

    @Nullable
    default BlockHitResult rayTrace(Vec3 vec3d, Vec3 vec3d1, BlockPos blockposition, VoxelShape voxelshape, BlockState iblockdata) {
        BlockHitResult movingobjectpositionblock = voxelshape.clip(vec3d, vec3d1, blockposition);

        if (movingobjectpositionblock != null) {
            BlockHitResult movingobjectpositionblock1 = iblockdata.getInteractionShape(this, blockposition).clip(vec3d, vec3d1, blockposition);

            if (movingobjectpositionblock1 != null && movingobjectpositionblock1.getLocation().subtract(vec3d).lengthSqr() < movingobjectpositionblock.getLocation().subtract(vec3d).lengthSqr()) {
                return movingobjectpositionblock.withDirection(movingobjectpositionblock1.getDirection());
            }
        }

        return movingobjectpositionblock;
    }

    static <T> T traverseBlocks(ClipContext raytrace, BiFunction<ClipContext, BlockPos, T> bifunction, Function<ClipContext, T> function) {
        Vec3 vec3d = raytrace.getFrom();
        Vec3 vec3d1 = raytrace.getTo();

        if (vec3d.equals(vec3d1)) {
            return function.apply(raytrace);
        } else {
            double d0 = Mth.lerp(-1.0E-7D, vec3d1.x, vec3d.x);
            double d1 = Mth.lerp(-1.0E-7D, vec3d1.y, vec3d.y);
            double d2 = Mth.lerp(-1.0E-7D, vec3d1.z, vec3d.z);
            double d3 = Mth.lerp(-1.0E-7D, vec3d.x, vec3d1.x);
            double d4 = Mth.lerp(-1.0E-7D, vec3d.y, vec3d1.y);
            double d5 = Mth.lerp(-1.0E-7D, vec3d.z, vec3d1.z);
            int i = Mth.floor(d3);
            int j = Mth.floor(d4);
            int k = Mth.floor(d5);
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition(i, j, k);
            T t0 = bifunction.apply(raytrace, blockposition_mutableblockposition);

            if (t0 != null) {
                return t0;
            } else {
                double d6 = d0 - d3;
                double d7 = d1 - d4;
                double d8 = d2 - d5;
                int l = Mth.sign(d6);
                int i1 = Mth.sign(d7);
                int j1 = Mth.sign(d8);
                double d9 = l == 0 ? Double.MAX_VALUE : (double) l / d6;
                double d10 = i1 == 0 ? Double.MAX_VALUE : (double) i1 / d7;
                double d11 = j1 == 0 ? Double.MAX_VALUE : (double) j1 / d8;
                double d12 = d9 * (l > 0 ? 1.0D - Mth.frac(d3) : Mth.frac(d3));
                double d13 = d10 * (i1 > 0 ? 1.0D - Mth.frac(d4) : Mth.frac(d4));
                double d14 = d11 * (j1 > 0 ? 1.0D - Mth.frac(d5) : Mth.frac(d5));

                T object; // CraftBukkit - decompile error

                do {
                    if (d12 > 1.0D && d13 > 1.0D && d14 > 1.0D) {
                        return function.apply(raytrace);
                    }

                    if (d12 < d13) {
                        if (d12 < d14) {
                            i += l;
                            d12 += d9;
                        } else {
                            k += j1;
                            d14 += d11;
                        }
                    } else if (d13 < d14) {
                        j += i1;
                        d13 += d10;
                    } else {
                        k += j1;
                        d14 += d11;
                    }

                    object = bifunction.apply(raytrace, blockposition_mutableblockposition.d(i, j, k));
                } while (object == null);

                return object;
            }
        }
    }
}
