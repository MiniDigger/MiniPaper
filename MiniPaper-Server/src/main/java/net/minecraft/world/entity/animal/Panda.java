package net.minecraft.world.entity.animal;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityTargetEvent; // CraftBukkit

public class Panda extends Animal {

    private static final EntityDataAccessor<Integer> UNHAPPY_COUNTER = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SNEEZE_COUNTER = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EAT_COUNTER = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MAIN_GENE_ID = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> HIDDEN_GENE_ID = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.BYTE);
    private static final TargetingConditions BREED_TARGETING = (new TargetingConditions()).range(8.0D).allowSameTeam().allowInvulnerable();
    private boolean gotBamboo;
    private boolean didBite;
    public int rollCounter;
    private Vec3 rollDelta;
    private float sitAmount;
    private float sitAmountO;
    private float onBackAmount;
    private float onBackAmountO;
    private float rollAmount;
    private float rollAmountO;
    private Panda.PandaLookAtPlayerGoal lookAtPlayerGoal;
    private static final Predicate<ItemEntity> PANDA_ITEMS = (entityitem) -> {
        Item item = entityitem.getItem().getItem();

        return (item == Blocks.BAMBOO.asItem() || item == Blocks.CAKE.asItem()) && entityitem.isAlive() && !entityitem.hasPickUpDelay();
    };

    public Panda(EntityType<? extends Panda> entitytypes, Level world) {
        super(entitytypes, world);
        this.moveControl = new Panda.PandaMoveControl(this);
        if (!this.isBaby()) {
            this.setCanPickUpLoot(true);
        }

    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

        return !this.getItemBySlot(enumitemslot).isEmpty() ? false : enumitemslot == EquipmentSlot.MAINHAND && super.canTakeItem(itemstack);
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(Panda.UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int i) {
        this.entityData.set(Panda.UNHAPPY_COUNTER, i);
    }

    public boolean isSneezing() {
        return this.getFlag(2);
    }

    public boolean isSitting() {
        return this.getFlag(8);
    }

    public void sit(boolean flag) {
        this.setFlag(8, flag);
    }

    public boolean isOnBack() {
        return this.getFlag(16);
    }

    public void setOnBack(boolean flag) {
        this.setFlag(16, flag);
    }

    public boolean isEating() {
        return (Integer) this.entityData.get(Panda.EAT_COUNTER) > 0;
    }

    public void eat(boolean flag) {
        this.entityData.set(Panda.EAT_COUNTER, flag ? 1 : 0);
    }

    private int getEatCounter() {
        return (Integer) this.entityData.get(Panda.EAT_COUNTER);
    }

    private void setEatCounter(int i) {
        this.entityData.set(Panda.EAT_COUNTER, i);
    }

    public void sneeze(boolean flag) {
        this.setFlag(2, flag);
        if (!flag) {
            this.setSneezeCounter(0);
        }

    }

    public int getSneezeCounter() {
        return (Integer) this.entityData.get(Panda.SNEEZE_COUNTER);
    }

    public void setSneezeCounter(int i) {
        this.entityData.set(Panda.SNEEZE_COUNTER, i);
    }

    public Panda.Gene getMainGene() {
        return Panda.Gene.byId((Byte) this.entityData.get(Panda.MAIN_GENE_ID));
    }

    public void setMainGene(Panda.Gene entitypanda_gene) {
        if (entitypanda_gene.getId() > 6) {
            entitypanda_gene = Panda.Gene.getRandom(this.random);
        }

        this.entityData.set(Panda.MAIN_GENE_ID, (byte) entitypanda_gene.getId());
    }

    public Panda.Gene getHiddenGene() {
        return Panda.Gene.byId((Byte) this.entityData.get(Panda.HIDDEN_GENE_ID));
    }

    public void setHiddenGene(Panda.Gene entitypanda_gene) {
        if (entitypanda_gene.getId() > 6) {
            entitypanda_gene = Panda.Gene.getRandom(this.random);
        }

        this.entityData.set(Panda.HIDDEN_GENE_ID, (byte) entitypanda_gene.getId());
    }

    public boolean isRolling() {
        return this.getFlag(4);
    }

    public void roll(boolean flag) {
        this.setFlag(4, flag);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Panda.UNHAPPY_COUNTER, 0);
        this.entityData.register(Panda.SNEEZE_COUNTER, 0);
        this.entityData.register(Panda.MAIN_GENE_ID, (byte) 0);
        this.entityData.register(Panda.HIDDEN_GENE_ID, (byte) 0);
        this.entityData.register(Panda.DATA_ID_FLAGS, (byte) 0);
        this.entityData.register(Panda.EAT_COUNTER, 0);
    }

    private boolean getFlag(int i) {
        return ((Byte) this.entityData.get(Panda.DATA_ID_FLAGS) & i) != 0;
    }

    private void setFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(Panda.DATA_ID_FLAGS);

        if (flag) {
            this.entityData.set(Panda.DATA_ID_FLAGS, (byte) (b0 | i));
        } else {
            this.entityData.set(Panda.DATA_ID_FLAGS, (byte) (b0 & ~i));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putString("MainGene", this.getMainGene().getName());
        nbttagcompound.putString("HiddenGene", this.getHiddenGene().getName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setMainGene(Panda.Gene.byName(nbttagcompound.getString("MainGene")));
        this.setHiddenGene(Panda.Gene.byName(nbttagcompound.getString("HiddenGene")));
    }

    @Nullable
    @Override
    public AgableMob getBreedOffspring(AgableMob entityageable) {
        Panda entitypanda = (Panda) EntityType.PANDA.create(this.level);

        if (entityageable instanceof Panda) {
            entitypanda.setGeneFromParents(this, (Panda) entityageable);
        }

        entitypanda.setAttributes();
        return entitypanda;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new Panda.PandaPanicGoal(this, 2.0D));
        this.goalSelector.addGoal(2, new Panda.PandaBreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new Panda.PandaAttackGoal(this, 1.2000000476837158D, true));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.0D, Ingredient.of(Blocks.BAMBOO.asItem()), false));
        this.goalSelector.addGoal(6, new Panda.PandaAvoidGoal<>(this, Player.class, 8.0F, 2.0D, 2.0D));
        this.goalSelector.addGoal(6, new Panda.PandaAvoidGoal<>(this, Monster.class, 4.0F, 2.0D, 2.0D));
        this.goalSelector.addGoal(7, new Panda.PandaSitGoal());
        this.goalSelector.addGoal(8, new Panda.PandaLieOnBackGoal(this));
        this.goalSelector.addGoal(8, new Panda.PandaSneezeGoal(this));
        this.lookAtPlayerGoal = new Panda.PandaLookAtPlayerGoal(this, Player.class, 6.0F);
        this.goalSelector.addGoal(9, this.lookAtPlayerGoal);
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(12, new Panda.PandaRollGoal(this));
        this.goalSelector.addGoal(13, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(14, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, (new Panda.PandaHurtByTargetGoal(this, new Class[0])).setAlertOthers(new Class[0]));
    }

    public static AttributeSupplier.Builder eZ() {
        return Mob.p().a(Attributes.MOVEMENT_SPEED, 0.15000000596046448D).a(Attributes.ATTACK_DAMAGE, 6.0D);
    }

    public Panda.Gene getVariant() {
        return Panda.Gene.getVariantFromGenes(this.getMainGene(), this.getHiddenGene());
    }

    public boolean isLazy() {
        return this.getVariant() == Panda.Gene.LAZY;
    }

    public boolean isWorried() {
        return this.getVariant() == Panda.Gene.WORRIED;
    }

    public boolean isPlayful() {
        return this.getVariant() == Panda.Gene.PLAYFUL;
    }

    public boolean isWeak() {
        return this.getVariant() == Panda.Gene.WEAK;
    }

    @Override
    public boolean isAggressive() {
        return this.getVariant() == Panda.Gene.AGGRESSIVE;
    }

    @Override
    public boolean canBeLeashed(Player entityhuman) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        this.playSound(SoundEvents.PANDA_BITE, 1.0F, 1.0F);
        if (!this.isAggressive()) {
            this.didBite = true;
        }

        return super.doHurtTarget(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isWorried()) {
            if (this.level.isThundering() && !this.isInWater()) {
                this.sit(true);
                this.eat(false);
            } else if (!this.isEating()) {
                this.sit(false);
            }
        }

        if (this.getTarget() == null) {
            this.gotBamboo = false;
            this.didBite = false;
        }

        if (this.getUnhappyCounter() > 0) {
            if (this.getTarget() != null) {
                this.lookAt((Entity) this.getTarget(), 90.0F, 90.0F);
            }

            if (this.getUnhappyCounter() == 29 || this.getUnhappyCounter() == 14) {
                this.playSound(SoundEvents.PANDA_CANT_BREED, 1.0F, 1.0F);
            }

            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }

        if (this.isSneezing()) {
            this.setSneezeCounter(this.getSneezeCounter() + 1);
            if (this.getSneezeCounter() > 20) {
                this.sneeze(false);
                this.afterSneeze();
            } else if (this.getSneezeCounter() == 1) {
                this.playSound(SoundEvents.PANDA_PRE_SNEEZE, 1.0F, 1.0F);
            }
        }

        if (this.isRolling()) {
            this.handleRoll();
        } else {
            this.rollCounter = 0;
        }

        if (this.isSitting()) {
            this.xRot = 0.0F;
        }

        this.updateSitAmount();
        this.handleEating();
        this.updateOnBackAnimation();
        this.updateRollAmount();
    }

    public boolean isScared() {
        return this.isWorried() && this.level.isThundering();
    }

    private void handleEating() {
        if (!this.isEating() && this.isSitting() && !this.isScared() && !this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && this.random.nextInt(80) == 1) {
            this.eat(true);
        } else if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() || !this.isSitting()) {
            this.eat(false);
        }

        if (this.isEating()) {
            this.addEatingParticles();
            if (!this.level.isClientSide && this.getEatCounter() > 80 && this.random.nextInt(20) == 1) {
                if (this.getEatCounter() > 100 && this.isFoodOrCake(this.getItemBySlot(EquipmentSlot.MAINHAND))) {
                    if (!this.level.isClientSide) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    }

                    this.sit(false);
                }

                this.eat(false);
                return;
            }

            this.setEatCounter(this.getEatCounter() + 1);
        }

    }

    private void addEatingParticles() {
        if (this.getEatCounter() % 5 == 0) {
            this.playSound(SoundEvents.PANDA_EAT, 0.5F + 0.5F * (float) this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);

            for (int i = 0; i < 6; ++i) {
                Vec3 vec3d = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, ((double) this.random.nextFloat() - 0.5D) * 0.1D);

                vec3d = vec3d.xRot(-this.xRot * 0.017453292F);
                vec3d = vec3d.yRot(-this.yRot * 0.017453292F);
                double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
                Vec3 vec3d1 = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.8D, d0, 1.0D + ((double) this.random.nextFloat() - 0.5D) * 0.4D);

                vec3d1 = vec3d1.yRot(-this.yBodyRot * 0.017453292F);
                vec3d1 = vec3d1.add(this.getX(), this.getEyeY() + 1.0D, this.getZ());
                this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItemBySlot(EquipmentSlot.MAINHAND)), vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
            }
        }

    }

    private void updateSitAmount() {
        this.sitAmountO = this.sitAmount;
        if (this.isSitting()) {
            this.sitAmount = Math.min(1.0F, this.sitAmount + 0.15F);
        } else {
            this.sitAmount = Math.max(0.0F, this.sitAmount - 0.19F);
        }

    }

    private void updateOnBackAnimation() {
        this.onBackAmountO = this.onBackAmount;
        if (this.isOnBack()) {
            this.onBackAmount = Math.min(1.0F, this.onBackAmount + 0.15F);
        } else {
            this.onBackAmount = Math.max(0.0F, this.onBackAmount - 0.19F);
        }

    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.15F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.19F);
        }

    }

    private void handleRoll() {
        ++this.rollCounter;
        if (this.rollCounter > 32) {
            this.roll(false);
        } else {
            if (!this.level.isClientSide) {
                Vec3 vec3d = this.getDeltaMovement();

                if (this.rollCounter == 1) {
                    float f = this.yRot * 0.017453292F;
                    float f1 = this.isBaby() ? 0.1F : 0.2F;

                    this.rollDelta = new Vec3(vec3d.x + (double) (-Mth.sin(f) * f1), 0.0D, vec3d.z + (double) (Mth.cos(f) * f1));
                    this.setDeltaMovement(this.rollDelta.add(0.0D, 0.27D, 0.0D));
                } else if ((float) this.rollCounter != 7.0F && (float) this.rollCounter != 15.0F && (float) this.rollCounter != 23.0F) {
                    this.setDeltaMovement(this.rollDelta.x, vec3d.y, this.rollDelta.z);
                } else {
                    this.setDeltaMovement(0.0D, this.onGround ? 0.27D : vec3d.y, 0.0D);
                }
            }

        }
    }

    private void afterSneeze() {
        Vec3 vec3d = this.getDeltaMovement();

        this.level.addParticle(ParticleTypes.SNEEZE, this.getX() - (double) (this.getBbWidth() + 1.0F) * 0.5D * (double) Mth.sin(this.yBodyRot * 0.017453292F), this.getEyeY() - 0.10000000149011612D, this.getZ() + (double) (this.getBbWidth() + 1.0F) * 0.5D * (double) Mth.cos(this.yBodyRot * 0.017453292F), vec3d.x, 0.0D, vec3d.z);
        this.playSound(SoundEvents.PANDA_SNEEZE, 1.0F, 1.0F);
        List<Panda> list = this.level.getEntitiesOfClass(Panda.class, this.getBoundingBox().inflate(10.0D));
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Panda entitypanda = (Panda) iterator.next();

            if (!entitypanda.isBaby() && entitypanda.onGround && !entitypanda.isInWater() && entitypanda.canPerformAction()) {
                entitypanda.jumpFromGround();
            }
        }

        if (!this.level.isClientSide() && this.random.nextInt(700) == 0 && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.spawnAtLocation((ItemLike) Items.SLIME_BALL);
        }

    }

    @Override
    protected void pickUpItem(ItemEntity entityitem) {
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, !(this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && Panda.PANDA_ITEMS.test(entityitem))).isCancelled()) { // CraftBukkit
            this.onItemPickup(entityitem);
            ItemStack itemstack = entityitem.getItem();

            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
            this.handDropChances[EquipmentSlot.MAINHAND.getIndex()] = 2.0F;
            this.take(entityitem, itemstack.getCount());
            entityitem.remove();
        }

    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        this.sit(false);
        return super.hurt(damagesource, f);
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        this.setMainGene(Panda.Gene.getRandom(this.random));
        this.setHiddenGene(Panda.Gene.getRandom(this.random));
        this.setAttributes();
        if (groupdataentity == null) {
            groupdataentity = new AgableMob.AgableMobGroupData();
            ((AgableMob.AgableMobGroupData) groupdataentity).setBabySpawnChance(0.2F);
        }

        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    public void setGeneFromParents(Panda entitypanda, @Nullable Panda entitypanda1) {
        if (entitypanda1 == null) {
            if (this.random.nextBoolean()) {
                this.setMainGene(entitypanda.getOneOfGenesRandomly());
                this.setHiddenGene(Panda.Gene.getRandom(this.random));
            } else {
                this.setMainGene(Panda.Gene.getRandom(this.random));
                this.setHiddenGene(entitypanda.getOneOfGenesRandomly());
            }
        } else if (this.random.nextBoolean()) {
            this.setMainGene(entitypanda.getOneOfGenesRandomly());
            this.setHiddenGene(entitypanda1.getOneOfGenesRandomly());
        } else {
            this.setMainGene(entitypanda1.getOneOfGenesRandomly());
            this.setHiddenGene(entitypanda.getOneOfGenesRandomly());
        }

        if (this.random.nextInt(32) == 0) {
            this.setMainGene(Panda.Gene.getRandom(this.random));
        }

        if (this.random.nextInt(32) == 0) {
            this.setHiddenGene(Panda.Gene.getRandom(this.random));
        }

    }

    private Panda.Gene getOneOfGenesRandomly() {
        return this.random.nextBoolean() ? this.getMainGene() : this.getHiddenGene();
    }

    public void setAttributes() {
        if (this.isWeak()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0D);
        }

        if (this.isLazy()) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.07000000029802322D);
        }

    }

    private void tryToSit() {
        if (!this.isInWater()) {
            this.setZza(0.0F);
            this.getNavigation().stop();
            this.sit(true);
        }

    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (this.isScared()) {
            return InteractionResult.PASS;
        } else if (this.isOnBack()) {
            this.setOnBack(false);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else if (this.isFood(itemstack)) {
            if (this.getTarget() != null) {
                this.gotBamboo = true;
            }

            if (this.isBaby()) {
                this.usePlayerItem(entityhuman, itemstack);
                this.ageUp((int) ((float) (-this.getAge() / 20) * 0.1F), true);
            } else if (!this.level.isClientSide && this.getAge() == 0 && this.canFallInLove()) {
                this.usePlayerItem(entityhuman, itemstack);
                this.setInLove(entityhuman);
            } else {
                if (this.level.isClientSide || this.isSitting() || this.isInWater()) {
                    return InteractionResult.PASS;
                }

                this.tryToSit();
                this.eat(true);
                ItemStack itemstack1 = this.getItemBySlot(EquipmentSlot.MAINHAND);

                if (!itemstack1.isEmpty() && !entityhuman.abilities.instabuild) {
                    this.spawnAtLocation(itemstack1);
                }

                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(itemstack.getItem(), 1));
                this.usePlayerItem(entityhuman, itemstack);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isAggressive() ? SoundEvents.PANDA_AGGRESSIVE_AMBIENT : (this.isWorried() ? SoundEvents.PANDA_WORRIED_AMBIENT : SoundEvents.PANDA_AMBIENT);
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(SoundEvents.PANDA_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.getItem() == Blocks.BAMBOO.asItem();
    }

    private boolean isFoodOrCake(ItemStack itemstack) {
        return this.isFood(itemstack) || itemstack.getItem() == Blocks.CAKE.asItem();
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.PANDA_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.PANDA_HURT;
    }

    public boolean canPerformAction() {
        return !this.isOnBack() && !this.isScared() && !this.isEating() && !this.isRolling() && !this.isSitting();
    }

    static class PandaPanicGoal extends PanicGoal {

        private final Panda panda;

        public PandaPanicGoal(Panda entitypanda, double d0) {
            super(entitypanda, d0);
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            if (!this.panda.isOnFire()) {
                return false;
            } else {
                BlockPos blockposition = this.lookForWater(this.mob.level, this.mob, 5, 4);

                if (blockposition != null) {
                    this.posX = (double) blockposition.getX();
                    this.posY = (double) blockposition.getY();
                    this.posZ = (double) blockposition.getZ();
                    return true;
                } else {
                    return this.findRandomPosition();
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (this.panda.isSitting()) {
                this.panda.getNavigation().stop();
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }

    static class PandaHurtByTargetGoal extends HurtByTargetGoal {

        private final Panda panda;

        public PandaHurtByTargetGoal(Panda entitypanda, Class<?>... aclass) {
            super(entitypanda, aclass);
            this.panda = entitypanda;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.panda.gotBamboo && !this.panda.didBite) {
                return super.canContinueToUse();
            } else {
                this.panda.setTarget((LivingEntity) null);
                return false;
            }
        }

        @Override
        protected void alertOther(Mob entityinsentient, LivingEntity entityliving) {
            if (entityinsentient instanceof Panda && ((Panda) entityinsentient).isAggressive()) {
                entityinsentient.setGoalTarget(entityliving, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit
            }

        }
    }

    static class PandaLieOnBackGoal extends Goal {

        private final Panda panda;
        private int cooldown;

        public PandaLieOnBackGoal(Panda entitypanda) {
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.cooldown < this.panda.tickCount && this.panda.isLazy() && this.panda.canPerformAction() && this.panda.random.nextInt(400) == 1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.panda.isInWater() && (this.panda.isLazy() || this.panda.random.nextInt(600) != 1) ? this.panda.random.nextInt(2000) != 1 : false;
        }

        @Override
        public void start() {
            this.panda.setOnBack(true);
            this.cooldown = 0;
        }

        @Override
        public void stop() {
            this.panda.setOnBack(false);
            this.cooldown = this.panda.tickCount + 200;
        }
    }

    class PandaSitGoal extends Goal {

        private int cooldown;

        public PandaSitGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown <= Panda.this.tickCount && !Panda.this.isBaby() && !Panda.this.isInWater() && Panda.this.canPerformAction() && Panda.this.getUnhappyCounter() <= 0) {
                List<ItemEntity> list = Panda.this.level.getEntitiesOfClass(ItemEntity.class, Panda.this.getBoundingBox().inflate(6.0D, 6.0D, 6.0D), Panda.PANDA_ITEMS);

                return !list.isEmpty() || !Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            } else {
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !Panda.this.isInWater() && (Panda.this.isLazy() || Panda.this.random.nextInt(600) != 1) ? Panda.this.random.nextInt(2000) != 1 : false;
        }

        @Override
        public void tick() {
            if (!Panda.this.isSitting() && !Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                Panda.this.tryToSit();
            }

        }

        @Override
        public void start() {
            List<ItemEntity> list = Panda.this.level.getEntitiesOfClass(ItemEntity.class, Panda.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Panda.PANDA_ITEMS);

            if (!list.isEmpty() && Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                Panda.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            } else if (!Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                Panda.this.tryToSit();
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemstack = Panda.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                Panda.this.spawnAtLocation(itemstack);
                Panda.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                int i = Panda.this.isLazy() ? Panda.this.random.nextInt(50) + 10 : Panda.this.random.nextInt(150) + 10;

                this.cooldown = Panda.this.tickCount + i * 20;
            }

            Panda.this.sit(false);
        }
    }

    static class PandaAvoidGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Panda panda;

        public PandaAvoidGoal(Panda entitypanda, Class<T> oclass, float f, double d0, double d1) {
            // Predicate predicate = IEntitySelector.g; // CraftBukkit - decompile error

            super(entitypanda, oclass, f, d0, d1, EntitySelector.NO_SPECTATORS::test); // CraftBukkit - decompile error
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.panda.isWorried() && this.panda.canPerformAction() && super.canUse();
        }
    }

    class PandaBreedGoal extends BreedGoal {

        private final Panda panda;
        private int unhappyCooldown;

        public PandaBreedGoal(Panda entitypanda, double d0) {
            super(entitypanda, d0);
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            if (super.canUse() && this.panda.getUnhappyCounter() == 0) {
                if (!this.canFindBamboo()) {
                    if (this.unhappyCooldown <= this.panda.tickCount) {
                        this.panda.setUnhappyCounter(32);
                        this.unhappyCooldown = this.panda.tickCount + 600;
                        if (this.panda.isEffectiveAi()) {
                            Player entityhuman = this.level.getNearestPlayer(Panda.BREED_TARGETING, (LivingEntity) this.panda);

                            this.panda.lookAtPlayerGoal.setTarget((LivingEntity) entityhuman);
                        }
                    }

                    return false;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

        private boolean canFindBamboo() {
            BlockPos blockposition = this.panda.blockPosition();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 8; ++j) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockposition_mutableblockposition.a((Vec3i) blockposition, k, i, l);
                            if (this.level.getType(blockposition_mutableblockposition).is(Blocks.BAMBOO)) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
    }

    static class PandaSneezeGoal extends Goal {

        private final Panda panda;

        public PandaSneezeGoal(Panda entitypanda) {
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.panda.isBaby() && this.panda.canPerformAction() ? (this.panda.isWeak() && this.panda.random.nextInt(500) == 1 ? true : this.panda.random.nextInt(6000) == 1) : false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.panda.sneeze(true);
        }
    }

    static class PandaRollGoal extends Goal {

        private final Panda panda;

        public PandaRollGoal(Panda entitypanda) {
            this.panda = entitypanda;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if ((this.panda.isBaby() || this.panda.isPlayful()) && this.panda.onGround) {
                if (!this.panda.canPerformAction()) {
                    return false;
                } else {
                    float f = this.panda.yRot * 0.017453292F;
                    int i = 0;
                    int j = 0;
                    float f1 = -Mth.sin(f);
                    float f2 = Mth.cos(f);

                    if ((double) Math.abs(f1) > 0.5D) {
                        i = (int) ((float) i + f1 / Math.abs(f1));
                    }

                    if ((double) Math.abs(f2) > 0.5D) {
                        j = (int) ((float) j + f2 / Math.abs(f2));
                    }

                    return this.panda.level.getType(this.panda.blockPosition().offset(i, -1, j)).isAir() ? true : (this.panda.isPlayful() && this.panda.random.nextInt(60) == 1 ? true : this.panda.random.nextInt(500) == 1);
                }
            } else {
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.panda.roll(true);
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
    }

    static class PandaLookAtPlayerGoal extends LookAtPlayerGoal {

        private final Panda panda;

        public PandaLookAtPlayerGoal(Panda entitypanda, Class<? extends LivingEntity> oclass, float f) {
            super(entitypanda, oclass, f);
            this.panda = entitypanda;
        }

        public void setTarget(LivingEntity entityliving) {
            this.lookAt = entityliving;
        }

        @Override
        public boolean canContinueToUse() {
            return this.lookAt != null && super.canContinueToUse();
        }

        @Override
        public boolean canUse() {
            if (this.mob.getRandom().nextFloat() >= this.probability) {
                return false;
            } else {
                if (this.lookAt == null) {
                    if (this.lookAtType == Player.class) {
                        this.lookAt = this.mob.level.getNearestPlayer(this.lookAtContext, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
                    } else {
                        this.lookAt = this.mob.level.getNearestLoadedEntity(this.lookAtType, this.lookAtContext, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ(), this.mob.getBoundingBox().inflate((double) this.lookDistance, 3.0D, (double) this.lookDistance));
                    }
                }

                return this.panda.canPerformAction() && this.lookAt != null;
            }
        }

        @Override
        public void tick() {
            if (this.lookAt != null) {
                super.tick();
            }

        }
    }

    static class PandaAttackGoal extends MeleeAttackGoal {

        private final Panda panda;

        public PandaAttackGoal(Panda entitypanda, double d0, boolean flag) {
            super(entitypanda, d0, flag);
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.panda.canPerformAction() && super.canUse();
        }
    }

    static class PandaMoveControl extends MoveControl {

        private final Panda panda;

        public PandaMoveControl(Panda entitypanda) {
            super(entitypanda);
            this.panda = entitypanda;
        }

        @Override
        public void tick() {
            if (this.panda.canPerformAction()) {
                super.tick();
            }
        }
    }

    public static enum Gene {

        NORMAL(0, "normal", false), LAZY(1, "lazy", false), WORRIED(2, "worried", false), PLAYFUL(3, "playful", false), BROWN(4, "brown", true), WEAK(5, "weak", true), AGGRESSIVE(6, "aggressive", false);

        private static final Panda.Gene[] BY_ID = (Panda.Gene[]) Arrays.stream(values()).sorted(Comparator.comparingInt(Panda.Gene::getId)).toArray((i) -> {
            return new Panda.Gene[i];
        });
        private final int id;
        private final String name;
        private final boolean isRecessive;

        private Gene(int i, String s, boolean flag) {
            this.id = i;
            this.name = s;
            this.isRecessive = flag;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public boolean isRecessive() {
            return this.isRecessive;
        }

        private static Panda.Gene getVariantFromGenes(Panda.Gene entitypanda_gene, Panda.Gene entitypanda_gene1) {
            return entitypanda_gene.isRecessive() ? (entitypanda_gene == entitypanda_gene1 ? entitypanda_gene : Panda.Gene.NORMAL) : entitypanda_gene;
        }

        public static Panda.Gene byId(int i) {
            if (i < 0 || i >= Panda.Gene.BY_ID.length) {
                i = 0;
            }

            return Panda.Gene.BY_ID[i];
        }

        public static Panda.Gene byName(String s) {
            Panda.Gene[] aentitypanda_gene = values();
            int i = aentitypanda_gene.length;

            for (int j = 0; j < i; ++j) {
                Panda.Gene entitypanda_gene = aentitypanda_gene[j];

                if (entitypanda_gene.name.equals(s)) {
                    return entitypanda_gene;
                }
            }

            return Panda.Gene.NORMAL;
        }

        public static Panda.Gene getRandom(Random random) {
            int i = random.nextInt(16);

            return i == 0 ? Panda.Gene.LAZY : (i == 1 ? Panda.Gene.WORRIED : (i == 2 ? Panda.Gene.PLAYFUL : (i == 4 ? Panda.Gene.AGGRESSIVE : (i < 9 ? Panda.Gene.WEAK : (i < 11 ? Panda.Gene.BROWN : Panda.Gene.NORMAL)))));
        }
    }
}
