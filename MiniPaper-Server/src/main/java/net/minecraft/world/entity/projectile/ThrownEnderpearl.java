package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class ThrownEnderpearl extends ThrowableItemProjectile {

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public ThrownEnderpearl(Level world, LivingEntity entityliving) {
        super(EntityType.ENDER_PEARL, entityliving, world);
    }

    @Override
    protected Item getDefaultItemOH() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        movingobjectpositionentity.getEntity().hurt(DamageSource.thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        Entity entity = this.getOwner();

        for (int i = 0; i < 32; ++i) {
            this.level.addParticle(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(), this.random.nextGaussian(), 0.0D, this.random.nextGaussian());
        }

        if (!this.level.isClientSide && !this.removed) {
            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                if (entityplayer.connection.getConnection().isConnected() && entityplayer.level == this.level && !entityplayer.isSleeping()) {
                    // CraftBukkit start - Fire PlayerTeleportEvent
                    org.bukkit.craftbukkit.entity.CraftPlayer player = entityplayer.getBukkitEntity();
                    org.bukkit.Location location = getBukkitEntity().getLocation();
                    location.setPitch(player.getLocation().getPitch());
                    location.setYaw(player.getLocation().getYaw());

                    PlayerTeleportEvent teleEvent = new PlayerTeleportEvent(player, player.getLocation(), location, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
                    Bukkit.getPluginManager().callEvent(teleEvent);

                    if (!teleEvent.isCancelled() && !entityplayer.connection.isDisconnected()) {
                        if (this.random.nextFloat() < 0.05F && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                            Endermite entityendermite = (Endermite) EntityType.ENDERMITE.create(this.level);

                            entityendermite.setPlayerSpawned(true);
                        entityendermite.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.yRot, entity.xRot);
                            this.level.addEntity(entityendermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                        }

                        if (entity.isPassenger()) {
                            entity.stopRiding();
                        }

                        entityplayer.connection.teleport(teleEvent.getTo());
                        entity.fallDistance = 0.0F;
                        CraftEventFactory.entityDamage = this;
                        entity.hurt(DamageSource.FALL, 5.0F);
                        CraftEventFactory.entityDamage = null;
                    }
                    // CraftBukkit end
                }
            } else if (entity != null) {
                entity.teleportTo(this.getX(), this.getY(), this.getZ());
                entity.fallDistance = 0.0F;
            }

            this.remove();
        }

    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (entity instanceof Player && !entity.isAlive()) {
            this.remove();
        } else {
            super.tick();
        }

    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel worldserver) {
        Entity entity = this.getOwner();

        if (entity != null && entity.level.getDimensionKey() != worldserver.getDimensionKey()) {
            this.setOwner((Entity) null);
        }

        return super.changeDimension(worldserver);
    }
}
