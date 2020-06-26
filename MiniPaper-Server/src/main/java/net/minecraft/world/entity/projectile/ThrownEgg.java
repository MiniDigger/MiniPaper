package net.minecraft.world.entity.projectile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEggThrowEvent;
// CraftBukkit end

public class ThrownEgg extends ThrowableItemProjectile {

    public ThrownEgg(net.minecraft.world.entity.EntityType<? extends ThrownEgg> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public ThrownEgg(Level world, LivingEntity entityliving) {
        super(net.minecraft.world.entity.EntityType.EGG, entityliving, world);
    }

    public ThrownEgg(Level world, double d0, double d1, double d2) {
        super(net.minecraft.world.entity.EntityType.EGG, d0, d1, d2, world);
    }

    @Override
    protected void onHitEntity(EntityHitResult movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        movingobjectpositionentity.getEntity().hurt(DamageSource.thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        if (!this.level.isClientSide) {
            boolean hatching = this.random.nextInt(8) == 0; // CraftBukkit
            if (true) {
                byte b0 = 1;

                if (this.random.nextInt(32) == 0) {
                    b0 = 4;
                }

                // CraftBukkit start
                if (!hatching) {
                    b0 = 0;
                }
                EntityType hatchingType = EntityType.CHICKEN;

                Entity shooter = this.getOwner();
                if (shooter instanceof ServerPlayer) {
                    PlayerEggThrowEvent event = new PlayerEggThrowEvent((Player) shooter.getBukkitEntity(), (org.bukkit.entity.Egg) this.getBukkitEntity(), hatching, b0, hatchingType);
                    this.level.getServerOH().getPluginManager().callEvent(event);

                    b0 = event.getNumHatches();
                    hatching = event.isHatching();
                    hatchingType = event.getHatchingType();
                }

                if (hatching) {
                    for (int i = 0; i < b0; ++i) {
                        Entity entity = level.getWorld().createEntity(new org.bukkit.Location(level.getWorld(), this.getX(), this.getY(), this.getZ(), this.yRot, 0.0F), hatchingType.getEntityClass());
                        if (entity.getBukkitEntity() instanceof Ageable) {
                            ((Ageable) entity.getBukkitEntity()).setBaby();
                        }
                        level.getWorld().addEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG);
                    }
                }
                // CraftBukkit end
            }

            this.level.broadcastEntityEvent(this, (byte) 3);
            this.remove();
        }

    }

    @Override
    protected Item getDefaultItemOH() {
        return Items.EGG;
    }
}
