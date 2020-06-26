package net.minecraft.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;

public class SnowballItem extends Item {

    public SnowballItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        // CraftBukkit - moved down
        // world.playSound((EntityHuman) null, entityhuman.locX(), entityhuman.locY(), entityhuman.locZ(), SoundEffects.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (ItemSnowball.RANDOM.nextFloat() * 0.4F + 0.8F));
        if (!world.isClientSide) {
            Snowball entitysnowball = new Snowball(world, entityhuman);

            entitysnowball.setItem(itemstack);
            entitysnowball.shootFromRotation(entityhuman, entityhuman.xRot, entityhuman.yRot, 0.0F, 1.5F, 1.0F);
            if (world.addFreshEntity(entitysnowball)) {
                if (!entityhuman.abilities.instabuild) {
                    itemstack.shrink(1);
                }

                world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (SnowballItem.random.nextFloat() * 0.4F + 0.8F));
            } else if (entityhuman instanceof ServerPlayer) {
                ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory();
            }
        }
        // CraftBukkit end

        entityhuman.awardStat(Stats.ITEM_USED.get(this));
        // CraftBukkit start - moved up
        /*
        if (!entityhuman.abilities.canInstantlyBuild) {
            itemstack.subtract(1);
        }
        */

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }
}
