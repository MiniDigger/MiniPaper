package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class EvokerFangs extends Entity {

    private int warmupDelayTicks;
    private boolean sentSpikeEvent;
    private int lifeTicks;
    private boolean clientSideAttackStarted;
    private LivingEntity owner;
    private UUID ownerUUID;

    public EvokerFangs(EntityType<? extends EvokerFangs> entitytypes, Level world) {
        super(entitytypes, world);
        this.lifeTicks = 22;
    }

    public EvokerFangs(Level world, double d0, double d1, double d2, float f, int i, LivingEntity entityliving) {
        this(EntityType.EVOKER_FANGS, world);
        this.warmupDelayTicks = i;
        this.setOwner(entityliving);
        this.yRot = f * 57.295776F;
        this.setPos(d0, d1, d2);
    }

    @Override
    protected void defineSynchedData() {}

    public void setOwner(@Nullable LivingEntity entityliving) {
        this.owner = entityliving;
        this.ownerUUID = entityliving == null ? null : entityliving.getUUID();
    }

    @Nullable
    public LivingEntity getOwner() {
        if (this.owner == null && this.ownerUUID != null && this.level instanceof ServerLevel) {
            Entity entity = ((ServerLevel) this.level).getEntity(this.ownerUUID);

            if (entity instanceof LivingEntity) {
                this.owner = (LivingEntity) entity;
            }
        }

        return this.owner;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.warmupDelayTicks = nbttagcompound.getInt("Warmup");
        if (nbttagcompound.hasUUID("Owner")) {
            this.ownerUUID = nbttagcompound.getUUID("Owner");
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putInt("Warmup", this.warmupDelayTicks);
        if (this.ownerUUID != null) {
            nbttagcompound.putUUID("Owner", this.ownerUUID);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            if (this.clientSideAttackStarted) {
                --this.lifeTicks;
                if (this.lifeTicks == 14) {
                    for (int i = 0; i < 12; ++i) {
                        double d0 = this.getX() + (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.getBbWidth() * 0.5D;
                        double d1 = this.getY() + 0.05D + this.random.nextDouble();
                        double d2 = this.getZ() + (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.getBbWidth() * 0.5D;
                        double d3 = (this.random.nextDouble() * 2.0D - 1.0D) * 0.3D;
                        double d4 = 0.3D + this.random.nextDouble() * 0.3D;
                        double d5 = (this.random.nextDouble() * 2.0D - 1.0D) * 0.3D;

                        this.level.addParticle(ParticleTypes.CRIT, d0, d1 + 1.0D, d2, d3, d4, d5);
                    }
                }
            }
        } else if (--this.warmupDelayTicks < 0) {
            if (this.warmupDelayTicks == -8) {
                List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.2D, 0.0D, 0.2D));
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    LivingEntity entityliving = (LivingEntity) iterator.next();

                    this.dealDamageTo(entityliving);
                }
            }

            if (!this.sentSpikeEvent) {
                this.level.broadcastEntityEvent(this, (byte) 4);
                this.sentSpikeEvent = true;
            }

            if (--this.lifeTicks < 0) {
                this.remove();
            }
        }

    }

    private void dealDamageTo(LivingEntity entityliving) {
        LivingEntity entityliving1 = this.getOwner();

        if (entityliving.isAlive() && !entityliving.isInvulnerable() && entityliving != entityliving1) {
            if (entityliving1 == null) {
                org.bukkit.craftbukkit.event.CraftEventFactory.entityDamage = this; // CraftBukkit
                entityliving.hurt(DamageSource.MAGIC, 6.0F);
                org.bukkit.craftbukkit.event.CraftEventFactory.entityDamage = null; // CraftBukkit
            } else {
                if (entityliving1.isAlliedTo(entityliving)) {
                    return;
                }

                entityliving.hurt(DamageSource.indirectMagic(this, entityliving1), 6.0F);
            }

        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
