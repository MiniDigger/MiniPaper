package net.minecraft.world.entity.monster;

import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.IntRange;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;

public class ZombifiedPiglin extends Zombie implements NeutralMob {

    private static final UUID SPEED_MODIFIER_ATTACKING_UUID = UUID.fromString("49455A49-7EC5-45BA-B886-3B90B23A1718");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(ZombifiedPiglin.SPEED_MODIFIER_ATTACKING_UUID, "Attacking speed boost", 0.05D, AttributeModifier.Operation.ADDITION);
    private static final IntRange FIRST_ANGER_SOUND_DELAY = TimeUtil.rangeOfSeconds(0, 1);
    private int playFirstAngerSoundIn;
    private static final IntRange PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;
    private static final IntRange ALERT_INTERVAL = TimeUtil.rangeOfSeconds(4, 6);
    private int ticksUntilNextAlert;

    public ZombifiedPiglin(EntityType<? extends ZombifiedPiglin> entitytypes, Level world) {
        super(entitytypes, world);
        this.setPathfindingMalus(BlockPathTypes.LAVA, 8.0F);
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    @Override
    public double getMyRidingOffset() {
        return this.isBaby() ? -0.16D : -0.45D;
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(new Class[0])); // CraftBukkit - decompile error
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public static AttributeSupplier.Builder eX() {
        return Zombie.eT().a(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D).a(Attributes.MOVEMENT_SPEED, 0.23000000417232513D).a(Attributes.ATTACK_DAMAGE, 5.0D);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (this.isAngry()) {
            if (!this.isBaby() && !attributemodifiable.hasModifier(ZombifiedPiglin.SPEED_MODIFIER_ATTACKING)) {
                attributemodifiable.addTransientModifier(ZombifiedPiglin.SPEED_MODIFIER_ATTACKING);
            }

            this.maybePlayFirstAngerSound();
        } else if (attributemodifiable.hasModifier(ZombifiedPiglin.SPEED_MODIFIER_ATTACKING)) {
            attributemodifiable.removeModifier(ZombifiedPiglin.SPEED_MODIFIER_ATTACKING);
        }

        this.updatePersistentAnger((ServerLevel) this.level, true);
        if (this.getTarget() != null) {
            this.maybeAlertOthers();
        }

        if (this.isAngry()) {
            this.lastHurtByPlayerTime = this.tickCount;
        }

        super.customServerAiStep();
    }

    private void maybePlayFirstAngerSound() {
        if (this.playFirstAngerSoundIn > 0) {
            --this.playFirstAngerSoundIn;
            if (this.playFirstAngerSoundIn == 0) {
                this.playAngerSound();
            }
        }

    }

    private void maybeAlertOthers() {
        if (this.ticksUntilNextAlert > 0) {
            --this.ticksUntilNextAlert;
        } else {
            if (this.getEntitySenses().canSee(this.getTarget())) {
                this.alertOthers();
            }

            this.ticksUntilNextAlert = ZombifiedPiglin.ALERT_INTERVAL.randomValue(this.random);
        }
    }

    private void alertOthers() {
        double d0 = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB axisalignedbb = AABB.unitCubeFromLowerCorner(this.position()).inflate(d0, 10.0D, d0);

        this.level.getLoadedEntitiesOfClass(ZombifiedPiglin.class, axisalignedbb).stream().filter((entitypigzombie) -> {
            return entitypigzombie != this;
        }).filter((entitypigzombie) -> {
            return entitypigzombie.getTarget() == null;
        }).filter((entitypigzombie) -> {
            return !entitypigzombie.isAlliedTo(this.getTarget());
        }).forEach((entitypigzombie) -> {
            entitypigzombie.setGoalTarget(this.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY, true); // CraftBukkit
        });
    }

    private void playAngerSound() {
        this.playSound(SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, this.getSoundVolume() * 2.0F, this.getVoicePitch() * 1.8F);
    }

    @Override
    public void setTarget(@Nullable LivingEntity entityliving) {
        if (this.getTarget() == null && entityliving != null) {
            this.playFirstAngerSoundIn = ZombifiedPiglin.FIRST_ANGER_SOUND_DELAY.randomValue(this.random);
            this.ticksUntilNextAlert = ZombifiedPiglin.ALERT_INTERVAL.randomValue(this.random);
        }

        if (entityliving instanceof Player) {
            this.setLastHurtByPlayer((Player) entityliving);
        }

        super.setTarget(entityliving);
    }

    @Override
    public void startPersistentAngerTimer() {
        // CraftBukkit start
        Entity entity = ((ServerLevel) this.level).getEntity(getPersistentAngerTarget());
        org.bukkit.event.entity.PigZombieAngerEvent event = new org.bukkit.event.entity.PigZombieAngerEvent((org.bukkit.entity.PigZombie) this.getBukkitEntity(), (entity == null) ? null : entity.getBukkitEntity(), ZombifiedPiglin.PERSISTENT_ANGER_TIME.randomValue(this.random));
        this.level.getServerOH().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.setPersistentAngerTarget(null);
            return;
        }
        this.setRemainingPersistentAngerTime(event.getNewAnger());
        // CraftBukkit end
    }

    public static boolean checkZombifiedPiglinSpawnRules(EntityType<ZombifiedPiglin> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return generatoraccess.getDifficulty() != Difficulty.PEACEFUL && generatoraccess.getType(blockposition.below()).getBlock() != Blocks.NETHER_WART_BLOCK;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader iworldreader) {
        return iworldreader.isUnobstructed(this) && !iworldreader.containsAnyLiquid(this.getBoundingBox());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        this.addPersistentAngerSaveData(nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.readPersistentAngerSaveData((ServerLevel) this.level, nbttagcompound);
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
    public boolean hurt(DamageSource damagesource, float f) {
        return this.isInvulnerableTo(damagesource) ? false : super.hurt(damagesource, f);
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isAngry() ? SoundEvents.ZOMBIFIED_PIGLIN_ANGRY : SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.ZOMBIFIED_PIGLIN_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.ZOMBIFIED_PIGLIN_DEATH;
    }

    @Override
    protected void populateDefaultEquipmentSlots(DifficultyInstance difficultydamagescaler) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0D);
    }

    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public boolean isPreventingPlayerRest(Player entityhuman) {
        return this.isAngryAt((LivingEntity) entityhuman);
    }
}
