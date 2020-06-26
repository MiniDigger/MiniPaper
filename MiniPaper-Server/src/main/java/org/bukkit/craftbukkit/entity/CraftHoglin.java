package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;

public class CraftHoglin extends CraftAnimals implements Hoglin {

    public CraftHoglin(CraftServer server, net.minecraft.world.entity.monster.hoglin.Hoglin entity) {
        super(server, entity);
    }

    @Override
    public net.minecraft.world.entity.monster.hoglin.Hoglin getHandle() {
        return (net.minecraft.world.entity.monster.hoglin.Hoglin) entity;
    }

    @Override
    public String toString() {
        return "CraftHoglin";
    }

    @Override
    public EntityType getType() {
        return EntityType.HOGLIN;
    }
}
