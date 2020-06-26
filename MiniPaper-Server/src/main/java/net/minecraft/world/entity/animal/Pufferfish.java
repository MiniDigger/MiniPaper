package net.minecraft.world.entity.animal;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class Pufferfish extends AbstractFish {

    private static final EntityDataAccessor<Integer> PUFF_STATE = SynchedEntityData.defineId(Pufferfish.class, EntityDataSerializers.INT);
    private int inflateCounter;
    private int deflateTimer;
    private static final Predicate<LivingEntity> NO_SPECTATORS_AND_NO_WATER_MOB = (entityliving) -> {
        return entityliving == null ? false : (entityliving instanceof Player && (entityliving.isSpectator() || ((Player) entityliving).isCreative()) ? false : entityliving.getMobType() != MobType.WATER);
    };

    public Pufferfish(EntityType<? extends Pufferfish> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Pufferfish.PUFF_STATE, 0);
    }

    public int getPuffState() {
        return (Integer) this.entityData.get(Pufferfish.PUFF_STATE);
    }

    public void setPuffState(int i) {
        this.entityData.set(Pufferfish.PUFF_STATE, i);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (Pufferfish.PUFF_STATE.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("PuffState", this.getPuffState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setPuffState(nbttagcompound.getInt("PuffState"));
    }

    @Override
    protected ItemStack getBucketItemStack() {
        return new ItemStack(Items.PUFFERFISH_BUCKET);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new Pufferfish.PufferfishPuffGoal(this));
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide && this.isAlive() && this.isEffectiveAi()) {
            if (this.inflateCounter > 0) {
                if (this.getPuffState() == 0) {
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_UP, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(1);
                } else if (this.inflateCounter > 40 && this.getPuffState() == 1) {
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_UP, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(2);
                }

                ++this.inflateCounter;
            } else if (this.getPuffState() != 0) {
                if (this.deflateTimer > 60 && this.getPuffState() == 2) {
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_OUT, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(1);
                } else if (this.deflateTimer > 100 && this.getPuffState() == 1) {
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_OUT, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(0);
                }

                ++this.deflateTimer;
            }
        }

        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.getPuffState() > 0) {
            List<Mob> list = this.level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(0.3D), Pufferfish.NO_SPECTATORS_AND_NO_WATER_MOB);
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Mob entityinsentient = (Mob) iterator.next();

                if (entityinsentient.isAlive()) {
                    this.touch(entityinsentient);
                }
            }
        }

    }

    private void touch(Mob entityinsentient) {
        int i = this.getPuffState();

        if (entityinsentient.hurt(DamageSource.mobAttack(this), (float) (1 + i))) {
            entityinsentient.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * i, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            this.playSound(SoundEvents.PUFFER_FISH_STING, 1.0F, 1.0F);
        }

    }

    @Override
    public void playerTouch(Player entityhuman) {
        int i = this.getPuffState();

        if (entityhuman instanceof ServerPlayer && i > 0 && entityhuman.hurt(DamageSource.mobAttack(this), (float) (1 + i))) {
            if (!this.isSilent()) {
                ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.PUFFER_FISH_STING, 0.0F));
            }

            entityhuman.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * i, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }

    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.PUFFER_FISH_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.PUFFER_FISH_DEATH;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.PUFFER_FISH_HURT;
    }

    @Override
    protected SoundEvent getSoundFlop() {
        return SoundEvents.PUFFER_FISH_FLOP;
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        return super.getDimensions(entitypose).scale(getScale(this.getPuffState()));
    }

    private static float getScale(int i) {
        switch (i) {
            case 0:
                return 0.5F;
            case 1:
                return 0.7F;
            default:
                return 1.0F;
        }
    }

    static class PufferfishPuffGoal extends Goal {

        private final Pufferfish fish;

        public PufferfishPuffGoal(Pufferfish entitypufferfish) {
            this.fish = entitypufferfish;
        }

        @Override
        public boolean canUse() {
            List<LivingEntity> list = this.fish.level.getEntitiesOfClass(LivingEntity.class, this.fish.getBoundingBox().inflate(2.0D), Pufferfish.NO_SPECTATORS_AND_NO_WATER_MOB);

            return !list.isEmpty();
        }

        @Override
        public void start() {
            this.fish.inflateCounter = 1;
            this.fish.deflateTimer = 0;
        }

        @Override
        public void stop() {
            this.fish.inflateCounter = 0;
        }

        @Override
        public boolean canContinueToUse() {
            List<LivingEntity> list = this.fish.level.getEntitiesOfClass(LivingEntity.class, this.fish.getBoundingBox().inflate(2.0D), Pufferfish.NO_SPECTATORS_AND_NO_WATER_MOB);

            return !list.isEmpty();
        }
    }
}
