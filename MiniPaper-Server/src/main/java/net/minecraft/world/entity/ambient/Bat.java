package net.minecraft.world.entity.ambient;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class Bat extends AmbientCreature {

    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Bat.class, EntityDataSerializers.BYTE);
    private static final TargetingConditions BAT_RESTING_TARGETING = (new TargetingConditions()).range(4.0D).allowSameTeam();
    private BlockPos targetPosition;

    public Bat(EntityType<? extends Bat> entitytypes, Level world) {
        super(entitytypes, world);
        this.setResting(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Bat.DATA_ID_FLAGS, (byte) 0);
    }

    @Override
    protected float getSoundVolume() {
        return 0.1F;
    }

    @Override
    protected float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Nullable
    @Override
    public SoundEvent getSoundAmbient() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    public static AttributeSupplier.Builder m() {
        return Mob.p().a(Attributes.MAX_HEALTH, 6.0D);
    }

    public boolean isResting() {
        return ((Byte) this.entityData.get(Bat.DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Bat.DATA_ID_FLAGS);

        if (flag) {
            this.entityData.set(Bat.DATA_ID_FLAGS, (byte) (b0 | 1));
        } else {
            this.entityData.set(Bat.DATA_ID_FLAGS, (byte) (b0 & -2));
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPosRaw(this.getX(), (double) Mth.floor(this.getY()) + 1.0D - (double) this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D));
        }

    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BlockPos blockposition = this.blockPosition();
        BlockPos blockposition1 = blockposition.above();

        if (this.isResting()) {
            boolean flag = this.isSilent();

            if (this.level.getType(blockposition1).isRedstoneConductor(this.level, blockposition)) {
                if (this.random.nextInt(200) == 0) {
                    this.yHeadRot = (float) this.random.nextInt(360);
                }

                if (this.level.getNearestPlayer(Bat.BAT_RESTING_TARGETING, (LivingEntity) this) != null) {
                    // CraftBukkit Start - Call BatToggleSleepEvent
                    if (CraftEventFactory.handleBatToggleSleepEvent(this, true)) {
                        this.setResting(false);
                        if (!flag) {
                            this.level.levelEvent((Player) null, 1025, blockposition, 0);
                        }
                    }
                    // CraftBukkit End
                }
            } else {
                // CraftBukkit Start - Call BatToggleSleepEvent
                if (CraftEventFactory.handleBatToggleSleepEvent(this, true)) {
                    this.setResting(false);
                    if (!flag) {
                        this.level.levelEvent((Player) null, 1025, blockposition, 0);
                    }
                }
                // CraftBukkit End - Call BatToggleSleepEvent
            }
        } else {
            if (this.targetPosition != null && (!this.level.isEmptyBlock(this.targetPosition) || this.targetPosition.getY() < 1)) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerThan((Position) this.position(), 2.0D)) {
                this.targetPosition = new BlockPos(this.getX() + (double) this.random.nextInt(7) - (double) this.random.nextInt(7), this.getY() + (double) this.random.nextInt(6) - 2.0D, this.getZ() + (double) this.random.nextInt(7) - (double) this.random.nextInt(7));
            }

            double d0 = (double) this.targetPosition.getX() + 0.5D - this.getX();
            double d1 = (double) this.targetPosition.getY() + 0.1D - this.getY();
            double d2 = (double) this.targetPosition.getZ() + 0.5D - this.getZ();
            Vec3 vec3d = this.getDeltaMovement();
            Vec3 vec3d1 = vec3d.add((Math.signum(d0) * 0.5D - vec3d.x) * 0.10000000149011612D, (Math.signum(d1) * 0.699999988079071D - vec3d.y) * 0.10000000149011612D, (Math.signum(d2) * 0.5D - vec3d.z) * 0.10000000149011612D);

            this.setDeltaMovement(vec3d1);
            float f = (float) (Mth.atan2(vec3d1.z, vec3d1.x) * 57.2957763671875D) - 90.0F;
            float f1 = Mth.wrapDegrees(f - this.yRot);

            this.zza = 0.5F;
            this.yRot += f1;
            if (this.random.nextInt(100) == 0 && this.level.getType(blockposition1).isRedstoneConductor(this.level, blockposition1)) {
                // CraftBukkit Start - Call BatToggleSleepEvent
                if (CraftEventFactory.handleBatToggleSleepEvent(this, false)) {
                    this.setResting(true);
                }
                // CraftBukkit End
            }
        }

    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        return false;
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, BlockState iblockdata, BlockPos blockposition) {}

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            if (!this.level.isClientSide && this.isResting()) {
                // CraftBukkit Start - Call BatToggleSleepEvent
                if (CraftEventFactory.handleBatToggleSleepEvent(this, true)) {
                    this.setResting(false);
                }
                // CraftBukkit End - Call BatToggleSleepEvent
            }

            return super.hurt(damagesource, f);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.entityData.set(Bat.DATA_ID_FLAGS, nbttagcompound.getByte("BatFlags"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putByte("BatFlags", (Byte) this.entityData.get(Bat.DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(EntityType<Bat> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        if (blockposition.getY() >= generatoraccess.getSeaLevel()) {
            return false;
        } else {
            int i = generatoraccess.getMaxLocalRawBrightness(blockposition);
            byte b0 = 4;

            if (isHalloween()) {
                b0 = 7;
            } else if (random.nextBoolean()) {
                return false;
            }

            return i > random.nextInt(b0) ? false : checkMobSpawnRules(entitytypes, generatoraccess, enummobspawn, blockposition, random);
        }
    }

    private static boolean isHalloween() {
        LocalDate localdate = LocalDate.now();
        int i = localdate.get(ChronoField.DAY_OF_MONTH);
        int j = localdate.get(ChronoField.MONTH_OF_YEAR);

        return j == 10 && i >= 20 || j == 11 && i <= 3;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height / 2.0F;
    }
}
