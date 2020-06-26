package net.minecraft.world.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TridentItem extends Item implements Vanishable {

    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public TridentItem(Item.Info item_info) {
        super(item_info);
        Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();

        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(TridentItem.BASE_ATTACK_DAMAGE_UUID, "Tool modifier", 8.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(TridentItem.BASE_ATTACK_SPEED_UUID, "Tool modifier", -2.9000000953674316D, AttributeModifier.Operation.ADDITION));
        this.defaultModifiers = builder.build();
    }

    @Override
    public boolean canAttackBlock(BlockState iblockdata, Level world, BlockPos blockposition, Player entityhuman) {
        return !entityhuman.isCreative();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack itemstack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack itemstack) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack itemstack, Level world, LivingEntity entityliving, int i) {
        if (entityliving instanceof Player) {
            Player entityhuman = (Player) entityliving;
            int j = this.getUseDuration(itemstack) - i;

            if (j >= 10) {
                int k = EnchantmentHelper.getRiptide(itemstack);

                if (k <= 0 || entityhuman.isInWaterOrRain()) {
                    if (!world.isClientSide) {
                        // CraftBukkit - moved down
                        /*
                        itemstack.damage(1, entityhuman, (entityhuman1) -> {
                            entityhuman1.broadcastItemBreak(entityliving.getRaisedHand());
                        });
                        */
                        if (k == 0) {
                            ThrownTrident entitythrowntrident = new ThrownTrident(world, entityhuman, itemstack);

                            entitythrowntrident.shootFromRotation(entityhuman, entityhuman.xRot, entityhuman.yRot, 0.0F, 2.5F + (float) k * 0.5F, 1.0F);
                            if (entityhuman.abilities.instabuild) {
                                entitythrowntrident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                            }

                            // CraftBukkit start
                            if (!world.addFreshEntity(entitythrowntrident)) {
                                if (entityhuman instanceof ServerPlayer) {
                                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory();
                                }
                                return;
                            }

                            itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                                entityhuman1.broadcastBreakEvent(entityliving.getUsedItemHand());
                            });
                            entitythrowntrident.tridentItem = itemstack.copy(); // SPIGOT-4511 update since damage call moved
                            // CraftBukkit end

                            world.playSound((Player) null, (Entity) entitythrowntrident, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
                            if (!entityhuman.abilities.instabuild) {
                                entityhuman.inventory.removeItem(itemstack);
                            }
                        }
                        // CraftBukkit start - SPIGOT-5458 also need in this branch :(
                        else {
                            itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                                entityhuman1.broadcastBreakEvent(entityliving.getUsedItemHand());
                            });
                        }
                        // CraftBukkkit end
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(this));
                    if (k > 0) {
                        // CraftBukkit start
                        org.bukkit.event.player.PlayerRiptideEvent event = new org.bukkit.event.player.PlayerRiptideEvent((org.bukkit.entity.Player) entityhuman.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack));
                        event.getPlayer().getServer().getPluginManager().callEvent(event);
                        // CraftBukkit end
                        float f = entityhuman.yRot;
                        float f1 = entityhuman.xRot;
                        float f2 = -Mth.sin(f * 0.017453292F) * Mth.cos(f1 * 0.017453292F);
                        float f3 = -Mth.sin(f1 * 0.017453292F);
                        float f4 = Mth.cos(f * 0.017453292F) * Mth.cos(f1 * 0.017453292F);
                        float f5 = Mth.sqrt(f2 * f2 + f3 * f3 + f4 * f4);
                        float f6 = 3.0F * ((1.0F + (float) k) / 4.0F);

                        f2 *= f6 / f5;
                        f3 *= f6 / f5;
                        f4 *= f6 / f5;
                        entityhuman.push((double) f2, (double) f3, (double) f4);
                        entityhuman.startAutoSpinAttack(20);
                        if (entityhuman.isOnGround()) {
                            float f7 = 1.1999999F;

                            entityhuman.move(MoverType.SELF, new Vec3(0.0D, 1.1999999284744263D, 0.0D));
                        }

                        SoundEvent soundeffect;

                        if (k >= 3) {
                            soundeffect = SoundEvents.TRIDENT_RIPTIDE_3;
                        } else if (k == 2) {
                            soundeffect = SoundEvents.TRIDENT_RIPTIDE_2;
                        } else {
                            soundeffect = SoundEvents.TRIDENT_RIPTIDE_1;
                        }

                        world.playSound((Player) null, (Entity) entityhuman, soundeffect, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }

                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.getDamageValue() >= itemstack.getMaxDamage() - 1) {
            return InteractionResultHolder.fail(itemstack);
        } else if (EnchantmentHelper.getRiptide(itemstack) > 0 && !entityhuman.isInWaterOrRain()) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            entityhuman.startUsingItem(enumhand);
            return InteractionResultHolder.consume(itemstack);
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack itemstack, LivingEntity entityliving, LivingEntity entityliving1) {
        itemstack.hurtAndBreak(1, entityliving1, (entityliving2) -> {
            entityliving2.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack itemstack, Level world, BlockState iblockdata, BlockPos blockposition, LivingEntity entityliving) {
        if ((double) iblockdata.getDestroySpeed(world, blockposition) != 0.0D) {
            itemstack.hurtAndBreak(2, entityliving, (entityliving1) -> {
                entityliving1.broadcastBreakEvent(EquipmentSlot.MAINHAND);
            });
        }

        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot enumitemslot) {
        return enumitemslot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(enumitemslot);
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
