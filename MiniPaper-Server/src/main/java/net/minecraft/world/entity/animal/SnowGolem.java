package net.minecraft.world.entity.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class SnowGolem extends AbstractGolem implements Shearable, RangedAttackMob {

    private static final EntityDataAccessor<Byte> DATA_PUMPKIN_ID = SynchedEntityData.defineId(SnowGolem.class, EntityDataSerializers.BYTE);

    public SnowGolem(EntityType<? extends SnowGolem> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 20, 10.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D, 1.0000001E-5F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (entityliving) -> {
            return entityliving instanceof Enemy;
        }));
    }

    public static AttributeSupplier.Builder m() {
        return Mob.p().a(Attributes.MAX_HEALTH, 4.0D).a(Attributes.MOVEMENT_SPEED, 0.20000000298023224D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(SnowGolem.DATA_PUMPKIN_ID, (byte) 16);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("Pumpkin", this.hasPumpkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("Pumpkin")) {
            this.setPumpkin(nbttagcompound.getBoolean("Pumpkin"));
        }

    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level.isClientSide) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY());
            int k = Mth.floor(this.getZ());

            if (this.level.getBiome(new BlockPos(i, 0, k)).getTemperature(new BlockPos(i, j, k)) > 1.0F) {
                this.hurt(CraftEventFactory.MELTING, 1.0F); // CraftBukkit - DamageSource.BURN -> CraftEventFactory.MELTING
            }

            if (!this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return;
            }

            BlockState iblockdata = Blocks.SNOW.getBlockData();

            for (int l = 0; l < 4; ++l) {
                i = Mth.floor(this.getX() + (double) ((float) (l % 2 * 2 - 1) * 0.25F));
                j = Mth.floor(this.getY());
                k = Mth.floor(this.getZ() + (double) ((float) (l / 2 % 2 * 2 - 1) * 0.25F));
                BlockPos blockposition = new BlockPos(i, j, k);

                if (this.level.getType(blockposition).isAir() && this.level.getBiome(blockposition).getTemperature(blockposition) < 0.8F && iblockdata.canSurvive(this.level, blockposition)) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this.level, blockposition, iblockdata, this); // CraftBukkit
                }
            }
        }

    }

    @Override
    public void performRangedAttack(LivingEntity entityliving, float f) {
        Snowball entitysnowball = new Snowball(this.level, this);
        double d0 = entityliving.getEyeY() - 1.100000023841858D;
        double d1 = entityliving.getX() - this.getX();
        double d2 = d0 - entitysnowball.getY();
        double d3 = entityliving.getZ() - this.getZ();
        float f1 = Mth.sqrt(d1 * d1 + d3 * d3) * 0.2F;

        entitysnowball.shoot(d1, d2 + (double) f1, d3, 1.6F, 12.0F);
        this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level.addFreshEntity(entitysnowball);
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 1.7F;
    }

    @Override
    protected InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.getItem() == Items.SHEARS && this.readyForShearing()) {
            // CraftBukkit start
            if (!CraftEventFactory.handlePlayerShearEntityEvent(entityhuman, this, itemstack, enumhand)) {
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            this.shear(SoundSource.PLAYERS);
            if (!this.level.isClientSide) {
                itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(enumhand);
                });
            }

            return InteractionResult.sidedSuccess(this.level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public void shear(SoundSource soundcategory) {
        this.level.playSound((Player) null, (Entity) this, SoundEvents.SNOW_GOLEM_SHEAR, soundcategory, 1.0F, 1.0F);
        if (!this.level.isClientSide()) {
            this.setPumpkin(false);
            this.spawnAtLocation(new ItemStack(Items.CARVED_PUMPKIN), 1.7F);
        }

    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && this.hasPumpkin();
    }

    public boolean hasPumpkin() {
        return ((Byte) this.entityData.get(SnowGolem.DATA_PUMPKIN_ID) & 16) != 0;
    }

    public void setPumpkin(boolean flag) {
        byte b0 = (Byte) this.entityData.get(SnowGolem.DATA_PUMPKIN_ID);

        if (flag) {
            this.entityData.set(SnowGolem.DATA_PUMPKIN_ID, (byte) (b0 | 16));
        } else {
            this.entityData.set(SnowGolem.DATA_PUMPKIN_ID, (byte) (b0 & -17));
        }

    }

    @Nullable
    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.SNOW_GOLEM_AMBIENT;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.SNOW_GOLEM_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.SNOW_GOLEM_DEATH;
    }
}
