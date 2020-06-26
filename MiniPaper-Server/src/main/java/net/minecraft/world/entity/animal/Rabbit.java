package net.minecraft.world.entity.animal;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class Rabbit extends Animal {

    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Rabbit.class, EntityDataSerializers.INT);
    private static final ResourceLocation KILLER_BUNNY = new ResourceLocation("killer_bunny");
    private int jumpTicks;
    private int jumpDuration;
    private boolean wasOnGround;
    private int jumpDelayTicks;
    private int moreCarrotTicks;

    public Rabbit(EntityType<? extends Rabbit> entitytypes, Level world) {
        super(entitytypes, world);
        this.jumpControl = new Rabbit.ControllerJumpRabbit(this);
        this.moveControl = new Rabbit.ControllerMoveRabbit(this);
        this.initializePathFinderGoals(); // CraftBukkit - moved code
    }

    // CraftBukkit start - code from constructor
    public void initializePathFinderGoals(){
        this.setSpeedModifier(0.0D);
    }
    // CraftBukkit end

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new Rabbit.PathfinderGoalRabbitPanic(this, 2.2D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0D, Ingredient.of(Items.CARROT, Items.GOLDEN_CARROT, Blocks.DANDELION), false));
        this.goalSelector.addGoal(4, new Rabbit.PathfinderGoalRabbitAvoidTarget<>(this, Player.class, 8.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(4, new Rabbit.PathfinderGoalRabbitAvoidTarget<>(this, Wolf.class, 10.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(4, new Rabbit.PathfinderGoalRabbitAvoidTarget<>(this, Monster.class, 4.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(5, new Rabbit.RaidGardenGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
    }

    @Override
    protected float getJumpPower() {
        if (!this.horizontalCollision && (!this.moveControl.hasWanted() || this.moveControl.getWantedY() <= this.getY() + 0.5D)) {
            Path pathentity = this.navigation.getPath();

            if (pathentity != null && pathentity.getIndex() < pathentity.getSize()) {
                Vec3 vec3d = pathentity.currentPos((Entity) this);

                if (vec3d.y > this.getY() + 0.5D) {
                    return 0.5F;
                }
            }

            return this.moveControl.getSpeedModifier() <= 0.6D ? 0.2F : 0.3F;
        } else {
            return 0.5F;
        }
    }

    @Override
    protected void jumpFromGround() {
        super.jumpFromGround();
        double d0 = this.moveControl.getSpeedModifier();

        if (d0 > 0.0D) {
            double d1 = getHorizontalDistanceSqr(this.getDeltaMovement());

            if (d1 < 0.01D) {
                this.moveRelative(0.1F, new Vec3(0.0D, 0.0D, 1.0D));
            }
        }

        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, (byte) 1);
        }

    }

    public void setSpeedModifier(double d0) {
        this.getNavigation().setSpeedModifier(d0);
        this.moveControl.setWantedPosition(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ(), d0);
    }

    @Override
    public void setJumping(boolean flag) {
        super.setJumping(flag);
        if (flag) {
            this.playSound(this.getSoundJump(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }

    }

    public void startJumping() {
        this.setJumping(true);
        this.jumpDuration = 10;
        this.jumpTicks = 0;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Rabbit.DATA_TYPE_ID, 0);
    }

    @Override
    public void customServerAiStep() {
        if (this.jumpDelayTicks > 0) {
            --this.jumpDelayTicks;
        }

        if (this.moreCarrotTicks > 0) {
            this.moreCarrotTicks -= this.random.nextInt(3);
            if (this.moreCarrotTicks < 0) {
                this.moreCarrotTicks = 0;
            }
        }

        if (this.onGround) {
            if (!this.wasOnGround) {
                this.setJumping(false);
                this.checkLandingDelay();
            }

            if (this.getRabbitType() == 99 && this.jumpDelayTicks == 0) {
                LivingEntity entityliving = this.getTarget();

                if (entityliving != null && this.distanceToSqr((Entity) entityliving) < 16.0D) {
                    this.facePoint(entityliving.getX(), entityliving.getZ());
                    this.moveControl.setWantedPosition(entityliving.getX(), entityliving.getY(), entityliving.getZ(), this.moveControl.getSpeedModifier());
                    this.startJumping();
                    this.wasOnGround = true;
                }
            }

            Rabbit.ControllerJumpRabbit entityrabbit_controllerjumprabbit = (Rabbit.ControllerJumpRabbit) this.jumpControl;

            if (!entityrabbit_controllerjumprabbit.c()) {
                if (this.moveControl.hasWanted() && this.jumpDelayTicks == 0) {
                    Path pathentity = this.navigation.getPath();
                    Vec3 vec3d = new Vec3(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ());

                    if (pathentity != null && pathentity.getIndex() < pathentity.getSize()) {
                        vec3d = pathentity.currentPos((Entity) this);
                    }

                    this.facePoint(vec3d.x, vec3d.z);
                    this.startJumping();
                }
            } else if (!entityrabbit_controllerjumprabbit.d()) {
                this.enableJumpControl();
            }
        }

        this.wasOnGround = this.onGround;
    }

    @Override
    public boolean canSpawnSprintParticle() {
        return false;
    }

    private void facePoint(double d0, double d1) {
        this.yRot = (float) (Mth.atan2(d1 - this.getZ(), d0 - this.getX()) * 57.2957763671875D) - 90.0F;
    }

    private void enableJumpControl() {
        ((Rabbit.ControllerJumpRabbit) this.jumpControl).a(true);
    }

    private void disableJumpControl() {
        ((Rabbit.ControllerJumpRabbit) this.jumpControl).a(false);
    }

    private void setLandingDelay() {
        if (this.moveControl.getSpeedModifier() < 2.2D) {
            this.jumpDelayTicks = 10;
        } else {
            this.jumpDelayTicks = 1;
        }

    }

    private void checkLandingDelay() {
        this.setLandingDelay();
        this.disableJumpControl();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.jumpTicks != this.jumpDuration) {
            ++this.jumpTicks;
        } else if (this.jumpDuration != 0) {
            this.jumpTicks = 0;
            this.jumpDuration = 0;
            this.setJumping(false);
        }

    }

    public static AttributeSupplier.Builder eM() {
        return Mob.p().a(Attributes.MAX_HEALTH, 3.0D).a(Attributes.MOVEMENT_SPEED, 0.30000001192092896D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("RabbitType", this.getRabbitType());
        nbttagcompound.putInt("MoreCarrotTicks", this.moreCarrotTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setRabbitType(nbttagcompound.getInt("RabbitType"));
        this.moreCarrotTicks = nbttagcompound.getInt("MoreCarrotTicks");
    }

    protected SoundEvent getSoundJump() {
        return SoundEvents.RABBIT_JUMP;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.RABBIT_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.RABBIT_DEATH;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (this.getRabbitType() == 99) {
            this.playSound(SoundEvents.RABBIT_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            return entity.hurt(DamageSource.mobAttack(this), 8.0F);
        } else {
            return entity.hurt(DamageSource.mobAttack(this), 3.0F);
        }
    }

    @Override
    public SoundSource getSoundSource() {
        return this.getRabbitType() == 99 ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        return this.isInvulnerableTo(damagesource) ? false : super.hurt(damagesource, f);
    }

    private boolean isTemptingItem(Item item) {
        return item == Items.CARROT || item == Items.GOLDEN_CARROT || item == Blocks.DANDELION.asItem();
    }

    @Override
    public Rabbit getBreedOffspring(AgableMob entityageable) {
        Rabbit entityrabbit = (Rabbit) EntityType.RABBIT.create(this.level);
        int i = this.getRandomRabbitType((LevelAccessor) this.level);

        if (this.random.nextInt(20) != 0) {
            if (entityageable instanceof Rabbit && this.random.nextBoolean()) {
                i = ((Rabbit) entityageable).getRabbitType();
            } else {
                i = this.getRabbitType();
            }
        }

        entityrabbit.setRabbitType(i);
        return entityrabbit;
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return this.isTemptingItem(itemstack.getItem());
    }

    public int getRabbitType() {
        return (Integer) this.entityData.get(Rabbit.DATA_TYPE_ID);
    }

    public void setRabbitType(int i) {
        if (i == 99) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(8.0D);
            this.goalSelector.addGoal(4, new Rabbit.PathfinderGoalKillerRabbitMeleeAttack(this));
            this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(new Class[0])); // CraftBukkit - decompile error
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Wolf.class, true));
            if (!this.hasCustomName()) {
                this.setCustomName(new TranslatableComponent(Util.makeDescriptionId("entity", Rabbit.KILLER_BUNNY)));
            }
        }

        this.entityData.set(Rabbit.DATA_TYPE_ID, i);
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        int i = this.getRandomRabbitType(generatoraccess);

        if (groupdataentity instanceof Rabbit.GroupDataRabbit) {
            i = ((Rabbit.GroupDataRabbit) groupdataentity).a;
        } else {
            groupdataentity = new Rabbit.GroupDataRabbit(i);
        }

        this.setRabbitType(i);
        return super.prepare(generatoraccess, difficultydamagescaler, enummobspawn, (SpawnGroupData) groupdataentity, nbttagcompound);
    }

    private int getRandomRabbitType(LevelAccessor generatoraccess) {
        Biome biomebase = generatoraccess.getBiome(this.blockPosition());
        int i = this.random.nextInt(100);

        return biomebase.getPrecipitation() == Biome.Precipitation.SNOW ? (i < 80 ? 1 : 3) : (biomebase.getBiomeCategory() == Biome.BiomeCategory.DESERT ? 4 : (i < 50 ? 0 : (i < 90 ? 5 : 2)));
    }

    public static boolean checkRabbitSpawnRules(EntityType<Rabbit> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        BlockState iblockdata = generatoraccess.getType(blockposition.below());

        return (iblockdata.is(Blocks.GRASS_BLOCK) || iblockdata.is(Blocks.SNOW) || iblockdata.is(Blocks.SAND)) && generatoraccess.getRawBrightness(blockposition, 0) > 8;
    }

    private boolean wantsMoreFood() {
        return this.moreCarrotTicks == 0;
    }

    static class PathfinderGoalKillerRabbitMeleeAttack extends MeleeAttackGoal {

        public PathfinderGoalKillerRabbitMeleeAttack(Rabbit entityrabbit) {
            super(entityrabbit, 1.4D, true);
        }

        @Override
        protected double getAttackReachSqr(LivingEntity entityliving) {
            return (double) (4.0F + entityliving.getBbWidth());
        }
    }

    static class PathfinderGoalRabbitPanic extends PanicGoal {

        private final Rabbit g;

        public PathfinderGoalRabbitPanic(Rabbit entityrabbit, double d0) {
            super(entityrabbit, d0);
            this.g = entityrabbit;
        }

        @Override
        public void tick() {
            super.tick();
            this.g.setSpeedModifier(this.speedModifier);
        }
    }

    static class RaidGardenGoal extends MoveToBlockGoal {

        private final Rabbit rabbit;
        private boolean wantsToRaid;
        private boolean canRaid;

        public RaidGardenGoal(Rabbit entityrabbit) {
            super(entityrabbit, 0.699999988079071D, 16);
            this.rabbit = entityrabbit;
        }

        @Override
        public boolean canUse() {
            if (this.nextStartTick <= 0) {
                if (!this.rabbit.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    return false;
                }

                this.canRaid = false;
                this.wantsToRaid = this.rabbit.wantsMoreFood();
                this.wantsToRaid = true;
            }

            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canRaid && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.getControllerLook().setLookAt((double) this.blockPos.getX() + 0.5D, (double) (this.blockPos.getY() + 1), (double) this.blockPos.getZ() + 0.5D, 10.0F, (float) this.rabbit.getMaxHeadXRot());
            if (this.isReachedTarget()) {
                Level world = this.rabbit.level;
                BlockPos blockposition = this.blockPos.above();
                BlockState iblockdata = world.getType(blockposition);
                Block block = iblockdata.getBlock();

                if (this.canRaid && block instanceof CarrotBlock) {
                    Integer integer = (Integer) iblockdata.getValue(CarrotBlock.AGE);

                    if (integer == 0) {
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.rabbit, blockposition, Blocks.AIR.getBlockData()).isCancelled()) {
                            return;
                        }
                        // CraftBukkit end
                        world.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 2);
                        world.destroyBlock(blockposition, true, this.rabbit);
                    } else {
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(
                                this.rabbit,
                                blockposition,
                                iblockdata.setValue(CarrotBlock.AGE, integer - 1)
                        ).isCancelled()) {
                            return;
                        }
                        // CraftBukkit end
                        world.setTypeAndData(blockposition, (BlockState) iblockdata.setValue(CarrotBlock.AGE, integer - 1), 2);
                        world.levelEvent(2001, blockposition, Block.getCombinedId(iblockdata));
                    }

                    this.rabbit.moreCarrotTicks = 40;
                }

                this.canRaid = false;
                this.nextStartTick = 10;
            }

        }

        @Override
        protected boolean isValidTarget(LevelReader iworldreader, BlockPos blockposition) {
            Block block = iworldreader.getType(blockposition).getBlock();

            if (block == Blocks.FARMLAND && this.wantsToRaid && !this.canRaid) {
                blockposition = blockposition.above();
                BlockState iblockdata = iworldreader.getType(blockposition);

                block = iblockdata.getBlock();
                if (block instanceof CarrotBlock && ((CarrotBlock) block).isRipe(iblockdata)) {
                    this.canRaid = true;
                    return true;
                }
            }

            return false;
        }
    }

    static class PathfinderGoalRabbitAvoidTarget<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Rabbit i;

        public PathfinderGoalRabbitAvoidTarget(Rabbit entityrabbit, Class<T> oclass, float f, double d0, double d1) {
            super(entityrabbit, oclass, f, d0, d1);
            this.i = entityrabbit;
        }

        @Override
        public boolean canUse() {
            return this.i.getRabbitType() != 99 && super.canUse();
        }
    }

    static class ControllerMoveRabbit extends MoveControl {

        private final Rabbit i;
        private double j;

        public ControllerMoveRabbit(Rabbit entityrabbit) {
            super(entityrabbit);
            this.i = entityrabbit;
        }

        @Override
        public void tick() {
            if (this.i.onGround && !this.i.jumping && !((Rabbit.ControllerJumpRabbit) this.i.jumpControl).c()) {
                this.i.setSpeedModifier(0.0D);
            } else if (this.hasWanted()) {
                this.i.setSpeedModifier(this.j);
            }

            super.tick();
        }

        @Override
        public void setWantedPosition(double d0, double d1, double d2, double d3) {
            if (this.i.isInWater()) {
                d3 = 1.5D;
            }

            super.setWantedPosition(d0, d1, d2, d3);
            if (d3 > 0.0D) {
                this.j = d3;
            }

        }
    }

    public class ControllerJumpRabbit extends JumpControl {

        private final Rabbit c;
        private boolean d;

        public ControllerJumpRabbit(Rabbit entityrabbit) {
            super(entityrabbit);
            this.c = entityrabbit;
        }

        public boolean c() {
            return this.jump;
        }

        public boolean d() {
            return this.d;
        }

        public void a(boolean flag) {
            this.d = flag;
        }

        @Override
        public void tick() {
            if (this.jump) {
                this.c.startJumping();
                this.jump = false;
            }

        }
    }

    public static class GroupDataRabbit extends AgableMob.AgableMobGroupData {

        public final int a;

        public GroupDataRabbit(int i) {
            this.a = i;
            this.setBabySpawnChance(1.0F);
        }
    }
}
