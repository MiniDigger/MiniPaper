package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.IntRange;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class EnderMan extends Monster implements NeutralMob {

    private static final UUID SPEED_MODIFIER_ATTACKING_UUID = UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A0");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(EnderMan.SPEED_MODIFIER_ATTACKING_UUID, "Attacking speed boost", 0.15000000596046448D, AttributeModifier.Operation.ADDITION);
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRY_STATE = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> DATA_CREEPY = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STARED_AT = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private static final Predicate<LivingEntity> ENDERMITE_SELECTOR = (entityliving) -> {
        return entityliving instanceof Endermite && ((Endermite) entityliving).isPlayerSpawned();
    };
    private int lastStareSound = Integer.MIN_VALUE;
    private int targetChangeTime;
    private static final IntRange PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;

    public EnderMan(EntityType<? extends EnderMan> entitytypes, Level world) {
        super(entitytypes, world);
        this.maxUpStep = 1.0F;
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EnderMan.EndermanFreezeWhenLookedAt(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D, 0.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new EnderMan.PathfinderGoalEndermanPlaceBlock(this));
        this.goalSelector.addGoal(11, new EnderMan.EndermanTakeBlockGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(2, new EnderMan.PathfinderGoalPlayerWhoLookedAtTarget(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Endermite.class, 10, true, false, EnderMan.ENDERMITE_SELECTOR));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    public static AttributeSupplier.Builder m() {
        return Monster.eS().a(Attributes.MAX_HEALTH, 40.0D).a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).a(Attributes.ATTACK_DAMAGE, 7.0D).a(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    public void setTarget(@Nullable LivingEntity entityliving) {
        // CraftBukkit start - fire event
        setGoalTarget(entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason.UNKNOWN, true);
    }

    @Override
    public boolean setGoalTarget(LivingEntity entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason reason, boolean fireEvent) {
        if (!super.setGoalTarget(entityliving, reason, fireEvent)) {
            return false;
        }
        entityliving = getTarget();
        // CraftBukkit end
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (entityliving == null) {
            this.targetChangeTime = 0;
            this.entityData.set(EnderMan.DATA_CREEPY, false);
            this.entityData.set(EnderMan.DATA_STARED_AT, false);
            attributemodifiable.removeModifier(EnderMan.SPEED_MODIFIER_ATTACKING);
        } else {
            this.targetChangeTime = this.tickCount;
            this.entityData.set(EnderMan.DATA_CREEPY, true);
            if (!attributemodifiable.hasModifier(EnderMan.SPEED_MODIFIER_ATTACKING)) {
                attributemodifiable.addTransientModifier(EnderMan.SPEED_MODIFIER_ATTACKING);
            }
        }
        return true;

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(EnderMan.DATA_CARRY_STATE, Optional.empty());
        this.entityData.register(EnderMan.DATA_CREEPY, false);
        this.entityData.register(EnderMan.DATA_STARED_AT, false);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(EnderMan.PERSISTENT_ANGER_TIME.randomValue(this.random));
    }

    @Override
    public void setRemainingPersistentAngerTime(int i) {
        this.remainingPersistentAngerTime = i;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void playStareSound() {
        if (this.tickCount >= this.lastStareSound + 400) {
            this.lastStareSound = this.tickCount;
            if (!this.isSilent()) {
                this.level.playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ENDERMAN_STARE, this.getSoundSource(), 2.5F, 1.0F, false);
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (EnderMan.DATA_CREEPY.equals(datawatcherobject) && this.hasBeenStaredAt() && this.level.isClientSide) {
            this.playStareSound();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        BlockState iblockdata = this.getCarried();

        if (iblockdata != null) {
            nbttagcompound.put("carriedBlockState", NbtUtils.writeBlockState(iblockdata));
        }

        this.addPersistentAngerSaveData(nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        BlockState iblockdata = null;

        if (nbttagcompound.contains("carriedBlockState", 10)) {
            iblockdata = NbtUtils.readBlockState(nbttagcompound.getCompound("carriedBlockState"));
            if (iblockdata.isAir()) {
                iblockdata = null;
            }
        }

        this.setCarried(iblockdata);
        this.readPersistentAngerSaveData((ServerLevel) this.level, nbttagcompound);
    }

    private boolean isLookingAtMe(Player entityhuman) {
        ItemStack itemstack = (ItemStack) entityhuman.inventory.armor.get(3);

        if (itemstack.getItem() == Blocks.CARVED_PUMPKIN.asItem()) {
            return false;
        } else {
            Vec3 vec3d = entityhuman.getViewVector(1.0F).normalize();
            Vec3 vec3d1 = new Vec3(this.getX() - entityhuman.getX(), this.getEyeY() - entityhuman.getEyeY(), this.getZ() - entityhuman.getZ());
            double d0 = vec3d1.length();

            vec3d1 = vec3d1.normalize();
            double d1 = vec3d.dot(vec3d1);

            return d1 > 1.0D - 0.025D / d0 ? entityhuman.canSee(this) : false;
        }
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 2.55F;
    }

    @Override
    public void aiStep() {
        if (this.level.isClientSide) {
            for (int i = 0; i < 2; ++i) {
                this.level.addParticle(ParticleTypes.PORTAL, this.getRandomX(0.5D), this.getRandomY() - 0.25D, this.getRandomZ(0.5D), (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
            }
        }

        this.jumping = false;
        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level, true);
        }

        super.aiStep();
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void customServerAiStep() {
        if (this.level.isDay() && this.tickCount >= this.targetChangeTime + 600) {
            float f = this.getBrightness();

            if (f > 0.5F && this.level.canSeeSky(this.blockPosition()) && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                this.setTarget((LivingEntity) null);
                this.teleport();
            }
        }

        super.customServerAiStep();
    }

    protected boolean teleport() {
        if (!this.level.isClientSide() && this.isAlive()) {
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * 64.0D;
            double d1 = this.getY() + (double) (this.random.nextInt(64) - 32);
            double d2 = this.getZ() + (this.random.nextDouble() - 0.5D) * 64.0D;

            return this.teleport(d0, d1, d2);
        } else {
            return false;
        }
    }

    private boolean teleportTowards(Entity entity) {
        Vec3 vec3d = new Vec3(this.getX() - entity.getX(), this.getY(0.5D) - entity.getEyeY(), this.getZ() - entity.getZ());

        vec3d = vec3d.normalize();
        double d0 = 16.0D;
        double d1 = this.getX() + (this.random.nextDouble() - 0.5D) * 8.0D - vec3d.x * 16.0D;
        double d2 = this.getY() + (double) (this.random.nextInt(16) - 8) - vec3d.y * 16.0D;
        double d3 = this.getZ() + (this.random.nextDouble() - 0.5D) * 8.0D - vec3d.z * 16.0D;

        return this.teleport(d1, d2, d3);
    }

    private boolean teleport(double d0, double d1, double d2) {
        BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition(d0, d1, d2);

        while (blockposition_mutableblockposition.getY() > 0 && !this.level.getType(blockposition_mutableblockposition).getMaterial().blocksMotion()) {
            blockposition_mutableblockposition.c(Direction.DOWN);
        }

        BlockState iblockdata = this.level.getType(blockposition_mutableblockposition);
        boolean flag = iblockdata.getMaterial().blocksMotion();
        boolean flag1 = iblockdata.getFluidState().is((Tag) FluidTags.WATER);

        if (flag && !flag1) {
            boolean flag2 = this.randomTeleport(d0, d1, d2, true);

            if (flag2 && !this.isSilent()) {
                this.level.playSound((Player) null, this.xo, this.yo, this.zo, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
            }

            return flag2;
        } else {
            return false;
        }
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isCreepy() ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damagesource, int i, boolean flag) {
        super.dropCustomDeathLoot(damagesource, i, flag);
        BlockState iblockdata = this.getCarried();

        if (iblockdata != null) {
            this.spawnAtLocation((ItemLike) iblockdata.getBlock());
        }

    }

    public void setCarried(@Nullable BlockState iblockdata) {
        this.entityData.set(EnderMan.DATA_CARRY_STATE, Optional.ofNullable(iblockdata));
    }

    @Nullable
    public BlockState getCarried() {
        return (BlockState) ((Optional) this.entityData.get(EnderMan.DATA_CARRY_STATE)).orElse((Object) null);
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else if (damagesource instanceof IndirectEntityDamageSource) {
            for (int i = 0; i < 64; ++i) {
                if (this.teleport()) {
                    return true;
                }
            }

            return false;
        } else {
            boolean flag = super.hurt(damagesource, f);

            if (!this.level.isClientSide() && this.random.nextInt(10) != 0) {
                this.teleport();
            }

            return flag;
        }
    }

    public boolean isCreepy() {
        return (Boolean) this.entityData.get(EnderMan.DATA_CREEPY);
    }

    public boolean hasBeenStaredAt() {
        return (Boolean) this.entityData.get(EnderMan.DATA_STARED_AT);
    }

    public void setBeingStaredAt() {
        this.entityData.set(EnderMan.DATA_STARED_AT, true);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCarried() != null;
    }

    static class EndermanTakeBlockGoal extends Goal {

        private final EnderMan enderman;

        public EndermanTakeBlockGoal(EnderMan entityenderman) {
            this.enderman = entityenderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarried() != null ? false : (!this.enderman.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? false : this.enderman.getRandom().nextInt(20) == 0);
        }

        @Override
        public void tick() {
            Random random = this.enderman.getRandom();
            Level world = this.enderman.level;
            int i = Mth.floor(this.enderman.getX() - 2.0D + random.nextDouble() * 4.0D);
            int j = Mth.floor(this.enderman.getY() + random.nextDouble() * 3.0D);
            int k = Mth.floor(this.enderman.getZ() - 2.0D + random.nextDouble() * 4.0D);
            BlockPos blockposition = new BlockPos(i, j, k);
            BlockState iblockdata = world.getType(blockposition);
            Block block = iblockdata.getBlock();
            Vec3 vec3d = new Vec3((double) Mth.floor(this.enderman.getX()) + 0.5D, (double) j + 0.5D, (double) Mth.floor(this.enderman.getZ()) + 0.5D);
            Vec3 vec3d1 = new Vec3((double) i + 0.5D, (double) j + 0.5D, (double) k + 0.5D);
            BlockHitResult movingobjectpositionblock = world.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.enderman));
            boolean flag = movingobjectpositionblock.getBlockPos().equals(blockposition);

            if (block.is((Tag) BlockTags.ENDERMAN_HOLDABLE) && flag) {
                // CraftBukkit start - Pickup event
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.enderman, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                    this.enderman.setCarried(iblockdata);
                    world.removeBlock(blockposition, false);
                }
                // CraftBukkit end
            }

        }
    }

    static class PathfinderGoalEndermanPlaceBlock extends Goal {

        private final EnderMan a;

        public PathfinderGoalEndermanPlaceBlock(EnderMan entityenderman) {
            this.a = entityenderman;
        }

        @Override
        public boolean canUse() {
            return this.a.getCarried() == null ? false : (!this.a.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? false : this.a.getRandom().nextInt(2000) == 0);
        }

        @Override
        public void tick() {
            Random random = this.a.getRandom();
            Level world = this.a.level;
            int i = Mth.floor(this.a.getX() - 1.0D + random.nextDouble() * 2.0D);
            int j = Mth.floor(this.a.getY() + random.nextDouble() * 2.0D);
            int k = Mth.floor(this.a.getZ() - 1.0D + random.nextDouble() * 2.0D);
            BlockPos blockposition = new BlockPos(i, j, k);
            BlockState iblockdata = world.getType(blockposition);
            BlockPos blockposition1 = blockposition.below();
            BlockState iblockdata1 = world.getType(blockposition1);
            BlockState iblockdata2 = this.a.getCarried();

            if (iblockdata2 != null && this.a(world, blockposition, iblockdata2, iblockdata, iblockdata1, blockposition1)) {
                // CraftBukkit start - Place event
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.a, blockposition, iblockdata2).isCancelled()) {
                world.setTypeAndData(blockposition, iblockdata2, 3);
                this.a.setCarried((BlockState) null);
                }
                // CraftBukkit end
            }

        }

        private boolean a(LevelReader iworldreader, BlockPos blockposition, BlockState iblockdata, BlockState iblockdata1, BlockState iblockdata2, BlockPos blockposition1) {
            return iblockdata1.isAir() && !iblockdata2.isAir() && iblockdata2.isCollisionShapeFullBlock(iworldreader, blockposition1) && iblockdata.canSurvive(iworldreader, blockposition);
        }
    }

    static class EndermanFreezeWhenLookedAt extends Goal {

        private final EnderMan enderman;
        private LivingEntity target;

        public EndermanFreezeWhenLookedAt(EnderMan entityenderman) {
            this.enderman = entityenderman;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.target = this.enderman.getTarget();
            if (!(this.target instanceof Player)) {
                return false;
            } else {
                double d0 = this.target.distanceToSqr((Entity) this.enderman);

                return d0 > 256.0D ? false : this.enderman.isLookingAtMe((Player) this.target);
            }
        }

        @Override
        public void start() {
            this.enderman.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.enderman.getControllerLook().setLookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
        }
    }

    static class PathfinderGoalPlayerWhoLookedAtTarget extends NearestAttackableTargetGoal<Player> {

        private final EnderMan i;
        private Player j;
        private int k;
        private int l;
        private final TargetingConditions m;
        private final TargetingConditions n = (new TargetingConditions()).allowUnseeable();

        public PathfinderGoalPlayerWhoLookedAtTarget(EnderMan entityenderman) {
            super(entityenderman, Player.class, false);
            this.i = entityenderman;
            this.m = (new TargetingConditions()).range(this.getFollowDistance()).selector((entityliving) -> {
                return entityenderman.isLookingAtMe((Player) entityliving);
            });
        }

        @Override
        public boolean canUse() {
            this.j = this.i.level.getNearestPlayer(this.m, (LivingEntity) this.i);
            return this.j != null;
        }

        @Override
        public void start() {
            this.k = 5;
            this.l = 0;
            this.i.setBeingStaredAt();
        }

        @Override
        public void stop() {
            this.j = null;
            super.stop();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.j != null) {
                if (!this.i.isLookingAtMe(this.j)) {
                    return false;
                } else {
                    this.i.lookAt((Entity) this.j, 10.0F, 10.0F);
                    return true;
                }
            } else {
                return this.target != null && this.n.test(this.i, this.target) ? true : super.canContinueToUse();
            }
        }

        @Override
        public void tick() {
            if (this.i.getTarget() == null) {
                super.setTarget((LivingEntity) null);
            }

            if (this.j != null) {
                if (--this.k <= 0) {
                    this.target = this.j;
                    this.j = null;
                    super.start();
                }
            } else {
                if (this.target != null && !this.i.isPassenger()) {
                    if (this.i.isLookingAtMe((Player) this.target)) {
                        if (this.target.distanceToSqr((Entity) this.i) < 16.0D) {
                            this.i.teleport();
                        }

                        this.l = 0;
                    } else if (this.target.distanceToSqr((Entity) this.i) > 256.0D && this.l++ >= 30 && this.i.teleportTowards((Entity) this.target)) {
                        this.l = 0;
                    }
                }

                super.tick();
            }

        }
    }
}
