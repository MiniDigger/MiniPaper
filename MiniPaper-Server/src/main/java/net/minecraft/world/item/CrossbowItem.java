package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CrossbowItem extends ProjectileWeaponItem implements Vanishable {

    private boolean startSoundPlayed = false;
    private boolean midLoadSoundPlayed = false;

    public CrossbowItem(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return CrossbowItem.ARROW_OR_FIREWORK;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return CrossbowItem.ARROW_ONLY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entityhuman, InteractionHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (isCharged(itemstack)) {
            performShooting(world, entityhuman, enumhand, itemstack, getShootingPower(itemstack), 1.0F);
            setCharged(itemstack, false);
            return InteractionResultHolder.consume(itemstack);
        } else if (!entityhuman.getProjectile(itemstack).isEmpty()) {
            if (!isCharged(itemstack)) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
                entityhuman.startUsingItem(enumhand);
            }

            return InteractionResultHolder.consume(itemstack);
        } else {
            return InteractionResultHolder.fail(itemstack);
        }
    }

    @Override
    public void releaseUsing(ItemStack itemstack, Level world, LivingEntity entityliving, int i) {
        int j = this.getUseDuration(itemstack) - i;
        float f = getPowerForTime(j, itemstack);

        if (f >= 1.0F && !isCharged(itemstack) && tryLoadProjectiles(entityliving, itemstack)) {
            setCharged(itemstack, true);
            SoundSource soundcategory = entityliving instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;

            world.playSound((Player) null, entityliving.getX(), entityliving.getY(), entityliving.getZ(), SoundEvents.CROSSBOW_LOADING_END, soundcategory, 1.0F, 1.0F / (CrossbowItem.random.nextFloat() * 0.5F + 1.0F) + 0.2F);
        }

    }

    private static boolean tryLoadProjectiles(LivingEntity entityliving, ItemStack itemstack) {
        int i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, itemstack);
        int j = i == 0 ? 1 : 3;
        boolean flag = entityliving instanceof Player && ((Player) entityliving).abilities.instabuild;
        ItemStack itemstack1 = entityliving.getProjectile(itemstack);
        ItemStack itemstack2 = itemstack1.copy();

        for (int k = 0; k < j; ++k) {
            if (k > 0) {
                itemstack1 = itemstack2.copy();
            }

            if (itemstack1.isEmpty() && flag) {
                itemstack1 = new ItemStack(Items.ARROW);
                itemstack2 = itemstack1.copy();
            }
            // CraftBukkit start - SPIGOT-4870, MC-150847
            else if (itemstack1.isEmpty()) {
                return false;
            }
            // CraftBukkit end

            if (!loadProjectile(entityliving, itemstack, itemstack1, k > 0, flag)) {
                return false;
            }
        }

        return true;
    }

    private static boolean loadProjectile(LivingEntity entityliving, ItemStack itemstack, ItemStack itemstack1, boolean flag, boolean flag1) {
        if (itemstack1.isEmpty()) {
            return false;
        } else {
            boolean flag2 = flag1 && itemstack1.getItem() instanceof ArrowItem;
            ItemStack itemstack2;

            if (!flag2 && !flag1 && !flag) {
                itemstack2 = itemstack1.split(1);
                if (itemstack1.isEmpty() && entityliving instanceof Player) {
                    ((Player) entityliving).inventory.removeItem(itemstack1);
                }
            } else {
                itemstack2 = itemstack1.copy();
            }

            addChargedProjectile(itemstack, itemstack2);
            return true;
        }
    }

    public static boolean isCharged(ItemStack itemstack) {
        CompoundTag nbttagcompound = itemstack.getTag();

        return nbttagcompound != null && nbttagcompound.getBoolean("Charged");
    }

    public static void setCharged(ItemStack itemstack, boolean flag) {
        CompoundTag nbttagcompound = itemstack.getOrCreateTag();

        nbttagcompound.putBoolean("Charged", flag);
    }

    private static void addChargedProjectile(ItemStack itemstack, ItemStack itemstack1) {
        CompoundTag nbttagcompound = itemstack.getOrCreateTag();
        ListTag nbttaglist;

        if (nbttagcompound.contains("ChargedProjectiles", 9)) {
            nbttaglist = nbttagcompound.getList("ChargedProjectiles", 10);
        } else {
            nbttaglist = new ListTag();
        }

        CompoundTag nbttagcompound1 = new CompoundTag();

        itemstack1.save(nbttagcompound1);
        nbttaglist.add(nbttagcompound1);
        nbttagcompound.put("ChargedProjectiles", nbttaglist);
    }

    private static List<ItemStack> getChargedProjectiles(ItemStack itemstack) {
        List<ItemStack> list = Lists.newArrayList();
        CompoundTag nbttagcompound = itemstack.getTag();

        if (nbttagcompound != null && nbttagcompound.contains("ChargedProjectiles", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("ChargedProjectiles", 10);

            if (nbttaglist != null) {
                for (int i = 0; i < nbttaglist.size(); ++i) {
                    CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);

                    list.add(ItemStack.of(nbttagcompound1));
                }
            }
        }

        return list;
    }

    private static void clearChargedProjectiles(ItemStack itemstack) {
        CompoundTag nbttagcompound = itemstack.getTag();

        if (nbttagcompound != null) {
            ListTag nbttaglist = nbttagcompound.getList("ChargedProjectiles", 9);

            nbttaglist.clear();
            nbttagcompound.put("ChargedProjectiles", nbttaglist);
        }

    }

    public static boolean containsChargedProjectile(ItemStack itemstack, Item item) {
        return getChargedProjectiles(itemstack).stream().anyMatch((itemstack1) -> {
            return itemstack1.getItem() == item;
        });
    }

    private static void shootProjectile(Level world, LivingEntity entityliving, InteractionHand enumhand, ItemStack itemstack, ItemStack itemstack1, float f, boolean flag, float f1, float f2, float f3) {
        if (!world.isClientSide) {
            boolean flag1 = itemstack1.getItem() == Items.FIREWORK_ROCKET;
            Object object;

            if (flag1) {
                object = new FireworkRocketEntity(world, itemstack1, entityliving, entityliving.getX(), entityliving.getEyeY() - 0.15000000596046448D, entityliving.getZ(), true);
            } else {
                object = getArrow(world, entityliving, itemstack, itemstack1);
                if (flag || f3 != 0.0F) {
                    ((AbstractArrow) object).pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
            }

            if (entityliving instanceof CrossbowAttackMob) {
                CrossbowAttackMob icrossbow = (CrossbowAttackMob) entityliving;

                icrossbow.shootCrossbowProjectile(icrossbow.getTarget(), itemstack, (Projectile) object, f3);
            } else {
                Vec3 vec3d = entityliving.getUpVector(1.0F);
                Quaternion quaternion = new Quaternion(new Vector3f(vec3d), f3, true);
                Vec3 vec3d1 = entityliving.getViewVector(1.0F);
                Vector3f vector3fa = new Vector3f(vec3d1);

                vector3fa.transform(quaternion);
                ((Projectile) object).shoot((double) vector3fa.x(), (double) vector3fa.y(), (double) vector3fa.z(), f1, f2);
            }
            // CraftBukkit start
            org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(entityliving, itemstack, (Entity) object, f);
            if (event.isCancelled()) {
                event.getProjectile().remove();
                return;
            }
            // CraftBukkit end

            itemstack.hurtAndBreak(flag1 ? 3 : 1, entityliving, (entityliving1) -> {
                entityliving1.broadcastBreakEvent(enumhand);
            });
            // CraftBukkit start
            if (event.getProjectile() == ((Entity) object).getBukkitEntity()) {
                if (!world.addFreshEntity((Entity) object)) {
                    if (entityliving instanceof ServerPlayer) {
                        ((ServerPlayer) entityliving).getBukkitEntity().updateInventory();
                    }
                    return;
                }
            }
            // CraftBukkit end
            world.playSound((Player) null, entityliving.getX(), entityliving.getY(), entityliving.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, f);
        }
    }

    private static AbstractArrow getArrow(Level world, LivingEntity entityliving, ItemStack itemstack, ItemStack itemstack1) {
        ArrowItem itemarrow = (ArrowItem) ((ArrowItem) (itemstack1.getItem() instanceof ArrowItem ? itemstack1.getItem() : Items.ARROW));
        AbstractArrow entityarrow = itemarrow.createArrow(world, itemstack1, entityliving);

        if (entityliving instanceof Player) {
            entityarrow.setCritArrow(true);
        }

        entityarrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        entityarrow.setShotFromCrossbow(true);
        int i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, itemstack);

        if (i > 0) {
            entityarrow.setPierceLevel((byte) i);
        }

        return entityarrow;
    }

    public static void performShooting(Level world, LivingEntity entityliving, InteractionHand enumhand, ItemStack itemstack, float f, float f1) {
        List<ItemStack> list = getChargedProjectiles(itemstack);
        float[] afloat = getShotPitches(entityliving.getRandom());

        for (int i = 0; i < list.size(); ++i) {
            ItemStack itemstack1 = (ItemStack) list.get(i);
            boolean flag = entityliving instanceof Player && ((Player) entityliving).abilities.instabuild;

            if (!itemstack1.isEmpty()) {
                if (i == 0) {
                    shootProjectile(world, entityliving, enumhand, itemstack, itemstack1, afloat[i], flag, f, f1, 0.0F);
                } else if (i == 1) {
                    shootProjectile(world, entityliving, enumhand, itemstack, itemstack1, afloat[i], flag, f, f1, -10.0F);
                } else if (i == 2) {
                    shootProjectile(world, entityliving, enumhand, itemstack, itemstack1, afloat[i], flag, f, f1, 10.0F);
                }
            }
        }

        onCrossbowShot(world, entityliving, itemstack);
    }

    private static float[] getShotPitches(Random random) {
        boolean flag = random.nextBoolean();

        return new float[]{1.0F, getRandomShotPitch(flag), getRandomShotPitch(!flag)};
    }

    private static float getRandomShotPitch(boolean flag) {
        float f = flag ? 0.63F : 0.43F;

        return 1.0F / (CrossbowItem.random.nextFloat() * 0.5F + 1.8F) + f;
    }

    private static void onCrossbowShot(Level world, LivingEntity entityliving, ItemStack itemstack) {
        if (entityliving instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entityliving;

            if (!world.isClientSide) {
                CriteriaTriggers.SHOT_CROSSBOW.trigger(entityplayer, itemstack);
            }

            entityplayer.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
        }

        clearChargedProjectiles(itemstack);
    }

    @Override
    public void onUseTick(Level world, LivingEntity entityliving, ItemStack itemstack, int i) {
        if (!world.isClientSide) {
            int j = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, itemstack);
            SoundEvent soundeffect = this.getStartSound(j);
            SoundEvent soundeffect1 = j == 0 ? SoundEvents.CROSSBOW_LOADING_MIDDLE : null;
            float f = (float) (itemstack.getUseDuration() - i) / (float) getChargeDuration(itemstack);

            if (f < 0.2F) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
            }

            if (f >= 0.2F && !this.startSoundPlayed) {
                this.startSoundPlayed = true;
                world.playSound((Player) null, entityliving.getX(), entityliving.getY(), entityliving.getZ(), soundeffect, SoundSource.PLAYERS, 0.5F, 1.0F);
            }

            if (f >= 0.5F && soundeffect1 != null && !this.midLoadSoundPlayed) {
                this.midLoadSoundPlayed = true;
                world.playSound((Player) null, entityliving.getX(), entityliving.getY(), entityliving.getZ(), soundeffect1, SoundSource.PLAYERS, 0.5F, 1.0F);
            }
        }

    }

    @Override
    public int getUseDuration(ItemStack itemstack) {
        return getChargeDuration(itemstack) + 3;
    }

    public static int getChargeDuration(ItemStack itemstack) {
        int i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, itemstack);

        return i == 0 ? 25 : 25 - 5 * i;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack itemstack) {
        return UseAnim.CROSSBOW;
    }

    private SoundEvent getStartSound(int i) {
        switch (i) {
            case 1:
                return SoundEvents.CROSSBOW_QUICK_CHARGE_1;
            case 2:
                return SoundEvents.CROSSBOW_QUICK_CHARGE_2;
            case 3:
                return SoundEvents.CROSSBOW_QUICK_CHARGE_3;
            default:
                return SoundEvents.CROSSBOW_LOADING_START;
        }
    }

    private static float getPowerForTime(int i, ItemStack itemstack) {
        float f = (float) i / (float) getChargeDuration(itemstack);

        if (f > 1.0F) {
            f = 1.0F;
        }

        return f;
    }

    private static float getShootingPower(ItemStack itemstack) {
        return itemstack.getItem() == Items.CROSSBOW && containsChargedProjectile(itemstack, Items.FIREWORK_ROCKET) ? 1.6F : 3.15F;
    }

    @Override
    public int getDefaultProjectileRange() {
        return 8;
    }
}
