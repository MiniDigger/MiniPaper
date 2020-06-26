package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class Silverfish extends Monster {

    private Silverfish.SilverfishWakeUpFriendsGoal friendsGoal;

    public Silverfish(EntityType<? extends Silverfish> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        this.friendsGoal = new Silverfish.SilverfishWakeUpFriendsGoal(this);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, this.friendsGoal);
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(5, new Silverfish.PathfinderGoalSilverfishHideInBlock(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(new Class[0])); // CraftBukkit - decompile error
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public double getMyRidingOffset() {
        return 0.1D;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.13F;
    }

    public static AttributeSupplier.Builder m() {
        return Monster.eS().a(Attributes.MAX_HEALTH, 8.0D).a(Attributes.MOVEMENT_SPEED, 0.25D).a(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected boolean isMovementNoisy() {
        return false;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos blockposition, BlockState iblockdata) {
        this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean hurt(DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(damagesource)) {
            return false;
        } else {
            if ((damagesource instanceof EntityDamageSource || damagesource == DamageSource.MAGIC) && this.friendsGoal != null) {
                this.friendsGoal.notifyHurt();
            }

            return super.hurt(damagesource, f);
        }
    }

    @Override
    public void tick() {
        this.yBodyRot = this.yRot;
        super.tick();
    }

    @Override
    public void setYBodyRot(float f) {
        this.yRot = f;
        super.setYBodyRot(f);
    }

    @Override
    public float getWalkTargetValue(BlockPos blockposition, LevelReader iworldreader) {
        return InfestedBlock.isCompatibleHostBlock(iworldreader.getType(blockposition.below())) ? 10.0F : super.getWalkTargetValue(blockposition, iworldreader);
    }

    public static boolean checkSliverfishSpawnRules(EntityType<Silverfish> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        if (checkAnyLightMonsterSpawnRules(entitytypes, generatoraccess, enummobspawn, blockposition, random)) {
            Player entityhuman = generatoraccess.getNearestPlayer((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, 5.0D, true);

            return entityhuman == null;
        } else {
            return false;
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    static class PathfinderGoalSilverfishHideInBlock extends RandomStrollGoal {

        private Direction h;
        private boolean i;

        public PathfinderGoalSilverfishHideInBlock(Silverfish entitysilverfish) {
            super(entitysilverfish, 1.0D, 10);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null) {
                return false;
            } else if (!this.mob.getNavigation().isDone()) {
                return false;
            } else {
                Random random = this.mob.getRandom();

                if (this.mob.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && random.nextInt(10) == 0) {
                    this.h = Direction.getRandom(random);
                    BlockPos blockposition = (new BlockPos(this.mob.getX(), this.mob.getY() + 0.5D, this.mob.getZ())).relative(this.h);
                    BlockState iblockdata = this.mob.level.getType(blockposition);

                    if (InfestedBlock.isCompatibleHostBlock(iblockdata)) {
                        this.i = true;
                        return true;
                    }
                }

                this.i = false;
                return super.canUse();
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.i ? false : super.canContinueToUse();
        }

        @Override
        public void start() {
            if (!this.i) {
                super.start();
            } else {
                Level world = this.mob.level;
                BlockPos blockposition = (new BlockPos(this.mob.getX(), this.mob.getY() + 0.5D, this.mob.getZ())).relative(this.h);
                BlockState iblockdata = world.getType(blockposition);

                if (InfestedBlock.isCompatibleHostBlock(iblockdata)) {
                    // CraftBukkit start
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.mob, blockposition, InfestedBlock.stateByHostBlock(iblockdata.getBlock())).isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    world.setTypeAndData(blockposition, InfestedBlock.stateByHostBlock(iblockdata.getBlock()), 3);
                    this.mob.spawnAnim();
                    this.mob.remove();
                }

            }
        }
    }

    static class SilverfishWakeUpFriendsGoal extends Goal {

        private final Silverfish silverfish;
        private int lookForFriends;

        public SilverfishWakeUpFriendsGoal(Silverfish entitysilverfish) {
            this.silverfish = entitysilverfish;
        }

        public void notifyHurt() {
            if (this.lookForFriends == 0) {
                this.lookForFriends = 20;
            }

        }

        @Override
        public boolean canUse() {
            return this.lookForFriends > 0;
        }

        @Override
        public void tick() {
            --this.lookForFriends;
            if (this.lookForFriends <= 0) {
                Level world = this.silverfish.level;
                Random random = this.silverfish.getRandom();
                BlockPos blockposition = this.silverfish.blockPosition();

                for (int i = 0; i <= 5 && i >= -5; i = (i <= 0 ? 1 : 0) - i) {
                    for (int j = 0; j <= 10 && j >= -10; j = (j <= 0 ? 1 : 0) - j) {
                        for (int k = 0; k <= 10 && k >= -10; k = (k <= 0 ? 1 : 0) - k) {
                            BlockPos blockposition1 = blockposition.offset(j, i, k);
                            BlockState iblockdata = world.getType(blockposition1);
                            Block block = iblockdata.getBlock();

                            if (block instanceof InfestedBlock) {
                                // CraftBukkit start
                                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.silverfish, blockposition1, Blocks.AIR.getBlockData()).isCancelled()) {
                                    continue;
                                }
                                // CraftBukkit end
                                if (world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                                    world.destroyBlock(blockposition1, true, this.silverfish);
                                } else {
                                    world.setTypeAndData(blockposition1, ((InfestedBlock) block).getHostBlock().getBlockData(), 3);
                                }

                                if (random.nextBoolean()) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
