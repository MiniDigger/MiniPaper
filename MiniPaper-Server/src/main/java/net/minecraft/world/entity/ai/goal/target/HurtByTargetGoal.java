package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

public class HurtByTargetGoal extends TargetGoal {

    private static final TargetingConditions HURT_BY_TARGETING = (new TargetingConditions()).allowUnseeable().ignoreInvisibilityTesting();
    private boolean alertSameType;
    private int timestamp;
    private final Class<?>[] toIgnoreDamage;
    private Class<?>[] toIgnoreAlert;

    public HurtByTargetGoal(PathfinderMob entitycreature, Class<?>... aclass) {
        super(entitycreature, true);
        this.toIgnoreDamage = aclass;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int i = this.mob.getLastHurtByMobTimestamp();
        LivingEntity entityliving = this.mob.getLastHurtByMob();

        if (i != this.timestamp && entityliving != null) {
            if (entityliving.getType() == EntityType.PLAYER && this.mob.level.getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER)) {
                return false;
            } else {
                Class[] aclass = this.toIgnoreDamage;
                int j = aclass.length;

                for (int k = 0; k < j; ++k) {
                    Class<?> oclass = aclass[k];

                    if (oclass.isAssignableFrom(entityliving.getClass())) {
                        return false;
                    }
                }

                return this.canAttack(entityliving, HurtByTargetGoal.HURT_BY_TARGETING);
            }
        } else {
            return false;
        }
    }

    public HurtByTargetGoal setAlertOthers(Class<?>... aclass) {
        this.alertSameType = true;
        this.toIgnoreAlert = aclass;
        return this;
    }

    @Override
    public void start() {
        this.mob.setGoalTarget(this.mob.getLastHurtByMob(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit - reason
        this.targetMob = this.mob.getTarget();
        this.timestamp = this.mob.getLastHurtByMobTimestamp();
        this.unseenMemoryTicks = 300;
        if (this.alertSameType) {
            this.alertOthers();
        }

        super.start();
    }

    protected void alertOthers() {
        double d0 = this.getFollowDistance();
        AABB axisalignedbb = AABB.unitCubeFromLowerCorner(this.mob.position()).inflate(d0, 10.0D, d0);
        List<Mob> list = this.mob.level.getLoadedEntitiesOfClass(this.mob.getClass(), axisalignedbb);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Mob entityinsentient = (Mob) iterator.next();

            if (this.mob != entityinsentient && entityinsentient.getTarget() == null && (!(this.mob instanceof TamableAnimal) || ((TamableAnimal) this.mob).getOwner() == ((TamableAnimal) entityinsentient).getOwner()) && !entityinsentient.isAlliedTo(this.mob.getLastHurtByMob())) {
                if (this.toIgnoreAlert != null) {
                    boolean flag = false;
                    Class[] aclass = this.toIgnoreAlert;
                    int i = aclass.length;

                    for (int j = 0; j < i; ++j) {
                        Class<?> oclass = aclass[j];

                        if (entityinsentient.getClass() == oclass) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        continue;
                    }
                }

                this.alertOther(entityinsentient, this.mob.getLastHurtByMob());
            }
        }

    }

    protected void alertOther(Mob entityinsentient, LivingEntity entityliving) {
        entityinsentient.setGoalTarget(entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY, true); // CraftBukkit - reason
    }
}
