package net.minecraft.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.Level;

public class EggItem extends Item {

    public EggItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        // world.playSound((EntityHuman) null, entityhuman.locX(), entityhuman.locY(), entityhuman.locZ(), SoundEffects.ENTITY_EGG_THROW, SoundCategory.PLAYERS, 0.5F, 0.4F / (ItemEgg.RANDOM.nextFloat() * 0.4F + 0.8F)); // CraftBukkit - moved down
        if (!world.isClientSide) {
            ThrownEgg entityegg = new ThrownEgg(world, entityhuman);

            entityegg.setItem(itemstack);
            entityegg.shootFromRotation(entityhuman, entityhuman.xRot, entityhuman.yRot, 0.0F, 1.5F, 1.0F);
            // CraftBukkit start
            if (!world.addFreshEntity(entityegg)) {
                if (entityhuman instanceof ServerPlayer) {
                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory();
                }
                return InteractionResultHolder.fail(itemstack);
            }
            // CraftBukkit end
        }
        world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.5F, 0.4F / (EggItem.random.nextFloat() * 0.4F + 0.8F)); // CraftBukkit - from above

        entityhuman.awardStat(Stats.ITEM_USED.get(this));
        if (!entityhuman.abilities.instabuild) {
            itemstack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }
}
