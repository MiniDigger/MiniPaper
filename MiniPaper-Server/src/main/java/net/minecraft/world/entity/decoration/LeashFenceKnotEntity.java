package net.minecraft.world.entity.decoration;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class LeashFenceKnotEntity extends HangingEntity {

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public LeashFenceKnotEntity(Level world, BlockPos blockposition) {
        super(EntityType.LEASH_KNOT, world, blockposition);
        this.setPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D);
        float f = 0.125F;
        float f1 = 0.1875F;
        float f2 = 0.25F;

        this.setBoundingBox(new AABB(this.getX() - 0.1875D, this.getY() - 0.25D + 0.125D, this.getZ() - 0.1875D, this.getX() + 0.1875D, this.getY() + 0.25D + 0.125D, this.getZ() + 0.1875D));
        this.forcedLoading = true;
    }

    @Override
    public void setPos(double d0, double d1, double d2) {
        super.setPos((double) Mth.floor(d0) + 0.5D, (double) Mth.floor(d1) + 0.5D, (double) Mth.floor(d2) + 0.5D);
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D);
        if (valid) ((ServerLevel) level).updateChunkPos(this); // CraftBukkit
    }

    @Override
    public void setDirection(Direction enumdirection) {}

    @Override
    public int getWidth() {
        return 9;
    }

    @Override
    public int getHeight() {
        return 9;
    }

    @Override
    protected float getEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return -0.0625F;
    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        this.playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {}

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {}

    @Override
    public InteractionResult interact(Player entityhuman, InteractionHand enumhand) {
        if (this.level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            boolean flag = false;
            double d0 = 7.0D;
            List<Mob> list = this.level.getEntitiesOfClass(Mob.class, new AABB(this.getX() - 7.0D, this.getY() - 7.0D, this.getZ() - 7.0D, this.getX() + 7.0D, this.getY() + 7.0D, this.getZ() + 7.0D));
            Iterator iterator = list.iterator();

            Mob entityinsentient;

            while (iterator.hasNext()) {
                entityinsentient = (Mob) iterator.next();
                if (entityinsentient.getLeashHolder() == entityhuman) {
                    // CraftBukkit start
                    if (CraftEventFactory.callPlayerLeashEntityEvent(entityinsentient, this, entityhuman).isCancelled()) {
                        ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundSetEntityLinkPacket(entityinsentient, entityinsentient.getLeashHolder()));
                        continue;
                    }
                    // CraftBukkit end
                    entityinsentient.setLeashedTo(this, true);
                    flag = true;
                }
            }

            if (!flag) {
                // CraftBukkit start - Move below
                // this.die();
                boolean die = true;
                // CraftBukkit end
                if (true || entityhuman.abilities.instabuild) { // CraftBukkit - Process for non-creative as well
                    iterator = list.iterator();

                    while (iterator.hasNext()) {
                        entityinsentient = (Mob) iterator.next();
                        if (entityinsentient.isLeashed() && entityinsentient.getLeashHolder() == this) {
                            // CraftBukkit start
                            if (CraftEventFactory.callPlayerUnleashEntityEvent(entityinsentient, entityhuman).isCancelled()) {
                                die = false;
                                continue;
                            }
                            entityinsentient.dropLeash(true, !entityhuman.abilities.instabuild); // false -> survival mode boolean
                            // CraftBukkit end
                        }
                    }
                    // CraftBukkit start
                    if (die) {
                        this.remove();
                    }
                    // CraftBukkit end
                }
            }

            return InteractionResult.CONSUME;
        }
    }

    @Override
    public boolean survives() {
        return this.level.getType(this.pos).getBlock().is((Tag) BlockTags.FENCES);
    }

    public static LeashFenceKnotEntity getOrCreateKnot(Level world, BlockPos blockposition) {
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();
        List<LeashFenceKnotEntity> list = world.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB((double) i - 1.0D, (double) j - 1.0D, (double) k - 1.0D, (double) i + 1.0D, (double) j + 1.0D, (double) k + 1.0D));
        Iterator iterator = list.iterator();

        LeashFenceKnotEntity entityleash;

        do {
            if (!iterator.hasNext()) {
                LeashFenceKnotEntity entityleash1 = new LeashFenceKnotEntity(world, blockposition);

                world.addFreshEntity(entityleash1);
                entityleash1.playPlacementSound();
                return entityleash1;
            }

            entityleash = (LeashFenceKnotEntity) iterator.next();
        } while (!entityleash.getPos().equals(blockposition));

        return entityleash;
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, this.getType(), 0, this.getPos());
    }
}
