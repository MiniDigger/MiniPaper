package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.event.entity.EntityCombustByEntityEvent; // CraftBukkit

public class SmallFireball extends Fireball {

    public SmallFireball(EntityType<? extends SmallFireball> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public SmallFireball(Level world, LivingEntity entityliving, double d0, double d1, double d2) {
        super(EntityType.SMALL_FIREBALL, entityliving, d0, d1, d2, world);
        // CraftBukkit start
        if (this.getOwner() != null && this.getOwner() instanceof Mob) {
            isIncendiary = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        }
        // CraftBukkit end
    }

    public SmallFireball(Level world, double d0, double d1, double d2, double d3, double d4, double d5) {
        super(EntityType.SMALL_FIREBALL, d0, d1, d2, d3, d4, d5, world);
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        if (!this.level.isClientSide) {
            Entity entity = movingobjectpositionentity.getEntity();

            if (!entity.fireImmune()) {
                Entity entity1 = this.getOwner();
                int i = entity.getRemainingFireTicks();

                // CraftBukkit start - Entity damage by entity event + combust event
                if (isIncendiary) {
                    EntityCombustByEntityEvent event = new EntityCombustByEntityEvent((org.bukkit.entity.Projectile) this.getBukkitEntity(), entity.getBukkitEntity(), 5);
                    entity.level.getServerOH().getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        entity.setOnFire(event.getDuration(), false);
                    }
                }
                // CraftBukkit end
                boolean flag = entity.hurt(DamageSource.fireball(this, entity1), 5.0F);

                if (!flag) {
                    entity.setRemainingFireTicks(i);
                } else if (entity1 instanceof LivingEntity) {
                    this.doEnchantDamageEffects((LivingEntity) entity1, entity);
                }
            }

        }
    }

    @Override
    protected void onHitBlock(BlockHitResult movingobjectpositionblock) {
        super.onHitBlock(movingobjectpositionblock);
        if (!this.level.isClientSide) {
            Entity entity = this.getOwner();

            if (isIncendiary) { // CraftBukkit
                BlockPos blockposition = movingobjectpositionblock.getBlockPos().relative(movingobjectpositionblock.getDirection());

                if (this.level.isEmptyBlock(blockposition) && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, blockposition, this).isCancelled()) { // CraftBukkit
                    this.level.setTypeUpdate(blockposition, BaseFireBlock.getState((BlockGetter) this.level, blockposition));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        if (!this.level.isClientSide) {
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
}
