package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class MilkBucketItem extends Item {

    public MilkBucketItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entityliving) {
        if (entityliving instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entityliving;

            CriteriaTriggers.CONSUME_ITEM.trigger(entityplayer, itemstack);
            entityplayer.awardStat(Stats.ITEM_USED.get(this));
        }

        if (entityliving instanceof Player && !((Player) entityliving).abilities.instabuild) {
            itemstack.shrink(1);
        }

        if (!world.isClientSide) {
            entityliving.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.MILK); // CraftBukkit
        }

        return itemstack.isEmpty() ? new ItemStack(Items.BUCKET) : itemstack;
    }

    @Override
    public int getUseDuration(ItemStack itemstack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack itemstack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        return ItemUtils.useDrink(world, entityhuman, enumhand);
    }
}
