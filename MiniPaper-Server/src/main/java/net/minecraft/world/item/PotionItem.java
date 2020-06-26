package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

public class PotionItem extends Item {

    public PotionItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return PotionUtils.setPotion(super.getDefaultInstance(), Potions.WATER);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entityliving) {
        Player entityhuman = entityliving instanceof Player ? (Player) entityliving : null;

        if (entityhuman instanceof ServerPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger((ServerPlayer) entityhuman, itemstack);
        }

        if (!world.isClientSide) {
            List<MobEffectInstance> list = PotionUtils.getMobEffects(itemstack);
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                if (mobeffect.getEffect().isInstantenous()) {
                    mobeffect.getEffect().applyInstantenousEffect(entityhuman, entityhuman, entityliving, mobeffect.getAmplifier(), 1.0D);
                } else {
                    entityliving.addEffect(new MobEffectInstance(mobeffect), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_DRINK); // CraftBukkit
                }
            }
        }

        if (entityhuman != null) {
            entityhuman.awardStat(Stats.ITEM_USED.get(this));
            if (!entityhuman.abilities.instabuild) {
                itemstack.shrink(1);
            }
        }

        if (entityhuman == null || !entityhuman.abilities.instabuild) {
            if (itemstack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }

            if (entityhuman != null) {
                entityhuman.inventory.add(new ItemStack(Items.GLASS_BOTTLE));
            }
        }

        return itemstack;
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

    @Override
    public String getDescriptionId(ItemStack itemstack) {
        return PotionUtils.getPotion(itemstack).getName(this.getDescriptionId() + ".effect.");
    }

    @Override
    public boolean isFoil(ItemStack itemstack) {
        return super.isFoil(itemstack) || !PotionUtils.getMobEffects(itemstack).isEmpty();
    }

    @Override
    public void fillItemCategory(CreativeModeTab creativemodetab, NonNullList<ItemStack> nonnulllist) {
        if (this.allowdedIn(creativemodetab)) {
            Iterator iterator = Registry.POTION.iterator();

            while (iterator.hasNext()) {
                Potion potionregistry = (Potion) iterator.next();

                if (potionregistry != Potions.EMPTY) {
                    nonnulllist.add(PotionUtils.setPotion(new ItemStack(this), potionregistry));
                }
            }
        }

    }
}
