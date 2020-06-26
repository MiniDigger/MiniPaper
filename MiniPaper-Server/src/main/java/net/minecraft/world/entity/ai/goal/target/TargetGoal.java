package net.minecraft.world.entity.ai.goal.target;

import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.scores.Team;
import org.bukkit.event.entity.EntityTargetEvent; // CraftBukkit

public abstract class TargetGoal extends Goal {

    protected final Mob mob;
    protected final boolean mustSee;
    private final boolean mustReach;
    private int reachCache;
    private int reachCacheTime;
    private int unseenTicks;
    protected LivingEntity targetMob;
    protected int unseenMemoryTicks;

    public TargetGoal(Mob entityinsentient, boolean flag) {
        this(entityinsentient, flag, false);
    }

    public TargetGoal(Mob entityinsentient, boolean flag, boolean flag1) {
        this.unseenMemoryTicks = 60;
        this.mob = entityinsentient;
        this.mustSee = flag;
        this.mustReach = flag1;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity entityliving = this.mob.getTarget();

        if (entityliving == null) {
            entityliving = this.targetMob;
        }

        if (entityliving == null) {
            return false;
        } else if (!entityliving.isAlive()) {
            return false;
        } else {
            Team scoreboardteambase = this.mob.getTeam();
            Team scoreboardteambase1 = entityliving.getTeam();

            if (scoreboardteambase != null && scoreboardteambase1 == scoreboardteambase) {
                return false;
            } else {
                double d0 = this.getFollowDistance();

                if (this.mob.distanceToSqr((Entity) entityliving) > d0 * d0) {
                    return false;
                } else {
                    if (this.mustSee) {
                        if (this.mob.getEntitySenses().canSee(entityliving)) {
                            this.unseenTicks = 0;
                        } else if (++this.unseenTicks > this.unseenMemoryTicks) {
                            return false;
                        }
                    }

                    if (entityliving instanceof Player && ((Player) entityliving).abilities.invulnerable) {
                        return false;
                    } else {
                        this.mob.setGoalTarget(entityliving, EntityTargetEvent.TargetReason.CLOSEST_ENTITY, true); // CraftBukkit
                        return true;
                    }
                }
            }
        }
    }

    protected double getFollowDistance() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public void start() {
        this.reachCache = 0;
        this.reachCacheTime = 0;
        this.unseenTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.setGoalTarget((LivingEntity) null, EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit
        this.targetMob = null;
    }

    protected boolean canAttack(@Nullable LivingEntity entityliving, TargetingConditions pathfindertargetcondition) {
        if (entityliving == null) {
            return false;
        } else if (!pathfindertargetcondition.test(this.mob, entityliving)) {
            return false;
        } else if (!this.mob.isWithinRestriction(entityliving.blockPosition())) {
            return false;
        } else {
            if (this.mustReach) {
                if (--this.reachCacheTime <= 0) {
                    this.reachCache = 0;
                }

                if (this.reachCache == 0) {
                    this.reachCache = this.canReach(entityliving) ? 1 : 2;
                }

                if (this.reachCache == 2) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean canReach(LivingEntity entityliving) {
        this.reachCacheTime = 10 + this.mob.getRandom().nextInt(5);
        Path pathentity = this.mob.getNavigation().createPath((Entity) entityliving, 0);

        if (pathentity == null) {
            return false;
        } else {
            Node pathpoint = pathentity.last();

            if (pathpoint == null) {
                return false;
            } else {
                int i = pathpoint.x - Mth.floor(entityliving.getX());
                int j = pathpoint.z - Mth.floor(entityliving.getZ());

                return (double) (i * i + j * j) <= 2.25D;
            }
        }
    }

    public TargetGoal setUnseenMemoryTicks(int i) {
        this.unseenMemoryTicks = i;
        return this;
    }
}
