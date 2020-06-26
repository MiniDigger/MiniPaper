package net.minecraft.world.entity.monster;

import java.util.Iterator;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class ElderGuardian extends Guardian {

    public static final float ELDER_SIZE_SCALE = EntityType.ELDER_GUARDIAN.getWidth() / EntityType.GUARDIAN.getWidth();

    public ElderGuardian(EntityType<? extends ElderGuardian> entitytypes, Level world) {
        super(entitytypes, world);
        this.setPersistenceRequired();
        if (this.randomStrollGoal != null) {
            this.randomStrollGoal.setInterval(400);
        }

    }

    public static AttributeSupplier.Builder m() {
        return Guardian.eN().a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).a(Attributes.ATTACK_DAMAGE, 8.0D).a(Attributes.MAX_HEALTH, 80.0D);
    }

    @Override
    public int getAttackDuration() {
        return 60;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isInWaterOrBubble() ? SoundEvents.ELDER_GUARDIAN_AMBIENT : SoundEvents.ELDER_GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return this.isInWaterOrBubble() ? SoundEvents.ELDER_GUARDIAN_HURT : SoundEvents.ELDER_GUARDIAN_HURT_LAND;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return this.isInWaterOrBubble() ? SoundEvents.ELDER_GUARDIAN_DEATH : SoundEvents.ELDER_GUARDIAN_DEATH_LAND;
    }

    @Override
    protected SoundEvent getSoundFlop() {
        return SoundEvents.ELDER_GUARDIAN_FLOP;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boolean flag = true;

        if ((this.tickCount + this.getId()) % 1200 == 0) {
            MobEffect mobeffectlist = MobEffects.DIG_SLOWDOWN;
            List<ServerPlayer> list = ((ServerLevel) this.level).getPlayers((entityplayer) -> {
                return this.distanceToSqr((Entity) entityplayer) < 2500.0D && entityplayer.gameMode.isSurvival();
            });
            boolean flag1 = true;
            boolean flag2 = true;
            boolean flag3 = true;
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (!entityplayer.hasEffect(mobeffectlist) || entityplayer.getEffect(mobeffectlist).getAmplifier() < 2 || entityplayer.getEffect(mobeffectlist).getDuration() < 1200) {
                    entityplayer.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, this.isSilent() ? 0.0F : 1.0F));
                    entityplayer.addEffect(new MobEffectInstance(mobeffectlist, 6000, 2), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }
        }

        if (!this.hasRestriction()) {
            this.restrictTo(this.blockPosition(), 16);
        }

    }
}
