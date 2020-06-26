package net.minecraft.world.entity.decoration;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

// CraftBukkit start
import org.bukkit.entity.Hanging;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
// CraftBukkit end

public abstract class HangingEntity extends Entity {

    protected static final Predicate<Entity> HANGING_ENTITY = (entity) -> {
        return entity instanceof HangingEntity;
    };
    private int checkInterval;
    public BlockPos pos;
    protected Direction direction;

    protected HangingEntity(EntityType<? extends HangingEntity> entitytypes, Level world) {
        super(entitytypes, world);
        this.direction = Direction.SOUTH;
    }

    protected HangingEntity(EntityType<? extends HangingEntity> entitytypes, Level world, BlockPos blockposition) {
        this(entitytypes, world);
        this.pos = blockposition;
    }

    @Override
    protected void defineSynchedData() {}

    public void setDirection(Direction enumdirection) {
        Validate.notNull(enumdirection);
        Validate.isTrue(enumdirection.getAxis().isHorizontal());
        this.direction = enumdirection;
        this.yRot = (float) (this.direction.get2DDataValue() * 90);
        this.yRotO = this.yRot;
        this.recalculateBoundingBox();
    }

    protected void recalculateBoundingBox() {
        if (this.direction != null) {
            // CraftBukkit start code moved in to calculateBoundingBox
            this.setBoundingBox(calculateBoundingBox(this, this.pos, this.direction, this.getWidth(), this.getHeight()));
            // CraftBukkit end
        }
    }

    // CraftBukkit start - break out BB calc into own method
    public static AABB calculateBoundingBox(@Nullable Entity entity, BlockPos blockPosition, Direction direction, int width, int height) {
        {
            double d0 = (double) blockPosition.getX() + 0.5D;
            double d1 = (double) blockPosition.getY() + 0.5D;
            double d2 = (double) blockPosition.getZ() + 0.5D;
            double d3 = 0.46875D;
            double d4 = offs(width);
            double d5 = offs(height);

            d0 -= (double) direction.getStepX() * 0.46875D;
            d2 -= (double) direction.getStepZ() * 0.46875D;
            d1 += d5;
            Direction enumdirection = direction.getCounterClockWise();

            d0 += d4 * (double) enumdirection.getStepX();
            d2 += d4 * (double) enumdirection.getStepZ();
            if (entity != null) {
                entity.setPosRaw(d0, d1, d2);
            }
            double d6 = (double) width;
            double d7 = (double) height;
            double d8 = (double) width;

            if (direction.getAxis() == Direction.Axis.Z) {
                d8 = 1.0D;
            } else {
                d6 = 1.0D;
            }

            d6 /= 32.0D;
            d7 /= 32.0D;
            d8 /= 32.0D;
            return new AABB(d0 - d6, d1 - d7, d2 - d8, d0 + d6, d1 + d7, d2 + d8);
        }
    }
    // CraftBukkit end

    private static double offs(int i) { // CraftBukkit - static
        return i % 32 == 0 ? 0.5D : 0.0D;
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide) {
            if (this.getY() < -64.0D) {
                this.outOfWorld();
            }

            if (this.checkInterval++ == this.level.spigotConfig.hangingTickFrequency) { // Spigot
                this.checkInterval = 0;
                if (!this.removed && !this.survives()) {
                    // CraftBukkit start - fire break events
                    Material material = this.level.getType(this.blockPosition()).getMaterial();
                    HangingBreakEvent.RemoveCause cause;

                    if (!material.equals(Material.AIR)) {
                        // TODO: This feels insufficient to catch 100% of suffocation cases
                        cause = HangingBreakEvent.RemoveCause.OBSTRUCTION;
                    } else {
                        cause = HangingBreakEvent.RemoveCause.PHYSICS;
                    }

                    HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), cause);
                    this.level.getServerOH().getPluginManager().callEvent(event);

                    if (removed || event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.remove();
                    this.dropItem((Entity) null);
                }
            }
        }

    }

    public boolean survives() {
        if (!this.level.noCollision(this)) {
            return false;
        } else {
            int i = Math.max(1, this.getWidth() / 16);
            int j = Math.max(1, this.getHeight() / 16);
            BlockPos blockposition = this.pos.relative(this.direction.getOpposite());
            Direction enumdirection = this.direction.getCounterClockWise();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

            for (int k = 0; k < i; ++k) {
                for (int l = 0; l < j; ++l) {
                    int i1 = (i - 1) / -2;
                    int j1 = (j - 1) / -2;

                    blockposition_mutableblockposition.g(blockposition).c(enumdirection, k + i1).c(Direction.UP, l + j1);
                    BlockState iblockdata = this.level.getType(blockposition_mutableblockposition);

                    if (!iblockdata.getMaterial().isSolid() && !DiodeBlock.isDiode(iblockdata)) {
                        return false;
                    }
                }
            }

            return this.level.getEntities(this, this.getBoundingBox(), HangingEntity.HANGING_ENTITY).isEmpty();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        if (entity instanceof Player) {
            Player entityhuman = (Player) entity;

            return !this.level.mayInteract(entityhuman, this.pos) ? true : this.hurt(DamageSource.playerAttack(entityhuman), 0.0F);
        } else {
            return false;
        }
    }

    @Override
    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            if (!this.removed && !this.level.isClientSide) {
                // CraftBukkit start - fire break events
                Entity damager = (damagesource instanceof IndirectEntityDamageSource) ? ((IndirectEntityDamageSource) damagesource).getProximateDamageSource() : damagesource.getEntity();
                HangingBreakEvent event;
                if (damager != null) {
                    event = new HangingBreakByEntityEvent((Hanging) this.getBukkitEntity(), damager.getBukkitEntity(), damagesource.isExplosion() ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.ENTITY);
                } else {
                    event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), damagesource.isExplosion() ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.DEFAULT);
                }

                this.level.getServerOH().getPluginManager().callEvent(event);

                if (this.removed || event.isCancelled()) {
                    return true;
                }
                // CraftBukkit end

                this.remove();
                this.markHurt();
                this.dropItem(damagesource.getEntity());
            }

            return true;
        }
    }

    @Override
    public void move(MoverType enummovetype, Vec3 vec3d) {
        if (!this.level.isClientSide && !this.removed && vec3d.lengthSqr() > 0.0D) {
            if (this.removed) return; // CraftBukkit

            // CraftBukkit start - fire break events
            // TODO - Does this need its own cause? Seems to only be triggered by pistons
            HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.PHYSICS);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (this.removed || event.isCancelled()) {
                return;
            }
            // CraftBukkit end

            this.remove();
            this.dropItem((Entity) null);
        }

    }

    @Override
    public void push(double d0, double d1, double d2) {
        if (false && !this.level.isClientSide && !this.removed && d0 * d0 + d1 * d1 + d2 * d2 > 0.0D) { // CraftBukkit - not needed
            this.remove();
            this.dropItem((Entity) null);
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        nbttagcompound.putByte("Facing", (byte) this.direction.get2DDataValue());
        BlockPos blockposition = this.getPos();

        nbttagcompound.putInt("TileX", blockposition.getX());
        nbttagcompound.putInt("TileY", blockposition.getY());
        nbttagcompound.putInt("TileZ", blockposition.getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        this.pos = new BlockPos(nbttagcompound.getInt("TileX"), nbttagcompound.getInt("TileY"), nbttagcompound.getInt("TileZ"));
        this.direction = Direction.from2DDataValue(nbttagcompound.getByte("Facing"));
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void dropItem(@Nullable Entity entity);

    public abstract void playPlacementSound();

    @Override
    public ItemEntity spawnAtLocation(ItemStack itemstack, float f) {
        ItemEntity entityitem = new ItemEntity(this.level, this.getX() + (double) ((float) this.direction.getStepX() * 0.15F), this.getY() + (double) f, this.getZ() + (double) ((float) this.direction.getStepZ() * 0.15F), itemstack);

        entityitem.setDefaultPickUpDelay();
        this.level.addFreshEntity(entityitem);
        return entityitem;
    }

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double d0, double d1, double d2) {
        this.pos = new BlockPos(d0, d1, d2);
        this.recalculateBoundingBox();
        this.hasImpulse = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public float rotate(Rotation enumblockrotation) {
        if (this.direction.getAxis() != Direction.Axis.Y) {
            switch (enumblockrotation) {
                case CLOCKWISE_180:
                    this.direction = this.direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    this.direction = this.direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    this.direction = this.direction.getClockWise();
            }
        }

        float f = Mth.wrapDegrees(this.yRot);

        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 90.0F;
            case CLOCKWISE_90:
                return f + 270.0F;
            default:
                return f;
        }
    }

    @Override
    public float mirror(Mirror enumblockmirror) {
        return this.rotate(enumblockmirror.getRotation(this.direction));
    }

    @Override
    public void thunderHit(LightningBolt entitylightning) {}

    @Override
    public void refreshDimensions() {}
}
