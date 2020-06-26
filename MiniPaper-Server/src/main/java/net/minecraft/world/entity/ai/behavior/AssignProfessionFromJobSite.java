package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
// CraftBukkit end

public class AssignProfessionFromJobSite extends Behavior<Villager> {

    public AssignProfessionFromJobSite() {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel worldserver, Villager entityvillager) {
        BlockPos blockposition = ((GlobalPos) entityvillager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get()).pos();

        return blockposition.closerThan((Position) entityvillager.position(), 2.0D) || entityvillager.assignProfessionWhenSpawned();
    }

    protected void start(ServerLevel worldserver, Villager entityvillager, long i) {
        GlobalPos globalpos = (GlobalPos) entityvillager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get();

        entityvillager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        entityvillager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalpos); // CraftBukkit - decompile error
        if (entityvillager.getVillagerData().getProfession() == VillagerProfession.NONE) {
            MinecraftServer minecraftserver = worldserver.getServer();

            Optional.ofNullable(minecraftserver.getWorldServer(globalpos.getDimensionManager())).flatMap((worldserver1) -> {
                return worldserver1.getPoiManager().getType(globalpos.pos());
            }).flatMap((villageplacetype) -> {
                return Registry.VILLAGER_PROFESSION.stream().filter((villagerprofession) -> {
                    return villagerprofession.getJobPoiType() == villageplacetype;
                }).findFirst();
            }).ifPresent((villagerprofession) -> {
                // CraftBukkit start - Fire VillagerCareerChangeEvent where Villager gets employed
                VillagerCareerChangeEvent event = CraftEventFactory.callVillagerCareerChangeEvent(entityvillager, CraftVillager.nmsToBukkitProfession(villagerprofession), VillagerCareerChangeEvent.ChangeReason.EMPLOYED);
                if (event.isCancelled()) {
                    return;
                }

                entityvillager.setVillagerData(entityvillager.getVillagerData().setProfession(CraftVillager.bukkitToNmsProfession(event.getProfession())));
                // CraftBukkit end
                entityvillager.refreshBrain(worldserver);
            });
        }
    }
}
