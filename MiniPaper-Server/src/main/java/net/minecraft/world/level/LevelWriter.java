package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public interface LevelWriter {

    boolean setBlock(BlockPos blockposition, BlockState iblockdata, int i, int j);

    default boolean setTypeAndData(BlockPos blockposition, BlockState iblockdata, int i) {
        return this.setBlock(blockposition, iblockdata, i, 512);
    }

    boolean removeBlock(BlockPos blockposition, boolean flag);

    default boolean destroyBlock(BlockPos blockposition, boolean flag) {
        return this.destroyBlock(blockposition, flag, (Entity) null);
    }

    default boolean destroyBlock(BlockPos blockposition, boolean flag, @Nullable Entity entity) {
        return this.destroyBlock(blockposition, flag, entity, 512);
    }

    boolean destroyBlock(BlockPos blockposition, boolean flag, @Nullable Entity entity, int i);

    default boolean addFreshEntity(Entity entity) {
        return false;
    }

    // CraftBukkit start
    default boolean addEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        return false;
    }
    // CraftBukkit end
}
