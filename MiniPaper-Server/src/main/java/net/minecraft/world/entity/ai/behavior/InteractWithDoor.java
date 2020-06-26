package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

public class InteractWithDoor extends Behavior<LivingEntity> {

    public InteractWithDoor() {
        super(ImmutableMap.of(MemoryModuleType.PATH, MemoryStatus.VALUE_PRESENT, MemoryModuleType.INTERACTABLE_DOORS, MemoryStatus.VALUE_PRESENT, MemoryModuleType.OPENED_DOORS, MemoryStatus.REGISTERED));
    }

    @Override
    protected void start(ServerLevel worldserver, LivingEntity entityliving, long i) {
        Brain<?> behaviorcontroller = entityliving.getBrain();
        Path pathentity = (Path) behaviorcontroller.getMemory(MemoryModuleType.PATH).get();
        List<GlobalPos> list = (List) behaviorcontroller.getMemory(MemoryModuleType.INTERACTABLE_DOORS).get();
        List<BlockPos> list1 = (List) pathentity.getNodes().stream().map((pathpoint) -> {
            return new BlockPos(pathpoint.x, pathpoint.y, pathpoint.z);
        }).collect(Collectors.toList());
        Set<BlockPos> set = this.getDoorsThatAreOnMyPath(worldserver, list, list1);
        int j = pathentity.getIndex() - 1;

        this.openOrCloseDoors(worldserver, list1, set, j, entityliving, behaviorcontroller);
    }

    private Set<BlockPos> getDoorsThatAreOnMyPath(ServerLevel worldserver, List<GlobalPos> list, List<BlockPos> list1) {
        Stream stream = list.stream().filter((globalpos) -> {
            return globalpos.getDimensionManager() == worldserver.getDimensionKey();
        }).map(GlobalPos::pos);

        list1.getClass();
        return (Set) stream.filter(list1::contains).collect(Collectors.toSet());
    }

    private void openOrCloseDoors(ServerLevel worldserver, List<BlockPos> list, Set<BlockPos> set, int i, LivingEntity entityliving, Brain<?> behaviorcontroller) {
        set.forEach((blockposition) -> {
            int j = list.indexOf(blockposition);
            BlockState iblockdata = worldserver.getType(blockposition);
            Block block = iblockdata.getBlock();

            if (BlockTags.WOODEN_DOORS.contains(block) && block instanceof DoorBlock) {
                boolean flag = j >= i;

                // CraftBukkit start - entities opening doors
                org.bukkit.event.entity.EntityInteractEvent event = new org.bukkit.event.entity.EntityInteractEvent(entityliving.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(entityliving.level, blockposition));
                entityliving.level.getServerOH().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
                // CaftBukkit end
                ((DoorBlock) block).setOpen(worldserver, blockposition, flag);
                GlobalPos globalpos = GlobalPos.create(worldserver.getDimensionKey(), blockposition);

                if (!behaviorcontroller.getMemory(MemoryModuleType.OPENED_DOORS).isPresent() && flag) {
                    behaviorcontroller.setMemory(MemoryModuleType.OPENED_DOORS, Sets.newHashSet(new GlobalPos[]{globalpos})); // CraftBukkit - decompile error
                } else {
                    behaviorcontroller.getMemory(MemoryModuleType.OPENED_DOORS).ifPresent((set1) -> {
                        if (flag) {
                            set1.add(globalpos);
                        } else {
                            set1.remove(globalpos);
                        }

                    });
                }
            }

        });
        closeAllOpenedDoors(worldserver, list, i, entityliving, behaviorcontroller);
    }

    public static void closeAllOpenedDoors(ServerLevel worldserver, List<BlockPos> list, int i, LivingEntity entityliving, Brain<?> behaviorcontroller) {
        behaviorcontroller.getMemory(MemoryModuleType.OPENED_DOORS).ifPresent((set) -> {
            Iterator iterator = set.iterator();

            while (iterator.hasNext()) {
                GlobalPos globalpos = (GlobalPos) iterator.next();
                BlockPos blockposition = globalpos.pos();
                int j = list.indexOf(blockposition);

                if (worldserver.getDimensionKey() != globalpos.getDimensionManager()) {
                    iterator.remove();
                } else {
                    BlockState iblockdata = worldserver.getType(blockposition);
                    Block block = iblockdata.getBlock();

                    if (BlockTags.WOODEN_DOORS.contains(block) && block instanceof DoorBlock && j < i && blockposition.closerThan((Position) entityliving.position(), 4.0D)) {
                        ((DoorBlock) block).setOpen(worldserver, blockposition, false);
                        iterator.remove();
                    }
                }
            }

        });
    }
}
