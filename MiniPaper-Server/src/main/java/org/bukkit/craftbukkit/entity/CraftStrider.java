package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Strider;

public class CraftStrider extends CraftAnimals implements Strider {

    public CraftStrider(CraftServer server, net.minecraft.world.entity.monster.Strider entity) {
        super(server, entity);
    }

    @Override
    public net.minecraft.world.entity.monster.Strider getHandle() {
        return (net.minecraft.world.entity.monster.Strider) entity;
    }

    @Override
    public String toString() {
        return "CraftStrider";
    }

    @Override
    public EntityType getType() {
        return EntityType.STRIDER;
    }
}
