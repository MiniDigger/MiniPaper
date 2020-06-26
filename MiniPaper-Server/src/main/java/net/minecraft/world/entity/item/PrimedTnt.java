package net.minecraft.world.entity.item;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.bukkit.event.entity.ExplosionPrimeEvent; // CraftBukkit

public class PrimedTnt extends Entity {

    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.INT);
    @Nullable
    private LivingEntity owner;
    private int life;
    public float yield = 4; // CraftBukkit - add field
    public boolean isIncendiary = false; // CraftBukkit - add field

    public PrimedTnt(EntityType<? extends PrimedTnt> entitytypes, Level world) {
        super(entitytypes, world);
        this.life = 80;
        this.blocksBuilding = true;
    }

    public PrimedTnt(Level world, double d0, double d1, double d2, @Nullable LivingEntity entityliving) {
        this(EntityType.TNT, world);
        this.setPos(d0, d1, d2);
        double d3 = world.random.nextDouble() * 6.2831854820251465D;

        this.setDeltaMovement(-Math.sin(d3) * 0.02D, 0.20000000298023224D, -Math.cos(d3) * 0.02D);
        this.setFuse(80);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
        this.owner = entityliving;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.register(PrimedTnt.DATA_FUSE_ID, 80);
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }

    @Override
    public void tick() {
        if (level.spigotConfig.currentPrimedTnt++ > level.spigotConfig.maxTntTicksPerTick) { return; } // Spigot
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        if (this.onGround) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
        }

        --this.life;
        if (this.life <= 0) {
            // CraftBukkit start - Need to reverse the order of the explosion and the entity death so we have a location for the event
            // this.die();
            if (!this.level.isClientSide) {
                this.explode();
            }
            this.remove();
            // CraftBukkit end
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level.isClientSide) {
                this.level.addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    private void explode() {
        // CraftBukkit start
        // float f = 4.0F;

        ExplosionPrimeEvent event = new ExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
        this.level.getServerOH().getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            this.level.explode(this, this.getX(), this.getY(0.0625D), this.getZ(), event.getRadius(), event.getFire(), Explosion.BlockInteraction.BREAK);
        }
        // CraftBukkit end
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putShort("Fuse", (short) this.getLife());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.setFuse(nbttagcompound.getShort("Fuse"));
    }

    @Nullable
    public LivingEntity getOwner() {
        return this.owner;
    }

    @Override
    protected float getEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.15F;
    }

    public void setFuse(int i) {
        this.entityData.set(PrimedTnt.DATA_FUSE_ID, i);
        this.life = i;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (PrimedTnt.DATA_FUSE_ID.equals(datawatcherobject)) {
            this.life = this.getFuse();
        }

    }

    public int getFuse() {
        return (Integer) this.entityData.get(PrimedTnt.DATA_FUSE_ID);
    }

    public int getLife() {
        return this.life;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
