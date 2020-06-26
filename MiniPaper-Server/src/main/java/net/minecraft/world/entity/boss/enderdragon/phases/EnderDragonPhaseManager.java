package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
// CraftBukkit end

public class EnderDragonPhaseManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final EnderDragon dragon;
    private final DragonPhaseInstance[] phases = new DragonPhaseInstance[EnderDragonPhase.getCount()];
    private DragonPhaseInstance currentPhase;

    public EnderDragonPhaseManager(EnderDragon entityenderdragon) {
        this.dragon = entityenderdragon;
        this.setPhase(EnderDragonPhase.HOVERING);
    }

    public void setPhase(EnderDragonPhase<?> dragoncontrollerphase) {
        if (this.currentPhase == null || dragoncontrollerphase != this.currentPhase.getPhase()) {
            if (this.currentPhase != null) {
                this.currentPhase.end();
            }

            // CraftBukkit start - Call EnderDragonChangePhaseEvent
            EnderDragonChangePhaseEvent event = new EnderDragonChangePhaseEvent(
                    (CraftEnderDragon) this.dragon.getBukkitEntity(),
                    (this.currentPhase == null) ? null : CraftEnderDragon.getBukkitPhase(this.currentPhase.getPhase()),
                    CraftEnderDragon.getBukkitPhase(dragoncontrollerphase)
            );
            this.dragon.level.getServerOH().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            dragoncontrollerphase = CraftEnderDragon.getMinecraftPhase(event.getNewPhase());
            // CraftBukkit end

            this.currentPhase = this.getPhase(dragoncontrollerphase);
            if (!this.dragon.level.isClientSide) {
                this.dragon.getEntityData().set(EnderDragon.DATA_PHASE, dragoncontrollerphase.getId());
            }

            EnderDragonPhaseManager.LOGGER.debug("Dragon is now in phase {} on the {}", dragoncontrollerphase, this.dragon.level.isClientSide ? "client" : "server");
            this.currentPhase.begin();
        }
    }

    public DragonPhaseInstance getCurrentPhase() {
        return this.currentPhase;
    }

    public <T extends DragonPhaseInstance> T getPhase(EnderDragonPhase<T> dragoncontrollerphase) {
        int i = dragoncontrollerphase.getId();

        if (this.phases[i] == null) {
            this.phases[i] = dragoncontrollerphase.createInstance(this.dragon);
        }

        return (T) this.phases[i]; // CraftBukkit - decompile error
    }
}
