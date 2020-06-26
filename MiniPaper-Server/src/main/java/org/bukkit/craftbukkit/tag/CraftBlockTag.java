package org.bukkit.craftbukkit.tag;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.SynchronizableTagCollection;
import net.minecraft.world.level.block.Block;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;

public class CraftBlockTag extends CraftTag<Block, Material> {

    public CraftBlockTag(SynchronizableTagCollection<Block> registry, ResourceLocation tag) {
        super(registry, tag);
    }

    @Override
    public boolean isTagged(Material item) {
        return getHandle().contains(CraftMagicNumbers.getBlock(item));
    }

    @Override
    public Set<Material> getValues() {
        return Collections.unmodifiableSet(getHandle().getValues().stream().map((block) -> CraftMagicNumbers.getMaterial(block)).collect(Collectors.toSet()));
    }
}
