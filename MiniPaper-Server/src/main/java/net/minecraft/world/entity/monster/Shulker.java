package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ShulkerSharedHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.event.entity.EntityTeleportEvent;
// CraftBukkit end

public class Shulker extends AbstractGolem implements Enemy {

    private static final UUID COVERED_ARMOR_MODIFIER_UUID = UUID.fromString("7E0292F2-9434-48D5-A29F-9583AF7DF27F");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(Shulker.COVERED_ARMOR_MODIFIER_UUID, "Covered armor bonus", 20.0D, AttributeModifier.Operation.ADDITION);
    protected static final EntityDataAccessor<Direction> DATA_ATTACH_FACE_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.DIRECTION);
    protected static final EntityDataAccessor<Optional<BlockPos>> DATA_ATTACH_POS_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    protected static final EntityDataAccessor<Byte> DATA_PEEK_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Byte> DATA_COLOR_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.BYTE);
    private float currentPeekAmountO;
    private float currentPeekAmount;
    private BlockPos oldAttachPosition = null;
    private int clientSideTeleportInterpolation;

    public Shulker(EntityType<? extends Shulker> entitytypes, Level world) {
        super(entitytypes, world);
        this.xpReward = 5;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new Shulker.ShulkerAttackGoal());
        this.goalSelector.addGoal(7, new Shulker.ShulkerPeekGoal());
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(new Class[0])); // CraftBukkit - decompile error
        this.targetSelector.addGoal(2, new Shulker.ShulkerNearestAttackGoal(this));
        this.targetSelector.addGoal(3, new Shulker.ShulkerDefenseAttackGoal(this));
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.SHULKER_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        if (!this.isClosed()) {
            super.playAmbientSound();
        }

    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.SHULKER_DEATH;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return this.isClosed() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHULKER_HURT;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Shulker.DATA_ATTACH_FACE_ID, Direction.DOWN);
        this.entityData.register(Shulker.DATA_ATTACH_POS_ID, Optional.empty());
        this.entityData.register(Shulker.DATA_PEEK_ID, (byte) 0);
        this.entityData.register(Shulker.DATA_COLOR_ID, (byte) 16);
    }

    public static AttributeSupplier.Builder m() {
        return Mob.p().a(Attributes.MAX_HEALTH, 30.0D);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Shulker.ShulkerBodyRotationControl(this);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.entityData.set(Shulker.DATA_ATTACH_FACE_ID, Direction.from3DDataValue(nbttagcompound.getByte("AttachFace")));
        this.entityData.set(Shulker.DATA_PEEK_ID, nbttagcompound.getByte("Peek"));
        this.entityData.set(Shulker.DATA_COLOR_ID, nbttagcompound.getByte("Color"));
        if (nbttagcompound.contains("APX")) {
            int i = nbttagcompound.getInt("APX");
            int j = nbttagcompound.getInt("APY");
            int k = nbttagcompound.getInt("APZ");

            this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.of(new BlockPos(i, j, k)));
        } else {
            this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.empty());
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putByte("AttachFace", (byte) ((Direction) this.entityData.get(Shulker.DATA_ATTACH_FACE_ID)).get3DDataValue());
        nbttagcompound.putByte("Peek", (Byte) this.entityData.get(Shulker.DATA_PEEK_ID));
        nbttagcompound.putByte("Color", (Byte) this.entityData.get(Shulker.DATA_COLOR_ID));
        BlockPos blockposition = this.getAttachPosition();

        if (blockposition != null) {
            nbttagcompound.putInt("APX", blockposition.getX());
            nbttagcompound.putInt("APY", blockposition.getY());
            nbttagcompound.putInt("APZ", blockposition.getZ());
        }

    }

    @Override
    public void tick() {
        super.tick();
        BlockPos blockposition = (BlockPos) ((Optional) this.entityData.get(Shulker.DATA_ATTACH_POS_ID)).orElse((Object) null);

        if (blockposition == null && !this.level.isClientSide) {
            blockposition = this.blockPosition();
            this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.of(blockposition));
        }

        float f;

        if (this.isPassenger()) {
            blockposition = null;
            f = this.getVehicle().yRot;
            this.yRot = f;
            this.yBodyRot = f;
            this.yBodyRotO = f;
            this.clientSideTeleportInterpolation = 0;
        } else if (!this.level.isClientSide) {
            BlockState iblockdata = this.level.getType(blockposition);
            Direction enumdirection;

            if (!iblockdata.isAir()) {
                if (iblockdata.is(Blocks.MOVING_PISTON)) {
                    enumdirection = (Direction) iblockdata.getValue(PistonBaseBlock.FACING);
                    if (this.level.isEmptyBlock(blockposition.relative(enumdirection))) {
                        blockposition = blockposition.relative(enumdirection);
                        this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.of(blockposition));
                    } else {
                        this.teleportSomewhere();
                    }
                } else if (iblockdata.is(Blocks.PISTON_HEAD)) {
                    enumdirection = (Direction) iblockdata.getValue(PistonHeadBlock.FACING);
                    if (this.level.isEmptyBlock(blockposition.relative(enumdirection))) {
                        blockposition = blockposition.relative(enumdirection);
                        this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.of(blockposition));
                    } else {
                        this.teleportSomewhere();
                    }
                } else {
                    this.teleportSomewhere();
                }
            }

            enumdirection = this.getAttachFace();
            if (!this.canAttachOnBlockFace(blockposition, enumdirection)) {
                Direction enumdirection1 = this.findAttachableFace(blockposition);

                if (enumdirection1 != null) {
                    this.entityData.set(Shulker.DATA_ATTACH_FACE_ID, enumdirection1);
                } else {
                    this.teleportSomewhere();
                }
            }
        }

        f = (float) this.getRawPeekAmount() * 0.01F;
        this.currentPeekAmountO = this.currentPeekAmount;
        if (this.currentPeekAmount > f) {
            this.currentPeekAmount = Mth.clamp(this.currentPeekAmount - 0.05F, f, 1.0F);
        } else if (this.currentPeekAmount < f) {
            this.currentPeekAmount = Mth.clamp(this.currentPeekAmount + 0.05F, 0.0F, f);
        }

        if (blockposition != null) {
            if (this.level.isClientSide) {
                if (this.clientSideTeleportInterpolation > 0 && this.oldAttachPosition != null) {
                    --this.clientSideTeleportInterpolation;
                } else {
                    this.oldAttachPosition = blockposition;
                }
            }

            this.setPosAndOldPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D);
            double d0 = 0.5D - (double) Mth.sin((0.5F + this.currentPeekAmount) * 3.1415927F) * 0.5D;
            double d1 = 0.5D - (double) Mth.sin((0.5F + this.currentPeekAmountO) * 3.1415927F) * 0.5D;
            Direction enumdirection2 = this.getAttachFace().getOpposite();

            this.setBoundingBox((new AABB(this.getX() - 0.5D, this.getY(), this.getZ() - 0.5D, this.getX() + 0.5D, this.getY() + 1.0D, this.getZ() + 0.5D)).expandTowards((double) enumdirection2.getStepX() * d0, (double) enumdirection2.getStepY() * d0, (double) enumdirection2.getStepZ() * d0));
            double d2 = d0 - d1;

            if (d2 > 0.0D) {
                List<Entity> list = this.level.getEntities(this, this.getBoundingBox());

                if (!list.isEmpty()) {
                    Iterator iterator = list.iterator();

                    while (iterator.hasNext()) {
                        Entity entity = (Entity) iterator.next();

                        if (!(entity instanceof Shulker) && !entity.noPhysics) {
                            entity.move(MoverType.SHULKER, new Vec3(d2 * (double) enumdirection2.getStepX(), d2 * (double) enumdirection2.getStepY(), d2 * (double) enumdirection2.getStepZ()));
                        }
                    }
                }
            }
        }

    }

    @Override
    public void move(MoverType enummovetype, Vec3 vec3d) {
        if (enummovetype == MoverType.SHULKER_BOX) {
            this.teleportSomewhere();
        } else {
            super.move(enummovetype, vec3d);
        }

    }

    @Override
    public void setPos(double d0, double d1, double d2) {
        super.setPos(d0, d1, d2);
        if (this.entityData != null && this.tickCount != 0) {
            Optional<BlockPos> optional = (Optional) this.entityData.get(Shulker.DATA_ATTACH_POS_ID);
            Optional<BlockPos> optional1 = Optional.of(new BlockPos(d0, d1, d2));

            if (!optional1.equals(optional)) {
                this.entityData.set(Shulker.DATA_ATTACH_POS_ID, optional1);
                this.entityData.set(Shulker.DATA_PEEK_ID, (byte) 0);
                this.hasImpulse = true;
            }

        }
    }

    @Nullable
    protected Direction findAttachableFace(BlockPos blockposition) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (this.canAttachOnBlockFace(blockposition, enumdirection)) {
                return enumdirection;
            }
        }

        return null;
    }

    private boolean canAttachOnBlockFace(BlockPos blockposition, Direction enumdirection) {
        return this.level.loadedAndEntityCanStandOnFace(blockposition.relative(enumdirection), (Entity) this, enumdirection.getOpposite()) && this.level.noCollision(this, ShulkerSharedHelper.openBoundingBox(blockposition, enumdirection.getOpposite()));
    }

    protected boolean teleportSomewhere() {
        if (!this.isNoAi() && this.isAlive()) {
            BlockPos blockposition = this.blockPosition();

            for (int i = 0; i < 5; ++i) {
                BlockPos blockposition1 = blockposition.offset(8 - this.random.nextInt(17), 8 - this.random.nextInt(17), 8 - this.random.nextInt(17));

                if (blockposition1.getY() > 0 && this.level.isEmptyBlock(blockposition1) && this.level.getWorldBorder().isWithinBounds(blockposition1) && this.level.noCollision(this, new AABB(blockposition1))) {
                    Direction enumdirection = this.findAttachableFace(blockposition1);

                    if (enumdirection != null) {
                        // CraftBukkit start
                        EntityTeleportEvent teleport = new EntityTeleportEvent(this.getBukkitEntity(), this.getBukkitEntity().getLocation(), new Location(this.level.getWorld(), blockposition1.getX(), blockposition1.getY(), blockposition1.getZ()));
                        this.level.getServerOH().getPluginManager().callEvent(teleport);
                        if (!teleport.isCancelled()) {
                            Location to = teleport.getTo();
                            blockposition1 = new BlockPos(to.getX(), to.getY(), to.getZ());
                        } else {
                            return false;
                        }
                        // CraftBukkit end
                        this.entityData.set(Shulker.DATA_ATTACH_FACE_ID, enumdirection);
                        this.playSound(SoundEvents.SHULKER_TELEPORT, 1.0F, 1.0F);
                        this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.of(blockposition1));
                        this.entityData.set(Shulker.DATA_PEEK_ID, (byte) 0);
                        this.setTarget((LivingEntity) null);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return true;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.setDeltaMovement(Vec3.ZERO);
        if (!this.isNoAi()) {
            this.yBodyRotO = 0.0F;
            this.yBodyRot = 0.0F;
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (Shulker.DATA_ATTACH_POS_ID.equals(datawatcherobject) && this.level.isClientSide && !this.isPassenger()) {
            BlockPos blockposition = this.getAttachPosition();

            if (blockposition != null) {
                if (this.oldAttachPosition == null) {
                    this.oldAttachPosition = blockposition;
                } else {
                    this.clientSideTeleportInterpolation = 6;
                }

                this.setPosAndOldPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D);
                if (valid) ((ServerLevel) level).updateChunkPos(this); // CraftBukkit
            }
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isClosed()) {
            Entity entity = damagesource.getDirectEntity();

            if (entity instanceof AbstractArrow) {
                return false;
            }
        }

        if (super.hurt(damagesource, f)) {
            if ((double) this.getHealth() < (double) this.getMaxHealth() * 0.5D && this.random.nextInt(4) == 0) {
                this.teleportSomewhere();
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isClosed() {
        return this.getRawPeekAmount() == 0;
    }

    @Nullable
    @Override
    public AABB getCollideBox() {
        return this.isAlive() ? this.getBoundingBox() : null;
    }

    public Direction getAttachFace() {
        return (Direction) this.entityData.get(Shulker.DATA_ATTACH_FACE_ID);
    }

    @Nullable
    public BlockPos getAttachPosition() {
        return (BlockPos) ((Optional) this.entityData.get(Shulker.DATA_ATTACH_POS_ID)).orElse((Object) null);
    }

    public void setAttachPosition(@Nullable BlockPos blockposition) {
        this.entityData.set(Shulker.DATA_ATTACH_POS_ID, Optional.ofNullable(blockposition));
    }

    public int getRawPeekAmount() {
        return (Byte) this.entityData.get(Shulker.DATA_PEEK_ID);
    }

    public void setRawPeekAmount(int i) {
        if (!this.level.isClientSide) {
            this.getAttribute(Attributes.ARMOR).removeModifier(Shulker.COVERED_ARMOR_MODIFIER);
            if (i == 0) {
                this.getAttribute(Attributes.ARMOR).addPermanentModifier(Shulker.COVERED_ARMOR_MODIFIER);
                this.playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 1.0F);
            } else {
                this.playSound(SoundEvents.SHULKER_OPEN, 1.0F, 1.0F);
            }
        }

        this.entityData.set(Shulker.DATA_PEEK_ID, (byte) i);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.5F;
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public float getPickRadius() {
        return 0.0F;
    }

    static class ShulkerDefenseAttackGoal extends NearestAttackableTargetGoal<LivingEntity> {

        public ShulkerDefenseAttackGoal(Shulker entityshulker) {
            super(entityshulker, LivingEntity.class, 10, true, false, (entityliving) -> {
                return entityliving instanceof Enemy;
            });
        }

        @Override
        public boolean canUse() {
            return this.mob.getTeam() == null ? false : super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double d0) {
            Direction enumdirection = ((Shulker) this.mob).getAttachFace();

            return enumdirection.getAxis() == Direction.Axis.X ? this.mob.getBoundingBox().inflate(4.0D, d0, d0) : (enumdirection.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(d0, d0, 4.0D) : this.mob.getBoundingBox().inflate(d0, 4.0D, d0));
        }
    }

    class ShulkerNearestAttackGoal extends NearestAttackableTargetGoal<Player> {

        public ShulkerNearestAttackGoal(Shulker entityshulker) {
            super(entityshulker, Player.class, true);
        }

        @Override
        public boolean canUse() {
            return Shulker.this.level.getDifficulty() == Difficulty.PEACEFUL ? false : super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double d0) {
            Direction enumdirection = ((Shulker) this.mob).getAttachFace();

            return enumdirection.getAxis() == Direction.Axis.X ? this.mob.getBoundingBox().inflate(4.0D, d0, d0) : (enumdirection.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(d0, d0, 4.0D) : this.mob.getBoundingBox().inflate(d0, 4.0D, d0));
        }
    }

    class ShulkerAttackGoal extends Goal {

        private int attackTime;

        public ShulkerAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity entityliving = Shulker.this.getTarget();

            return entityliving != null && entityliving.isAlive() ? Shulker.this.level.getDifficulty() != Difficulty.PEACEFUL : false;
        }

        @Override
        public void start() {
            this.attackTime = 20;
            Shulker.this.setRawPeekAmount(100);
        }

        @Override
        public void stop() {
            Shulker.this.setRawPeekAmount(0);
        }

        @Override
        public void tick() {
            if (Shulker.this.level.getDifficulty() != Difficulty.PEACEFUL) {
                --this.attackTime;
                LivingEntity entityliving = Shulker.this.getTarget();

                Shulker.this.getControllerLook().setLookAt(entityliving, 180.0F, 180.0F);
                double d0 = Shulker.this.distanceToSqr((Entity) entityliving);

                if (d0 < 400.0D) {
                    if (this.attackTime <= 0) {
                        this.attackTime = 20 + Shulker.this.random.nextInt(10) * 20 / 2;
                        Shulker.this.level.addFreshEntity(new ShulkerBullet(Shulker.this.level, Shulker.this, entityliving, Shulker.this.getAttachFace().getAxis()));
                        Shulker.this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (Shulker.this.random.nextFloat() - Shulker.this.random.nextFloat()) * 0.2F + 1.0F);
                    }
                } else {
                    Shulker.this.setTarget((LivingEntity) null);
                }

                super.tick();
            }
        }
    }

    class ShulkerPeekGoal extends Goal {

        private int peekTime;

        private ShulkerPeekGoal() {}

        @Override
        public boolean canUse() {
            return Shulker.this.getTarget() == null && Shulker.this.random.nextInt(40) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return Shulker.this.getTarget() == null && this.peekTime > 0;
        }

        @Override
        public void start() {
            this.peekTime = 20 * (1 + Shulker.this.random.nextInt(3));
            Shulker.this.setRawPeekAmount(30);
        }

        @Override
        public void stop() {
            if (Shulker.this.getTarget() == null) {
                Shulker.this.setRawPeekAmount(0);
            }

        }

        @Override
        public void tick() {
            --this.peekTime;
        }
    }

    class ShulkerBodyRotationControl extends BodyRotationControl {

        public ShulkerBodyRotationControl(Mob entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void clientTick() {}
    }
}
