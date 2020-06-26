package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.EntityTeleportEvent;
// CraftBukkit end

public class FollowOwnerGoal extends Goal {

    private final TamableAnimal tamable;
    private LivingEntity owner;
    private final LevelReader level;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float stopDistance;
    private final float startDistance;
    private float oldWaterCost;
    private final boolean canFly;

    public FollowOwnerGoal(TamableAnimal entitytameableanimal, double d0, float f, float f1, boolean flag) {
        this.tamable = entitytameableanimal;
        this.level = entitytameableanimal.level;
        this.speedModifier = d0;
        this.navigation = entitytameableanimal.getNavigation();
        this.startDistance = f;
        this.stopDistance = f1;
        this.canFly = flag;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        if (!(entitytameableanimal.getNavigation() instanceof GroundPathNavigation) && !(entitytameableanimal.getNavigation() instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for FollowOwnerGoal");
        }
    }

    @Override
    public boolean canUse() {
        LivingEntity entityliving = this.tamable.getOwner();

        if (entityliving == null) {
            return false;
        } else if (entityliving.isSpectator()) {
            return false;
        } else if (this.tamable.isOrderedToSit()) {
            return false;
        } else if (this.tamable.distanceToSqr((Entity) entityliving) < (double) (this.startDistance * this.startDistance)) {
            return false;
        } else {
            this.owner = entityliving;
            return true;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.navigation.isDone() ? false : (this.tamable.isOrderedToSit() ? false : this.tamable.distanceToSqr((Entity) this.owner) > (double) (this.stopDistance * this.stopDistance));
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.tamable.getPathfindingMalus(BlockPathTypes.WATER);
        this.tamable.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.tamable.setPathfindingMalus(BlockPathTypes.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        this.tamable.getControllerLook().setLookAt(this.owner, 10.0F, (float) this.tamable.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;
            if (!this.tamable.isLeashed() && !this.tamable.isPassenger()) {
                if (this.tamable.distanceToSqr((Entity) this.owner) >= 144.0D) {
                    this.teleportToOwner();
                } else {
                    this.navigation.moveTo((Entity) this.owner, this.speedModifier);
                }

            }
        }
    }

    private void teleportToOwner() {
        BlockPos blockposition = this.owner.blockPosition();

        for (int i = 0; i < 10; ++i) {
            int j = this.randomIntInclusive(-3, 3);
            int k = this.randomIntInclusive(-1, 1);
            int l = this.randomIntInclusive(-3, 3);
            boolean flag = this.maybeTeleportTo(blockposition.getX() + j, blockposition.getY() + k, blockposition.getZ() + l);

            if (flag) {
                return;
            }
        }

    }

    private boolean maybeTeleportTo(int i, int j, int k) {
        if (Math.abs((double) i - this.owner.getX()) < 2.0D && Math.abs((double) k - this.owner.getZ()) < 2.0D) {
            return false;
        } else if (!this.canTeleportTo(new BlockPos(i, j, k))) {
            return false;
        } else {
            // CraftBukkit start
            CraftEntity entity = this.tamable.getBukkitEntity();
            Location to = new Location(entity.getWorld(), (double) i + 0.5D, (double) j, (double) k + 0.5D, this.tamable.yRot, this.tamable.xRot);
            EntityTeleportEvent event = new EntityTeleportEvent(entity, entity.getLocation(), to);
            this.tamable.level.getServerOH().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }
            to = event.getTo();

            this.tamable.moveTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
            // CraftBukkit end
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos blockposition) {
        BlockPathTypes pathtype = WalkNodeEvaluator.a((BlockGetter) this.level, blockposition.i());

        if (pathtype != BlockPathTypes.WALKABLE) {
            return false;
        } else {
            BlockState iblockdata = this.level.getType(blockposition.below());

            if (!this.canFly && iblockdata.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockposition1 = blockposition.subtract(this.tamable.blockPosition());

                return this.level.noCollision(this.tamable, this.tamable.getBoundingBox().move(blockposition1));
            }
        }
    }

    private int randomIntInclusive(int i, int j) {
        return this.tamable.getRandom().nextInt(j - i + 1) + i;
    }
}
