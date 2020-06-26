package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
// CraftBukkit end

public class ResetProfession extends Behavior<Villager> {

    public ResetProfession() {
        super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_ABSENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel worldserver, Villager entityvillager) {
        VillagerData villagerdata = entityvillager.getVillagerData();

        return villagerdata.getProfession() != VillagerProfession.NONE && villagerdata.getProfession() != VillagerProfession.NITWIT && entityvillager.getVillagerXp() == 0 && villagerdata.getLevel() <= 1;
    }

    protected void start(ServerLevel worldserver, Villager entityvillager, long i) {
        // CraftBukkit start
        VillagerCareerChangeEvent event = CraftEventFactory.callVillagerCareerChangeEvent(entityvillager, CraftVillager.nmsToBukkitProfession(VillagerProfession.NONE), VillagerCareerChangeEvent.ChangeReason.EMPLOYED);
        if (event.isCancelled()) {
            return;
        }

        entityvillager.setVillagerData(entityvillager.getVillagerData().setProfession(CraftVillager.bukkitToNmsProfession(event.getProfession())));
        // CraftBukkit end
        entityvillager.refreshBrain(worldserver);
    }
}
