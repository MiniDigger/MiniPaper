package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

public class VillagerMakeLove extends Behavior<Villager> {

    private long birthTimestamp;

    public VillagerMakeLove() {
        super(ImmutableMap.of(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT), 350, 350);
    }

    protected boolean checkExtraStartConditions(ServerLevel worldserver, Villager entityvillager) {
        return this.isBreedingPossible(entityvillager);
    }

    protected boolean b(ServerLevel worldserver, Villager entityvillager, long i) {
        return i <= this.birthTimestamp && this.isBreedingPossible(entityvillager);
    }

    protected void a(ServerLevel worldserver, Villager entityvillager, long i) {
        AgableMob entityageable = (AgableMob) entityvillager.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();

        BehaviorUtils.lockGazeAndWalkToEachOther(entityvillager, entityageable, 0.5F);
        worldserver.broadcastEntityEvent(entityageable, (byte) 18);
        worldserver.broadcastEntityEvent(entityvillager, (byte) 18);
        int j = 275 + entityvillager.getRandom().nextInt(50);

        this.birthTimestamp = i + (long) j;
    }

    protected void stop(ServerLevel worldserver, Villager entityvillager, long i) {
        Villager entityvillager1 = (Villager) entityvillager.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();

        if (entityvillager.distanceToSqr((Entity) entityvillager1) <= 5.0D) {
            BehaviorUtils.lockGazeAndWalkToEachOther(entityvillager, entityvillager1, 0.5F);
            if (i >= this.birthTimestamp) {
                entityvillager.eatAndDigestFood();
                entityvillager1.eatAndDigestFood();
                this.tryToGiveBirth(worldserver, entityvillager, entityvillager1);
            } else if (entityvillager.getRandom().nextInt(35) == 0) {
                worldserver.broadcastEntityEvent(entityvillager1, (byte) 12);
                worldserver.broadcastEntityEvent(entityvillager, (byte) 12);
            }

        }
    }

    private void tryToGiveBirth(ServerLevel worldserver, Villager entityvillager, Villager entityvillager1) {
        Optional<BlockPos> optional = this.takeVacantBed(worldserver, entityvillager);

        if (!optional.isPresent()) {
            worldserver.broadcastEntityEvent(entityvillager1, (byte) 13);
            worldserver.broadcastEntityEvent(entityvillager, (byte) 13);
        } else {
            Optional<Villager> optional1 = this.breed(entityvillager, entityvillager1);

            if (optional1.isPresent()) {
                this.giveBedToChild(worldserver, (Villager) optional1.get(), (BlockPos) optional.get());
            } else {
                worldserver.getPoiManager().release((BlockPos) optional.get());
                DebugPackets.sendPoiTicketCountPacket(worldserver, (BlockPos) optional.get());
            }
        }

    }

    protected void tick(ServerLevel worldserver, Villager entityvillager, long i) {
        entityvillager.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
    }

    private boolean isBreedingPossible(Villager entityvillager) {
        Brain<Villager> behaviorcontroller = entityvillager.getBrain();
        Optional<AgableMob> optional = behaviorcontroller.getMemory(MemoryModuleType.BREED_TARGET).filter((entityageable) -> {
            return entityageable.getType() == EntityType.VILLAGER;
        });

        return !optional.isPresent() ? false : BehaviorUtils.targetIsValid(behaviorcontroller, MemoryModuleType.BREED_TARGET, EntityType.VILLAGER) && entityvillager.canBreed() && ((AgableMob) optional.get()).canBreed();
    }

    private Optional<BlockPos> takeVacantBed(ServerLevel worldserver, Villager entityvillager) {
        return worldserver.getPoiManager().take(PoiType.HOME.getPredicate(), (blockposition) -> {
            return this.canReach(entityvillager, blockposition);
        }, entityvillager.blockPosition(), 48);
    }

    private boolean canReach(Villager entityvillager, BlockPos blockposition) {
        Path pathentity = entityvillager.getNavigation().createPath(blockposition, PoiType.HOME.getValidRange());

        return pathentity != null && pathentity.canReach();
    }

    private Optional<Villager> breed(Villager entityvillager, Villager entityvillager1) {
        Villager entityvillager2 = entityvillager.getBreedOffspring(entityvillager1);
        // CraftBukkit start - call EntityBreedEvent
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityvillager2, entityvillager, entityvillager1, null, null, 0).isCancelled()) {
            return Optional.empty();
        }
        // CraftBukkit end

        if (entityvillager2 == null) {
            return Optional.empty();
        } else {
            entityvillager.setAge(6000);
            entityvillager1.setAge(6000);
            entityvillager2.setAge(-24000);
            entityvillager2.moveTo(entityvillager.getX(), entityvillager.getY(), entityvillager.getZ(), 0.0F, 0.0F);
            entityvillager.level.addEntity(entityvillager2, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - added SpawnReason
            entityvillager.level.broadcastEntityEvent(entityvillager2, (byte) 12);
            return Optional.of(entityvillager2);
        }
    }

    private void giveBedToChild(ServerLevel worldserver, Villager entityvillager, BlockPos blockposition) {
        GlobalPos globalpos = GlobalPos.create(worldserver.getDimensionKey(), blockposition);

        entityvillager.getBrain().setMemory(MemoryModuleType.HOME, globalpos); // CraftBukkit - decompile error
    }
}
