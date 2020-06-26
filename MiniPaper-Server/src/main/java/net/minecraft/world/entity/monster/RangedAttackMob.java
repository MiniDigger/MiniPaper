package net.minecraft.world.entity.monster;

import net.minecraft.world.entity.LivingEntity;

public interface RangedAttackMob {

    void performRangedAttack(LivingEntity entityliving, float f); default void rangedAttack(LivingEntity entityliving, float f) { performRangedAttack(entityliving, f); } // Paper - OBFHELPER

    // - see EntitySkeletonAbstract melee goal
    void setAggressive(boolean flag); default void setChargingAttack(boolean charging) { setAggressive(charging); }; // Paper
}
