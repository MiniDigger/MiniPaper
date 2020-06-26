package net.minecraft.world.entity.animal;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.IntRange;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class IronGolem extends AbstractGolem implements NeutralMob {

    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(IronGolem.class, EntityDataSerializers.BYTE);
    private int attackAnimationTick;
    private int offerFlowerTick;
    private static final IntRange PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;

    public IronGolem(EntityType<? extends IronGolem> entitytypes, Level world) {
        super(entitytypes, world);
        this.maxUpStep = 1.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9D, 32.0F));
        this.goalSelector.addGoal(2, new MoveBackToVillageGoal(this, 0.6D, false));
        this.goalSelector.addGoal(4, new GolemRandomStrollInVillageGoal(this, 0.6D));
        this.goalSelector.addGoal(5, new OfferFlowerGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new DefendVillageTargetGoal(this));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false, (entityliving) -> {
            return entityliving instanceof Enemy && !(entityliving instanceof Creeper);
        }));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(IronGolem.DATA_FLAGS_ID, (byte) 0);
    }

    public static AttributeSupplier.Builder m() {
        return Mob.p().a(Attributes.MAX_HEALTH, 100.0D).a(Attributes.MOVEMENT_SPEED, 0.25D).a(Attributes.KNOCKBACK_RESISTANCE, 1.0D).a(Attributes.ATTACK_DAMAGE, 15.0D);
    }

    @Override
    protected int decreaseAirSupply(int i) {
        return i;
    }

    @Override
    protected void doPush(Entity entity) {
        if (entity instanceof Enemy && !(entity instanceof Creeper) && this.getRandom().nextInt(20) == 0) {
            this.setGoalTarget((LivingEntity) entity, org.bukkit.event.entity.EntityTargetLivingEntityEvent.TargetReason.COLLISION, true); // CraftBukkit - set reason
        }

        super.doPush(entity);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.attackAnimationTick > 0) {
            --this.attackAnimationTick;
        }

        if (this.offerFlowerTick > 0) {
            --this.offerFlowerTick;
        }

        if (getHorizontalDistanceSqr(this.getDeltaMovement()) > 2.500000277905201E-7D && this.random.nextInt(5) == 0) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - 0.20000000298023224D);
            int k = Mth.floor(this.getZ());
            BlockState iblockdata = this.level.getType(new BlockPos(i, j, k));

            if (!iblockdata.isAir()) {
                this.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), this.getX() + ((double) this.random.nextFloat() - 0.5D) * (double) this.getBbWidth(), this.getY() + 0.1D, this.getZ() + ((double) this.random.nextFloat() - 0.5D) * (double) this.getBbWidth(), 4.0D * ((double) this.random.nextFloat() - 0.5D), 0.5D, ((double) this.random.nextFloat() - 0.5D) * 4.0D);
            }
        }

        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level, true);
        }

    }

    @Override
    public boolean canAttackType(EntityType<?> entitytypes) {
        return this.isPlayerCreated() && entitytypes == EntityType.PLAYER ? false : (entitytypes == EntityType.CREEPER ? false : super.canAttackType(entitytypes));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("PlayerCreated", this.isPlayerCreated());
        this.addPersistentAngerSaveData(nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setPlayerCreated(nbttagcompound.getBoolean("PlayerCreated"));
        this.readPersistentAngerSaveData((ServerLevel) this.level, nbttagcompound);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(IronGolem.PERSISTENT_ANGER_TIME.randomValue(this.random));
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

    private float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        this.attackAnimationTick = 10;
        this.level.broadcastEntityEvent(this, (byte) 4);
        float f = this.getAttackDamage();
        float f1 = (int) f > 0 ? f / 2.0F + (float) this.random.nextInt((int) f) : f;
        boolean flag = entity.hurt(DamageSource.mobAttack(this), f1);

        if (flag) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(0.0D, 0.4000000059604645D, 0.0D));
            this.doEnchantDamageEffects((LivingEntity) this, entity);
        }

        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F);
        return flag;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        IronGolem.Crackiness entityirongolem_cracklevel = this.getCrackiness();
        boolean flag = super.hurt(damagesource, f);

        if (flag && this.getCrackiness() != entityirongolem_cracklevel) {
            this.playSound(SoundEvents.IRON_GOLEM_DAMAGE, 1.0F, 1.0F);
        }

        return flag;
    }

    public IronGolem.Crackiness getCrackiness() {
        return IronGolem.Crackiness.byFraction(this.getHealth() / this.getMaxHealth());
    }

    public void offerFlower(boolean flag) {
        if (flag) {
            this.offerFlowerTick = 400;
            this.level.broadcastEntityEvent(this, (byte) 11);
        } else {
            this.offerFlowerTick = 0;
            this.level.broadcastEntityEvent(this, (byte) 34);
        }

    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        Item item = itemstack.getItem();

        if (item != Items.IRON_INGOT) {
            return InteractionResult.PASS;
        } else {
            float f = this.getHealth();

            this.heal(25.0F);
            if (this.getHealth() == f) {
                return InteractionResult.PASS;
            } else {
                float f1 = 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;

                this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, f1);
                if (!entityhuman.abilities.instabuild) {
                    itemstack.shrink(1);
                }

                return InteractionResult.sidedSuccess(this.level.isClientSide);
            }
        }
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 1.0F);
    }

    public boolean isPlayerCreated() {
        return ((Byte) this.entityData.get(IronGolem.DATA_FLAGS_ID) & 1) != 0;
    }

    public void setPlayerCreated(boolean flag) {
        byte b0 = (Byte) this.entityData.get(IronGolem.DATA_FLAGS_ID);

        if (flag) {
            this.entityData.set(IronGolem.DATA_FLAGS_ID, (byte) (b0 | 1));
        } else {
            this.entityData.set(IronGolem.DATA_FLAGS_ID, (byte) (b0 & -2));
        }

    }

    @Override
    public void die(DamageSource damagesource) {
        super.die(damagesource);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader iworldreader) {
        BlockPos blockposition = this.blockPosition();
        BlockPos blockposition1 = blockposition.below();
        BlockState iblockdata = iworldreader.getType(blockposition1);

        if (!iblockdata.entityCanStandOn((BlockGetter) iworldreader, blockposition1, (Entity) this)) {
            return false;
        } else {
            for (int i = 1; i < 3; ++i) {
                BlockPos blockposition2 = blockposition.above(i);
                BlockState iblockdata1 = iworldreader.getType(blockposition2);

                if (!NaturalSpawner.isValidEmptySpawnBlock((BlockGetter) iworldreader, blockposition2, iblockdata1, iblockdata1.getFluidState(), EntityType.IRON_GOLEM)) {
                    return false;
                }
            }

            return NaturalSpawner.isValidEmptySpawnBlock((BlockGetter) iworldreader, blockposition, iworldreader.getType(blockposition), Fluids.EMPTY.defaultFluidState(), EntityType.IRON_GOLEM) && iworldreader.isUnobstructed(this);
        }
    }

    public static enum Crackiness {

        NONE(1.0F), LOW(0.75F), MEDIUM(0.5F), HIGH(0.25F);

        private static final List<IronGolem.Crackiness> BY_DAMAGE = (List) Stream.of(values()).sorted(Comparator.comparingDouble((entityirongolem_cracklevel) -> {
            return (double) entityirongolem_cracklevel.fraction;
        })).collect(ImmutableList.toImmutableList());
        private final float fraction;

        private Crackiness(float f) {
            this.fraction = f;
        }

        public static IronGolem.Crackiness byFraction(float f) {
            Iterator iterator = IronGolem.Crackiness.BY_DAMAGE.iterator();

            IronGolem.Crackiness entityirongolem_cracklevel;

            do {
                if (!iterator.hasNext()) {
                    return IronGolem.Crackiness.NONE;
                }

                entityirongolem_cracklevel = (IronGolem.Crackiness) iterator.next();
            } while (f >= entityirongolem_cracklevel.fraction);

            return entityirongolem_cracklevel;
        }
    }
}
