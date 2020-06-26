package net.minecraft.world.inventory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface ContainerLevelAccess {

    // CraftBukkit start
    default Level getWorld() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default BlockPos getPosition() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default org.bukkit.Location getLocation() {
        return new org.bukkit.Location(getWorld().getWorld(), getPosition().getX(), getPosition().getY(), getPosition().getZ());
    }
    // CraftBukkit end

    ContainerLevelAccess NULL = new ContainerLevelAccess() {
        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> bifunction) {
            return Optional.empty();
        }
    };

    static ContainerLevelAccess create(final Level world, final BlockPos blockposition) {
        return new ContainerLevelAccess() {
            // CraftBukkit start
            @Override
            public Level getWorld() {
                return world;
            }

            @Override
            public BlockPos getPosition() {
                return blockposition;
            }
            // CraftBukkit end

            @Override
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> bifunction) {
                return Optional.of(bifunction.apply(world, blockposition));
            }
        };
    }

    <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> bifunction);

    default <T> T evaluate(BiFunction<Level, BlockPos, T> bifunction, T t0) {
        return this.evaluate(bifunction).orElse(t0);
    }

    default void execute(BiConsumer<Level, BlockPos> biconsumer) {
        this.evaluate((world, blockposition) -> {
            biconsumer.accept(world, blockposition);
            return Optional.empty();
        });
    }
}
