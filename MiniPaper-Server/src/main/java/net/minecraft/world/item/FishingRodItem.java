package net.minecraft.world.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.bukkit.event.player.PlayerFishEvent; // CraftBukkit

public class FishingRodItem extends Item implements Vanishable {

    public FishingRodItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        int i;

        if (entityhuman.fishing != null) {
            if (!world.isClientSide) {
                i = entityhuman.fishing.retrieve(itemstack);
                itemstack.hurtAndBreak(i, entityhuman, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(enumhand);
                });
            }

            world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (FishingRodItem.random.nextFloat() * 0.4F + 0.8F));
        } else {
            // world.playSound((EntityHuman) null, entityhuman.locX(), entityhuman.locY(), entityhuman.locZ(), SoundEffects.ENTITY_FISHING_BOBBER_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (ItemFishingRod.RANDOM.nextFloat() * 0.4F + 0.8F));
            if (!world.isClientSide) {
                i = EnchantmentHelper.getFishingSpeedBonus(itemstack);
                int j = EnchantmentHelper.getFishingLuckBonus(itemstack);

                // CraftBukkit start
                FishingHook entityfishinghook = new FishingHook(entityhuman, world, j, i);
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((org.bukkit.entity.Player) entityhuman.getBukkitEntity(), null, (org.bukkit.entity.FishHook) entityfishinghook.getBukkitEntity(), PlayerFishEvent.State.FISHING);
                world.getServerOH().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    entityhuman.fishing = null;
                    return new InteractionResultHolder(InteractionResult.PASS, itemstack);
                }
                world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (FishingRodItem.random.nextFloat() * 0.4F + 0.8F));
                world.addFreshEntity(entityfishinghook);
                // CraftBukkit end
            }

            entityhuman.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
