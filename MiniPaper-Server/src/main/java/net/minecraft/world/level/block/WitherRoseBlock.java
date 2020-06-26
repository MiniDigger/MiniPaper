package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WitherRoseBlock extends FlowerBlock {

    public WitherRoseBlock(MobEffect mobeffectlist, BlockBehaviour.Info blockbase_info) {
        super(mobeffectlist, 8, blockbase_info);
    }

    @Override
    protected boolean mayPlaceOn(BlockState iblockdata, BlockGetter iblockaccess, BlockPos blockposition) {
        return super.mayPlaceOn(iblockdata, iblockaccess, blockposition) || iblockdata.is(Blocks.NETHERRACK) || iblockdata.is(Blocks.SOUL_SAND) || iblockdata.is(Blocks.SOUL_SOIL);
    }

    @Override
    public void entityInside(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) {
        if (!world.isClientSide && world.getDifficulty() != Difficulty.PEACEFUL) {
            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;

                if (!entityliving.isInvulnerableTo(DamageSource.WITHER)) {
                    entityliving.addEffect(new MobEffectInstance(MobEffects.WITHER, 40), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WITHER_ROSE); // CraftBukkit
                }
            }

        }
    }
}
