package net.minecraft.world.effect;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
// CraftBukkit end

public class MobEffect {

    private final Map<Attribute, AttributeModifier> attributeModifiers = Maps.newHashMap();
    private final MobEffectCategory category;
    private final int color;
    @Nullable
    private String descriptionId;

    @Nullable
    public static MobEffect byId(int i) {
        return (MobEffect) Registry.MOB_EFFECT.byId(i);
    }

    public static int getId(MobEffect mobeffectlist) {
        return Registry.MOB_EFFECT.getId(mobeffectlist); // CraftBukkit - decompile error
    }

    protected MobEffect(MobEffectCategory mobeffectinfo, int i) {
        this.category = mobeffectinfo;
        this.color = i;
    }

    public void applyEffectTick(LivingEntity entityliving, int i) {
        if (this == MobEffects.REGENERATION) {
            if (entityliving.getHealth() < entityliving.getMaxHealth()) {
                entityliving.heal(1.0F, RegainReason.MAGIC_REGEN); // CraftBukkit
            }
        } else if (this == MobEffects.POISON) {
            if (entityliving.getHealth() > 1.0F) {
                entityliving.hurt(CraftEventFactory.POISON, 1.0F);  // CraftBukkit - DamageSource.MAGIC -> CraftEventFactory.POISON
            }
        } else if (this == MobEffects.WITHER) {
            entityliving.hurt(DamageSource.WITHER, 1.0F);
        } else if (this == MobEffects.HUNGER && entityliving instanceof Player) {
            ((Player) entityliving).causeFoodExhaustion(0.005F * (float) (i + 1));
        } else if (this == MobEffects.SATURATION && entityliving instanceof Player) {
            if (!entityliving.level.isClientSide) {
                // CraftBukkit start
                Player entityhuman = (Player) entityliving;
                int oldFoodLevel = entityhuman.getFoodData().foodLevel;

                org.bukkit.event.entity.FoodLevelChangeEvent event = CraftEventFactory.callFoodLevelChangeEvent(entityhuman, i + 1 + oldFoodLevel);

                if (!event.isCancelled()) {
                    entityhuman.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 1.0F);
                }

                ((ServerPlayer) entityhuman).connection.sendPacket(new ClientboundSetHealthPacket(((ServerPlayer) entityhuman).getBukkitEntity().getScaledHealth(), entityhuman.getFoodData().foodLevel, entityhuman.getFoodData().saturationLevel));
                // CraftBukkit end
            }
        } else if ((this != MobEffects.HEAL || entityliving.isInvertedHealAndHarm()) && (this != MobEffects.HARM || !entityliving.isInvertedHealAndHarm())) {
            if (this == MobEffects.HARM && !entityliving.isInvertedHealAndHarm() || this == MobEffects.HEAL && entityliving.isInvertedHealAndHarm()) {
                entityliving.hurt(DamageSource.MAGIC, (float) (6 << i));
            }
        } else {
            entityliving.heal((float) Math.max(4 << i, 0), RegainReason.MAGIC); // CraftBukkit
        }

    }

    public void applyInstantenousEffect(@Nullable Entity entity, @Nullable Entity entity1, LivingEntity entityliving, int i, double d0) {
        int j;

        if ((this != MobEffects.HEAL || entityliving.isInvertedHealAndHarm()) && (this != MobEffects.HARM || !entityliving.isInvertedHealAndHarm())) {
            if ((this != MobEffects.HARM || entityliving.isInvertedHealAndHarm()) && (this != MobEffects.HEAL || !entityliving.isInvertedHealAndHarm())) {
                this.applyEffectTick(entityliving, i);
            } else {
                j = (int) (d0 * (double) (6 << i) + 0.5D);
                if (entity == null) {
                    entityliving.hurt(DamageSource.MAGIC, (float) j);
                } else {
                    entityliving.hurt(DamageSource.indirectMagic(entity, entity1), (float) j);
                }
            }
        } else {
            j = (int) (d0 * (double) (4 << i) + 0.5D);
            entityliving.heal((float) j, RegainReason.MAGIC); // CraftBukkit
        }

    }

    public boolean isDurationEffectTick(int i, int j) {
        int k;

        if (this == MobEffects.REGENERATION) {
            k = 50 >> j;
            return k > 0 ? i % k == 0 : true;
        } else if (this == MobEffects.POISON) {
            k = 25 >> j;
            return k > 0 ? i % k == 0 : true;
        } else if (this == MobEffects.WITHER) {
            k = 40 >> j;
            return k > 0 ? i % k == 0 : true;
        } else {
            return this == MobEffects.HUNGER;
        }
    }

    public boolean isInstantenous() {
        return false;
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("effect", Registry.MOB_EFFECT.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public Component getDisplayName() {
        return new TranslatableComponent(this.getDescriptionId());
    }

    public int getColor() {
        return this.color;
    }

    public MobEffect addAttributeModifier(Attribute attributebase, String s, double d0, AttributeModifier.Operation attributemodifier_operation) {
        AttributeModifier attributemodifier = new AttributeModifier(UUID.fromString(s), this::getDescriptionId, d0, attributemodifier_operation);

        this.attributeModifiers.put(attributebase, attributemodifier);
        return this;
    }

    public void removeAttributeModifiers(LivingEntity entityliving, AttributeMap attributemapbase, int i) {
        Iterator iterator = this.attributeModifiers.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Attribute, AttributeModifier> entry = (Entry) iterator.next();
            AttributeInstance attributemodifiable = attributemapbase.getInstance((Attribute) entry.getKey());

            if (attributemodifiable != null) {
                attributemodifiable.removeModifier((AttributeModifier) entry.getValue());
            }
        }

    }

    public void addAttributeModifiers(LivingEntity entityliving, AttributeMap attributemapbase, int i) {
        Iterator iterator = this.attributeModifiers.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Attribute, AttributeModifier> entry = (Entry) iterator.next();
            AttributeInstance attributemodifiable = attributemapbase.getInstance((Attribute) entry.getKey());

            if (attributemodifiable != null) {
                AttributeModifier attributemodifier = (AttributeModifier) entry.getValue();

                attributemodifiable.removeModifier(attributemodifier);
                attributemodifiable.addPermanentModifier(new AttributeModifier(attributemodifier.getId(), this.getDescriptionId() + " " + i, this.getAttributeModifierValue(i, attributemodifier), attributemodifier.getOperation()));
            }
        }

    }

    public double getAttributeModifierValue(int i, AttributeModifier attributemodifier) {
        return attributemodifier.getAmount() * (double) (i + 1);
    }
}
