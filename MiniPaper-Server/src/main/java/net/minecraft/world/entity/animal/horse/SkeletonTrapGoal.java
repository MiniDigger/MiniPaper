package net.minecraft.world.entity.animal.horse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class SkeletonTrapGoal extends Goal {

    private final SkeletonHorse horse;

    public SkeletonTrapGoal(SkeletonHorse entityhorseskeleton) {
        this.horse = entityhorseskeleton;
    }

    @Override
    public boolean canUse() {
        return this.horse.level.hasNearbyAlivePlayer(this.horse.getX(), this.horse.getY(), this.horse.getZ(), 10.0D);
    }

    @Override
    public void tick() {
        DifficultyInstance difficultydamagescaler = this.horse.level.getDamageScaler(this.horse.blockPosition());

        this.horse.setTrap(false);
        this.horse.setTamed(true);
        this.horse.setAge(0);
        LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(this.horse.level);

        entitylightning.moveTo(this.horse.getX(), this.horse.getY(), this.horse.getZ());
        entitylightning.setVisualOnly(true);
        ((ServerLevel) this.horse.level).strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.TRAP); // CraftBukkit
        Skeleton entityskeleton = this.createSkeleton(difficultydamagescaler, this.horse);

        if (entityskeleton != null) entityskeleton.startRiding(this.horse); // CraftBukkit

        for (int i = 0; i < 3; ++i) {
            AbstractHorse entityhorseabstract = this.createHorse(difficultydamagescaler);
            if (entityhorseabstract == null) continue; // CraftBukkit
            Skeleton entityskeleton1 = this.createSkeleton(difficultydamagescaler, entityhorseabstract);

            if (entityskeleton1 != null) entityskeleton1.startRiding(entityhorseabstract); // CraftBukkit
            entityhorseabstract.push(this.horse.getRandom().nextGaussian() * 0.5D, 0.0D, this.horse.getRandom().nextGaussian() * 0.5D);
        }

    }

    private AbstractHorse createHorse(DifficultyInstance difficultydamagescaler) {
        SkeletonHorse entityhorseskeleton = (SkeletonHorse) EntityType.SKELETON_HORSE.create(this.horse.level);

        entityhorseskeleton.prepare(this.horse.level, difficultydamagescaler, MobSpawnType.TRIGGERED, (SpawnGroupData) null, (CompoundTag) null);
        entityhorseskeleton.setPos(this.horse.getX(), this.horse.getY(), this.horse.getZ());
        entityhorseskeleton.invulnerableTime = 60;
        entityhorseskeleton.setPersistenceRequired();
        entityhorseskeleton.setTamed(true);
        entityhorseskeleton.setAge(0);
        if (!entityhorseskeleton.level.addEntity(entityhorseskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRAP)) return null; // CraftBukkit
        return entityhorseskeleton;
    }

    private Skeleton createSkeleton(DifficultyInstance difficultydamagescaler, AbstractHorse entityhorseabstract) {
        Skeleton entityskeleton = (Skeleton) EntityType.SKELETON.create(entityhorseabstract.level);

        entityskeleton.prepare(entityhorseabstract.level, difficultydamagescaler, MobSpawnType.TRIGGERED, (SpawnGroupData) null, (CompoundTag) null);
        entityskeleton.setPos(entityhorseabstract.getX(), entityhorseabstract.getY(), entityhorseabstract.getZ());
        entityskeleton.invulnerableTime = 60;
        entityskeleton.setPersistenceRequired();
        if (entityskeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            entityskeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        }

        entityskeleton.setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(entityskeleton.getRandom(), entityskeleton.getMainHandItem(), (int) (5.0F + difficultydamagescaler.getSpecialMultiplier() * (float) entityskeleton.getRandom().nextInt(18)), false));
        entityskeleton.setItemSlot(EquipmentSlot.HEAD, EnchantmentHelper.enchantItem(entityskeleton.getRandom(), entityskeleton.getItemBySlot(EquipmentSlot.HEAD), (int) (5.0F + difficultydamagescaler.getSpecialMultiplier() * (float) entityskeleton.getRandom().nextInt(18)), false));
        if (!entityskeleton.level.addEntity(entityskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.JOCKEY)) return null; // CraftBukkit
        return entityskeleton;
    }
}
