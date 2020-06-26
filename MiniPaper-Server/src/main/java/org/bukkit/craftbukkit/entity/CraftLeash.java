package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LeashHitch;

public class CraftLeash extends CraftHanging implements LeashHitch {
    public CraftLeash(CraftServer server, LeashFenceKnotEntity entity) {
        super(server, entity);
    }

    @Override
    public LeashFenceKnotEntity getHandle() {
        return (LeashFenceKnotEntity) entity;
    }

    @Override
    public String toString() {
        return "CraftLeash";
    }

    @Override
    public EntityType getType() {
        return EntityType.LEASH_HITCH;
    }
}