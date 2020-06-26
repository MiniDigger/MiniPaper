package org.bukkit.craftbukkit.inventory;

import net.minecraft.world.Container;
import org.bukkit.inventory.SmithingInventory;

public class CraftInventorySmithing extends CraftResultInventory implements SmithingInventory {

    public CraftInventorySmithing(Container inventory, Container resultInventory) {
        super(inventory, resultInventory);
    }
}
