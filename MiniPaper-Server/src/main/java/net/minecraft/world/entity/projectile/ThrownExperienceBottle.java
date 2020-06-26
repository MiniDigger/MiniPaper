package net.minecraft.world.entity.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class ThrownExperienceBottle extends ThrowableItemProjectile {

    public ThrownExperienceBottle(EntityType<? extends ThrownExperienceBottle> entitytypes, Level world) {
        super(entitytypes, world);
    }

    public ThrownExperienceBottle(Level world, LivingEntity entityliving) {
        super(EntityType.EXPERIENCE_BOTTLE, entityliving, world);
    }

    public ThrownExperienceBottle(Level world, double d0, double d1, double d2) {
        super(EntityType.EXPERIENCE_BOTTLE, d0, d1, d2, world);
    }

    @Override
    protected Item getDefaultItemOH() {
        return Items.EXPERIENCE_BOTTLE;
    }

    @Override
    protected float getGravity() {
        return 0.07F;
    }

    @Override
    protected void onHit(HitResult movingobjectposition) {
        super.onHit(movingobjectposition);
        if (!this.level.isClientSide) {
            // CraftBukkit - moved to after event
            // this.world.triggerEffect(2002, this.getChunkCoordinates(), PotionUtil.a(Potions.WATER));
            int i = 3 + this.level.random.nextInt(5) + this.level.random.nextInt(5);

            // CraftBukkit start
            org.bukkit.event.entity.ExpBottleEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callExpBottleEvent(this, i);
            i = event.getExperience();
            if (event.getShowEffect()) {
                this.level.levelEvent(2002, this.blockPosition(), PotionUtils.getColor(Potions.WATER));
            }
            // CraftBukkit end

            while (i > 0) {
                int j = ExperienceOrb.getExperienceValue(i);

                i -= j;
                this.level.addFreshEntity(new ExperienceOrb(this.level, this.getX(), this.getY(), this.getZ(), j));
            }

            this.remove();
        }

    }
}
