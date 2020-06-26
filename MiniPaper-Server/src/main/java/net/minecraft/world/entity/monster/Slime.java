package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
<<<<<<< HEAD
=======
// Paper start
import com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent;
import com.destroystokyo.paper.event.entity.SlimeSwimEvent;
import com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent;
import com.destroystokyo.paper.event.entity.SlimeWanderEvent;
import org.bukkit.entity.LivingEntity;
// Paper end
>>>>>>> Toothpick
// CraftBukkit start
import java.util.ArrayList;
import java.util.List;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
// CraftBukkit end

public class Slime extends Mob implements Enemy {

    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Slime.class, EntityDataSerializers.INT);
    public float targetSquish;
    public float squish;
    public float oSquish;
    private boolean wasOnGround;

    public Slime(EntityType<? extends Slime> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveControl = new Slime.ControllerMoveSlime(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Slime.PathfinderGoalSlimeRandomJump(this));
        this.goalSelector.addGoal(2, new Slime.PathfinderGoalSlimeNearestPlayer(this));
        this.goalSelector.addGoal(3, new Slime.PathfinderGoalSlimeRandomDirection(this));
        this.goalSelector.addGoal(5, new Slime.PathfinderGoalSlimeIdle(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entityliving) -> {
            return Math.abs(entityliving.getY() - this.getY()) <= 4.0D;
        }));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Slime.ID_SIZE, 1);
    }

    public void setSize(int i, boolean flag) {
        this.entityData.set(Slime.ID_SIZE, i);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double) (i * i));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) (0.2F + 0.1F * (float) i));
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double) i);
        if (flag) {
            this.setHealth(this.getMaxHealth());
        }

        this.xpReward = i;
    }

    public int getSize() {
        return (Integer) this.entityData.get(Slime.ID_SIZE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Size", this.getSize() - 1);
        nbttagcompound.putBoolean("wasOnGround", this.wasOnGround);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        int i = nbttagcompound.getInt("Size");

        if (i < 0) {
            i = 0;
        }

        this.setSize(i + 1, false);
        super.readAdditionalSaveData(nbttagcompound);
        this.wasOnGround = nbttagcompound.getBoolean("wasOnGround");
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected ParticleOptions getParticleType() {
        return ParticleTypes.ITEM_SLIME;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return this.getSize() > 0;
    }

    @Override
    public void tick() {
        this.squish += (this.targetSquish - this.squish) * 0.5F;
        this.oSquish = this.squish;
        super.tick();
        if (this.onGround && !this.wasOnGround) {
            int i = this.getSize();

            for (int j = 0; j < i * 8; ++j) {
                float f = this.random.nextFloat() * 6.2831855F;
                float f1 = this.random.nextFloat() * 0.5F + 0.5F;
                float f2 = Mth.sin(f) * (float) i * 0.5F * f1;
                float f3 = Mth.cos(f) * (float) i * 0.5F * f1;

                this.level.addParticle(this.getParticleType(), this.getX() + (double) f2, this.getY(), this.getZ() + (double) f3, 0.0D, 0.0D, 0.0D);
            }

            this.playSound(this.getSoundSquish(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            this.targetSquish = -0.5F;
        } else if (!this.onGround && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }

        this.wasOnGround = this.onGround;
        this.decreaseSquish();
    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (Slime.ID_SIZE.equals(datawatcherobject)) {
            this.refreshDimensions();
            this.yRot = this.yHeadRot;
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public EntityType<? extends Slime> getType() {
        return (EntityType<? extends Slime>) super.getType(); // CraftBukkit - decompile error
    }

    @Override
    public void remove() {
        int i = this.getSize();

        if (!this.level.isClientSide && i > 1 && this.isDeadOrDying()) {
            Component ichatbasecomponent = this.getCustomName();
            boolean flag = this.isNoAi();
            float f = (float) i / 4.0F;
            int j = i / 2;
            int k = 2 + this.random.nextInt(3);

            // CraftBukkit start
            SlimeSplitEvent event = new SlimeSplitEvent((org.bukkit.entity.Slime) this.getBukkitEntity(), k);
            this.level.getServerOH().getPluginManager().callEvent(event);

            if (!event.isCancelled() && event.getCount() > 0) {
                k = event.getCount();
            } else {
                super.remove();
                return;
            }
            List<LivingEntity> slimes = new ArrayList<>(j);
            // CraftBukkit end

            for (int l = 0; l < k; ++l) {
                float f1 = ((float) (l % 2) - 0.5F) * f;
                float f2 = ((float) (l / 2) - 0.5F) * f;
                Slime entityslime = (Slime) this.getType().create(this.level);

                if (this.isPersistenceRequired()) {
                    entityslime.setPersistenceRequired();
                }

                entityslime.setCustomName(ichatbasecomponent);
                entityslime.setNoAi(flag);
                entityslime.setInvulnerable(this.isInvulnerable());
                entityslime.setSize(j, true);
                entityslime.moveTo(this.getX() + (double) f1, this.getY() + 0.5D, this.getZ() + (double) f2, this.random.nextFloat() * 360.0F, 0.0F);
                slimes.add(entityslime); // CraftBukkit
            }

            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, slimes, EntityTransformEvent.TransformReason.SPLIT).isCancelled()) {
                return;
            }
            for (LivingEntity living : slimes) {
                this.level.addEntity(living, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT); // CraftBukkit - SpawnReason
            }
            // CraftBukkit end
        }

        super.remove();
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem && this.isDealsDamage()) {
            this.dealDamage((LivingEntity) entity);
        }

    }

    @Override
    public void playerTouch(Player entityhuman) {
        if (this.isDealsDamage()) {
            this.dealDamage((LivingEntity) entityhuman);
        }

    }

    protected void dealDamage(LivingEntity entityliving) {
        if (this.isAlive()) {
            int i = this.getSize();

            if (this.distanceToSqr((Entity) entityliving) < 0.6D * (double) i * 0.6D * (double) i && this.canSee(entityliving) && entityliving.hurt(DamageSource.mobAttack(this), this.getAttackDamage())) {
                this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.doEnchantDamageEffects((LivingEntity) this, (Entity) entityliving);
            }
        }

    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.625F * entitysize.height;
    }

    protected boolean isDealsDamage() {
        return !this.isTiny() && this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return this.isTiny() ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return this.isTiny() ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
    }

    protected SoundEvent getSoundSquish() {
        return this.isTiny() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH;
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return this.getSize() == 1 ? this.getType().getDefaultLootTable() : BuiltInLootTables.EMPTY;
    }

    public static boolean checkSlimeSpawnRules(EntityType<Slime> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        if (generatoraccess.getDifficulty() != Difficulty.PEACEFUL) {
            Biome biomebase = generatoraccess.getBiome(blockposition);

            if (biomebase == Biomes.SWAMP && blockposition.getY() > 50 && blockposition.getY() < 70 && random.nextFloat() < 0.5F && random.nextFloat() < generatoraccess.getMoonBrightness() && generatoraccess.getMaxLocalRawBrightness(blockposition) <= random.nextInt(8)) {
                return checkMobSpawnRules(entitytypes, generatoraccess, enummobspawn, blockposition, random);
            }

            if (!(generatoraccess instanceof WorldGenLevel)) {
                return false;
            }

            ChunkPos chunkcoordintpair = new ChunkPos(blockposition);
            boolean flag = WorldgenRandom.seedSlimeChunk(chunkcoordintpair.x, chunkcoordintpair.z, ((WorldGenLevel) generatoraccess).getSeed(), generatoraccess.getLevel().spigotConfig.slimeSeed).nextInt(10) == 0; // Spigot

            if (random.nextInt(10) == 0 && flag && blockposition.getY() < 40) {
                return checkMobSpawnRules(entitytypes, generatoraccess, enummobspawn, blockposition, random);
            }
        }

        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F * (float) this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Override
    protected void jumpFromGround() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x, (double) this.getJumpPower(), vec3d.z);
        this.hasImpulse = true;
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        int i = this.random.nextInt(3);

        if (i < 2 && this.random.nextFloat() < 0.5F * difficultydamagescaler.getSpecialMultiplier()) {
            ++i;
        }

        int j = 1 << i;

        this.setSize(j, true);
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
    }

    private float getSoundPitch() {
        float f = this.isTiny() ? 1.4F : 0.8F;

        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * f;
    }

    protected SoundEvent getSoundJump() {
        return this.isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    @Override
    public EntityDimensions getDimensions(Pose entitypose) {
        return super.getDimensions(entitypose).scale(0.255F * (float) this.getSize());
    }

    static class PathfinderGoalSlimeIdle extends Goal {

        private final Slime a;

        public PathfinderGoalSlimeIdle(Slime entityslime) {
            this.a = entityslime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
<<<<<<< HEAD
            return !this.a.isPassenger();
=======
            return !this.a.isPassenger() && this.a.canWander && new SlimeWanderEvent((org.bukkit.entity.Slime) this.a.getBukkitEntity()).callEvent(); // Paper
>>>>>>> Toothpick
        }

        @Override
        public void tick() {
            ((Slime.ControllerMoveSlime) this.a.getMoveControl()).a(1.0D);
        }
    }

    static class PathfinderGoalSlimeRandomJump extends Goal {

        private final Slime a;

        public PathfinderGoalSlimeRandomJump(Slime entityslime) {
            this.a = entityslime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
            entityslime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
<<<<<<< HEAD
            return (this.a.isInWater() || this.a.isInLava()) && this.a.getMoveControl() instanceof Slime.ControllerMoveSlime;
=======
            return (this.a.isInWater() || this.a.isInLava()) && this.a.getMoveControl() instanceof net.minecraft.world.entity.monster.Slime.ControllerMoveSlime && this.a.canWander && new SlimeSwimEvent((org.bukkit.entity.Slime) this.a.getBukkitEntity()).callEvent(); // Paper
>>>>>>> Toothpick
        }

        @Override
        public void tick() {
            if (this.a.getRandom().nextFloat() < 0.8F) {
                this.a.getJumpControl().jump();
            }

            ((Slime.ControllerMoveSlime) this.a.getMoveControl()).a(1.2D);
        }
    }

    static class PathfinderGoalSlimeRandomDirection extends Goal {

        private final Slime a;
        private float b;
        private int c;

        public PathfinderGoalSlimeRandomDirection(Slime entityslime) {
            this.a = entityslime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.a.getTarget() == null && (this.a.onGround || this.a.isInWater() || this.a.isInLava() || this.a.hasEffect(MobEffects.LEVITATION)) && this.a.getMoveControl() instanceof Slime.ControllerMoveSlime;
        }

        @Override
        public void tick() {
            if (--this.c <= 0) {
                this.c = 40 + this.a.getRandom().nextInt(60);
<<<<<<< HEAD
                this.b = (float) this.a.getRandom().nextInt(360);
=======
                // Paper start
                SlimeChangeDirectionEvent event = new SlimeChangeDirectionEvent((org.bukkit.entity.Slime) this.a.getBukkitEntity(), (float) this.a.getRandom().nextInt(360));
                if (!this.a.canWander || !event.callEvent()) return;
                this.b = event.getNewYaw();
                // Paper end
>>>>>>> Toothpick
            }

            ((Slime.ControllerMoveSlime) this.a.getMoveControl()).a(this.b, false);
        }
    }

    static class PathfinderGoalSlimeNearestPlayer extends Goal {

        private final Slime a;
        private int b;

        public PathfinderGoalSlimeNearestPlayer(Slime entityslime) {
            this.a = entityslime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity entityliving = this.a.getTarget();

<<<<<<< HEAD
            return entityliving == null ? false : (!entityliving.isAlive() ? false : (entityliving instanceof Player && ((Player) entityliving).abilities.invulnerable ? false : this.a.getMoveControl() instanceof Slime.ControllerMoveSlime));
=======
            // Paper start
            if (entityliving == null || !entityliving.isAlive()) {
                return false;
            }
            if (entityliving instanceof Player && ((Player) entityliving).abilities.invulnerable) {
                return false;
            }
            return this.a.getMoveControl() instanceof net.minecraft.world.entity.monster.Slime.ControllerMoveSlime && this.a.canWander && new SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.a.getBukkitEntity(), (LivingEntity) entityliving.getBukkitEntity()).callEvent();
            // Paper end
>>>>>>> Toothpick
        }

        @Override
        public void start() {
            this.b = 300;
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = this.a.getTarget();

<<<<<<< HEAD
            return entityliving == null ? false : (!entityliving.isAlive() ? false : (entityliving instanceof Player && ((Player) entityliving).abilities.invulnerable ? false : --this.b > 0));
=======
            // Paper start
            if (entityliving == null || !entityliving.isAlive()) {
                return false;
            }
            if (entityliving instanceof Player && ((Player) entityliving).abilities.invulnerable) {
                return false;
            }
            return --this.b > 0 && this.a.canWander && new SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.a.getBukkitEntity(), (LivingEntity) entityliving.getBukkitEntity()).callEvent();
            // Paper end
>>>>>>> Toothpick
        }

        @Override
        public void tick() {
            this.a.lookAt((Entity) this.a.getTarget(), 10.0F, 10.0F);
            ((Slime.ControllerMoveSlime) this.a.getMoveControl()).a(this.a.yRot, this.a.isDealsDamage());
        }
    }

    static class ControllerMoveSlime extends MoveControl {

        private float i;
        private int j;
        private final Slime k;
        private boolean l;

        public ControllerMoveSlime(Slime entityslime) {
            super(entityslime);
            this.k = entityslime;
            this.i = 180.0F * entityslime.yRot / 3.1415927F;
        }

        public void a(float f, boolean flag) {
            this.i = f;
            this.l = flag;
        }

        public void a(double d0) {
            this.speedModifier = d0;
            this.operation = MoveControl.Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            this.mob.yRot = this.rotlerp(this.mob.yRot, this.i, 90.0F);
            this.mob.yHeadRot = this.mob.yRot;
            this.mob.yBodyRot = this.mob.yRot;
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                this.mob.setZza(0.0F);
            } else {
                this.operation = MoveControl.Operation.WAIT;
                if (this.mob.isOnGround()) {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                    if (this.j-- <= 0) {
                        this.j = this.k.getJumpDelay();
                        if (this.l) {
                            this.j /= 3;
                        }

                        this.k.getJumpControl().jump();
                        if (this.k.doPlayJumpSound()) {
                            this.k.playSound(this.k.getSoundJump(), this.k.getSoundVolume(), this.k.getSoundPitch());
                        }
                    } else {
                        this.k.xxa = 0.0F;
                        this.k.zza = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                }

            }
        }
    }
}
