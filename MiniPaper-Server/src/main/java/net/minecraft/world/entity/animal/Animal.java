package net.minecraft.world.entity.animal;

import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

public abstract class Animal extends AgableMob {

    public int inLove;
    public UUID loveCause;
    public ItemStack breedItem; // CraftBukkit - Add breedItem variable

    protected Animal(EntityType<? extends Animal> entitytypes, Level world) {
        super(entitytypes, world);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    protected void customServerAiStep() {
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        super.customServerAiStep();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        if (this.inLove > 0) {
            --this.inLove;
            if (this.inLove % 10 == 0) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;

                this.level.addParticle(ParticleTypes.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
            }
        }

    }

    /* CraftBukkit start
    // Function disabled as it has no special function anymore after
    // setSitting is disabled.
    @Override
    public boolean damageEntity(DamageSource damagesource, float f) {
        if (this.isInvulnerable(damagesource)) {
            return false;
        } else {
            this.loveTicks = 0;
            return super.damageEntity(damagesource, f);
        }
    }
    // CraftBukkit end */

    @Override
    public float getWalkTargetValue(BlockPos blockposition, LevelReader iworldreader) {
        return iworldreader.getType(blockposition.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : iworldreader.getBrightness(blockposition) - 0.5F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("InLove", this.inLove);
        if (this.loveCause != null) {
            nbttagcompound.putUUID("LoveCause", this.loveCause);
        }

    }

    @Override
    public double getMyRidingOffset() {
        return 0.14D;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.inLove = nbttagcompound.getInt("InLove");
        this.loveCause = nbttagcompound.hasUUID("LoveCause") ? nbttagcompound.getUUID("LoveCause") : null;
    }

    public static boolean checkAnimalSpawnRules(EntityType<? extends Animal> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return generatoraccess.getType(blockposition.below()).is(Blocks.GRASS_BLOCK) && generatoraccess.getRawBrightness(blockposition, 0) > 8;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return false;
    }

    @Override
    protected int getExperienceReward(Player entityhuman) {
        return 1 + this.level.random.nextInt(3);
    }

    public boolean isFood(ItemStack itemstack) {
        return itemstack.getItem() == Items.WHEAT;
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (this.isFood(itemstack)) {
            int i = this.getAge();

            if (!this.level.isClientSide && i == 0 && this.canFallInLove()) {
                this.usePlayerItem(entityhuman, itemstack);
                this.setInLove(entityhuman);
                return InteractionResult.SUCCESS;
            }

            if (this.isBaby()) {
                this.usePlayerItem(entityhuman, itemstack);
                this.ageUp((int) ((float) (-i / 20) * 0.1F), true);
                return InteractionResult.sidedSuccess(this.level.isClientSide);
            }

            if (this.level.isClientSide) {
                return InteractionResult.CONSUME;
            }
        }

        return super.mobInteract(entityhuman, enumhand);
    }

    protected void usePlayerItem(Player entityhuman, ItemStack itemstack) {
        if (!entityhuman.abilities.instabuild) {
            itemstack.shrink(1);
        }

    }

    public boolean canFallInLove() {
        return this.inLove <= 0;
    }

    public void setInLove(@Nullable Player entityhuman) {
        this.inLove = 600;
        if (entityhuman != null) {
            this.loveCause = entityhuman.getUUID();
        }
        this.breedItem = entityhuman.inventory.getSelected(); // CraftBukkit

        this.level.broadcastEntityEvent(this, (byte) 18);
    }

    public void setInLoveTime(int i) {
        this.inLove = i;
    }

    public int getInLoveTime() {
        return this.inLove;
    }

    @Nullable
    public ServerPlayer getLoveCause() {
        if (this.loveCause == null) {
            return null;
        } else {
            Player entityhuman = this.level.getPlayerByUUID(this.loveCause);

            return entityhuman instanceof ServerPlayer ? (ServerPlayer) entityhuman : null;
        }
    }

    public boolean isInLove() {
        return this.inLove > 0;
    }

    public void resetLove() {
        this.inLove = 0;
    }

    public boolean canMate(Animal entityanimal) {
        return entityanimal == this ? false : (entityanimal.getClass() != this.getClass() ? false : this.isInLove() && entityanimal.isInLove());
    }

    public void spawnChildFromBreeding(Level world, Animal entityanimal) {
        AgableMob entityageable = this.getBreedOffspring(entityanimal);

        if (entityageable != null) {
            // CraftBukkit start - set persistence for tame animals
            if (entityageable instanceof TamableAnimal && ((TamableAnimal) entityageable).isTame()) {
                entityageable.persistenceRequired = true;
            }
            // CraftBukkit end
            ServerPlayer entityplayer = this.getLoveCause();

            if (entityplayer == null && entityanimal.getLoveCause() != null) {
                entityplayer = entityanimal.getLoveCause();
            }
            // CraftBukkit start - call EntityBreedEvent
            int experience = this.getRandom().nextInt(7) + 1;
            org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityageable, this, entityanimal, entityplayer, this.breedItem, experience);
            if (entityBreedEvent.isCancelled()) {
                return;
            }
            experience = entityBreedEvent.getExperience();
            // CraftBukkit end

            if (entityplayer != null) {
                entityplayer.awardStat(Stats.ANIMALS_BRED);
                CriteriaTriggers.BRED_ANIMALS.trigger(entityplayer, this, entityanimal, entityageable);
            }

            this.setAge(6000);
            entityanimal.setAge(6000);
            this.resetLove();
            entityanimal.resetLove();
            entityageable.setBaby(true);
            entityageable.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            world.addEntity(entityageable, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - added SpawnReason
            world.broadcastEntityEvent(this, (byte) 18);
            if (world.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                // CraftBukkit start - use event experience
                if (experience > 0) {
                    world.addFreshEntity(new ExperienceOrb(world, this.getX(), this.getY(), this.getZ(), experience));
                }
                // CraftBukkit end
            }

        }
    }
}
