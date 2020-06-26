package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.event.entity.ExplosionPrimeEvent; // CraftBukkit

public class WitherSkull extends AbstractHurtingProjectile {

    private static final EntityDataAccessor<Boolean> DATA_DANGEROUS = SynchedEntityData.defineId(WitherSkull.class, EntityDataSerializers.BOOLEAN);

    public WitherSkull(EntityType<? extends WitherSkull> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public WitherSkull(Level world, LivingEntity entityliving, double d0, double d1, double d2) {
        super(EntityType.WITHER_SKULL, entityliving, d0, d1, d2, world);
    }

    @Override
    protected float getInertia() {
        return this.isDangerous() ? 0.73F : super.getInertia();
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public float getBlockExplosionResistance(Explosion explosion, BlockGetter iblockaccess, BlockPos blockposition, BlockState iblockdata, FluidState fluid, float f) {
        return this.isDangerous() && WitherBoss.canDestroy(iblockdata) ? Math.min(0.8F, f) : f;
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        if (!this.level.isClientSide) {
            Entity entity = movingobjectpositionentity.getEntity();
            Entity entity1 = this.getOwner();
            boolean flag;

            if (entity1 instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity1;

                flag = entity.hurt(DamageSource.witherSkull(this, (Entity) entityliving), 8.0F);
                if (flag) {
                    if (entity.isAlive()) {
                        this.doEnchantDamageEffects(entityliving, entity);
                    } else {
                        entityliving.heal(5.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.WITHER); // CraftBukkit
                    }
                }
            } else {
                flag = entity.hurt(DamageSource.MAGIC, 5.0F);
            }

            if (flag && entity instanceof LivingEntity) {
                byte b0 = 0;

                if (this.level.getDifficulty() == Difficulty.NORMAL) {
                    b0 = 10;
                } else if (this.level.getDifficulty() == Difficulty.HARD) {
                    b0 = 40;
                }

                if (b0 > 0) {
                    ((LivingEntity) entity).addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * b0, 1), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        if (!this.level.isClientSide) {
            Explosion.BlockInteraction explosion_effect = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.NONE;

            // CraftBukkit start
            // this.world.createExplosion(this, this.locX(), this.locY(), this.locZ(), 1.0F, false, explosion_effect);
            ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), 1.0F, false);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                this.level.explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), explosion_effect);
            }
            // CraftBukkit end
            this.remove();
        }

    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(WitherSkull.DATA_DANGEROUS, false);
    }

    public boolean isDangerous() {
        return (Boolean) this.entityData.get(WitherSkull.DATA_DANGEROUS);
    }

    public void setDangerous(boolean flag) {
        this.entityData.set(WitherSkull.DATA_DANGEROUS, flag);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }
}
