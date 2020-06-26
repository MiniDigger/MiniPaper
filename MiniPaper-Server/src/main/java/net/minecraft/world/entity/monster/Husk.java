package net.minecraft.world.entity.monster;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class Husk extends Zombie {

    public Husk(EntityType<? extends Husk> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public static boolean checkHuskSpawnRules(EntityType<Husk> entitytypes, LevelAccessor generatoraccess, MobSpawnType enummobspawn, BlockPos blockposition, Random random) {
        return checkMonsterSpawnRules(entitytypes, generatoraccess, enummobspawn, blockposition, random) && (enummobspawn == MobSpawnType.SPAWNER || generatoraccess.canSeeSky(blockposition));
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected SoundEvent getSoundAmbient() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getSoundHurt(DamageSource damagesource) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    protected SoundEvent getSoundDeath() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    protected SoundEvent getSoundStep() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean flag = super.doHurtTarget(entity);

        if (flag && this.getMainHandItem().isEmpty() && entity instanceof LivingEntity) {
            float f = this.level.getDamageScaler(this.blockPosition()).getEffectiveDifficulty();

            ((LivingEntity) entity).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int) f), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }

        return flag;
    }

    @Override
    protected boolean convertsInWater() {
        return true;
    }

    @Override
    protected void doUnderWaterConversion() {
        this.convertToZombieType(EntityType.ZOMBIE);
        if (!this.isSilent()) {
            this.level.levelEvent((Player) null, 1041, this.blockPosition(), 0);
        }

    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }
}
