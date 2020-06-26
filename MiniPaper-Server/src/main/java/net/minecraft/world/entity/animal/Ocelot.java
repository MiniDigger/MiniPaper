package net.minecraft.world.entity.animal;

import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Ocelot extends Animal {

    private static final Ingredient TEMPT_INGREDIENT = Ingredient.of(Items.COD, Items.SALMON);
    private static final EntityDataAccessor<Boolean> DATA_TRUSTING = SynchedEntityData.defineId(Ocelot.class, EntityDataSerializers.BOOLEAN);
    private Ocelot.OcelotAvoidEntityGoal<Player> ocelotAvoidPlayersGoal;
    private Ocelot.OcelotTemptGoal temptGoal;

    public Ocelot(EntityType<? extends Ocelot> entitytypes, Level world) {
        super(entitytypes, world);
        this.reassessTrustingGoals();
    }

    private boolean isTrusting() {
        return (Boolean) this.entityData.get(Ocelot.DATA_TRUSTING);
    }

    private void setTrusting(boolean flag) {
        this.entityData.set(Ocelot.DATA_TRUSTING, flag);
        this.reassessTrustingGoals();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("Trusting", this.isTrusting());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setTrusting(nbttagcompound.getBoolean("Trusting"));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Ocelot.DATA_TRUSTING, false);
    }

    @Override
    protected void registerGoals() {
        this.temptGoal = new Ocelot.OcelotTemptGoal(this, 0.6D, Ocelot.TEMPT_INGREDIENT, true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, this.temptGoal);
        this.goalSelector.addGoal(7, new LeapAtTargetGoal(this, 0.3F));
        this.goalSelector.addGoal(8, new OcelotAttackGoal(this));
        this.goalSelector.addGoal(9, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 0.8D, 1.0000001E-5F));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Chicken.class, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, false, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    @Override
    public void customServerAiStep() {
        if (this.getMoveControl().hasWanted()) {
            double d0 = this.getMoveControl().getSpeedModifier();

            if (d0 == 0.6D) {
                this.setPose(Pose.CROUCHING);
                this.setSprinting(false);
            } else if (d0 == 1.33D) {
                this.setPose(Pose.STANDING);
                this.setSprinting(true);
            } else {
                this.setPose(Pose.STANDING);
                this.setSprinting(false);
            }
        } else {
            this.setPose(Pose.STANDING);
            this.setSprinting(false);
        }

    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isTrusting() /*&& this.ticksLived > 2400*/; // CraftBukkit
    }

    public static AttributeSupplier.Builder eL() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).a(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.OCELOT_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 900;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.OCELOT_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.OCELOT_DEATH;
    }

    private float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        return entity.hurt(DamageSource.mobAttack(this), this.getAttackDamage());
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        return this.isInvulnerableTo(damagesource) ? false : super.hurt(damagesource, f);
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if ((this.temptGoal == null || this.temptGoal.isRunning()) && !this.isTrusting() && this.isFood(itemstack) && entityhuman.distanceToSqr((Entity) this) < 9.0D) {
            this.usePlayerItem(entityhuman, itemstack);
            if (!this.level.isClientSide) {
                // CraftBukkit - added event call and isCancelled check
                if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) {
                    this.setTrusting(true);
                    this.spawnTrustingParticles(true);
                    this.level.broadcastEntityEvent(this, (byte) 41);
                } else {
                    this.spawnTrustingParticles(false);
                    this.level.broadcastEntityEvent(this, (byte) 40);
                }
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    private void spawnTrustingParticles(boolean flag) {
        SimpleParticleType particletype = ParticleTypes.HEART;

        if (!flag) {
            particletype = ParticleTypes.SMOKE;
        }

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level.addParticle(particletype, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    protected void reassessTrustingGoals() {
        if (this.ocelotAvoidPlayersGoal == null) {
            this.ocelotAvoidPlayersGoal = new Ocelot.OcelotAvoidEntityGoal<>(this, Player.class, 16.0F, 0.8D, 1.33D);
        }

        this.goalSelector.removeGoal((Goal) this.ocelotAvoidPlayersGoal);
        if (!this.isTrusting()) {
            this.goalSelector.addGoal(4, this.ocelotAvoidPlayersGoal);
        }

    }

    @Override
    public Ocelot getBreedOffspring(AgableMob entityageable) {
        return (Ocelot) EntityType.OCELOT.create(this.level);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return Ocelot.TEMPT_INGREDIENT.test(itemstack);
    }

    public static boolean checkOcelotSpawnRules(EntityType<Ocelot> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return random.nextInt(3) != 0;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader iworldreader) {
        if (iworldreader.isUnobstructed(this) && !iworldreader.containsAnyLiquid(this.getBoundingBox())) {
            BlockPos blockposition = this.blockPosition();

            if (blockposition.getY() < iworldreader.getSeaLevel()) {
                return false;
            }

            BlockState iblockdata = iworldreader.getType(blockposition.below());

            if (iblockdata.is(Blocks.GRASS_BLOCK) || iblockdata.is((Tag) BlockTags.LEAVES)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
            ((AgableMob.AgableMobGroupData) groupdataentity).setBabySpawnChance(1.0F);
        }

        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    static class OcelotTemptGoal extends TemptGoal {

        private final Ocelot ocelot;

        public OcelotTemptGoal(Ocelot entityocelot, double d0, Ingredient recipeitemstack, boolean flag) {
            super(entityocelot, d0, recipeitemstack, flag);
            this.ocelot = entityocelot;
        }

        @Override
        protected boolean canScare() {
            return super.canScare() && !this.ocelot.isTrusting();
        }
    }

    static class OcelotAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Ocelot ocelot;

        public OcelotAvoidEntityGoal(Ocelot entityocelot, Class<T> oclass, float f, double d0, double d1) {
            // Predicate predicate = IEntitySelector.e; // CraftBukkit - decompile error

            super(entityocelot, oclass, f, d0, d1, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test); // CraftBukkit - decompile error
            this.ocelot = entityocelot;
        }

        @Override
        public boolean canUse() {
            return !this.ocelot.isTrusting() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.ocelot.isTrusting() && super.canContinueToUse();
        }
    }
}
