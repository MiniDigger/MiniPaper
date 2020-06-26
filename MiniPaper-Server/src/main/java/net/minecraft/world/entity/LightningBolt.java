package net.minecraft.world.entity;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class LightningBolt extends Entity {

    private int life;
    public long seed;
    private int flashes;
    public boolean visualOnly;
    @Nullable
    private ServerPlayer cause;
    public boolean isSilent = false; // Spigot

    public LightningBolt(EntityType<? extends LightningBolt> entitytypes, Level world) {
        super(entitytypes, world);
        this.noCulling = true;
        this.life = 2;
        this.seed = this.random.nextLong();
        this.flashes = this.random.nextInt(3) + 1;
    }

    public void setVisualOnly(boolean flag) {
        this.visualOnly = flag;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.WEATHER;
    }

    public void setCause(@Nullable ServerPlayer entityplayer) {
        this.cause = entityplayer;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isSilent && this.life == 2) { // Spigot
            Difficulty enumdifficulty = this.level.getDifficulty();

            if (enumdifficulty == Difficulty.NORMAL || enumdifficulty == Difficulty.HARD) {
                this.spawnFire(4);
            }

            // CraftBukkit start - Use relative location for far away sounds
            // this.world.playSound((EntityHuman) null, this.locX(), this.locY(), this.locZ(), SoundEffects.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000.0F, 0.8F + this.random.nextFloat() * 0.2F);
            float pitch = 0.8F + this.random.nextFloat() * 0.2F;
            int viewDistance = ((ServerLevel) this.level).getServerOH().getViewDistance() * 16;
            for (ServerPlayer player : (List<ServerPlayer>) (List) this.level.players()) {
                double deltaX = this.getX() - player.getX();
                double deltaZ = this.getZ() - player.getZ();
                double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (distanceSquared > viewDistance * viewDistance) {
                    double deltaLength = Math.sqrt(distanceSquared);
                    double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                    double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                    player.connection.sendPacket(new ClientboundSoundPacket(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, relativeX, this.getY(), relativeZ, 10000.0F, pitch));
                } else {
                    player.connection.sendPacket(new ClientboundSoundPacket(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, this.getX(), this.getY(), this.getZ(), 10000.0F, pitch));
                }
            }
            // CraftBukkit end
            this.level.playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0F, 0.5F + this.random.nextFloat() * 0.2F);
        }

        --this.life;
        if (this.life < 0) {
            if (this.flashes == 0) {
                this.remove();
            } else if (this.life < -this.random.nextInt(10)) {
                --this.flashes;
                this.life = 1;
                this.seed = this.random.nextLong();
                this.spawnFire(0);
            }
        }

        if (this.life >= 0 && !this.visualOnly) { // CraftBukkit - add !this.isEffect
            if (this.level.isClientSide) {
                this.level.setSkyFlashTime(2);
            } else if (!this.visualOnly) {
                double d0 = 3.0D;
                List<Entity> list = this.level.getEntities(this, new AABB(this.getX() - 3.0D, this.getY() - 3.0D, this.getZ() - 3.0D, this.getX() + 3.0D, this.getY() + 6.0D + 3.0D, this.getZ() + 3.0D), Entity::isAlive);
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();

                    entity.thunderHit(this);
                }

                if (this.cause != null) {
                    CriteriaTriggers.CHANNELED_LIGHTNING.trigger(this.cause, (Collection) list);
                }
            }
        }

    }

    private void spawnFire(int i) {
        if (!this.visualOnly && !this.level.isClientSide && this.level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            BlockPos blockposition = this.blockPosition();
            BlockState iblockdata = BaseFireBlock.getState((BlockGetter) this.level, blockposition);

            if (this.level.getType(blockposition).isAir() && iblockdata.canSurvive(this.level, blockposition)) {
                // CraftBukkit start - add "!isEffect"
                if (!visualOnly && !CraftEventFactory.callBlockIgniteEvent(level, blockposition, this).isCancelled()) {
                    this.level.setTypeUpdate(blockposition, iblockdata);
                }
                // CraftBukkit end
            }

            for (int j = 0; j < i; ++j) {
                BlockPos blockposition1 = blockposition.offset(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);

                iblockdata = BaseFireBlock.getState((BlockGetter) this.level, blockposition1);
                if (this.level.getType(blockposition1).isAir() && iblockdata.canSurvive(this.level, blockposition1)) {
                    // CraftBukkit start - add "!isEffect"
                    if (!visualOnly && !CraftEventFactory.callBlockIgniteEvent(level, blockposition1, this).isCancelled()) {
                        this.level.setTypeUpdate(blockposition1, iblockdata);
                    }
                    // CraftBukkit end
                }
            }

        }
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag nbttagcompound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag nbttagcompound) {}

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
