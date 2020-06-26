package org.bukkit.craftbukkit.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.Motive;
import org.bukkit.Art;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftArt;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Painting;

public class CraftPainting extends CraftHanging implements Painting {

    public CraftPainting(CraftServer server, net.minecraft.world.entity.decoration.Painting entity) {
        super(server, entity);
    }

    @Override
    public Art getArt() {
        Motive art = getHandle().motive;
        return CraftArt.NotchToBukkit(art);
    }

    @Override
    public boolean setArt(Art art) {
        return setArt(art, false);
    }

    @Override
    public boolean setArt(Art art, boolean force) {
        net.minecraft.world.entity.decoration.Painting painting = this.getHandle();
        Motive oldArt = painting.motive;
        painting.motive = CraftArt.BukkitToNotch(art);
        painting.setDirection(painting.getDirection());
        if (!force && !painting.survives()) {
            // Revert painting since it doesn't fit
            painting.motive = oldArt;
            painting.setDirection(painting.getDirection());
            return false;
        }
        this.update();
        return true;
    }

    @Override
    public boolean setFacingDirection(BlockFace face, boolean force) {
        if (super.setFacingDirection(face, force)) {
            update();
            return true;
        }

        return false;
    }

    private void update() {
        ServerLevel world = ((CraftWorld) getWorld()).getHandle();
        net.minecraft.world.entity.decoration.Painting painting = net.minecraft.world.entity.EntityType.PAINTING.create(world);
        painting.pos = getHandle().pos;
        painting.motive = getHandle().motive;
        painting.setDirection(getHandle().getDirection());
        getHandle().remove();
        getHandle().hurtMarked = true; // because this occurs when the painting is broken, so it might be important
        world.addFreshEntity(painting);
        this.entity = painting;
    }

    @Override
    public net.minecraft.world.entity.decoration.Painting getHandle() {
        return (net.minecraft.world.entity.decoration.Painting) entity;
    }

    @Override
    public String toString() {
        return "CraftPainting{art=" + getArt() + "}";
    }

    @Override
    public EntityType getType() {
        return EntityType.PAINTING;
    }
}
