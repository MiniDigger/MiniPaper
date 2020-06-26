package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityUnleashEvent;
// CraftBukkit end

public abstract class PathfinderMob extends Mob {

    protected PathfinderMob(EntityType<? extends PathfinderMob> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public float getWalkTargetValue(BlockPos blockposition) {
        return this.getWalkTargetValue(blockposition, (LevelReader) this.level);
    }

    public float getWalkTargetValue(BlockPos blockposition, LevelReader iworldreader) {
        return 0.0F;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor generatoraccess, MobSpawnType enummobspawn) {
        return this.getWalkTargetValue(this.blockPosition(), (LevelReader) generatoraccess) >= 0.0F;
    }

    public boolean isPathFinding() {
        return !this.getNavigation().isDone();
    }

    @Override
    protected void tickLeash() {
        super.tickLeash();
        Entity entity = this.getLeashHolder();

        if (entity != null && entity.level == this.level) {
            this.restrictTo(entity.blockPosition(), 5);
            float f = this.distanceTo(entity);

            if (this instanceof TamableAnimal && ((TamableAnimal) this).isInSittingPose()) {
                if (f > 10.0F) {
                    this.level.getServerOH().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), EntityUnleashEvent.UnleashReason.DISTANCE)); // CraftBukkit
                    this.dropLeash(true, true);
                }

                return;
            }

            this.onLeashDistance(f);
            if (f > 10.0F) {
                this.level.getServerOH().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), EntityUnleashEvent.UnleashReason.DISTANCE)); // CraftBukkit
                this.dropLeash(true, true);
                this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            } else if (f > 6.0F) {
                double d0 = (entity.getX() - this.getX()) / (double) f;
                double d1 = (entity.getY() - this.getY()) / (double) f;
                double d2 = (entity.getZ() - this.getZ()) / (double) f;

                this.setDeltaMovement(this.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.4D, d0), Math.copySign(d1 * d1 * 0.4D, d1), Math.copySign(d2 * d2 * 0.4D, d2)));
            } else {
                this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
                float f1 = 2.0F;
                Vec3 vec3d = (new Vec3(entity.getX() - this.getX(), entity.getY() - this.getY(), entity.getZ() - this.getZ())).normalize().scale((double) Math.max(f - 2.0F, 0.0F));

                this.getNavigation().moveTo(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z, this.followLeashSpeed());
            }
        }

    }

    protected double followLeashSpeed() {
        return 1.0D;
    }

    protected void onLeashDistance(float f) {}
}