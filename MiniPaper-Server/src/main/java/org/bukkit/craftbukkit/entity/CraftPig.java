package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;

public class CraftPig extends CraftAnimals implements Pig {
    public CraftPig(CraftServer server, net.minecraft.world.entity.animal.Pig entity) {
        super(server, entity);
    }

    @Override
    public boolean hasSaddle() {
        return getHandle().isSaddled();
    }

    @Override
    public void setSaddle(boolean saddled) {
        getHandle().steering.setSaddle(saddled);
    }

    @Override
    public net.minecraft.world.entity.animal.Pig getHandle() {
        return (net.minecraft.world.entity.animal.Pig) entity;
    }

    @Override
    public String toString() {
        return "CraftPig";
    }

    @Override
    public EntityType getType() {
        return EntityType.PIG;
    }
}
