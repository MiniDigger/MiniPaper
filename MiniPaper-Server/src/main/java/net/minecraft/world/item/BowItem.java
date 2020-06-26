package net.minecraft.world.item;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public class BowItem extends ProjectileWeaponItem implements Vanishable {

    public BowItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public void releaseUsing(ItemStack itemstack, Level world, LivingEntity entityliving, int i) {
        if (entityliving instanceof Player) {
            Player entityhuman = (Player) entityliving;
            boolean flag = entityhuman.abilities.instabuild || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, itemstack) > 0;
            ItemStack itemstack1 = entityhuman.getProjectile(itemstack);

            if (!itemstack1.isEmpty() || flag) {
                if (itemstack1.isEmpty()) {
                    itemstack1 = new ItemStack(Items.ARROW);
                }

                int j = this.getUseDuration(itemstack) - i;
                float f = getPowerForTime(j);

                if ((double) f >= 0.1D) {
                    boolean flag1 = flag && itemstack1.getItem() == Items.ARROW;

                    if (!world.isClientSide) {
                        ArrowItem itemarrow = (ArrowItem) ((ArrowItem) (itemstack1.getItem() instanceof ArrowItem ? itemstack1.getItem() : Items.ARROW));
                        AbstractArrow entityarrow = itemarrow.createArrow(world, itemstack1, (LivingEntity) entityhuman);

                        entityarrow.shootFromRotation(entityhuman, entityhuman.xRot, entityhuman.yRot, 0.0F, f * 3.0F, 1.0F);
                        if (f == 1.0F) {
                            entityarrow.setCritArrow(true);
                        }

                        int k = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, itemstack);

                        if (k > 0) {
                            entityarrow.setBaseDamage(entityarrow.getBaseDamage() + (double) k * 0.5D + 0.5D);
                        }

                        int l = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, itemstack);

                        if (l > 0) {
                            entityarrow.setKnockback(l);
                        }

                        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, itemstack) > 0) {
                            entityarrow.setSecondsOnFire(100);
                        }
                        // CraftBukkit start
                        org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(entityhuman, itemstack, entityarrow, f);
                        if (event.isCancelled()) {
                            event.getProjectile().remove();
                            return;
                        }
                        // CraftBukkit end

                        itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                            entityhuman1.broadcastBreakEvent(entityhuman.getUsedItemHand());
                        });
                        if (flag1 || entityhuman.abilities.instabuild && (itemstack1.getItem() == Items.SPECTRAL_ARROW || itemstack1.getItem() == Items.TIPPED_ARROW)) {
                            entityarrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        }

                        // CraftBukkit start
                        if (event.getProjectile() == entityarrow.getBukkitEntity()) {
                            if (!world.addFreshEntity(entityarrow)) {
                                if (entityhuman instanceof ServerPlayer) {
                                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory();
                                }
                                return;
                            }
                        }
                        // CraftBukkit end
                    }

                    world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F / (BowItem.random.nextFloat() * 0.4F + 1.2F) + f * 0.5F);
                    if (!flag1 && !entityhuman.abilities.instabuild) {
                        itemstack1.shrink(1);
                        if (itemstack1.isEmpty()) {
                            entityhuman.inventory.removeItem(itemstack1);
                        }
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(this));
                }
            }
        }
    }

    public static float getPowerForTime(int i) {
        float f = (float) i / 20.0F;

        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }

        return f;
    }

    @Override
    public int getUseDuration(ItemStack itemstack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack itemstack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        boolean flag = !entityhuman.getProjectile(itemstack).isEmpty();

        if (!entityhuman.abilities.instabuild && !flag) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            entityhuman.startUsingItem(enumhand);
            return InteractionResultHolder.consume(itemstack);
        }
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return BowItem.ARROW_ONLY;
    }

    @Override
    public int getDefaultProjectileRange() {
        return 15;
    }
}
