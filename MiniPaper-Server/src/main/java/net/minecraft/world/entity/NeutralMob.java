package net.minecraft.world.entity;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public interface NeutralMob {

    int getRemainingPersistentAngerTime();

    void setRemainingPersistentAngerTime(int i);

    @Nullable
    UUID getPersistentAngerTarget();

    void setPersistentAngerTarget(@Nullable UUID uuid);

    void startPersistentAngerTimer();

    default void addPersistentAngerSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putInt("AngerTime", this.getRemainingPersistentAngerTime());
        if (this.getPersistentAngerTarget() != null) {
            nbttagcompound.putUUID("AngryAt", this.getPersistentAngerTarget());
        }

    }

    default void readPersistentAngerSaveData(ServerLevel worldserver, CompoundTag nbttagcompound) {
        this.setRemainingPersistentAngerTime(nbttagcompound.getInt("AngerTime"));
        if (!nbttagcompound.hasUUID("AngryAt")) {
            this.setPersistentAngerTarget((UUID) null);
        } else {
            UUID uuid = nbttagcompound.getUUID("AngryAt");

            this.setPersistentAngerTarget(uuid);
            Entity entity = worldserver.getEntity(uuid);

            if (entity != null) {
                if (entity instanceof Mob) {
                    this.setLastHurtByMob((Mob) entity);
                }

                if (entity.getType() == EntityType.PLAYER) {
                    this.setLastHurtByPlayer((Player) entity);
                }

            }
        }
    }

    default void updatePersistentAnger(ServerLevel worldserver, boolean flag) {
        LivingEntity entityliving = this.getTarget();
        UUID uuid = this.getPersistentAngerTarget();

        if ((entityliving == null || entityliving.isDeadOrDying()) && uuid != null && worldserver.getEntity(uuid) instanceof Mob) {
            this.stopBeingAngry();
        } else {
            if (entityliving != null && !Objects.equals(uuid, entityliving.getUUID())) {
                this.setPersistentAngerTarget(entityliving.getUUID());
                this.startPersistentAngerTimer();
            }

            if (this.getRemainingPersistentAngerTime() > 0 && (entityliving == null || entityliving.getType() != EntityType.PLAYER || !flag)) {
                this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
                if (this.getRemainingPersistentAngerTime() == 0) {
                    this.stopBeingAngry();
                }
            }

        }
    }

    default boolean isAngryAt(LivingEntity entityliving) {
        return !EntitySelector.ATTACK_ALLOWED.test(entityliving) ? false : (entityliving.getType() == EntityType.PLAYER && this.isAngryAtAllPlayers(entityliving.level) ? true : entityliving.getUUID().equals(this.getPersistentAngerTarget()));
    }

    default boolean isAngryAtAllPlayers(Level world) {
        return world.getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        return this.getRemainingPersistentAngerTime() > 0;
    }

    default void playerDied(Player entityhuman) {
        if (entityhuman.level.getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            if (entityhuman.getUUID().equals(this.getPersistentAngerTarget())) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob((LivingEntity) null);
        this.setPersistentAngerTarget((UUID) null);
        this.setGoalTarget((LivingEntity) null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit
        this.setRemainingPersistentAngerTime(0);
    }

    void setLastHurtByMob(@Nullable LivingEntity entityliving);

    void setLastHurtByPlayer(@Nullable Player entityhuman);

    void setTarget(@Nullable LivingEntity entityliving);

    boolean setGoalTarget(LivingEntity entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason reason, boolean fireEvent); // CraftBukkit

    @Nullable
    LivingEntity getTarget();
}
