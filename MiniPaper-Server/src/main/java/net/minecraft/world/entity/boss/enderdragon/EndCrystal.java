package net.minecraft.world.entity.boss.enderdragon;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.dimension.end.EndDragonFight;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EndCrystal extends Entity {

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM_TARGET = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_SHOW_BOTTOM = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
    public int time;

    public EndCrystal(EntityType<? extends EndCrystal> entitytypes, Level world) {
        super(entitytypes, world);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EndCrystal(Level world, double d0, double d1, double d2) {
        this(EntityType.END_CRYSTAL, world);
        this.setPos(d0, d1, d2);
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().register(EndCrystal.DATA_BEAM_TARGET, Optional.empty());
        this.getEntityData().register(EndCrystal.DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        ++this.time;
        if (this.level instanceof ServerLevel) {
            BlockPos blockposition = this.blockPosition();

            if (((ServerLevel) this.level).dragonFight() != null && this.level.getType(blockposition).isAir()) {
                // CraftBukkit start
                if (!CraftEventFactory.callBlockIgniteEvent(this.level, blockposition, this).isCancelled()) {
                    this.level.setTypeUpdate(blockposition, BaseFireBlock.getState((BlockGetter) this.level, blockposition));
                }
                // CraftBukkit end
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {
        if (this.getBeamTarget() != null) {
            nbttagcompound.put("BeamTarget", NbtUtils.writeBlockPos(this.getBeamTarget()));
        }

        nbttagcompound.putBoolean("ShowBottom", this.showsBottom());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {
        if (nbttagcompound.contains("BeamTarget", 10)) {
            this.setBeamTarget(NbtUtils.readBlockPos(nbttagcompound.getCompound("BeamTarget")));
        }

        if (nbttagcompound.contains("ShowBottom", 1)) {
            this.setShowBottom(nbttagcompound.getBoolean("ShowBottom"));
        }

    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (damagesource.getEntity() instanceof EnderDragon) {
            return false;
        } else {
            if (!this.removed && !this.level.isClientSide) {
                // CraftBukkit start - All non-living entities need this
                if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f)) {
                    return false;
                }
                // CraftBukkit end
                this.remove();
                if (!damagesource.isExplosion()) {
                    // CraftBukkit start
                    ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), 6.0F, false);
                    this.level.getServerOH().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        this.removed = false;
                        return false;
                    }
                    this.level.explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Explosion.BlockInteraction.DESTROY);
                    // CraftBukkit end
                }

                this.onDestroyedBy(damagesource);
            }

            return true;
        }
    }

    @Override
    public void kill() {
        this.onDestroyedBy(DamageSource.GENERIC);
        super.kill();
    }

    private void onDestroyedBy(DamageSource damagesource) {
        if (this.level instanceof ServerLevel) {
            EndDragonFight enderdragonbattle = ((ServerLevel) this.level).dragonFight();

            if (enderdragonbattle != null) {
                enderdragonbattle.onCrystalDestroyed(this, damagesource);
            }
        }

    }

    public void setBeamTarget(@Nullable BlockPos blockposition) {
        this.getEntityData().set(EndCrystal.DATA_BEAM_TARGET, Optional.ofNullable(blockposition));
    }

    @Nullable
    public BlockPos getBeamTarget() {
        return (BlockPos) ((Optional) this.getEntityData().get(EndCrystal.DATA_BEAM_TARGET)).orElse((Object) null);
    }

    public void setShowBottom(boolean flag) {
        this.getEntityData().set(EndCrystal.DATA_SHOW_BOTTOM, flag);
    }

    public boolean showsBottom() {
        return (Boolean) this.getEntityData().get(EndCrystal.DATA_SHOW_BOTTOM);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
