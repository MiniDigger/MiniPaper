package net.minecraft.world.entity.animal;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.CatLieOnBedGoal;
import net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

public class Cat extends TamableAnimal {

    private static final Ingredient TEMPT_INGREDIENT = Ingredient.of(Items.COD, Items.SALMON);
    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_LYING = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RELAX_STATE_ONE = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.INT);
    public static final Map<Integer, ResourceLocation> TEXTURE_BY_TYPE = (Map) Util.make(Maps.newHashMap(), (hashmap) -> { // CraftBukkit - decompile error
        hashmap.put(0, new ResourceLocation("textures/entity/cat/tabby.png"));
        hashmap.put(1, new ResourceLocation("textures/entity/cat/black.png"));
        hashmap.put(2, new ResourceLocation("textures/entity/cat/red.png"));
        hashmap.put(3, new ResourceLocation("textures/entity/cat/siamese.png"));
        hashmap.put(4, new ResourceLocation("textures/entity/cat/british_shorthair.png"));
        hashmap.put(5, new ResourceLocation("textures/entity/cat/calico.png"));
        hashmap.put(6, new ResourceLocation("textures/entity/cat/persian.png"));
        hashmap.put(7, new ResourceLocation("textures/entity/cat/ragdoll.png"));
        hashmap.put(8, new ResourceLocation("textures/entity/cat/white.png"));
        hashmap.put(9, new ResourceLocation("textures/entity/cat/jellie.png"));
        hashmap.put(10, new ResourceLocation("textures/entity/cat/all_black.png"));
    });
    private Cat.CatAvoidEntityGoal<Player> avoidPlayersGoal;
    private TemptGoal temptGoal;
    private float lieDownAmount;
    private float lieDownAmountO;
    private float lieDownAmountTail;
    private float lieDownAmountOTail;
    private float relaxStateOneAmount;
    private float relaxStateOneAmountO;

    public Cat(EntityType<? extends Cat> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public ResourceLocation getResourceLocation() {
        return (ResourceLocation) Cat.TEXTURE_BY_TYPE.getOrDefault(this.getCatType(), Cat.TEXTURE_BY_TYPE.get(0));
    }

    @Override
    protected void registerGoals() {
        this.temptGoal = new Cat.CatTemptGoal(this, 0.6D, Cat.TEMPT_INGREDIENT, true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new Cat.CatRelaxOnOwnerGoal(this));
        this.goalSelector.addGoal(3, this.temptGoal);
        this.goalSelector.addGoal(5, new CatLieOnBedGoal(this, 1.1D, 8));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 5.0F, false));
        this.goalSelector.addGoal(7, new CatSitOnBlockGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LeapAtTargetGoal(this, 0.3F));
        this.goalSelector.addGoal(9, new OcelotAttackGoal(this));
        this.goalSelector.addGoal(10, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.8D, 1.0000001E-5F));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new NonTameRandomTargetGoal<>(this, Rabbit.class, false, (Predicate) null));
        this.targetSelector.addGoal(1, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public int getCatType() {
        return (Integer) this.entityData.get(Cat.DATA_TYPE_ID);
    }

    public void setCatType(int i) {
        if (i < 0 || i >= 11) {
            i = this.random.nextInt(10);
        }

        this.entityData.set(Cat.DATA_TYPE_ID, i);
    }

    public void setLying(boolean flag) {
        this.entityData.set(Cat.IS_LYING, flag);
    }

    public boolean isLying() {
        return (Boolean) this.entityData.get(Cat.IS_LYING);
    }

    public void setRelaxStateOne(boolean flag) {
        this.entityData.set(Cat.RELAX_STATE_ONE, flag);
    }

    public boolean isRelaxStateOne() {
        return (Boolean) this.entityData.get(Cat.RELAX_STATE_ONE);
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId((Integer) this.entityData.get(Cat.DATA_COLLAR_COLOR));
    }

    public void setCollarColor(DyeColor enumcolor) {
        this.entityData.set(Cat.DATA_COLLAR_COLOR, enumcolor.getId());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Cat.DATA_TYPE_ID, 1);
        this.entityData.register(Cat.IS_LYING, false);
        this.entityData.register(Cat.RELAX_STATE_ONE, false);
        this.entityData.register(Cat.DATA_COLLAR_COLOR, DyeColor.RED.getId());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("CatType", this.getCatType());
        nbttagcompound.putByte("CollarColor", (byte) this.getCollarColor().getId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setCatType(nbttagcompound.getInt("CatType"));
        if (nbttagcompound.contains("CollarColor", 99)) {
            this.setCollarColor(DyeColor.byId(nbttagcompound.getInt("CollarColor")));
        }

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

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return this.isTame() ? (this.isInLove() ? SoundEvents.CAT_PURR : (this.random.nextInt(4) == 0 ? SoundEvents.CAT_PURREOW : SoundEvents.CAT_AMBIENT)) : SoundEvents.CAT_STRAY_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    public void hiss() {
        this.playSound(SoundEvents.CAT_HISS, this.getSoundVolume(), this.getVoicePitch());
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.CAT_DEATH;
    }

    public static AttributeSupplier.Builder fb() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).a(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    public boolean causeFallDamage(float f, float f1) {
        return false;
    }

    @Override
    protected void usePlayerItem(Player entityhuman, ItemStack itemstack) {
        if (this.isFood(itemstack)) {
            this.playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
        }

        super.usePlayerItem(entityhuman, itemstack);
    }

    private float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        return entity.hurt(DamageSource.mobAttack(this), this.getAttackDamage());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.temptGoal != null && this.temptGoal.isRunning() && !this.isTame() && this.tickCount % 100 == 0) {
            this.playSound(SoundEvents.CAT_BEG_FOR_FOOD, 1.0F, 1.0F);
        }

        this.handleLieDown();
    }

    private void handleLieDown() {
        if ((this.isLying() || this.isRelaxStateOne()) && this.tickCount % 5 == 0) {
            this.playSound(SoundEvents.CAT_PURR, 0.6F + 0.4F * (this.random.nextFloat() - this.random.nextFloat()), 1.0F);
        }

        this.updateLieDownAmount();
        this.updateRelaxStateOneAmount();
    }

    private void updateLieDownAmount() {
        this.lieDownAmountO = this.lieDownAmount;
        this.lieDownAmountOTail = this.lieDownAmountTail;
        if (this.isLying()) {
            this.lieDownAmount = Math.min(1.0F, this.lieDownAmount + 0.15F);
            this.lieDownAmountTail = Math.min(1.0F, this.lieDownAmountTail + 0.08F);
        } else {
            this.lieDownAmount = Math.max(0.0F, this.lieDownAmount - 0.22F);
            this.lieDownAmountTail = Math.max(0.0F, this.lieDownAmountTail - 0.13F);
        }

    }

    private void updateRelaxStateOneAmount() {
        this.relaxStateOneAmountO = this.relaxStateOneAmount;
        if (this.isRelaxStateOne()) {
            this.relaxStateOneAmount = Math.min(1.0F, this.relaxStateOneAmount + 0.1F);
        } else {
            this.relaxStateOneAmount = Math.max(0.0F, this.relaxStateOneAmount - 0.13F);
        }

    }

    @Override
    public Cat getBreedOffspring(AgableMob entityageable) {
        Cat entitycat = (Cat) EntityType.CAT.create(this.level);

        if (entityageable instanceof Cat) {
            if (this.random.nextBoolean()) {
                entitycat.setCatType(this.getCatType());
            } else {
                entitycat.setCatType(((Cat) entityageable).getCatType());
            }

            if (this.isTame()) {
                entitycat.setOwnerUUID(this.getOwnerUUID());
                entitycat.setTame(true);
                if (this.random.nextBoolean()) {
                    entitycat.setCollarColor(this.getCollarColor());
                } else {
                    entitycat.setCollarColor(((Cat) entityageable).getCollarColor());
                }
            }
        }

        return entitycat;
    }

    @Override
    public boolean canMate(Animal entityanimal) {
        if (!this.isTame()) {
            return false;
        } else if (!(entityanimal instanceof Cat)) {
            return false;
        } else {
            Cat entitycat = (Cat) entityanimal;

            return entitycat.isTame() && super.canMate(entityanimal);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        groupdataentity = super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, groupdataentity, nbttagcompound);
        if (generatoraccess.getMoonBrightness() > 0.9F) {
            this.setCatType(this.random.nextInt(11));
        } else {
            this.setCatType(this.random.nextInt(10));
        }

        Level world = generatoraccess.getLevel();

        if (world instanceof ServerLevel && ((ServerLevel) world).getStructureManager().getStructureAt(this.blockPosition(), true, StructureFeature.SWAMP_HUT).isValid()) {
            this.setCatType(10);
            this.setPersistenceRequired();
        }

        return groupdataentity;
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        Item item = itemstack.getItem();

        if (this.level.isClientSide) {
            return this.isTame() && this.isOwnedBy((LivingEntity) entityhuman) ? InteractionResult.SUCCESS : (this.isFood(itemstack) && (this.getHealth() < this.getMaxHealth() || !this.isTame()) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        } else {
            InteractionResult enuminteractionresult;

            if (this.isTame()) {
                if (this.isOwnedBy((LivingEntity) entityhuman)) {
                    if (!(item instanceof DyeItem)) {
                        if (item.isEdible() && this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                            this.usePlayerItem(entityhuman, itemstack);
                            this.heal((float) item.getFoodProperties().getNutrition());
                            return InteractionResult.CONSUME;
                        }

                        enuminteractionresult = super.mobInteract(entityhuman, enumhand);
                        if (!enuminteractionresult.consumesAction() || this.isBaby()) {
                            this.setOrderedToSit(!this.isOrderedToSit());
                        }

                        return enuminteractionresult;
                    }

                    DyeColor enumcolor = ((DyeItem) item).getDyeColor();

                    if (enumcolor != this.getCollarColor()) {
                        this.setCollarColor(enumcolor);
                        if (!entityhuman.abilities.instabuild) {
                            itemstack.shrink(1);
                        }

                        this.setPersistenceRequired();
                        return InteractionResult.CONSUME;
                    }
                }
            } else if (this.isFood(itemstack)) {
                this.usePlayerItem(entityhuman, itemstack);
                if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) { // CraftBukkit
                    this.tame(entityhuman);
                    this.setOrderedToSit(true);
                    this.level.broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level.broadcastEntityEvent(this, (byte) 6);
                }

                this.setPersistenceRequired();
                return InteractionResult.CONSUME;
            }

            enuminteractionresult = super.mobInteract(entityhuman, enumhand);
            if (enuminteractionresult.consumesAction()) {
                this.setPersistenceRequired();
            }

            return enuminteractionresult;
        }
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return Cat.TEMPT_INGREDIENT.test(itemstack);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return entitysize.height * 0.5F;
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isTame() && this.tickCount > 2400;
    }

    @Override
    protected void reassessTameGoals() {
        if (this.avoidPlayersGoal == null) {
            this.avoidPlayersGoal = new Cat.CatAvoidEntityGoal<>(this, Player.class, 16.0F, 0.8D, 1.33D);
        }

        this.goalSelector.removeGoal((Goal) this.avoidPlayersGoal);
        if (!this.isTame()) {
            this.goalSelector.addGoal(4, this.avoidPlayersGoal);
        }

    }

    static class CatRelaxOnOwnerGoal extends Goal {

        private final Cat cat;
        private Player ownerPlayer;
        private BlockPos goalPos;
        private int onBedTicks;

        public CatRelaxOnOwnerGoal(Cat entitycat) {
            this.cat = entitycat;
        }

        @Override
        public boolean canUse() {
            if (!this.cat.isTame()) {
                return false;
            } else if (this.cat.isOrderedToSit()) {
                return false;
            } else {
                LivingEntity entityliving = this.cat.getOwner();

                if (entityliving instanceof Player) {
                    this.ownerPlayer = (Player) entityliving;
                    if (!entityliving.isSleeping()) {
                        return false;
                    }

                    if (this.cat.distanceToSqr((Entity) this.ownerPlayer) > 100.0D) {
                        return false;
                    }

                    BlockPos blockposition = this.ownerPlayer.blockPosition();
                    BlockState iblockdata = this.cat.level.getType(blockposition);

                    if (iblockdata.getBlock().is((Tag) BlockTags.BEDS)) {
                        this.goalPos = (BlockPos) iblockdata.getOptionalValue(BedBlock.FACING).map((enumdirection) -> {
                            return blockposition.relative(enumdirection.getOpposite());
                        }).orElseGet(() -> {
                            return new BlockPos(blockposition);
                        });
                        return !this.spaceIsOccupied();
                    }
                }

                return false;
            }
        }

        private boolean spaceIsOccupied() {
            List<Cat> list = this.cat.level.getEntitiesOfClass(Cat.class, (new AABB(this.goalPos)).inflate(2.0D));
            Iterator iterator = list.iterator();

            Cat entitycat;

            do {
                do {
                    if (!iterator.hasNext()) {
                        return false;
                    }

                    entitycat = (Cat) iterator.next();
                } while (entitycat == this.cat);
            } while (!entitycat.isLying() && !entitycat.isRelaxStateOne());

            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.cat.isTame() && !this.cat.isOrderedToSit() && this.ownerPlayer != null && this.ownerPlayer.isSleeping() && this.goalPos != null && !this.spaceIsOccupied();
        }

        @Override
        public void start() {
            if (this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo((double) this.goalPos.getX(), (double) this.goalPos.getY(), (double) this.goalPos.getZ(), 1.100000023841858D);
            }

        }

        @Override
        public void stop() {
            this.cat.setLying(false);
            float f = this.cat.level.getTimeOfDay(1.0F);

            if (this.ownerPlayer.getSleepTimer() >= 100 && (double) f > 0.77D && (double) f < 0.8D && (double) this.cat.level.getRandom().nextFloat() < 0.7D) {
                this.giveMorningGift();
            }

            this.onBedTicks = 0;
            this.cat.setRelaxStateOne(false);
            this.cat.getNavigation().stop();
        }

        private void giveMorningGift() {
            Random random = this.cat.getRandom();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();

            blockposition_mutableblockposition.g(this.cat.blockPosition());
            this.cat.randomTeleport((double) (blockposition_mutableblockposition.getX() + random.nextInt(11) - 5), (double) (blockposition_mutableblockposition.getY() + random.nextInt(5) - 2), (double) (blockposition_mutableblockposition.getZ() + random.nextInt(11) - 5), false);
            blockposition_mutableblockposition.g(this.cat.blockPosition());
            LootTable loottable = this.cat.level.getServer().getLootTables().get(BuiltInLootTables.CAT_MORNING_GIFT);
            LootContext.Builder loottableinfo_builder = (new LootContext.Builder((ServerLevel) this.cat.level)).set(LootContextParams.BLOCK_POS, blockposition_mutableblockposition).set(LootContextParams.THIS_ENTITY, this.cat).withRandom(random);
            List<ItemStack> list = loottable.getRandomItems(loottableinfo_builder.create(LootContextParamSets.GIFT));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemStack itemstack = (ItemStack) iterator.next();

                this.cat.level.addFreshEntity(new ItemEntity(this.cat.level, (double) blockposition_mutableblockposition.getX() - (double) Mth.sin(this.cat.yBodyRot * 0.017453292F), (double) blockposition_mutableblockposition.getY(), (double) blockposition_mutableblockposition.getZ() + (double) Mth.cos(this.cat.yBodyRot * 0.017453292F), itemstack));
            }

        }

        @Override
        public void tick() {
            if (this.ownerPlayer != null && this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo((double) this.goalPos.getX(), (double) this.goalPos.getY(), (double) this.goalPos.getZ(), 1.100000023841858D);
                if (this.cat.distanceToSqr((Entity) this.ownerPlayer) < 2.5D) {
                    ++this.onBedTicks;
                    if (this.onBedTicks > 16) {
                        this.cat.setLying(true);
                        this.cat.setRelaxStateOne(false);
                    } else {
                        this.cat.lookAt((Entity) this.ownerPlayer, 45.0F, 45.0F);
                        this.cat.setRelaxStateOne(true);
                    }
                } else {
                    this.cat.setLying(false);
                }
            }

        }
    }

    static class CatTemptGoal extends TemptGoal {

        @Nullable
        private LivingEntity selectedPlayer; // CraftBukkit
        private final Cat cat;

        public CatTemptGoal(Cat entitycat, double d0, Ingredient recipeitemstack, boolean flag) {
            super(entitycat, d0, recipeitemstack, flag);
            this.cat = entitycat;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.selectedPlayer == null && this.mob.getRandom().nextInt(600) == 0) {
                this.selectedPlayer = this.player;
            } else if (this.mob.getRandom().nextInt(500) == 0) {
                this.selectedPlayer = null;
            }

        }

        @Override
        protected boolean canScare() {
            return this.selectedPlayer != null && this.selectedPlayer.equals(this.player) ? false : super.canScare();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.cat.isTame();
        }
    }

    static class CatAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Cat cat;

        public CatAvoidEntityGoal(Cat entitycat, Class<T> oclass, float f, double d0, double d1) {
            // Predicate predicate = IEntitySelector.e; // CraftBukkit - decompile error

            super(entitycat, oclass, f, d0, d1, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test); // CraftBukkit - decompile error
            this.cat = entitycat;
        }

        @Override
        public boolean canUse() {
            return !this.cat.isTame() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.cat.isTame() && super.canContinueToUse();
        }
    }
}
