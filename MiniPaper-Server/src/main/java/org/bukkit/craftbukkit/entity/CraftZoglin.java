package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zoglin;

public class CraftZoglin extends CraftMonster implements Zoglin {

    public CraftZoglin(CraftServer server, net.minecraft.world.entity.monster.Zoglin entity) {
        super(server, entity);
    }

    @Override
    public net.minecraft.world.entity.monster.Zoglin getHandle() {
        return (net.minecraft.world.entity.monster.Zoglin) entity;
    }

    @Override
    public String toString() {
        return "CraftZoglin";
    }

    @Override
    public EntityType getType() {
        return EntityType.ZOGLIN;
    }
}
