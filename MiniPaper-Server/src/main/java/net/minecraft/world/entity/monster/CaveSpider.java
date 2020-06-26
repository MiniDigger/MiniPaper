package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class CaveSpider extends Spider {

    public CaveSpider(EntityType<? extends CaveSpider> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public static AttributeSupplier.Builder m() {
        return Spider.eL().a(Attributes.MAX_HEALTH, 12.0D);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (super.doHurtTarget(entity)) {
            if (entity instanceof LivingEntity) {
                byte b0 = 0;

                if (this.level.getDifficulty() == Difficulty.NORMAL) {
                    b0 = 7;
                } else if (this.level.getDifficulty() == Difficulty.HARD) {
                    b0 = 15;
                }

                if (b0 > 0) {
                    ((LivingEntity) entity).addEffect(new MobEffectInstance(MobEffects.POISON, b0 * 20, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public SpawnGroupData prepare(LevelAccessor generatoraccess, DifficultyInstance difficultydamagescaler, MobSpawnType enummobspawn, @Nullable SpawnGroupData groupdataentity, @Nullable CompoundTag nbttagcompound) {
        return groupdataentity;
    }

    @Override
    protected float getStandingEyeHeight(Pose entitypose, EntityDimensions entitysize) {
        return 0.45F;
    }
}
