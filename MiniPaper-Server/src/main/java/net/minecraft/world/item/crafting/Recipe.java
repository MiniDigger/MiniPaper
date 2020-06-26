package net.minecraft.world.item.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface Recipe<C extends Container> {

    boolean matches(C c0, Level world);

    ItemStack assemble(C c0);

    ItemStack getResultItem();

    default NonNullList<ItemStack> getRemainingItems(C c0) {
        NonNullList<ItemStack> nonnulllist = NonNullList.withSize(c0.getContainerSize(), ItemStack.EMPTY);

        for (int i = 0; i < nonnulllist.size(); ++i) {
            Item item = c0.getItem(i).getItem();

            if (item.hasCraftingRemainingItem()) {
                nonnulllist.set(i, new ItemStack(item.getCraftingRemainingItem()));
            }
        }

        return nonnulllist;
    }

    default NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    default boolean isSpecial() {
        return false;
    }

    ResourceLocation getId();

    RecipeSerializer<?> getRecipeSerializer();

    RecipeType<?> getType();

    org.bukkit.inventory.Recipe toBukkitRecipe(); // CraftBukkit
}
