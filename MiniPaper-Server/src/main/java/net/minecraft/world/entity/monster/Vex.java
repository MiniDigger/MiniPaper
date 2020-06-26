package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityTargetEvent;

public class Vex extends Monster {

    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Vex.class, EntityDataSerializers.BYTE);
    private Mob owner;
    @Nullable
    private BlockPos boundOrigin;
    private boolean hasLimitedLife;
    private int limitedLifeTicks;

    public Vex(EntityType<? extends Vex> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveControl = new Vex.VexMoveControl(this);
        this.xpReward = 3;
    }

    @Override
    public void move(MoverType enummovetype, Vec3 vec3d) {
        super.move(enummovetype, vec3d);
        this.checkInsideBlocks();
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.hasLimitedLife && --this.limitedLifeTicks <= 0) {
            this.limitedLifeTicks = 20;
            this.hurt(DamageSource.STARVE, 1.0F);
        }

    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new Vex.VexChargeAttackGoal());
        this.goalSelector.addGoal(8, new Vex.VexRandomMoveGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{Raider.class})).setAlertOthers(new Class[0])); // CraftBukkit - decompile error
        this.targetSelector.addGoal(2, new Vex.VexCopyOwnerTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder m() {
        return Monster.eS().a(Attributes.MAX_HEALTH, 14.0D).a(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Vex.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("BoundX")) {
            this.boundOrigin = new BlockPos(nbttagcompound.getInt("BoundX"), nbttagcompound.getInt("BoundY"), nbttagcompound.getInt("BoundZ"));
        }

        if (nbttagcompound.contains("LifeTicks")) {
            this.setLimitedLife(nbttagcompound.getInt("LifeTicks"));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (this.boundOrigin != null) {
            nbttagcompound.putInt("BoundX", this.boundOrigin.getX());
            nbttagcompound.putInt("BoundY", this.boundOrigin.getY());
            nbttagcompound.putInt("BoundZ", this.boundOrigin.getZ());
        }

        if (this.hasLimitedLife) {
            nbttagcompound.putInt("LifeTicks", this.limitedLifeTicks);
        }

    }

    public Mob getOwner() {
        return this.owner;
    }

    @Nullable
    public BlockPos getBoundOrigin() {
        return this.boundOrigin;
    }

    public void setBoundOrigin(@Nullable BlockPos blockposition) {
        this.boundOrigin = blockposition;
    }

    private boolean getVexFlag(int i) {
        byte b0 = (Byte) this.entityData.get(Vex.DATA_FLAGS_ID);

        return (b0 & i) != 0;
    }

    private void setVexFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(Vex.DATA_FLAGS_ID);
        int j;

        if (flag) {
            j = b0 | i;
        } else {
            j = b0 & ~i;
        }

        this.entityData.set(Vex.DATA_FLAGS_ID, (byte) (j & 255));
    }

    public boolean isCharging() {
        return this.getVexFlag(1);
    }

    public void setIsCharging(boolean flag) {
        this.setVexFlag(1, flag);
    }

    public void setOwner(Mob entityinsentient) {
        this.owner = entityinsentient;
    }

    public void setLimitedLife(int i) {
        this.hasLimitedLife = true;
        this.limitedLifeTicks = i;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    public float getBrightness() {
        return 1.0F;
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.populateDefaultEquipmentSlots(difficultydamagescaler);
        this.populateDefaultEquipmentEnchantments(difficultydamagescaler);
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    @Override
    protected void populateDefaultEquipmentSlots(DifficultyInstance difficultydamagescaler) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    class VexCopyOwnerTargetGoal extends TargetGoal {

        private final TargetingConditions copyOwnerTargeting = (new TargetingConditions()).allowUnseeable().ignoreInvisibilityTesting();

        public VexCopyOwnerTargetGoal(PathfinderMob entitycreature) {
            super(entitycreature, false);
        }

        @Override
        public boolean canUse() {
            return Vex.this.owner != null && Vex.this.owner.getTarget() != null && this.canAttack(Vex.this.owner.getTarget(), this.copyOwnerTargeting);
        }

        @Override
        public void start() {
            Vex.this.setGoalTarget(Vex.this.owner.getTarget(), EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET, true); // CraftBukkit
            super.start();
        }
    }

    class VexRandomMoveGoal extends Goal {

        public VexRandomMoveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !Vex.this.getMoveControl().hasWanted() && Vex.this.random.nextInt(7) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos blockposition = Vex.this.getBoundOrigin();

            if (blockposition == null) {
                blockposition = Vex.this.blockPosition();
            }

            for (int i = 0; i < 3; ++i) {
                BlockPos blockposition1 = blockposition.offset(Vex.this.random.nextInt(15) - 7, Vex.this.random.nextInt(11) - 5, Vex.this.random.nextInt(15) - 7);

                if (Vex.this.level.isEmptyBlock(blockposition1)) {
                    Vex.this.moveControl.setWantedPosition((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.5D, (double) blockposition1.getZ() + 0.5D, 0.25D);
                    if (Vex.this.getTarget() == null) {
                        Vex.this.getControllerLook().setLookAt((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.5D, (double) blockposition1.getZ() + 0.5D, 180.0F, 20.0F);
                    }
                    break;
                }
            }

        }
    }

    class VexChargeAttackGoal extends Goal {

        public VexChargeAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Vex.this.getTarget() != null && !Vex.this.getMoveControl().hasWanted() && Vex.this.random.nextInt(7) == 0 ? Vex.this.distanceToSqr((Entity) Vex.this.getTarget()) > 4.0D : false;
        }

        @Override
        public boolean canContinueToUse() {
            return Vex.this.getMoveControl().hasWanted() && Vex.this.isCharging() && Vex.this.getTarget() != null && Vex.this.getTarget().isAlive();
        }

        @Override
        public void start() {
            LivingEntity entityliving = Vex.this.getTarget();
            Vec3 vec3d = entityliving.getEyePosition(1.0F);

            Vex.this.moveControl.setWantedPosition(vec3d.x, vec3d.y, vec3d.z, 1.0D);
            Vex.this.setIsCharging(true);
            Vex.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            Vex.this.setIsCharging(false);
        }

        @Override
        public void tick() {
            LivingEntity entityliving = Vex.this.getTarget();

            if (Vex.this.getBoundingBox().intersects(entityliving.getBoundingBox())) {
                Vex.this.doHurtTarget(entityliving);
                Vex.this.setIsCharging(false);
            } else {
                double d0 = Vex.this.distanceToSqr((Entity) entityliving);

                if (d0 < 9.0D) {
                    Vec3 vec3d = entityliving.getEyePosition(1.0F);

                    Vex.this.moveControl.setWantedPosition(vec3d.x, vec3d.y, vec3d.z, 1.0D);
                }
            }

        }
    }

    class VexMoveControl extends MoveControl {

        public VexMoveControl(Vex entityvex) {
            super(entityvex);
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                Vec3 vec3d = new Vec3(this.wantedX - Vex.this.getX(), this.wantedY - Vex.this.getY(), this.wantedZ - Vex.this.getZ());
                double d0 = vec3d.length();

                if (d0 < Vex.this.getBoundingBox().getSize()) {
                    this.operation = MoveControl.Operation.WAIT;
                    Vex.this.setDeltaMovement(Vex.this.getDeltaMovement().scale(0.5D));
                } else {
                    Vex.this.setDeltaMovement(Vex.this.getDeltaMovement().add(vec3d.scale(this.speedModifier * 0.05D / d0)));
                    if (Vex.this.getTarget() == null) {
                        Vec3 vec3d1 = Vex.this.getDeltaMovement();

                        Vex.this.yRot = -((float) Mth.atan2(vec3d1.x, vec3d1.z)) * 57.295776F;
                        Vex.this.yBodyRot = Vex.this.yRot;
                    } else {
                        double d1 = Vex.this.getTarget().getX() - Vex.this.getX();
                        double d2 = Vex.this.getTarget().getZ() - Vex.this.getZ();

                        Vex.this.yRot = -((float) Mth.atan2(d1, d2)) * 57.295776F;
                        Vex.this.yBodyRot = Vex.this.yRot;
                    }
                }

            }
        }
    }
}
