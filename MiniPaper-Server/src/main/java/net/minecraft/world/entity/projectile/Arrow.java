package net.minecraft.world.entity.projectile;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

public class Arrow extends AbstractArrow {

    private static final EntityDataAccessor<Integer> ID_EFFECT_COLOR = SynchedEntityData.defineId(Arrow.class, EntityDataSerializers.INT);
    private Potion potion;
    public final Set<MobEffectInstance> effects;
    private boolean fixedColor;

    public Arrow(EntityType<? extends Arrow> entitytypes, Level world) {
        super(entitytypes, world);
        this.potion = Potions.EMPTY;
        this.effects = Sets.newHashSet();
    }

    public Arrow(Level world, double d0, double d1, double d2) {
        super(EntityType.ARROW, d0, d1, d2, world);
        this.potion = Potions.EMPTY;
        this.effects = Sets.newHashSet();
    }

    public Arrow(Level world, LivingEntity entityliving) {
        super(EntityType.ARROW, entityliving, world);
        this.potion = Potions.EMPTY;
        this.effects = Sets.newHashSet();
    }

    public void setEffectsFromItem(ItemStack itemstack) {
        if (itemstack.getItem() == Items.TIPPED_ARROW) {
            this.potion = PotionUtils.getPotion(itemstack);
            Collection<MobEffectInstance> collection = PotionUtils.getCustomEffects(itemstack);

            if (!collection.isEmpty()) {
                Iterator iterator = collection.iterator();

                while (iterator.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                    this.effects.add(new MobEffectInstance(mobeffect));
                }
            }

            int i = getCustomColor(itemstack);

            if (i == -1) {
                this.updateColor();
            } else {
                this.setFixedColor(i);
            }
        } else if (itemstack.getItem() == Items.ARROW) {
            this.potion = Potions.EMPTY;
            this.effects.clear();
            this.entityData.set(Arrow.ID_EFFECT_COLOR, -1);
        }

    }

    public static int getCustomColor(ItemStack itemstack) {
        CompoundTag nbttagcompound = itemstack.getTag();

        return nbttagcompound != null && nbttagcompound.contains("CustomPotionColor", 99) ? nbttagcompound.getInt("CustomPotionColor") : -1;
    }

    private void updateColor() {
        this.fixedColor = false;
        if (this.potion == Potions.EMPTY && this.effects.isEmpty()) {
            this.entityData.set(Arrow.ID_EFFECT_COLOR, -1);
        } else {
            this.entityData.set(Arrow.ID_EFFECT_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, (Collection) this.effects)));
        }

    }

    public void addEffect(MobEffectInstance mobeffect) {
        this.effects.add(mobeffect);
        this.getEntityData().set(Arrow.ID_EFFECT_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, (Collection) this.effects)));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.register(Arrow.ID_EFFECT_COLOR, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            if (this.inGround) {
                if (this.inGroundTime % 5 == 0) {
                    this.makeParticle(1);
                }
            } else {
                this.makeParticle(2);
            }
        } else if (this.inGround && this.inGroundTime != 0 && !this.effects.isEmpty() && this.inGroundTime >= 600) {
            this.level.broadcastEntityEvent(this, (byte) 0);
            this.potion = Potions.EMPTY;
            this.effects.clear();
            this.entityData.set(Arrow.ID_EFFECT_COLOR, -1);
        }

    }

    private void makeParticle(int i) {
        int j = this.getColor();

        if (j != -1 && i > 0) {
            double d0 = (double) (j >> 16 & 255) / 255.0D;
            double d1 = (double) (j >> 8 & 255) / 255.0D;
            double d2 = (double) (j >> 0 & 255) / 255.0D;

            for (int k = 0; k < i; ++k) {
                this.level.addParticle(ParticleTypes.ENTITY_EFFECT, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), d0, d1, d2);
            }

        }
    }

    // CraftBukkit start accessor methods
    public void refreshEffects() {
        this.getEntityData().set(Arrow.ID_EFFECT_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, (Collection) this.effects)));
    }

    public String getTypeOH() {
        return Registry.POTION.getKey(this.potion).toString();
    }

    public void setType(String string) {
        this.potion = Registry.POTION.get(new ResourceLocation(string));
        this.getEntityData().set(Arrow.ID_EFFECT_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, (Collection) this.effects)));
    }

    public boolean isTipped() {
        return !(this.effects.isEmpty() && this.potion == Potions.EMPTY);
    }
    // CraftBukkit end

    public int getColor() {
        return (Integer) this.entityData.get(Arrow.ID_EFFECT_COLOR);
    }

    public void setFixedColor(int i) {
        this.fixedColor = true;
        this.entityData.set(Arrow.ID_EFFECT_COLOR, i);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        if (this.potion != Potions.EMPTY && this.potion != null) {
            nbttagcompound.putString("Potion", Registry.POTION.getKey(this.potion).toString());
        }

        if (this.fixedColor) {
            nbttagcompound.putInt("Color", this.getColor());
        }

        if (!this.effects.isEmpty()) {
            ListTag nbttaglist = new ListTag();
            Iterator iterator = this.effects.iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                nbttaglist.add(mobeffect.save(new CompoundTag()));
            }

            nbttagcompound.put("CustomPotionEffects", nbttaglist);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        if (nbttagcompound.contains("Potion", 8)) {
            this.potion = PotionUtils.getPotion(nbttagcompound);
        }

        Iterator iterator = PotionUtils.getCustomEffects(nbttagcompound).iterator();

        while (iterator.hasNext()) {
            MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

            this.addEffect(mobeffect);
        }

        if (nbttagcompound.contains("Color", 99)) {
            this.setFixedColor(nbttagcompound.getInt("Color"));
        } else {
            this.updateColor();
        }

    }

    @Override
    protected void doPostHurtEffects(LivingEntity entityliving) {
        super.doPostHurtEffects(entityliving);
        Iterator iterator = this.potion.getEffects().iterator();

        MobEffectInstance mobeffect;

        while (iterator.hasNext()) {
            mobeffect = (MobEffectInstance) iterator.next();
            entityliving.addEffect(new MobEffectInstance(mobeffect.getEffect(), Math.max(mobeffect.getDuration() / 8, 1), mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible()), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
        }

        if (!this.effects.isEmpty()) {
            iterator = this.effects.iterator();

            while (iterator.hasNext()) {
                mobeffect = (MobEffectInstance) iterator.next();
                entityliving.addEffect(mobeffect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
            }
        }

    }

    @Override
    protected ItemStack getPickupItem() {
        if (this.effects.isEmpty() && this.potion == Potions.EMPTY) {
            return new ItemStack(Items.ARROW);
        } else {
            ItemStack itemstack = new ItemStack(Items.TIPPED_ARROW);

            PotionUtils.setPotion(itemstack, this.potion);
            PotionUtils.setCustomEffects(itemstack, (Collection) this.effects);
            if (this.fixedColor) {
                itemstack.getOrCreateTag().putInt("CustomPotionColor", this.getColor());
            }

            return itemstack;
        }
    }
}
