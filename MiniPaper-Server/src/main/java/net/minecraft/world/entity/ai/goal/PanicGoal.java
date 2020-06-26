package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

public class PanicGoal extends Goal {

    protected final PathfinderMob mob;
    protected final double speedModifier;
    protected double posX;
    protected double posY;
    protected double posZ;
    protected boolean isRunning;

    public PanicGoal(PathfinderMob entitycreature, double d0) {
        this.mob = entitycreature;
        this.speedModifier = d0;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getLastHurtByMob() == null && !this.mob.isOnFire()) {
            return false;
        } else {
            if (this.mob.isOnFire()) {
                BlockPos blockposition = this.lookForWater(this.mob.level, this.mob, 5, 4);

                if (blockposition != null) {
                    this.posX = (double) blockposition.getX();
                    this.posY = (double) blockposition.getY();
                    this.posZ = (double) blockposition.getZ();
                    return true;
                }
            }

            return this.findRandomPosition();
        }
    }

    protected boolean findRandomPosition() {
        Vec3 vec3d = RandomPos.getPos(this.mob, 5, 4);

        if (vec3d == null) {
            return false;
        } else {
            this.posX = vec3d.x;
            this.posY = vec3d.y;
            this.posZ = vec3d.z;
            return true;
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
        this.isRunning = true;
    }

    @Override
    public void stop() {
        this.isRunning = false;
    }

    @Override
    public boolean canContinueToUse() {
        // CraftBukkit start - introduce a temporary timeout hack until this is fixed properly
        if ((this.mob.tickCount - this.mob.lastHurtByMobTimestamp) > 100) {
            this.mob.setLastHurtByMob((LivingEntity) null);
            return false;
        }
        // CraftBukkit end
        return !this.mob.getNavigation().isDone();
    }

    @Nullable
    protected BlockPos lookForWater(BlockGetter iblockaccess, Entity entity, int i, int j) {
        BlockPos blockposition = entity.blockPosition();
        int k = blockposition.getX();
        int l = blockposition.getY();
        int i1 = blockposition.getZ();
        float f = (float) (i * i * j * 2);
        BlockPos blockposition1 = null;
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

        for (int j1 = k - i; j1 <= k + i; ++j1) {
            for (int k1 = l - j; k1 <= l + j; ++k1) {
                for (int l1 = i1 - i; l1 <= i1 + i; ++l1) {
                    blockposition_mutableblockposition.d(j1, k1, l1);
                    if (iblockaccess.getFluidState(blockposition_mutableblockposition).is((Tag) FluidTags.WATER)) {
                        float f1 = (float) ((j1 - k) * (j1 - k) + (k1 - l) * (k1 - l) + (l1 - i1) * (l1 - i1));

                        if (f1 < f) {
                            f = f1;
                            blockposition1 = new BlockPos(blockposition_mutableblockposition);
                        }
                    }
                }
            }
        }

        return blockposition1;
    }
}
