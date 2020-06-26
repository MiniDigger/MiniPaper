package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class IndirectEntityDamageSource extends EntityDamageSource {

    private final Entity owner;

    public IndirectEntityDamageSource(String s, Entity entity, @Nullable Entity entity1) {
        super(s, entity);
        this.owner = entity1;
    }

    @Nullable
    @Override
    public Entity getDirectEntity() {
        return this.entity;
    }

    @Nullable
    @Override
    public Entity getEntity() {
        return this.owner;
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity entityliving) {
        Component ichatbasecomponent = this.owner == null ? this.entity.getDisplayName() : this.owner.getDisplayName();
        ItemStack itemstack = this.owner instanceof LivingEntity ? ((LivingEntity) this.owner).getMainHandItem() : ItemStack.EMPTY;
        String s = "death.attack." + this.msgId;
        String s1 = s + ".item";

        return !itemstack.isEmpty() && itemstack.hasCustomHoverName() ? new TranslatableComponent(s1, new Object[]{entityliving.getDisplayName(), ichatbasecomponent, itemstack.getDisplayName()}) : new TranslatableComponent(s, new Object[]{entityliving.getDisplayName(), ichatbasecomponent});
    }

    // CraftBukkit start
    public Entity getProximateDamageSource() {
        return super.getEntity();
    }
    // CraftBukkit end
}
