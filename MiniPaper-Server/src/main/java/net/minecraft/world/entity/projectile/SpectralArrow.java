package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class SpectralArrow extends AbstractArrow {

    public int duration = 200;

    public SpectralArrow(EntityType<? extends SpectralArrow> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public SpectralArrow(Level world, LivingEntity entityliving) {
        super(EntityType.SPECTRAL_ARROW, entityliving, world);
    }

    public SpectralArrow(Level world, double d0, double d1, double d2) {
        super(EntityType.SPECTRAL_ARROW, d0, d1, d2, world);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide && !this.inGround) {
            this.level.addParticle(ParticleTypes.INSTANT_EFFECT, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(Items.SPECTRAL_ARROW);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity entityliving) {
        super.doPostHurtEffects(entityliving);
        MobEffectInstance mobeffect = new MobEffectInstance(MobEffects.GLOWING, this.duration, 0);

        entityliving.addEffect(mobeffect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("Duration")) {
            this.duration = nbttagcompound.getInt("Duration");
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Duration", this.duration);
    }
}
