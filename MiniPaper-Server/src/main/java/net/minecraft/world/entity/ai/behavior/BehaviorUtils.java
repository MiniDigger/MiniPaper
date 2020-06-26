package net.minecraft.world.entity.ai.behavior;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

public class BehaviorUtils {

    public static void lockGazeAndWalkToEachOther(LivingEntity entityliving, LivingEntity entityliving1, float f) {
        lookAtEachOther(entityliving, entityliving1);
        setWalkAndLookTargetMemoriesToEachOther(entityliving, entityliving1, f);
    }

    public static boolean entityIsVisible(Brain<?> behaviorcontroller, LivingEntity entityliving) {
        return behaviorcontroller.getMemory(MemoryModuleType.VISIBLE_LIVING_ENTITIES).filter((list) -> {
            return list.contains(entityliving);
        }).isPresent();
    }

    public static boolean targetIsValid(Brain<?> behaviorcontroller, MemoryModuleType<? extends LivingEntity> memorymoduletype, EntityType<?> entitytypes) {
        return targetIsValid(behaviorcontroller, memorymoduletype, (entityliving) -> {
            return entityliving.getType() == entitytypes;
        });
    }

    private static boolean targetIsValid(Brain<?> behaviorcontroller, MemoryModuleType<? extends LivingEntity> memorymoduletype, Predicate<LivingEntity> predicate) {
        return behaviorcontroller.getMemory(memorymoduletype).filter(predicate).filter(LivingEntity::isAlive).filter((entityliving) -> {
            return entityIsVisible(behaviorcontroller, entityliving);
        }).isPresent();
    }

    private static void lookAtEachOther(LivingEntity entityliving, LivingEntity entityliving1) {
        lookAtEntity(entityliving, entityliving1);
        lookAtEntity(entityliving1, entityliving);
    }

    public static void lookAtEntity(LivingEntity entityliving, LivingEntity entityliving1) {
        entityliving.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new EntityTracker(entityliving1, true))); // CraftBukkit - decompile error
    }

    private static void setWalkAndLookTargetMemoriesToEachOther(LivingEntity entityliving, LivingEntity entityliving1, float f) {
        boolean flag = true;

        setWalkAndLookTargetMemories(entityliving, (Entity) entityliving1, f, 2);
        setWalkAndLookTargetMemories(entityliving1, (Entity) entityliving, f, 2);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity entityliving, Entity entity, float f, int i) {
        WalkTarget memorytarget = new WalkTarget(new EntityTracker(entity, false), f, i);

        entityliving.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new EntityTracker(entity, true))); // CraftBukkit - decompile error
        entityliving.getBrain().setMemory(MemoryModuleType.WALK_TARGET, memorytarget); // CraftBukkit - decompile error
    }

    public static void setWalkAndLookTargetMemories(LivingEntity entityliving, BlockPos blockposition, float f, int i) {
        WalkTarget memorytarget = new WalkTarget(new BlockPosTracker(blockposition), f, i);

        entityliving.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new BlockPosTracker(blockposition))); // CraftBukkit - decompile error
        entityliving.getBrain().setMemory(MemoryModuleType.WALK_TARGET, memorytarget); // CraftBukkit - decompile error
    }

    public static void throwItem(LivingEntity entityliving, ItemStack itemstack, Vec3 vec3d) {
        if (itemstack.isEmpty()) return; // CraftBukkit - SPIGOT-4940: no empty loot
        double d0 = entityliving.getEyeY() - 0.30000001192092896D;
        ItemEntity entityitem = new ItemEntity(entityliving.level, entityliving.getX(), d0, entityliving.getZ(), itemstack);
        float f = 0.3F;
        Vec3 vec3d1 = vec3d.subtract(entityliving.position());

        vec3d1 = vec3d1.normalize().scale(0.30000001192092896D);
        entityitem.setDeltaMovement(vec3d1);
        entityitem.setDefaultPickUpDelay();
        entityliving.level.addFreshEntity(entityitem);
    }

    public static SectionPos findSectionClosestToVillage(ServerLevel worldserver, SectionPos sectionposition, int i) {
        int j = worldserver.sectionsToVillage(sectionposition);
        Stream<SectionPos> stream = SectionPos.cube(sectionposition, i).filter((sectionposition1) -> { // CraftBukkit - decompile error
            return worldserver.sectionsToVillage(sectionposition1) < j;
        });

        worldserver.getClass();
        return (SectionPos) stream.min(Comparator.comparingInt(worldserver::sectionsToVillage)).orElse(sectionposition);
    }

    public static boolean isWithinAttackRange(Mob entityinsentient, LivingEntity entityliving, int i) {
        Item item = entityinsentient.getMainHandItem().getItem();

        if (item instanceof ProjectileWeaponItem && entityinsentient.canFireProjectileWeapon((ProjectileWeaponItem) item)) {
            int j = ((ProjectileWeaponItem) item).getDefaultProjectileRange() - i;

            return entityinsentient.closerThan((Entity) entityliving, (double) j);
        } else {
            return isWithinMeleeAttackRange((LivingEntity) entityinsentient, entityliving);
        }
    }

    public static boolean isWithinMeleeAttackRange(LivingEntity entityliving, LivingEntity entityliving1) {
        double d0 = entityliving.distanceToSqr(entityliving1.getX(), entityliving1.getY(), entityliving1.getZ());
        double d1 = (double) (entityliving.getBbWidth() * 2.0F * entityliving.getBbWidth() * 2.0F + entityliving1.getBbWidth());

        return d0 <= d1;
    }

    public static boolean isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(LivingEntity entityliving, LivingEntity entityliving1, double d0) {
        Optional<LivingEntity> optional = entityliving.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);

        if (!optional.isPresent()) {
            return false;
        } else {
            double d1 = entityliving.distanceToSqr(((LivingEntity) optional.get()).position());
            double d2 = entityliving.distanceToSqr(entityliving1.position());

            return d2 > d1 + d0 * d0;
        }
    }

    public static boolean canSee(LivingEntity entityliving, LivingEntity entityliving1) {
        Brain<?> behaviorcontroller = entityliving.getBrain();

        return !behaviorcontroller.hasMemoryValue(MemoryModuleType.VISIBLE_LIVING_ENTITIES) ? false : ((List) behaviorcontroller.getMemory(MemoryModuleType.VISIBLE_LIVING_ENTITIES).get()).contains(entityliving1);
    }

    public static LivingEntity getNearestTarget(LivingEntity entityliving, Optional<LivingEntity> optional, LivingEntity entityliving1) {
        return !optional.isPresent() ? entityliving1 : getTargetNearestMe(entityliving, (LivingEntity) optional.get(), entityliving1);
    }

    public static LivingEntity getTargetNearestMe(LivingEntity entityliving, LivingEntity entityliving1, LivingEntity entityliving2) {
        Vec3 vec3d = entityliving1.position();
        Vec3 vec3d1 = entityliving2.position();

        return entityliving.distanceToSqr(vec3d) < entityliving.distanceToSqr(vec3d1) ? entityliving1 : entityliving2;
    }

    public static Optional<LivingEntity> getLivingEntityFromUUIDMemory(LivingEntity entityliving, MemoryModuleType<UUID> memorymoduletype) {
        Optional<UUID> optional = entityliving.getBrain().getMemory(memorymoduletype);

        return optional.map((uuid) -> {
            return (LivingEntity) ((ServerLevel) entityliving.level).getEntity(uuid);
        });
    }

    public static Stream<Villager> getNearbyVillagersWithCondition(Villager entityvillager, Predicate<Villager> predicate) {
        return (Stream) entityvillager.getBrain().getMemory(MemoryModuleType.LIVING_ENTITIES).map((list) -> {
            return list.stream().filter((entityliving) -> {
                return entityliving instanceof Villager && entityliving != entityvillager;
            }).map((entityliving) -> {
                return (Villager) entityliving;
            }).filter(LivingEntity::isAlive).filter(predicate);
        }).orElseGet(Stream::empty);
    }
}
