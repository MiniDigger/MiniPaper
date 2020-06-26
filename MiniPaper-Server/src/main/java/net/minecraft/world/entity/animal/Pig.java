package net.minecraft.world.entity.animal;

import com.google.common.collect.UnmodifiableIterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemBasedSteering;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class Pig extends Animal implements ItemSteerable, Saddleable {

    private static final EntityDataAccessor<Boolean> DATA_SADDLE_ID = SynchedEntityData.defineId(Pig.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BOOST_TIME = SynchedEntityData.defineId(Pig.class, EntityDataSerializers.INT);
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.CARROT, Items.POTATO, Items.BEETROOT);
    public final ItemBasedSteering steering;

    public Pig(EntityType<? extends Pig> entitytypes, Level world) {
        super(entitytypes, world);
        this.steering = new ItemBasedSteering(this.entityData, Pig.DATA_BOOST_TIME, Pig.DATA_SADDLE_ID);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.2D, Ingredient.of(Items.CARROT_ON_A_STICK), false));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.2D, false, Pig.FOOD_ITEMS));
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder eL() {
        return Mob.p().a(Attributes.MAX_HEALTH, 10.0D).a(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Nullable
    @Override
    public Entity getControllingPassenger() {
        return this.getPassengers().isEmpty() ? null : (Entity) this.getPassengers().get(0);
    }

    @Override
    public boolean canBeControlledByRider() {
        Entity entity = this.getControllingPassenger();

        if (!(entity instanceof Player)) {
            return false;
        } else {
            Player entityhuman = (Player) entity;

            return entityhuman.getMainHandItem().getItem() == Items.CARROT_ON_A_STICK || entityhuman.getOffhandItem().getItem() == Items.CARROT_ON_A_STICK;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> datawatcherobject) {
        if (Pig.DATA_BOOST_TIME.equals(datawatcherobject) && this.level.isClientSide) {
            this.steering.onSynced();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Pig.DATA_SADDLE_ID, false);
        this.entityData.register(Pig.DATA_BOOST_TIME, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        this.steering.addAdditionalSaveData(nbttagcompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.steering.readAdditionalSaveData(nbttagcompound);
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.PIG_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.PIG_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.PIG_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(SoundEvents.PIG_STEP, 0.15F, 1.0F);
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        boolean flag = this.isFood(entityhuman.getItemInHand(enumhand));

        if (!flag && this.isSaddled() && !this.isVehicle()) {
            if (!this.level.isClientSide) {
                entityhuman.startRiding(this);
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            InteractionResult enuminteractionresult = super.mobInteract(entityhuman, enumhand);

            if (!enuminteractionresult.consumesAction()) {
                ItemStack itemstack = entityhuman.getItemInHand(enumhand);

                return itemstack.getItem() == Items.SADDLE ? itemstack.interactLivingEntity(entityhuman, (LivingEntity) this, enumhand) : InteractionResult.PASS;
            } else {
                return enuminteractionresult;
            }
        }
    }

    @Override
    public boolean isSaddleable() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (this.isSaddled()) {
            this.spawnAtLocation((ItemLike) Items.SADDLE);
        }

    }

    @Override
    public boolean isSaddled() {
        return this.steering.hasSaddle();
    }

    @Override
    public void equipSaddle(@Nullable SoundSource soundcategory) {
        this.steering.setSaddle(true);
        if (soundcategory != null) {
            this.level.playSound((Player) null, (Entity) this, SoundEvents.PIG_SADDLE, soundcategory, 0.5F, 1.0F);
        }

    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity entityliving) {
        Direction enumdirection = this.getMotionDirection();

        if (enumdirection.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(entityliving);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(enumdirection);
            BlockPos blockposition = this.blockPosition();
            BlockPos.MutableBlockPosition blockposition_mutableblockposition = new BlockPos.MutableBlockPosition();
            UnmodifiableIterator unmodifiableiterator = entityliving.getDismountPoses().iterator();

            while (unmodifiableiterator.hasNext()) {
                Pose entitypose = (Pose) unmodifiableiterator.next();
                AABB axisalignedbb = entityliving.getLocalBoundsForPose(entitypose);
                int[][] aint1 = aint;
                int i = aint.length;

                for (int j = 0; j < i; ++j) {
                    int[] aint2 = aint1[j];

                    blockposition_mutableblockposition.d(blockposition.getX() + aint2[0], blockposition.getY(), blockposition.getZ() + aint2[1]);
                    double d0 = this.level.getRelativeFloorHeight(blockposition_mutableblockposition);

                    if (DismountHelper.isFloorValid(d0)) {
                        Vec3 vec3d = Vec3.upFromBottomCenterOf((Vec3i) blockposition_mutableblockposition, d0);

                        if (DismountHelper.canDismountTo(this.level, entityliving, axisalignedbb.move(vec3d))) {
                            entityliving.setPose(entitypose);
                            return vec3d;
                        }
                    }
                }
            }

            return super.getDismountLocationForPassenger(entityliving);
        }
    }

    @Override
    public void thunderHit(LightningBolt entitylightning) {
        if (this.level.getDifficulty() != Difficulty.PEACEFUL) {
            ZombifiedPiglin entitypigzombie = (ZombifiedPiglin) EntityType.ZOMBIFIED_PIGLIN.create(this.level);

            entitypigzombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            entitypigzombie.moveTo(this.getX(), this.getY(), this.getZ(), this.yRot, this.xRot);
            entitypigzombie.setNoAi(this.isNoAi());
            entitypigzombie.setBaby(this.isBaby());
            if (this.hasCustomName()) {
                entitypigzombie.setCustomName(this.getCustomName());
                entitypigzombie.setCustomNameVisible(this.isCustomNameVisible());
            }

            entitypigzombie.setPersistenceRequired();
            // CraftBukkit start
            if (CraftEventFactory.callPigZapEvent(this, entitylightning, entitypigzombie).isCancelled()) {
                return;
            }
            // CraftBukkit - added a reason for spawning this creature
            this.level.addEntity(entitypigzombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING);
            // CraftBukkit end
            this.remove();
        } else {
            super.thunderHit(entitylightning);
        }

    }

    @Override
    public void travel(Vec3 vec3d) {
        this.travel((Mob) this, this.steering, vec3d);
    }

    @Override
    public float getSteeringSpeed() {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.225F;
    }

    @Override
    public void travelWithInput(Vec3 vec3d) {
        super.travel(vec3d);
    }

    @Override
    public boolean boost() {
        return this.steering.boost(this.getRandom());
    }

    @Override
    public Pig getBreedOffspring(AgableMob entityageable) {
        return (Pig) EntityType.PIG.create(this.level);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return Pig.FOOD_ITEMS.test(itemstack);
    }
}
