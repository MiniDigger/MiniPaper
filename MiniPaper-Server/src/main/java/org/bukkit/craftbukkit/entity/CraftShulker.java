package org.bukkit.craftbukkit.entity;

import org.bukkit.DyeColor;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;

public class CraftShulker extends CraftGolem implements Shulker {

    public CraftShulker(CraftServer server, net.minecraft.world.entity.monster.Shulker entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "CraftShulker";
    }

    @Override
    public EntityType getType() {
        return EntityType.SHULKER;
    }

    @Override
    public net.minecraft.world.entity.monster.Shulker getHandle() {
        return (net.minecraft.world.entity.monster.Shulker) entity;
    }

    @Override
    public DyeColor getColor() {
        return DyeColor.getByWoolData(getHandle().getEntityData().get(net.minecraft.world.entity.monster.Shulker.DATA_COLOR_ID));
    }

    @Override
    public void setColor(DyeColor color) {
        getHandle().getEntityData().set(net.minecraft.world.entity.monster.Shulker.DATA_COLOR_ID, (color == null) ? 16 : color.getWoolData());
    }
}
