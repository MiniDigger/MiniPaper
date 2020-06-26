package net.minecraft.world.entity.animal.horse;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class TraderLlama extends Llama {

    private int despawnDelay = 47999;

    public TraderLlama(EntityType<? extends TraderLlama> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    protected Llama makeBabyLlama() {
        return (Llama) EntityType.TRADER_LLAMA.create(this.level);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("DespawnDelay", this.despawnDelay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("DespawnDelay", 99)) {
            this.despawnDelay = nbttagcompound.getInt("DespawnDelay");
        }

    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0D));
        this.targetSelector.addGoal(1, new TraderLlama.TraderLlamaDefendWanderingTraderGoal(this));
    }

    @Override
    protected void doPlayerRide(Player entityhuman) {
        Entity entity = this.getLeashHolder();

        if (!(entity instanceof WanderingTrader)) {
            super.doPlayerRide(entityhuman);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level.isClientSide) {
            this.maybeDespawn();
        }

    }

    private void maybeDespawn() {
        if (this.canDespawn()) {
            this.despawnDelay = this.isLeashedToWanderingTrader() ? ((WanderingTrader) this.getLeashHolder()).getDespawnDelay() - 1 : this.despawnDelay - 1;
            if (this.despawnDelay <= 0) {
                this.dropLeash(true, false);
                this.remove();
            }

        }
    }

    private boolean canDespawn() {
        return !this.isTamed() && !this.isLeashedToSomethingOtherThanTheWanderingTrader() && !this.hasOnePlayerPassenger();
    }

    private boolean isLeashedToWanderingTrader() {
        return this.getLeashHolder() instanceof WanderingTrader;
    }

    private boolean isLeashedToSomethingOtherThanTheWanderingTrader() {
        return this.isLeashed() && !this.isLeashedToWanderingTrader();
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        if (enummobspawn == MobSpawnType.EVENT) {
            this.setAge(0);
        }

        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
            ((AgableMob.AgableMobGroupData) groupdataentity).setShouldSpawnBaby(false);
        }

        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    public class TraderLlamaDefendWanderingTraderGoal extends TargetGoal {

        private final Llama llama;
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        public TraderLlamaDefendWanderingTraderGoal(Llama entityllama) {
            super(entityllama, false);
            this.llama = entityllama;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!this.llama.isLeashed()) {
                return false;
            } else {
                Entity entity = this.llama.getLeashHolder();

                if (!(entity instanceof WanderingTrader)) {
                    return false;
                } else {
                    WanderingTrader entityvillagertrader = (WanderingTrader) entity;

                    this.ownerLastHurtBy = entityvillagertrader.getLastHurtByMob();
                    int i = entityvillagertrader.getLastHurtByMobTimestamp();

                    return i != this.timestamp && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT);
                }
            }
        }

        @Override
        public void start() {
            this.mob.setGoalTarget(this.ownerLastHurtBy, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER, true); // CraftBukkit
            Entity entity = this.llama.getLeashHolder();

            if (entity instanceof WanderingTrader) {
                this.timestamp = ((WanderingTrader) entity).getLastHurtByMobTimestamp();
            }

            super.start();
        }
    }
}
