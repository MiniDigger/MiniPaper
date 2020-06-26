package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.event.entity.ExplosionPrimeEvent; // CraftBukkit

public class LargeFireball extends Fireball {

    public int explosionPower = 1;

    public LargeFireball(EntityType<? extends LargeFireball> entitytypes, Level world) {
        super(entitytypes, world);
        isIncendiary = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit
    }

    public LargeFireball(Level world, LivingEntity entityliving, double d0, double d1, double d2) {
        super(EntityType.FIREBALL, entityliving, d0, d1, d2, world);
        isIncendiary = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        if (!this.level.isClientSide) {
            boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

            // CraftBukkit start - fire ExplosionPrimeEvent
            ExplosionPrimeEvent event = new ExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                // give 'this' instead of (Entity) null so we know what causes the damage
                this.level.explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), flag ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.NONE);
            }
            // CraftBukkit end
            this.remove();
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        if (!this.level.isClientSide) {
            Entity entity = movingobjectpositionentity.getEntity();
            Entity entity1 = this.getOwner();

            entity.hurt(DamageSource.fireball(this, entity1), 6.0F);
            if (entity1 instanceof LivingEntity) {
                this.doEnchantDamageEffects((LivingEntity) entity1, entity);
            }

        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("ExplosionPower", this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("ExplosionPower", 99)) {
            // CraftBukkit - set bukkitYield when setting explosionpower
            bukkitYield = this.explosionPower = nbttagcompound.getInt("ExplosionPower");
        }

    }
}
