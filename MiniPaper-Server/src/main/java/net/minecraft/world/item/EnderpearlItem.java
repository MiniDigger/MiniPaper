package net.minecraft.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;

public class EnderpearlItem extends Item {

    public EnderpearlItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        // CraftBukkit start - change order
        if (!world.isClientSide) {
            ThrownEnderpearl entityenderpearl = new ThrownEnderpearl(world, entityhuman);

            entityenderpearl.setItem(itemstack);
            entityenderpearl.shootFromRotation(entityhuman, entityhuman.xRot, entityhuman.yRot, 0.0F, 1.5F, 1.0F);
            if (!world.addFreshEntity(entityenderpearl)) {
                if (entityhuman instanceof ServerPlayer) {
                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory();
                }
                return new InteractionResultHolder(InteractionResult.FAIL, itemstack);
            }
        }

        world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (EnderpearlItem.random.nextFloat() * 0.4F + 0.8F));
        entityhuman.getCooldowns().addCooldown(this, 20);
        // CraftBukkit end

        entityhuman.awardStat(Stats.ITEM_USED.get(this));
        if (!entityhuman.abilities.instabuild) {
            itemstack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }
}
