package net.minecraft.world.entity.ai.goal;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class RemoveBlockGoal extends MoveToBlockGoal {

    private final Block blockToRemove;
    private final Mob removerMob;
    private int ticksSinceReachedGoal;

    public RemoveBlockGoal(Block block, PathfinderMob entitycreature, double d0, int i) {
        super(entitycreature, d0, 24, i);
        this.blockToRemove = block;
        this.removerMob = entitycreature;
    }

    @Override
    public boolean canUse() {
        if (!this.removerMob.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        } else if (this.nextStartTick > 0) {
            --this.nextStartTick;
            return false;
        } else if (this.tryFindBlock()) {
            this.nextStartTick = 20;
            return true;
        } else {
            this.nextStartTick = this.nextStartTick(this.mob);
            return false;
        }
    }

    private boolean tryFindBlock() {
        return this.blockPos != null && this.isValidTarget((LevelReader) this.mob.level, this.blockPos) ? true : this.findNearestBlock();
    }

    @Override
    public void stop() {
        super.stop();
        this.removerMob.fallDistance = 1.0F;
    }

    @Override
    public void start() {
        super.start();
        this.ticksSinceReachedGoal = 0;
    }

    public void playDestroyProgressSound(LevelAccessor generatoraccess, BlockPos blockposition) {}

    public void playBreakSound(Level world, BlockPos blockposition) {}

    @Override
    public void tick() {
        super.tick();
        Level world = this.removerMob.level;
        BlockPos blockposition = this.removerMob.blockPosition();
        BlockPos blockposition1 = this.getPosWithBlock(blockposition, (BlockGetter) world);
        Random random = this.removerMob.getRandom();

        if (this.isReachedTarget() && blockposition1 != null) {
            Vec3 vec3d;
            double d0;

            if (this.ticksSinceReachedGoal > 0) {
                vec3d = this.removerMob.getDeltaMovement();
                this.removerMob.setDeltaMovement(vec3d.x, 0.3D, vec3d.z);
                if (!world.isClientSide) {
                    d0 = 0.08D;
                    ((ServerLevel) world).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EGG)), (double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.7D, (double) blockposition1.getZ() + 0.5D, 3, ((double) random.nextFloat() - 0.5D) * 0.08D, ((double) random.nextFloat() - 0.5D) * 0.08D, ((double) random.nextFloat() - 0.5D) * 0.08D, 0.15000000596046448D);
                }
            }

            if (this.ticksSinceReachedGoal % 2 == 0) {
                vec3d = this.removerMob.getDeltaMovement();
                this.removerMob.setDeltaMovement(vec3d.x, -0.3D, vec3d.z);
                if (this.ticksSinceReachedGoal % 6 == 0) {
                    this.playDestroyProgressSound((LevelAccessor) world, this.blockPos);
                }
            }

            if (this.ticksSinceReachedGoal > 60) {
                // CraftBukkit start - Step on eggs
                EntityInteractEvent event = new EntityInteractEvent(this.removerMob.getBukkitEntity(), CraftBlock.at(world, blockposition1));
                world.getServerOH().getPluginManager().callEvent((EntityInteractEvent) event);

                if (event.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                world.removeBlock(blockposition1, false);
                if (!world.isClientSide) {
                    for (int i = 0; i < 20; ++i) {
                        d0 = random.nextGaussian() * 0.02D;
                        double d1 = random.nextGaussian() * 0.02D;
                        double d2 = random.nextGaussian() * 0.02D;

                        ((ServerLevel) world).sendParticles(ParticleTypes.POOF, (double) blockposition1.getX() + 0.5D, (double) blockposition1.getY(), (double) blockposition1.getZ() + 0.5D, 1, d0, d1, d2, 0.15000000596046448D);
                    }

                    this.playBreakSound(world, blockposition1);
                }
            }

            ++this.ticksSinceReachedGoal;
        }

    }

    @Nullable
    private BlockPos getPosWithBlock(BlockPos blockposition, BlockGetter iblockaccess) {
        if (iblockaccess.getType(blockposition).is(this.blockToRemove)) {
            return blockposition;
        } else {
            BlockPos[] ablockposition = new BlockPos[]{blockposition.below(), blockposition.west(), blockposition.east(), blockposition.north(), blockposition.south(), blockposition.below().below()};
            BlockPos[] ablockposition1 = ablockposition;
            int i = ablockposition.length;

            for (int j = 0; j < i; ++j) {
                BlockPos blockposition1 = ablockposition1[j];

                if (iblockaccess.getType(blockposition1).is(this.blockToRemove)) {
                    return blockposition1;
                }
            }

            return null;
        }
    }

    @Override
    protected boolean isValidTarget(LevelReader iworldreader, BlockPos blockposition) {
        ChunkAccess ichunkaccess = iworldreader.getChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4, ChunkStatus.FULL, false);

        return ichunkaccess == null ? false : ichunkaccess.getType(blockposition).is(this.blockToRemove) && ichunkaccess.getType(blockposition.above()).isAir() && ichunkaccess.getType(blockposition.above(2)).isAir();
    }
}
