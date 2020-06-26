package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import java.util.UUID;
import net.minecraft.world.entity.animal.Animal;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Animals;

public class CraftAnimals extends CraftAgeable implements Animals {

    public CraftAnimals(CraftServer server, Animal entity) {
        super(server, entity);
    }

    @Override
    public Animal getHandle() {
        return (Animal) entity;
    }

    @Override
    public String toString() {
        return "CraftAnimals";
    }

    @Override
    public UUID getBreedCause() {
        return getHandle().loveCause;
    }

    @Override
    public void setBreedCause(UUID uuid) {
        getHandle().loveCause = uuid;
    }

    @Override
    public boolean isLoveMode() {
        return getHandle().isInLove();
    }

    @Override
    public void setLoveModeTicks(int ticks) {
        Preconditions.checkArgument(ticks >= 0, "Love mode ticks must be positive or 0");
        getHandle().setInLoveTime(ticks);
    }

    @Override
    public int getLoveModeTicks() {
        return getHandle().inLove;
    }
}