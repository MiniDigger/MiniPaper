package net.minecraft.world;

import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.bukkit.craftbukkit.entity.CraftHumanEntity; // CraftBukkit

public interface Container extends Clearable {

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int i);

    ItemStack removeItem(int i, int j);

    ItemStack removeItemNoUpdate(int i);

    void setItem(int i, ItemStack itemstack);

    int getMaxStackSize(); // CraftBukkit

    void setChanged();

    boolean stillValid(Player entityhuman);

    default void startOpen(Player entityhuman) {}

    default void stopOpen(Player entityhuman) {}

    default boolean canPlaceItem(int i, ItemStack itemstack) {
        return true;
    }

    default int countItem(Item item) {
        int i = 0;

        for (int j = 0; j < this.getContainerSize(); ++j) {
            ItemStack itemstack = this.getItem(j);

            if (itemstack.getItem().equals(item)) {
                i += itemstack.getCount();
            }
        }

        return i;
    }

    default boolean hasAnyOf(Set<Item> set) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (set.contains(itemstack.getItem()) && itemstack.getCount() > 0) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start
    java.util.List<ItemStack> getContents();

    void onOpen(CraftHumanEntity who);

    void onClose(CraftHumanEntity who);

    java.util.List<org.bukkit.entity.HumanEntity> getViewers();

    org.bukkit.inventory.InventoryHolder getOwner();

    void setMaxStackSize(int size);

    org.bukkit.Location getLocation();

    default Recipe getCurrentRecipe() {
        return null;
    }

    default void setCurrentRecipe(Recipe recipe) {
    }

    int MAX_STACK = 64;
    // CraftBukkit end
}
